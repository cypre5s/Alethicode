"""暴露内部 graph API 的 FastAPI 入口。"""

from __future__ import annotations

import asyncio
import json
import logging
import uuid
from contextlib import asynccontextmanager
from datetime import datetime, timezone
from typing import Any

from fastapi import Depends, FastAPI, HTTPException, Request
from sse_starlette.sse import EventSourceResponse
from pydantic import BaseModel

from app.graph.builder import build_tutor_graph
from app.graph.runtime_events import ServerEvent, build_runtime_event
from app.auth import require_internal_service_key
from app.clients.java_tools_client import JavaToolsClient
from app.clients.llm_client import LlmClient
from app.nodes.coach_plan import build_plan_payload

from cachetools import TTLCache

logger = logging.getLogger("tutor_graph.main")

TERMINAL_SERVER_EVENTS = frozenset({"TASK_COMPLETED", "TASK_FAILED", "TASK_EXPIRED"})
INTERRUPT_TIMEOUT_SECONDS = 1800
EVENT_CLEANUP_DELAY_SECONDS = 300

# run 运行态使用 TTLCache 控制上限，避免异常未终止 run 长期堆积导致进程 OOM。
_MAX_TRACKED_RUNS = 10_000
_RUN_BOOKKEEPING_TTL_SECONDS = 3 * 60 * 60  # 3h — strictly longer than interrupt timeout + buffer

_graph = None
_java_client: JavaToolsClient | None = None
_llm_client: LlmClient | None = None
_active_runs: TTLCache = TTLCache(maxsize=_MAX_TRACKED_RUNS, ttl=_RUN_BOOKKEEPING_TTL_SECONDS)
_run_events: TTLCache = TTLCache(maxsize=_MAX_TRACKED_RUNS, ttl=_RUN_BOOKKEEPING_TTL_SECONDS)
_run_threads: TTLCache = TTLCache(maxsize=_MAX_TRACKED_RUNS, ttl=_RUN_BOOKKEEPING_TTL_SECONDS)
_background_tasks: set[asyncio.Task] = set()
_active_runs_lock = asyncio.Lock()


@asynccontextmanager
async def lifespan(app: FastAPI):
    """初始化外部客户端和 checkpointer。

    生产必须显式使用 PostgreSQL checkpointer，测试环境才允许选择 memory 模式。
    """
    global _graph, _java_client, _llm_client
    from app import config

    _java_client = JavaToolsClient(config.JAVA_TOOL_BASE_URL, config.INTERNAL_SERVICE_KEY)
    _llm_client = LlmClient(
        provider=config.LLM_PROVIDER,
        model=config.LLM_MODEL,
        api_key=config.LLM_API_KEY,
        base_url=config.LLM_BASE_URL,
        temperature=config.LLM_TEMPERATURE,
    )

    checkpointer_cm = None
    checkpointer = None
    mode = config.CHECKPOINTER_MODE
    if mode == "postgres":
        from langgraph.checkpoint.postgres.aio import AsyncPostgresSaver
        checkpointer_cm = AsyncPostgresSaver.from_conn_string(config.DATABASE_URI)
        checkpointer = await checkpointer_cm.__aenter__()
        await checkpointer.setup()
        logger.info("tutor_graph: using AsyncPostgresSaver checkpointer")
    elif mode == "memory":
        from langgraph.checkpoint.memory import MemorySaver
        checkpointer = MemorySaver()
        logger.warning(
            "tutor_graph: using in-memory checkpointer (TUTOR_GRAPH_CHECKPOINTER=memory); "
            "state will NOT survive restart — never enable in production"
        )
    else:
        raise RuntimeError(
            f"Unsupported TUTOR_GRAPH_CHECKPOINTER={mode!r} (expected 'postgres' or 'memory')"
        )

    _graph = build_tutor_graph(
        java_client=_java_client,
        llm_client=_llm_client,
        checkpointer=checkpointer,
    )
    try:
        yield
    finally:
        # SIGTERM 安全关闭顺序：取消后台任务、清理运行态、关闭 HTTP 客户端和 checkpointer。
        logger.info("tutor_graph: beginning graceful shutdown; active_runs=%d pending_tasks=%d",
                    len(_active_runs), len(_background_tasks))
        for task in list(_background_tasks):
            task.cancel()
        # 给取消中的任务短暂时间传播 CancelledError 并清理资源。
        if _background_tasks:
            try:
                await asyncio.wait(_background_tasks, timeout=5.0)
            except Exception as exc:  # pragma: no cover - 防御性关闭路径
                logger.debug("shutdown wait failed: %s", exc)
        if _java_client:
            await _java_client.close()
        if checkpointer_cm is not None:
            await checkpointer_cm.__aexit__(None, None, None)
        logger.info("tutor_graph: shutdown complete")


app = FastAPI(title="tutor-graph", lifespan=lifespan)

# app 构造后再注册 OpenTelemetry，确保 FastAPIInstrumentor 能正确挂载 middleware。
from app.observability import configure_otel  # noqa: E402

configure_otel(app)


def _track_task(task: asyncio.Task) -> None:
    """持有后台任务强引用，避免任务过早被 GC 回收。"""
    _background_tasks.add(task)
    task.add_done_callback(_background_tasks.discard)


@app.get("/health")
async def health():
    return {"status": "ok"}


class CreateThreadRequest(BaseModel):
    session_id: str
    user_id: int
    problem_id: int
    language: str
    context: dict | None = None


class CreateThreadResponse(BaseModel):
    thread_id: str
    session_id: str


# 单实例 thread 级上下文缓存；重启后调用方需要重新建立上下文。
_thread_contexts: dict[str, dict] = {}


@app.post("/internal/graph/threads", response_model=CreateThreadResponse)
async def create_thread(req: CreateThreadRequest, _auth: None = Depends(require_internal_service_key)):
    thread_id = f"thread_{uuid.uuid4().hex[:16]}"
    if req.context:
        if req.context.get("source") == "classroom_assignment" and "anti_cheating" not in req.context:
            raise HTTPException(status_code=422, detail="classroom_assignment context must declare anti_cheating")
        _thread_contexts[thread_id] = dict(req.context)
    return CreateThreadResponse(thread_id=thread_id, session_id=req.session_id)


class CreateRunRequest(BaseModel):
    session_id: str
    thread_id: str
    run_id: str | None = None
    user_id: int
    problem_id: int
    language: str
    event: str
    event_data: dict = {}


class CreateRunResponse(BaseModel):
    run_id: str
    session_id: str
    thread_id: str
    runtime_state: str


@app.post("/internal/graph/runs", response_model=CreateRunResponse)
async def create_run(req: CreateRunRequest, _auth: None = Depends(require_internal_service_key)):
    run_id = req.run_id or f"run_{uuid.uuid4().hex[:16]}"

    async with _active_runs_lock:
        if req.session_id in _active_runs:
            raise HTTPException(status_code=409, detail="Session already has an active run")
        _active_runs[req.session_id] = run_id
        _run_events[run_id] = []
        _run_threads[run_id] = req.thread_id

    _run_events[run_id].append(
        build_runtime_event(
            server_event=ServerEvent.TASK_QUEUED,
            session_id=req.session_id,
            run_id=run_id,
            thread_id=req.thread_id,
            runtime_state="QUEUED",
            client_event=req.event,
        )
    )

    _track_task(asyncio.create_task(_execute_run(req, run_id)))

    return CreateRunResponse(
        run_id=run_id,
        session_id=req.session_id,
        thread_id=req.thread_id,
        runtime_state="QUEUED",
    )


async def _is_thread_interrupted(thread_id: str) -> bool:
    try:
        state = await _graph.aget_state({"configurable": {"thread_id": thread_id}})
        return bool(state and state.next)
    except Exception as e:
        logger.debug("interrupt-check failed: %s", e)
        return False


async def _execute_run(req: CreateRunRequest, run_id: str):
    thread_config = {"configurable": {"thread_id": req.thread_id}}
    try:
        _run_events[run_id].append(
            build_runtime_event(
                server_event=ServerEvent.TASK_STARTED,
                session_id=req.session_id,
                run_id=run_id,
                thread_id=req.thread_id,
                runtime_state="RUNNING",
                client_event=req.event,
            )
        )

        existing_state = None
        try:
            snapshot = await _graph.aget_state(thread_config)
            if snapshot and snapshot.values:
                existing_state = dict(snapshot.values)
        except Exception as e:
            logger.debug("no existing state for thread %s: %s", req.thread_id, e)

        thread_context = _thread_contexts.get(req.thread_id, {})

        now = datetime.now(timezone.utc).isoformat()
        if existing_state and existing_state.get("session_id"):
            input_state = {
                **existing_state,
                "run_id": run_id,
                "client_event": req.event.upper(),
                "event_data": req.event_data,
                "behavior_metrics": req.event_data.get(
                    "behavior_metrics", existing_state.get("behavior_metrics", {})
                ),
                "context": thread_context or existing_state.get("context", {}),
                "runtime_state": "QUEUED",
                "failure_bucket": None,
                "last_error": None,
                "updated_at": now,
            }
        else:
            input_state = {
                "session_id": req.session_id,
                "thread_id": req.thread_id,
                "run_id": run_id,
                "user_id": req.user_id,
                "problem_id": req.problem_id,
                "language": req.language,
                "client_event": req.event.upper(),
                "event_data": req.event_data,
                "behavior_metrics": req.event_data.get("behavior_metrics", {}),
                "node_outputs": {},
                "evidence_pack": {},
                "learner_state": {},
                "context": thread_context,
                "available_actions": [],
                "active_plan": {},
                "plan_id": None,
                "plan_status": "idle",
                "current_step_index": None,
                "current_checkpoint": {},
                "last_student_evidence": {},
                "evidence_assessment": {},
                "remediation_depth": 0,
                "plan_recommendation": {},
                "recommendation_reason": "",
                "recommended_by": "",
                "trigger_features": {},
                "pending_human_action": "",
                "runtime_state": "QUEUED",
                "failure_bucket": None,
                "last_error": None,
                "side_effects": {},
                "execution_trace": [],
                "created_at": now,
                "updated_at": now,
            }

        result = await _graph.ainvoke(input_state, thread_config)

        if await _is_thread_interrupted(req.thread_id):
            post_state = await _graph.aget_state(thread_config)
            interrupt_state = post_state.values if post_state else result
            _run_events[run_id].append(
                build_runtime_event(
                    server_event=ServerEvent.APPROVAL_REQUESTED,
                    session_id=req.session_id,
                    run_id=run_id,
                    thread_id=req.thread_id,
                    trace_id=interrupt_state.get("trace_id", "") if isinstance(interrupt_state, dict) else "",
                    runtime_state="WAITING_HUMAN_APPROVAL",
                    client_event=req.event,
                    approval_state="pending",
                    data={
                        "phase": interrupt_state.get("current_phase", "") if isinstance(interrupt_state, dict) else "",
                        "node_outputs": interrupt_state.get("node_outputs", {}) if isinstance(interrupt_state, dict) else {},
                        "pending_human_action": "confirm_transfer",
                        "plan": build_plan_payload(interrupt_state if isinstance(interrupt_state, dict) else {}),
                        "recommendation_reason": interrupt_state.get("recommendation_reason", "") if isinstance(interrupt_state, dict) else "",
                    },
                )
            )
            _track_task(asyncio.create_task(_schedule_interrupt_timeout(req.session_id, run_id, req.thread_id)))
            return

        result_dict = result if isinstance(result, dict) else {}
        server_event = (
            ServerEvent.TASK_COMPLETED
            if result_dict.get("runtime_state") != "FAILED"
            else ServerEvent.TASK_FAILED
        )
        _run_events[run_id].append(
            build_runtime_event(
                server_event=server_event,
                session_id=req.session_id,
                run_id=run_id,
                thread_id=req.thread_id,
                trace_id=result_dict.get("trace_id", ""),
                runtime_state=result_dict.get("runtime_state", "COMPLETED"),
                client_event=req.event,
                failure_bucket=result_dict.get("failure_bucket"),
                data={
                    "phase": result_dict.get("current_phase", ""),
                    "node_outputs": result_dict.get("node_outputs", {}),
                    "available_actions": result_dict.get("available_actions", []),
                    "pending_human_action": result_dict.get("pending_human_action", ""),
                    "behavior_metrics": result_dict.get("behavior_metrics", {}),
                    "plan": build_plan_payload(result_dict),
                    "recommendation_reason": result_dict.get("recommendation_reason", ""),
                    "error": result_dict.get("last_error"),
                },
            )
        )
        async with _active_runs_lock:
            _active_runs.pop(req.session_id, None)
        _track_task(asyncio.create_task(_schedule_event_cleanup(run_id)))

    except Exception as e:
        logger.exception("Run %s failed", run_id)
        _run_events.setdefault(run_id, []).append(
            build_runtime_event(
                server_event=ServerEvent.TASK_FAILED,
                session_id=req.session_id,
                run_id=run_id,
                thread_id=req.thread_id,
                runtime_state="FAILED",
                failure_bucket="SYSTEM_ERROR",
                data={"error": str(e)},
            )
        )
        async with _active_runs_lock:
            _active_runs.pop(req.session_id, None)
        _track_task(asyncio.create_task(_schedule_event_cleanup(run_id)))


async def _schedule_event_cleanup(run_id: str, delay_seconds: int = EVENT_CLEANUP_DELAY_SECONDS):
    """延迟清理 run 事件，避免内存泄漏。"""
    try:
        await asyncio.sleep(delay_seconds)
    except asyncio.CancelledError:
        return
    _run_events.pop(run_id, None)
    _run_threads.pop(run_id, None)


async def _schedule_interrupt_timeout(session_id: str, run_id: str, thread_id: str,
                                       delay_seconds: int = INTERRUPT_TIMEOUT_SECONDS):
    """人工确认超时后释放 run 和会话占用。"""
    try:
        await asyncio.sleep(delay_seconds)
    except asyncio.CancelledError:
        return

    if not await _is_thread_interrupted(thread_id):
        return

    _run_events.setdefault(run_id, []).append(
        build_runtime_event(
            server_event=ServerEvent.TASK_EXPIRED,
            session_id=session_id,
            run_id=run_id,
            thread_id=thread_id,
            runtime_state="EXPIRED",
            data={"reason": "interrupt_timeout"},
        )
    )
    async with _active_runs_lock:
        if _active_runs.get(session_id) == run_id:
            _active_runs.pop(session_id, None)
    _track_task(asyncio.create_task(_schedule_event_cleanup(run_id)))


@app.get("/internal/graph/threads/{thread_id}/state")
async def get_thread_state(thread_id: str, _auth: None = Depends(require_internal_service_key)):
    if _graph is None:
        raise HTTPException(status_code=503, detail="Graph not initialized")
    config = {"configurable": {"thread_id": thread_id}}
    try:
        state = await _graph.aget_state(config)
        return state.values if state else {}
    except Exception:
        logger.exception("get_thread_state failed for %s", thread_id)
        return {}


@app.get("/internal/graph/threads/{thread_id}/checkpoints")
async def list_checkpoints(
        thread_id: str,
        limit: int = 20,
        _auth: None = Depends(require_internal_service_key),
):
    if _graph is None:
        raise HTTPException(status_code=503, detail="Graph not initialized")
    config = {"configurable": {"thread_id": thread_id}}
    checkpoints: list[dict] = []
    try:
        async for state in _graph.aget_state_history(config, limit=limit):
            metadata = state.metadata or {}
            values = state.values or {}
            label = metadata.get("source") or values.get("current_phase", "") or ""
            checkpoints.append({
                "checkpoint_id": state.config.get("configurable", {}).get("checkpoint_id", ""),
                "phase": values.get("current_phase", ""),
                "label": str(label),
                "created_at": state.created_at or "",
            })
    except Exception as e:
        logger.warning("Checkpoint listing failed for %s: %s", thread_id, e)
    return {"thread_id": thread_id, "checkpoints": checkpoints}


@app.get("/internal/graph/runs/{run_id}/events")
async def get_run_events(
        run_id: str,
        request: Request = None,
        _auth: None = Depends(require_internal_service_key),
):
    """通过 SSE 推送 run 事件，不支持 SSE 时返回 JSON。"""
    events = _run_events.get(run_id, [])

    accept = ""
    if request:
        accept = request.headers.get("accept", "")

    if "text/event-stream" in accept:
        async def event_generator():
            sent = 0
            while True:
                current_events = _run_events.get(run_id, [])
                while sent < len(current_events):
                    evt = current_events[sent]
                    sent += 1
                    yield {
                        "event": evt.get("server_event", "message"),
                        "data": json.dumps(evt, ensure_ascii=False),
                    }
                    if evt.get("server_event") in TERMINAL_SERVER_EVENTS:
                        return
                await asyncio.sleep(0.5)

        return EventSourceResponse(event_generator())

    return {"run_id": run_id, "events": events}


@app.post("/internal/graph/runs/{run_id}/cancel")
async def cancel_run(run_id: str, _auth: None = Depends(require_internal_service_key)):
    async with _active_runs_lock:
        for session_id, active_run_id in list(_active_runs.items()):
            if active_run_id == run_id:
                _active_runs.pop(session_id, None)
                break
    thread_id = _run_threads.get(run_id)
    _run_events.setdefault(run_id, []).append(
        build_runtime_event(
            server_event=ServerEvent.TASK_EXPIRED,
            run_id=run_id,
            thread_id=thread_id or "",
            runtime_state="EXPIRED",
            data={"reason": "cancelled"},
        )
    )
    _track_task(asyncio.create_task(_schedule_event_cleanup(run_id)))
    return {"run_id": run_id, "status": "cancelled"}


class RestoreRequest(BaseModel):
    checkpoint_id: str


@app.post("/internal/graph/threads/{thread_id}/restore")
async def restore_checkpoint(
        thread_id: str,
        req: RestoreRequest,
        _auth: None = Depends(require_internal_service_key),
):
    if _graph is None:
        raise HTTPException(status_code=503, detail="Graph not initialized")

    target_config = {
        "configurable": {
            "thread_id": thread_id,
            "checkpoint_id": req.checkpoint_id,
        }
    }
    try:
        snapshot = await _graph.aget_state(target_config)
        if not snapshot or not snapshot.values:
            raise HTTPException(status_code=404, detail="Checkpoint not found")

        result = await _graph.ainvoke(None, target_config)
        result_dict = result if isinstance(result, dict) else {}
        restored_state = {
            "session_id": result_dict.get("session_id", ""),
            "thread_id": thread_id,
            "phase": result_dict.get("current_phase", ""),
            "node_outputs": result_dict.get("node_outputs", {}),
            "available_actions": result_dict.get("available_actions", []),
            "plan": build_plan_payload(result_dict),
            "recommendation_reason": result_dict.get("recommendation_reason", ""),
        }

        return {
            "run_id": f"run_restore_{uuid.uuid4().hex[:12]}",
            "thread_id": thread_id,
            "session_id": result_dict.get("session_id", ""),
            "runtime_state": "RESTORING",
            **restored_state,
            "restored_state": restored_state,
        }
    except HTTPException:
        raise
    except Exception as e:
        logger.exception("Restore failed for %s/%s", thread_id, req.checkpoint_id)
        raise HTTPException(status_code=500, detail=f"Restore failed: {e}")


class ResumeRequest(BaseModel):
    action: str
    data: dict = {}


@app.post("/internal/graph/runs/{run_id}/resume")
async def resume_run(
        run_id: str,
        req: ResumeRequest,
        _auth: None = Depends(require_internal_service_key),
):
    if _graph is None:
        raise HTTPException(status_code=503, detail="Graph not initialized")

    events = _run_events.get(run_id, [])
    if not events:
        raise HTTPException(status_code=404, detail=f"Run {run_id} not found or already expired")

    thread_id = _run_threads.get(run_id) or next(
        (e["thread_id"] for e in events if e.get("thread_id")), None
    )
    session_id = next((e["session_id"] for e in events if e.get("session_id")), None)

    if not thread_id:
        raise HTTPException(status_code=404, detail="Cannot find thread for this run")

    if not await _is_thread_interrupted(thread_id):
        raise HTTPException(status_code=409, detail="No pending interrupt for this run")

    if req.action not in {"confirm", "reject", "modify"}:
        raise HTTPException(status_code=422, detail="action must be confirm, reject, or modify")

    client_event = next((e.get("client_event") for e in events if e.get("client_event")), "")

    _run_events[run_id].append(
        build_runtime_event(
            server_event=ServerEvent.APPROVAL_RESOLVED,
            session_id=session_id or "",
            run_id=run_id,
            thread_id=thread_id,
            runtime_state="RUNNING",
            client_event=client_event,
            data={"action": req.action},
        )
    )

    from langgraph.types import Command
    resume_value = {"action": req.action, "data": req.data}
    config = {"configurable": {"thread_id": thread_id}}

    try:
        result = await _graph.ainvoke(Command(resume=resume_value), config)
        result_dict = result if isinstance(result, dict) else {}

        server_event = (
            ServerEvent.TASK_COMPLETED
            if result_dict.get("runtime_state") != "FAILED"
            else ServerEvent.TASK_FAILED
        )
        _run_events[run_id].append(
            build_runtime_event(
                server_event=server_event,
                session_id=session_id or "",
                run_id=run_id,
                thread_id=thread_id,
                trace_id=result_dict.get("trace_id", ""),
                runtime_state=result_dict.get("runtime_state", "COMPLETED"),
                client_event=client_event,
                failure_bucket=result_dict.get("failure_bucket"),
                data={
                    "phase": result_dict.get("current_phase", ""),
                    "node_outputs": result_dict.get("node_outputs", {}),
                    "available_actions": result_dict.get("available_actions", []),
                    "pending_human_action": result_dict.get("pending_human_action", ""),
                    "behavior_metrics": result_dict.get("behavior_metrics", {}),
                    "plan": build_plan_payload(result_dict),
                    "recommendation_reason": result_dict.get("recommendation_reason", ""),
                    "error": result_dict.get("last_error"),
                },
            )
        )

        if session_id:
            async with _active_runs_lock:
                if _active_runs.get(session_id) == run_id:
                    _active_runs.pop(session_id, None)
        _track_task(asyncio.create_task(_schedule_event_cleanup(run_id)))

        return {
            "run_id": run_id,
            "action": req.action,
            "runtime_state": result_dict.get("runtime_state", "COMPLETED"),
        }
    except Exception as e:
        logger.exception("Resume failed for run %s", run_id)
        _run_events[run_id].append(
            build_runtime_event(
                server_event=ServerEvent.TASK_FAILED,
                session_id=session_id or "",
                run_id=run_id,
                thread_id=thread_id,
                runtime_state="FAILED",
                failure_bucket="SYSTEM_ERROR",
                data={"error": str(e)},
            )
        )
        if session_id:
            async with _active_runs_lock:
                if _active_runs.get(session_id) == run_id:
                    _active_runs.pop(session_id, None)
        raise HTTPException(status_code=500, detail=f"Resume failed: {e}")
