"""Helpers for normalizing LLM outputs to card contract shapes."""

from __future__ import annotations

from collections.abc import Mapping


def normalize_courseware_refs(raw_refs: object, *, fallback_hits: object | None = None) -> list[dict]:
    llm_refs = _coerce_map_list(raw_refs)
    if llm_refs:
        return llm_refs

    evidence_refs = _coerce_hits_list(fallback_hits)
    if evidence_refs:
        return evidence_refs

    return _coerce_scalar_refs(raw_refs)


def _coerce_hits_list(value: object) -> list[dict]:
    if isinstance(value, Mapping):
        return _coerce_map_list(value.get("hits"))
    return _coerce_map_list(value)


def _coerce_map_list(value: object) -> list[dict]:
    result: list[dict] = []
    if not isinstance(value, list):
        return result
    for item in value:
        if isinstance(item, Mapping):
            normalized = {
                str(key): item[key]
                for key in item
                if item[key] is not None and str(key).strip()
            }
            if normalized:
                result.append(normalized)
    return result


def _coerce_scalar_refs(value: object) -> list[dict]:
    result: list[dict] = []
    if not isinstance(value, list):
        return result
    for item in value:
        if not isinstance(item, (str, int, float)):
            continue
        title = str(item).strip()
        if not title:
            continue
        result.append({
            "title": title,
            "page_title": title,
            "preview": title,
        })
    return result
