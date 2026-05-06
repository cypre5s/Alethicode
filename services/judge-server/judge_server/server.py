import hashlib
import json
import logging
import os
import shutil
import threading
import uuid

import requests
from flask import Flask, request, Response

from compiler import Compiler
from config import (JUDGER_WORKSPACE_BASE, SPJ_SRC_DIR, SPJ_EXE_DIR, COMPILER_USER_UID, SPJ_USER_UID,
                    RUN_USER_UID, RUN_GROUP_GID, TEST_CASE_DIR)
from exception import TokenVerificationFailed, CompileError, SPJCompileError, JudgeClientError
from judge_client import JudgeClient
from streaming import SseStreamBridge
from utils import server_info, logger, token, ProblemIOMode
from worker_pool import (
    PRIORITY_FORMAL,
    QueueFull,
    VALID_PRIORITIES,
    get_worker_pool,
)

app = Flask(__name__)
DEBUG = os.environ.get("judger_debug") == "1"
app.debug = DEBUG

_callback_logger = logging.getLogger("judge_server.callback")


_SRC_NAME_TO_LANGUAGE = {
    "solution.py": "Python3",
    "main.py": "Python3",
    "main.c": "C",
    "main.cpp": "C++",
    "Main.java": "Java",
    "main.js": "JavaScript",
    "main.go": "Go",
}


def _detect_language(language_config):
    """从 language_config 推断语言名称，供 diagnosis 使用。"""
    compile_cfg = language_config.get("compile")
    if compile_cfg and isinstance(compile_cfg, dict):
        src_name = compile_cfg.get("src_name", "")
        lang = _SRC_NAME_TO_LANGUAGE.get(src_name)
        if lang:
            return lang
    run_cfg = language_config.get("run", {})
    command = run_cfg.get("command", "")
    if "python" in command.lower():
        return "Python3"
    if "java" in command.lower():
        return "Java"
    if "node" in command.lower():
        return "JavaScript"
    return ""


class InitSubmissionEnv(object):
    def __init__(self, judger_workspace, submission_id, init_test_case_dir=False):
        self.work_dir = os.path.join(judger_workspace, submission_id)
        self.init_test_case_dir = init_test_case_dir
        if init_test_case_dir:
            self.test_case_dir = os.path.join(self.work_dir, "submission_" + submission_id)
        else:
            self.test_case_dir = None

    def __enter__(self):
        try:
            os.mkdir(self.work_dir)
            if self.init_test_case_dir:
                os.mkdir(self.test_case_dir)
            os.chown(self.work_dir, COMPILER_USER_UID, RUN_GROUP_GID)
            os.chmod(self.work_dir, 0o711)
        except Exception as e:
            logger.exception(e)
            raise JudgeClientError("failed to create runtime dir")
        return self.work_dir, self.test_case_dir

    def __exit__(self, exc_type, exc_val, exc_tb):
        if not DEBUG:
            try:
                shutil.rmtree(self.work_dir)
            except Exception as e:
                logger.exception(e)
                raise JudgeClientError("failed to clean runtime dir")


class JudgeServer:
    @classmethod
    def ping(cls):
        data = server_info()
        data["action"] = "pong"
        return data

    @classmethod
    def judge(cls, language_config, src, max_cpu_time, max_memory, test_case_id=None, test_case=None,
              spj_version=None, spj_config=None, spj_compile_config=None, spj_src=None, output=False,
              io_mode=None, priority=PRIORITY_FORMAL, on_case_done=None, rule_type="ACM"):
        """跑完一个判题请求。

        Phase 1 新增：``priority`` / ``on_case_done``。
        Phase 3 新增：``rule_type``（``"ACM"`` / ``"OI"``）。
        """
        if priority not in VALID_PRIORITIES:
            raise JudgeClientError("invalid priority: %s" % (priority,))
        if not io_mode:
            io_mode = {"io_mode": ProblemIOMode.standard}

        if not (test_case or test_case_id) or (test_case and test_case_id):
            raise JudgeClientError("invalid parameter")
        # init
        compile_config = language_config.get("compile")
        run_config = language_config["run"]
        submission_id = uuid.uuid4().hex

        is_spj = spj_version and spj_config

        if is_spj:
            spj_exe_path = os.path.join(SPJ_EXE_DIR, spj_config["exe_name"].format(spj_version=spj_version))
            # spj src has not been compiled
            if not os.path.isfile(spj_exe_path):
                logger.warning("%s does not exists, spj src will be recompiled")
                cls.compile_spj(spj_version=spj_version, src=spj_src,
                                spj_compile_config=spj_compile_config)

        init_test_case_dir = bool(test_case)
        with InitSubmissionEnv(JUDGER_WORKSPACE_BASE, submission_id=str(submission_id), init_test_case_dir=init_test_case_dir) as dirs:
            submission_dir, test_case_dir = dirs
            test_case_dir = test_case_dir or os.path.join(TEST_CASE_DIR, test_case_id)

            if compile_config:
                src_path = os.path.join(submission_dir, compile_config["src_name"])

                # write source code into file
                with open(src_path, "w", encoding="utf-8") as f:
                    f.write(src)
                os.chown(src_path, COMPILER_USER_UID, 0)
                os.chmod(src_path, 0o400)

                # compile source code, return exe file path
                exe_path = Compiler().compile(compile_config=compile_config,
                                              src_path=src_path,
                                              output_dir=submission_dir)
                try:
                    # Java exe_path is SOME_PATH/Main, but the real path is SOME_PATH/Main.class
                    # We ignore it temporarily
                    os.chown(exe_path, RUN_USER_UID, 0)
                    os.chmod(exe_path, 0o500)
                except Exception:
                    pass
            else:
                exe_path = os.path.join(submission_dir, run_config["exe_name"])
                with open(exe_path, "w", encoding="utf-8") as f:
                    f.write(src)

            if init_test_case_dir:
                info = {"test_case_number": len(test_case), "spj": is_spj, "test_cases": {}}
                # write test case
                for index, item in enumerate(test_case):
                    index += 1
                    item_info = {}

                    input_name = str(index) + ".in"
                    item_info["input_name"] = input_name
                    input_data = item["input"].encode("utf-8")
                    item_info["input_size"] = len(input_data)

                    with open(os.path.join(test_case_dir, input_name), "wb") as f:
                        f.write(input_data)
                    if not is_spj:
                        output_name = str(index) + ".out"
                        item_info["output_name"] = output_name
                        output_data = item["output"].encode("utf-8")
                        item_info["output_md5"] = hashlib.md5(output_data).hexdigest()
                        item_info["output_size"] = len(output_data)
                        item_info["stripped_output_md5"] = hashlib.md5(output_data.rstrip()).hexdigest()

                        with open(os.path.join(test_case_dir, output_name), "wb") as f:
                            f.write(output_data)
                    info["test_cases"][index] = item_info
                with open(os.path.join(test_case_dir, "info"), "w") as f:
                    json.dump(info, f)

            detected_language = _detect_language(language_config)
            judge_client = JudgeClient(run_config=language_config["run"],
                                       exe_path=exe_path,
                                       max_cpu_time=max_cpu_time,
                                       max_memory=max_memory,
                                       test_case_dir=test_case_dir,
                                       submission_dir=submission_dir,
                                       spj_version=spj_version,
                                       spj_config=spj_config,
                                       output=output,
                                       io_mode=io_mode,
                                       language=detected_language,
                                       src=src)
            run_result = judge_client.run(priority=priority, on_case_done=on_case_done, rule_type=rule_type)

            return run_result

    @classmethod
    def compile_spj(cls, spj_version, src, spj_compile_config):
        spj_compile_config["src_name"] = spj_compile_config["src_name"].format(spj_version=spj_version)
        spj_compile_config["exe_name"] = spj_compile_config["exe_name"].format(spj_version=spj_version)

        spj_src_path = os.path.join(SPJ_SRC_DIR, spj_compile_config["src_name"])

        # if spj source code not found, then write it into file
        if not os.path.exists(spj_src_path):
            with open(spj_src_path, "w", encoding="utf-8") as f:
                f.write(src)
            os.chown(spj_src_path, COMPILER_USER_UID, 0)
            os.chmod(spj_src_path, 0o400)

        try:
            exe_path = Compiler().compile(compile_config=spj_compile_config,
                                          src_path=spj_src_path,
                                          output_dir=SPJ_EXE_DIR)
            os.chown(exe_path, SPJ_USER_UID, 0)
            os.chmod(exe_path, 0o500)
        # turn common CompileError into SPJCompileError
        except CompileError as e:
            raise SPJCompileError(e.message)
        return "success"


@app.route('/', defaults={'path': ''})
@app.route('/<path:path>', methods=["POST"])
def server(path):
    if path not in ("judge", "ping", "compile_spj"):
        return Response(
            json.dumps({"err": "InvalidRequest", "data": "404"}),
            mimetype='application/json',
            status=404,
        )

    _token = request.headers.get("X-Judge-Server-Token")
    if _token != token:
        ret = {"err": "TokenVerificationFailed", "data": "invalid token"}
        return Response(json.dumps(ret), mimetype='application/json', status=401)

    try:
        data = request.json or {}
    except Exception:
        data = {}

    if path == "judge":
        return _dispatch_judge(data)

    try:
        ret = {"err": None, "data": getattr(JudgeServer, path)(**data)}
    except (CompileError, TokenVerificationFailed, SPJCompileError, JudgeClientError) as e:
        logger.exception(e)
        ret = {"err": e.__class__.__name__, "data": e.message}
    except Exception as e:
        logger.exception(e)
        ret = {"err": "JudgeClientError", "data": e.__class__.__name__ + " :" + str(e)}
    return Response(json.dumps(ret), mimetype='application/json')


def _dispatch_judge(data):
    """根据 ``stream`` / ``callback_url`` 入参把 ``/judge`` 分流到三种模式。

    - 默认（不传两个参数）：同步阻塞 + 完整 JSON 返回，与上游一致。
    - ``stream=true``：返回 ``text/event-stream``，每个测试点完成时推送一次
      ``event: case``，全部完成后推送 ``event: done``。
    - ``callback_url=...``：判题在后台 thread 跑，立即 ``202 Accepted`` +
      ``{"accepted": true, "submission_id": ...}``；每个测试点完成时 POST
      到 ``callback_url`` 推送 ``{"event": "case", ...}``，全部完成时 POST
      ``{"event": "done", "data": [...]}``。

    三种模式严格互斥（同时传 ``stream`` 与 ``callback_url`` 拒绝）。
    ``priority`` 入参在三种模式下都生效。
    """
    payload = dict(data)  # 不污染调用方
    stream = bool(payload.pop("stream", False))
    callback_url = payload.pop("callback_url", None)
    priority = payload.pop("priority", PRIORITY_FORMAL)
    rule_type = payload.pop("rule_type", "ACM")
    if priority not in VALID_PRIORITIES:
        ret = {"err": "JudgeClientError", "data": "invalid priority: %s" % (priority,)}
        return Response(json.dumps(ret), mimetype='application/json', status=400)
    if stream and callback_url:
        ret = {"err": "JudgeClientError", "data": "stream and callback_url are mutually exclusive"}
        return Response(json.dumps(ret), mimetype='application/json', status=400)

    payload["rule_type"] = rule_type
    if stream:
        return _judge_streaming(payload, priority)
    if callback_url:
        return _judge_async_callback(payload, priority, callback_url)
    return _judge_sync(payload, priority)


def _safe_judge_call(payload, priority, on_case_done):
    """统一调 ``JudgeServer.judge`` 并把异常包成上游 ``ret`` 字典。"""
    try:
        data = JudgeServer.judge(priority=priority, on_case_done=on_case_done, **payload)
        ret = {"err": None, "data": data}
        return ret
    except (CompileError, TokenVerificationFailed, SPJCompileError, JudgeClientError) as exc:
        logger.exception(exc)
        return {"err": exc.__class__.__name__, "data": exc.message}
    except QueueFull as exc:
        logger.warning("queue full: %s", exc)
        return {"err": "QueueFull", "data": str(exc)}
    except Exception as exc:  # noqa: BLE001
        logger.exception(exc)
        return {"err": "JudgeClientError", "data": exc.__class__.__name__ + " :" + str(exc)}


def _judge_sync(payload, priority):
    ret = _safe_judge_call(payload, priority, on_case_done=None)
    status = 200
    if ret["err"] == "TokenVerificationFailed":
        status = 401
    elif ret["err"] == "QueueFull":
        status = 503
    elif ret["err"] is not None:
        status = 200  # 与上游一致：业务错误也返回 200，由 ret.err 判定
    return Response(json.dumps(ret), mimetype='application/json', status=status)


def _judge_streaming(payload, priority):
    bridge = SseStreamBridge(
        judge_runner=lambda on_case_done: JudgeServer.judge(
            priority=priority,
            on_case_done=on_case_done,
            **payload,
        ),
    )
    return Response(
        bridge.stream(),
        mimetype='text/event-stream',
        headers={
            "Cache-Control": "no-cache",
            "X-Accel-Buffering": "no",
        },
    )


def _judge_async_callback(payload, priority, callback_url):
    submission_id = uuid.uuid4().hex

    def _post(event_type, body):
        try:
            requests.post(
                callback_url,
                json={
                    "event": event_type,
                    "submission_id": submission_id,
                    "payload": body,
                },
                timeout=5,
            )
        except Exception:  # noqa: BLE001
            _callback_logger.exception(
                "callback POST failed: url=%s event=%s submission=%s",
                callback_url,
                event_type,
                submission_id,
            )

    def _on_case_done(case_result):
        _post("case", case_result)

    def _runner():
        ret = _safe_judge_call(payload, priority, on_case_done=_on_case_done)
        if ret["err"] is None:
            _post("done", ret["data"])
        else:
            _post("error", ret)

    threading.Thread(
        target=_runner,
        name=f"judge-callback-{submission_id[:8]}",
        daemon=True,
    ).start()

    accepted = {
        "err": None,
        "data": {
            "accepted": True,
            "submission_id": submission_id,
            "callback_url": callback_url,
            "priority": priority,
        },
    }
    return Response(json.dumps(accepted), mimetype='application/json', status=202)


try:
    from explain.service import build_explain_handler
    build_explain_handler(app)
except Exception:
    logger.warning("explain module not available; /explain endpoint disabled")

try:
    from metrics.exporter import build_metrics_handler
    build_metrics_handler(app)
except Exception:
    logger.warning("metrics module not available; /metrics endpoint disabled")

try:
    from trace.tracer import build_trace_handler
    build_trace_handler(app)
except Exception:
    logger.warning("trace module not available; /trace endpoint disabled")

if DEBUG:
    logger.info("DEBUG=ON")

# gunicorn -w 4 -b 0.0.0.0:8080 server:app
if __name__ == "__main__":
    app.run(debug=DEBUG)
