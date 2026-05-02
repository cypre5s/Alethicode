#!/usr/bin/env python3
"""rag_quality_regression.py — Phase 4 发布闸门：召回质量回归。

判定规则：

  1. 加载 query 集 (`scripts/rag_regression_queries.json`，可被 `--queries` 覆盖)，
     每条形如:
        {
          "query":            "什么是计算思维？",
          "endpoint":         "courseware",        // courseware|similar-error|memory|transfer
          "user_id":          1,                   // 仅 similar-error / memory 需要
          "language_pack_id": 34,
          "expected_keywords": ["Computational Thinking", "Programming Technology"],
          "min_hits":         1
        }
  2. 调 alethicode-rag 对应 endpoint 取 raw_context + chunks，
     计算 hit@5：keyword 在 raw_context 命中 + chunks 命中合并；
     mrr：第一个命中的 hit 排名倒数。
  3. 输出 JSON 报告 (`--output rag_quality_regression_report.json`，
     可 `--print` 打印到 stdout)。
  4. 任一指标低于阈值（默认 hit@5 ≥ 0.7、mrr ≥ 0.5）退出码 1，
     供 CI / argo / GitOps gate 判定发布通过与否。

使用示例：
  RAG_SERVICE_URL=http://127.0.0.1:8200 \\
    RAG_INTERNAL_TOKEN=dev-internal-key \\
    python scripts/rag_quality_regression.py --print
"""

from __future__ import annotations

import argparse
import asyncio
import json
import logging
import os
import sys
from dataclasses import dataclass, field, asdict
from pathlib import Path
from typing import Any

import httpx

logger = logging.getLogger("rag_regression")
logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")


DEFAULT_QUERIES_PATH = Path(__file__).resolve().parent / "rag_regression_queries.json"


@dataclass
class CaseResult:
    query: str
    endpoint: str
    expected_keywords: list[str]
    matched_keywords: list[str]
    chunk_count: int
    raw_context_chars: int
    rank_of_first_hit: int | None  # 1-indexed; None if no keyword hit
    hit_at_5: bool
    error: str | None = None

    def reciprocal_rank(self) -> float:
        if self.rank_of_first_hit is None:
            return 0.0
        if self.rank_of_first_hit > 5:
            return 0.0
        return 1.0 / float(self.rank_of_first_hit)


@dataclass
class RegressionReport:
    total_cases: int
    passed_cases: int
    hit_at_5: float
    mrr: float
    threshold_hit_at_5: float
    threshold_mrr: float
    passed_gate: bool
    cases: list[CaseResult] = field(default_factory=list)


async def run_one(
        client: httpx.AsyncClient, case: dict[str, Any], top_k: int
) -> CaseResult:
    endpoint = case["endpoint"]
    body: dict[str, Any] = {"query": case["query"], "top_k": top_k}
    if endpoint in ("similar-error", "memory"):
        if "user_id" not in case:
            return CaseResult(case["query"], endpoint, [], [], 0, 0, None, False,
                              error=f"{endpoint} requires user_id")
        body["user_id"] = case["user_id"]
        if endpoint == "similar-error":
            body.setdefault("error_taxonomy", case.get("error_taxonomy"))
    if endpoint == "courseware":
        body["language_pack_id"] = case.get("language_pack_id")
        body["kc_ids"] = case.get("kc_ids", [])
    if endpoint == "transfer":
        body["current_problem_id"] = case.get("current_problem_id")
        body["kc_ids"] = case.get("kc_ids", [])

    expected_keywords: list[str] = list(case.get("expected_keywords", []))
    try:
        resp = await client.post(f"/v1/rag/query/{endpoint}", json=body)
        if resp.status_code >= 400:
            return CaseResult(
                case["query"], endpoint, expected_keywords, [], 0, 0,
                None, False, error=f"http {resp.status_code}: {resp.text[:300]}",
            )
        data = resp.json()
    except Exception as exc:
        return CaseResult(
            case["query"], endpoint, expected_keywords, [], 0, 0,
            None, False, error=f"transport: {exc}",
        )

    raw_context = data.get("raw_context") or ""
    chunks = data.get("chunks") or []

    matched: list[str] = []
    rank_of_first_hit: int | None = None
    haystack_per_chunk = [str(c.get("content", "")) for c in chunks]

    for keyword in expected_keywords:
        if not keyword:
            continue
        kw_lower = keyword.lower()
        if kw_lower in raw_context.lower():
            matched.append(keyword)
            if rank_of_first_hit is None:
                rank_of_first_hit = 1
            continue
        for idx, chunk_text in enumerate(haystack_per_chunk):
            if kw_lower in chunk_text.lower():
                matched.append(keyword)
                rank_of_first_hit = (
                    idx + 1 if rank_of_first_hit is None else min(rank_of_first_hit, idx + 1)
                )
                break

    hit_at_5 = bool(matched)
    return CaseResult(
        query=case["query"],
        endpoint=endpoint,
        expected_keywords=expected_keywords,
        matched_keywords=matched,
        chunk_count=len(chunks),
        raw_context_chars=len(raw_context),
        rank_of_first_hit=rank_of_first_hit,
        hit_at_5=hit_at_5,
    )


async def run_regression(args: argparse.Namespace) -> RegressionReport:
    queries_path = Path(args.queries) if args.queries else DEFAULT_QUERIES_PATH
    if not queries_path.exists():
        raise FileNotFoundError(f"queries file not found: {queries_path}")
    with queries_path.open("r", encoding="utf-8") as f:
        cases: list[dict[str, Any]] = json.load(f)

    base_url = os.environ.get("RAG_SERVICE_URL", "http://127.0.0.1:8200").rstrip("/")
    token = os.environ.get("RAG_INTERNAL_TOKEN", "dev-internal-key")
    timeout = httpx.Timeout(60.0)

    async with httpx.AsyncClient(
        base_url=base_url, timeout=timeout,
        headers={"X-Internal-Token": token},
    ) as client:
        results = await asyncio.gather(*(run_one(client, c, args.top_k) for c in cases))

    passed = sum(1 for r in results if r.hit_at_5)
    hit_at_5 = passed / len(results) if results else 0.0
    mrr = sum(r.reciprocal_rank() for r in results) / len(results) if results else 0.0

    return RegressionReport(
        total_cases=len(results),
        passed_cases=passed,
        hit_at_5=round(hit_at_5, 4),
        mrr=round(mrr, 4),
        threshold_hit_at_5=args.threshold_hit_at_5,
        threshold_mrr=args.threshold_mrr,
        passed_gate=(hit_at_5 >= args.threshold_hit_at_5 and mrr >= args.threshold_mrr),
        cases=results,
    )


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawTextHelpFormatter)
    p.add_argument("--queries", type=str, default=None,
                   help=f"path to query set JSON (default: {DEFAULT_QUERIES_PATH})")
    p.add_argument("--top-k", type=int, default=5)
    p.add_argument("--threshold-hit-at-5", type=float, default=0.7,
                   help="发布 gate hit@5 下限（默认 0.7）")
    p.add_argument("--threshold-mrr", type=float, default=0.5,
                   help="发布 gate MRR 下限（默认 0.5）")
    p.add_argument("--output", type=str, default="rag_quality_regression_report.json")
    p.add_argument("--print", action="store_true", help="同时把报告打印到 stdout")
    return p.parse_args()


def main() -> int:
    args = parse_args()
    report = asyncio.run(run_regression(args))
    payload = {
        "total_cases": report.total_cases,
        "passed_cases": report.passed_cases,
        "hit_at_5": report.hit_at_5,
        "mrr": report.mrr,
        "threshold_hit_at_5": report.threshold_hit_at_5,
        "threshold_mrr": report.threshold_mrr,
        "passed_gate": report.passed_gate,
        "cases": [asdict(c) for c in report.cases],
    }
    Path(args.output).write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    logger.info(
        "regression: total=%d hit@5=%.3f mrr=%.3f passed_gate=%s",
        report.total_cases, report.hit_at_5, report.mrr, report.passed_gate,
    )
    if args.print:
        print(json.dumps(payload, ensure_ascii=False, indent=2))
    return 0 if report.passed_gate else 1


if __name__ == "__main__":
    sys.exit(main())
