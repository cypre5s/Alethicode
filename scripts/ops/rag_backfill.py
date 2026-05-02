#!/usr/bin/env python3
"""rag_backfill.py — 历史业务数据全量回填到 alethicode-rag。

回填 3 类实体到 LightRAG（KV/Vector on PG, Graph on Memgraph）：

  * courseware-page  ← language_pack_page.{id, page_text, language_pack_id, document_id, page_no, page_title}
  * notebook         ← ai_learner_notebook.{id, root_cause, error_taxonomy, student_reflection, fix_outcome, problem_id, user_id}
  * memory           ← ai_learner_memory.{user_id+":"+memory_key, memory_value, memory_type, source_problem_id}

依赖：
  pip install asyncpg httpx pydantic-settings python-dotenv

调用方式：
  # 估算成本（仅扫总数）
  python scripts/ops/rag_backfill.py --estimate
  # 取最旧 100 条做尺寸校准
  python scripts/ops/rag_backfill.py --limit 100
  # 全量
  python scripts/ops/rag_backfill.py --all
  # 仅某类
  python scripts/ops/rag_backfill.py --all --entity-types courseware-page

  # 重试历史失败行
  python scripts/ops/rag_backfill.py --retry-errors

  # 清空进度（开发期 reset）
  python scripts/ops/rag_backfill.py --reset

进度策略：
  - 每个 entity_type 在 rag_backfill_progress 一行；脚本启动读 last_id 做断点续传起点
  - 每个失败行落 rag_backfill_errors 一行；--retry-errors 会逐条重试
  - 并发受 --concurrency 控制（默认 5，匹配 deepseek 速率）

成本估算（基于 plan.md，已根据 Phase 0 实测调整）：
  - 课件 11000 段 × ~1.5 chunk/段 ≈ 16500 chunks
  - 每 chunk 1.3 次 LLM 调用，input ~30M tokens × $0.14 + output ~22M tokens × $0.28 ≈ $10
  - notebook + memory 量级远小于课件（万级用户 × 个位 notebook），合计估算 < $15
"""

from __future__ import annotations

import argparse
import asyncio
import json
import logging
import os
import sys
from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import Any, Awaitable, Callable, Iterable

import asyncpg
import httpx

logger = logging.getLogger("rag_backfill")
logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")


@dataclass
class BackfillSettings:
    rag_base_url: str
    rag_internal_token: str
    pg_host: str
    pg_port: int
    pg_user: str
    pg_password: str
    pg_database: str
    concurrency: int
    limit: int | None
    entity_types: list[str]
    language_pack_id: int | None = None
    request_timeout_seconds: float = 600.0

    @classmethod
    def from_env_and_args(cls, args: argparse.Namespace) -> "BackfillSettings":
        return cls(
            rag_base_url=os.environ.get("RAG_SERVICE_URL", "http://127.0.0.1:8200").rstrip("/"),
            rag_internal_token=os.environ.get("RAG_INTERNAL_TOKEN", "dev-internal-key"),
            pg_host=os.environ.get("POSTGRES_HOST", "127.0.0.1"),
            pg_port=int(os.environ.get("POSTGRES_PORT", "5436")),
            pg_user=os.environ.get("POSTGRES_USER", "onlinejudge"),
            pg_password=os.environ.get("POSTGRES_PASSWORD", ""),
            pg_database=os.environ.get("POSTGRES_DATABASE", "alethicode"),
            concurrency=args.concurrency,
            limit=args.limit,
            entity_types=args.entity_types,
            language_pack_id=getattr(args, "language_pack_id", None),
        )


@dataclass
class IndexCandidate:
    entity_type: str
    entity_id: str
    content: str
    metadata: dict[str, Any] = field(default_factory=dict)


@dataclass
class BackfillStats:
    total: int = 0
    finished: int = 0
    failed: int = 0
    last_id: str | None = None


# ---------- candidate fetchers ----------


async def fetch_courseware_pages(
        pool: asyncpg.Pool, *, after_id: int, limit: int | None,
        language_pack_id: int | None = None
) -> list[IndexCandidate]:
    """Fetch course-page rows with optional `language_pack_id` filter so callers
    can target a single course pack (e.g. only Python 语言基础) instead of the
    whole table. ``after_id`` keeps the resumable progress semantics.
    """
    if language_pack_id is None:
        sql = """
            SELECT id, language_pack_id, document_id, page_no, page_title, page_text
            FROM language_pack_page
            WHERE id > $1
              AND coalesce(page_text, '') <> ''
            ORDER BY id ASC
            LIMIT $2
        """
        fetch_limit = limit if limit is not None else 1_000_000
        rows = await pool.fetch(sql, after_id, fetch_limit)
    else:
        sql = """
            SELECT id, language_pack_id, document_id, page_no, page_title, page_text
            FROM language_pack_page
            WHERE id > $1
              AND language_pack_id = $2
              AND coalesce(page_text, '') <> ''
            ORDER BY id ASC
            LIMIT $3
        """
        fetch_limit = limit if limit is not None else 1_000_000
        rows = await pool.fetch(sql, after_id, language_pack_id, fetch_limit)
    return [
        IndexCandidate(
            entity_type="courseware-page",
            entity_id=str(r["id"]),
            content=r["page_text"] or "",
            metadata={
                "language_pack_id": r["language_pack_id"],
                "document_id": r["document_id"],
                "page_no": r["page_no"],
                "page_title": r["page_title"] or "",
                "source_path": f"language_pack/{r['language_pack_id']}/p{r['page_no']}",
            },
        )
        for r in rows
    ]


async def fetch_notebooks(
        pool: asyncpg.Pool, *, after_id: str, limit: int | None
) -> list[IndexCandidate]:
    sql = """
        SELECT id::text AS id, user_id, problem_id, error_taxonomy,
               coalesce(root_cause, '') AS root_cause,
               coalesce(student_reflection, '') AS student_reflection,
               coalesce(fix_outcome, '') AS fix_outcome,
               update_time
        FROM ai_learner_notebook
        WHERE is_deleted = false
          AND id::text > $1
        ORDER BY id::text ASC
        LIMIT $2
    """
    fetch_limit = limit if limit is not None else 1_000_000
    rows = await pool.fetch(sql, after_id, fetch_limit)
    candidates: list[IndexCandidate] = []
    for r in rows:
        summary_parts = [
            f"错误类型：{r['error_taxonomy'] or 'unknown'}",
            f"根因：{r['root_cause']}",
            f"反思：{r['student_reflection']}",
            f"修复结果：{r['fix_outcome']}",
        ]
        summary = "；".join(p for p in summary_parts if p)
        if not summary.strip():
            continue
        candidates.append(
            IndexCandidate(
                entity_type="notebook",
                entity_id=r["id"],
                content=summary,
                metadata={
                    "user_id": r["user_id"],
                    "problem_id": r["problem_id"],
                    "error_taxonomy": r["error_taxonomy"],
                    "root_cause": r["root_cause"],
                    "notebook_id": r["id"],
                },
            )
        )
    return candidates


async def fetch_memories(
        pool: asyncpg.Pool, *, after_id: str, limit: int | None
) -> list[IndexCandidate]:
    # Cursor format: `user_id:memory_key` — must match the entity_id we set
    # below, otherwise progress.last_id and the next SQL `>` filter disagree
    # and the script either skips rows or loops forever (实测 Phase 2 第一次跑
    # 把 44 条 memory 跑成 3090 条，根因就是 cursor 与 entity_id 不一致)。
    sql = """
        SELECT id, user_id, memory_key, memory_value, memory_type,
               source_type, source_problem_id, updated_at
        FROM ai_learner_memory
        WHERE enabled = true
          AND coalesce(memory_value, '') <> ''
          AND (user_id::text || ':' || memory_key) > $1
        ORDER BY (user_id::text || ':' || memory_key) ASC
        LIMIT $2
    """
    fetch_limit = limit if limit is not None else 1_000_000
    rows = await pool.fetch(sql, after_id, fetch_limit)
    candidates: list[IndexCandidate] = []
    for r in rows:
        entity_id = f"{r['user_id']}:{r['memory_key']}"
        candidates.append(
            IndexCandidate(
                entity_type="memory",
                entity_id=entity_id,
                content=r["memory_value"],
                metadata={
                    "user_id": r["user_id"],
                    "memory_type": r["memory_type"],
                    "source_type": r["source_type"],
                    "source_problem_id": r["source_problem_id"],
                    "memory_key": r["memory_key"],
                },
            )
        )
    return candidates


# ---------- progress / errors ----------


async def load_progress(pool: asyncpg.Pool, entity_type: str) -> tuple[BackfillStats, str]:
    """Load existing progress row; return (stats, last_id_string).

    last_id semantics:
      * courseware-page: numeric id as string ("0" if no progress)
      * notebook       : uuid string ("" if no progress)
      * memory         : composite "id:user_id:memory_key" string ("" if no progress)
    """
    row = await pool.fetchrow(
        "SELECT total, finished, failed, last_id FROM rag_backfill_progress WHERE entity_type = $1",
        entity_type,
    )
    if row is None:
        await pool.execute(
            """
            INSERT INTO rag_backfill_progress(entity_type, started_at)
            VALUES ($1, now())
            ON CONFLICT (entity_type) DO NOTHING
            """,
            entity_type,
        )
        default_last = "0" if entity_type == "courseware-page" else ""
        return BackfillStats(), default_last
    return (
        BackfillStats(
            total=row["total"] or 0,
            finished=row["finished"] or 0,
            failed=row["failed"] or 0,
            last_id=row["last_id"],
        ),
        row["last_id"] or ("0" if entity_type == "courseware-page" else ""),
    )


async def save_progress(
        pool: asyncpg.Pool, entity_type: str, stats: BackfillStats, *, finished_at: bool = False
) -> None:
    await pool.execute(
        """
        UPDATE rag_backfill_progress
        SET last_id = $1, total = $2, finished = $3, failed = $4,
            finished_at = CASE WHEN $5 THEN now() ELSE finished_at END,
            updated_at = now()
        WHERE entity_type = $6
        """,
        stats.last_id,
        stats.total,
        stats.finished,
        stats.failed,
        finished_at,
        entity_type,
    )


async def record_error(pool: asyncpg.Pool, entity_type: str, entity_id: str, error: str) -> None:
    # 显式 ::varchar(64) cast，否则 asyncpg 把 $1/$2 同时推断为 text 与
    # varchar 时报 AmbiguousParameterError (Phase 2 实测)。
    await pool.execute(
        """
        INSERT INTO rag_backfill_errors(entity_type, entity_id, attempt, error_text)
        VALUES ($1::varchar(64), $2::varchar(255),
                COALESCE((SELECT MAX(attempt) FROM rag_backfill_errors
                          WHERE entity_type = $1::varchar(64) AND entity_id = $2::varchar(255)), 0) + 1,
                $3)
        """,
        entity_type,
        entity_id,
        error[:2000],
    )


# ---------- HTTP indexer ----------


class RagIndexer:
    def __init__(self, settings: BackfillSettings):
        # WSL/dev 场景下 host 上有 HTTPS_PROXY=127.0.0.1:7892，httpx 默认 trust_env=True
        # 会读到这个代理；NO_PROXY 中的 `127.*` 通配符代理客户端不识别（需要
        # `127.0.0.1` 精确串），导致到 alethicode-rag (127.0.0.1:8200) 的请求被
        # 代理拦截后回 502 Bad Gateway。alethicode-rag 是同机本地服务，永远不
        # 应该走代理。trust_env=False 关闭 httpx 的 env 自动识别，强制直连。
        self._client = httpx.AsyncClient(
            base_url=settings.rag_base_url,
            timeout=httpx.Timeout(settings.request_timeout_seconds),
            headers={"X-Internal-Token": settings.rag_internal_token},
            trust_env=False,
        )

    async def aclose(self) -> None:
        await self._client.aclose()

    async def index(self, candidate: IndexCandidate) -> None:
        resp = await self._client.post(
            f"/v1/rag/index/{candidate.entity_type}",
            json={
                "entity_id": candidate.entity_id,
                "content": candidate.content,
                "metadata": candidate.metadata,
            },
        )
        if resp.status_code >= 400:
            raise RuntimeError(
                f"index {candidate.entity_type}:{candidate.entity_id} -> {resp.status_code} {resp.text[:300]}"
            )


# ---------- main runner ----------


FETCHERS: dict[str, Callable[..., Awaitable[list[IndexCandidate]]]] = {
    "courseware-page": fetch_courseware_pages,
    "notebook": fetch_notebooks,
    "memory": fetch_memories,
}


async def run_entity(
        pool: asyncpg.Pool, indexer: RagIndexer, entity_type: str, settings: BackfillSettings
) -> BackfillStats:
    """Drain candidates in MICRO-batches to bound the blast radius of a stuck POST.

    Phase 2 实测发现：LightRAG 1.4.15 在高并发 ingest 下偶发 pipeline 锁竞态，
    会让 1 个 POST 永久挂起，从而把整个 `asyncio.gather(...)` 也卡住，导致
    `save_progress` 长时间不刷新。修法：把 page_size 收到 10，每批用
    `asyncio.wait_for(...)` 包裹一个上限（默认 5 分钟），超时即把这批未完成的
    候选直接落 errors 表标记失败，下次 `--retry-errors` 单独重跑。
    """
    stats, last_id = await load_progress(pool, entity_type)
    fetcher = FETCHERS[entity_type]

    page_size = 10
    batch_timeout_seconds = 300.0
    remaining = settings.limit

    while True:
        request_limit = page_size
        if remaining is not None:
            request_limit = min(page_size, remaining)
            if request_limit <= 0:
                break

        kwargs: dict[str, Any]
        if entity_type == "courseware-page":
            kwargs = {"after_id": int(last_id or 0), "limit": request_limit}
            if settings.language_pack_id is not None:
                kwargs["language_pack_id"] = settings.language_pack_id
        else:
            kwargs = {"after_id": last_id, "limit": request_limit}
        candidates = await fetcher(pool, **kwargs)
        if not candidates:
            break

        sem = asyncio.Semaphore(settings.concurrency)

        async def _one(cand: IndexCandidate) -> tuple[IndexCandidate, bool, str | None]:
            async with sem:
                try:
                    await indexer.index(cand)
                    return cand, True, None
                except Exception as exc:
                    return cand, False, str(exc)

        gathered = asyncio.gather(*(_one(c) for c in candidates), return_exceptions=True)
        try:
            results: list[tuple[IndexCandidate, bool, str | None]] = await asyncio.wait_for(
                gathered, timeout=batch_timeout_seconds
            )
        except asyncio.TimeoutError:
            logger.warning(
                "[%s] batch timed out after %.0fs (last_id=%s); marking all candidates failed and continuing",
                entity_type, batch_timeout_seconds, last_id,
            )
            gathered.cancel()
            try:
                await gathered
            except (asyncio.CancelledError, BaseException):
                pass
            results = [(c, False, "batch timed out") for c in candidates]

        for cand, ok, err in results:
            if isinstance(ok, BaseException):
                # asyncio.gather(return_exceptions=True) may yield raw exceptions
                stats.total += 1
                stats.failed += 1
                err_text = str(ok)
                logger.warning("FAIL %s:%s -> %s", cand.entity_type, cand.entity_id, err_text)
                await record_error(pool, cand.entity_type, cand.entity_id, err_text)
                continue
            stats.total += 1
            if ok:
                stats.finished += 1
            else:
                stats.failed += 1
                logger.warning("FAIL %s:%s -> %s", cand.entity_type, cand.entity_id, err)
                await record_error(pool, cand.entity_type, cand.entity_id, err or "unknown")

        last = candidates[-1]
        if entity_type == "courseware-page":
            last_id = last.entity_id
            stats.last_id = last_id
        else:
            stats.last_id = last.entity_id
            last_id = last.entity_id
        await save_progress(pool, entity_type, stats)
        logger.info(
            "[%s] processed %d (total=%d finished=%d failed=%d), last_id=%s",
            entity_type,
            len(candidates),
            stats.total,
            stats.finished,
            stats.failed,
            stats.last_id,
        )
        if remaining is not None:
            remaining -= len(candidates)
            if remaining <= 0:
                break

    await save_progress(pool, entity_type, stats, finished_at=settings.limit is None)
    return stats


async def estimate_only(pool: asyncpg.Pool, language_pack_id: int | None = None) -> None:
    if language_pack_id is None:
        queries = {
            "courseware-page": ("SELECT count(*) FROM language_pack_page WHERE coalesce(page_text, '') <> ''", []),
            "notebook": ("SELECT count(*) FROM ai_learner_notebook WHERE is_deleted = false", []),
            "memory": ("SELECT count(*) FROM ai_learner_memory WHERE enabled = true", []),
        }
    else:
        queries = {
            "courseware-page": (
                "SELECT count(*) FROM language_pack_page WHERE coalesce(page_text, '') <> '' AND language_pack_id = $1",
                [language_pack_id],
            ),
        }
    for entity_type, (sql, params) in queries.items():
        count = await pool.fetchval(sql, *params)
        logger.info("[estimate] %s rows=%d", entity_type, count)


async def reset_progress(pool: asyncpg.Pool) -> None:
    await pool.execute("DELETE FROM rag_backfill_progress")
    await pool.execute("DELETE FROM rag_backfill_errors")
    logger.info("rag_backfill_progress + errors cleared")


async def retry_errors(pool: asyncpg.Pool, indexer: RagIndexer, settings: BackfillSettings) -> None:
    rows = await pool.fetch(
        """
        SELECT entity_type, entity_id
        FROM rag_backfill_errors
        WHERE entity_type = ANY($1::text[])
        GROUP BY entity_type, entity_id
        """,
        settings.entity_types,
    )
    if not rows:
        logger.info("no failed rows to retry")
        return

    candidates_by_id: dict[tuple[str, str], IndexCandidate] = {}
    courseware_ids = [int(r["entity_id"]) for r in rows if r["entity_type"] == "courseware-page"]
    if courseware_ids:
        cw_rows = await pool.fetch(
            """
            SELECT id, language_pack_id, document_id, page_no, page_title, page_text
            FROM language_pack_page WHERE id = ANY($1::bigint[])
            """,
            courseware_ids,
        )
        for r in cw_rows:
            candidates_by_id[("courseware-page", str(r["id"]))] = IndexCandidate(
                entity_type="courseware-page",
                entity_id=str(r["id"]),
                content=r["page_text"] or "",
                metadata={
                    "language_pack_id": r["language_pack_id"],
                    "document_id": r["document_id"],
                    "page_no": r["page_no"],
                    "page_title": r["page_title"] or "",
                },
            )
    notebook_ids = [r["entity_id"] for r in rows if r["entity_type"] == "notebook"]
    if notebook_ids:
        nb_rows = await pool.fetch(
            """
            SELECT id::text AS id, user_id, problem_id, error_taxonomy,
                   coalesce(root_cause, '') AS root_cause,
                   coalesce(student_reflection, '') AS student_reflection,
                   coalesce(fix_outcome, '') AS fix_outcome
            FROM ai_learner_notebook WHERE id::text = ANY($1::text[])
            """,
            notebook_ids,
        )
        for r in nb_rows:
            summary = "；".join(filter(None, [
                f"错误类型：{r['error_taxonomy'] or 'unknown'}",
                f"根因：{r['root_cause']}",
                f"反思：{r['student_reflection']}",
                f"修复结果：{r['fix_outcome']}",
            ]))
            if summary.strip():
                candidates_by_id[("notebook", r["id"])] = IndexCandidate(
                    entity_type="notebook",
                    entity_id=r["id"],
                    content=summary,
                    metadata={"user_id": r["user_id"], "problem_id": r["problem_id"]},
                )
    # memory entity_id is composite; we just look up by user_id, memory_key.
    mem_targets = [r["entity_id"] for r in rows if r["entity_type"] == "memory"]
    for entity_id in mem_targets:
        try:
            user_id_str, memory_key = entity_id.split(":", 1)
            user_id = int(user_id_str)
        except ValueError:
            continue
        mem = await pool.fetchrow(
            """
            SELECT user_id, memory_key, memory_value, memory_type, source_type, source_problem_id
            FROM ai_learner_memory WHERE user_id = $1 AND memory_key = $2 AND enabled = true
            """,
            user_id,
            memory_key,
        )
        if mem and mem["memory_value"]:
            candidates_by_id[("memory", entity_id)] = IndexCandidate(
                entity_type="memory",
                entity_id=entity_id,
                content=mem["memory_value"],
                metadata={
                    "user_id": mem["user_id"],
                    "memory_type": mem["memory_type"],
                    "source_type": mem["source_type"],
                    "source_problem_id": mem["source_problem_id"],
                },
            )

    if not candidates_by_id:
        logger.info("retry candidates resolved to 0; nothing to do")
        return

    sem = asyncio.Semaphore(settings.concurrency)

    async def _one(cand: IndexCandidate) -> None:
        async with sem:
            try:
                await indexer.index(cand)
                await pool.execute(
                    "DELETE FROM rag_backfill_errors WHERE entity_type = $1 AND entity_id = $2",
                    cand.entity_type, cand.entity_id,
                )
                logger.info("retry OK %s:%s", cand.entity_type, cand.entity_id)
            except Exception as exc:
                logger.warning("retry FAIL %s:%s -> %s", cand.entity_type, cand.entity_id, exc)
                await record_error(pool, cand.entity_type, cand.entity_id, str(exc))

    await asyncio.gather(*(_one(c) for c in candidates_by_id.values()))


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawTextHelpFormatter)
    p.add_argument("--all", action="store_true", help="run full backfill")
    p.add_argument("--limit", type=int, default=None, help="cap the per-run row count for cost calibration")
    p.add_argument(
        "--entity-types", nargs="*",
        default=["courseware-page", "notebook", "memory"],
        choices=["courseware-page", "notebook", "memory"],
    )
    p.add_argument("--concurrency", type=int, default=5)
    p.add_argument("--estimate", action="store_true", help="print row counts only")
    p.add_argument("--reset", action="store_true", help="DROP all progress + errors, then exit")
    p.add_argument("--retry-errors", action="store_true", help="re-run only previously-failed rows")
    p.add_argument(
        "--language-pack-id", type=int, default=None,
        help="restrict courseware-page backfill to a single language_pack.id (e.g. 43 for Python 语言基础); ignored for notebook/memory entity types"
    )
    return p.parse_args()


async def amain(args: argparse.Namespace) -> int:
    settings = BackfillSettings.from_env_and_args(args)
    pool = await asyncpg.create_pool(
        host=settings.pg_host,
        port=settings.pg_port,
        user=settings.pg_user,
        password=settings.pg_password,
        database=settings.pg_database,
        min_size=1,
        max_size=4,
    )
    if pool is None:
        logger.error("could not create pg pool")
        return 1

    indexer = RagIndexer(settings)
    try:
        if args.estimate:
            await estimate_only(pool, language_pack_id=args.language_pack_id)
            return 0
        if args.reset:
            await reset_progress(pool)
            return 0
        if args.retry_errors:
            await retry_errors(pool, indexer, settings)
            return 0

        if not args.all and args.limit is None:
            logger.error("--all or --limit N required (use --estimate to just see row counts)")
            return 2

        for entity_type in settings.entity_types:
            stats = await run_entity(pool, indexer, entity_type, settings)
            logger.info(
                "DONE %s total=%d finished=%d failed=%d last_id=%s",
                entity_type, stats.total, stats.finished, stats.failed, stats.last_id,
            )
        return 0
    finally:
        await indexer.aclose()
        await pool.close()


def main() -> int:
    args = parse_args()
    return asyncio.run(amain(args))


if __name__ == "__main__":
    sys.exit(main())
