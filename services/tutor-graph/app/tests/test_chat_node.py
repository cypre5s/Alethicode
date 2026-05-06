"""chat 节点测试：卡片注入、引用过滤、课件旁证标记、空输入处理。"""

from __future__ import annotations

from unittest.mock import AsyncMock, MagicMock

import pytest

from app.nodes.chat import chat_node


@pytest.mark.asyncio
async def test_chat_injects_last_cards_into_prompt():
    captured = {}

    async def fake_generate_json(system_prompt, user_msg, **kwargs):
        captured["system"] = system_prompt
        captured["user"] = user_msg
        return {
            "content": "我看到你刚才那张图...",
            "history": [],
            "referenced_card_ids": ["C-V-001"],
        }

    llm = MagicMock()
    llm.generate_json = AsyncMock(side_effect=fake_generate_json)

    state = {
        "client_event": "CHAT",
        "current_phase": "ERROR_FEEDBACK",
        "user_mode": "chat",
        "event_data": {"message": "@card:C-V-001 这张图哪里错了"},
        "node_outputs": {},
        "evidence_pack": {"learner_state": {}},
        "references": [
            {
                "card_id": "C-V-001",
                "card_type": "visualize",
                "mode_when_produced": "error_diag",
                "short_text": "for i in range(5) 的迭代图",
            }
        ],
        "last_cards": [
            {
                "card_id": "C-E-002",
                "card_type": "error_diagnosis",
                "mode_when_produced": "error_diag",
                "short_text": "range 边界差一",
            }
        ],
    }

    result = await chat_node(state, llm_client=llm)

    assert "C-V-001" in captured["user"]
    assert "for i in range(5)" in captured["user"]
    assert "C-E-002" in captured["user"]
    assert result["node_outputs"]["chat"]["referenced_card_ids"] == ["C-V-001"]


@pytest.mark.asyncio
async def test_chat_filters_fabricated_card_ids():
    """LLM must not be allowed to invent card ids the user / last cards never saw."""

    llm = MagicMock()
    llm.generate_json = AsyncMock(return_value={
        "content": "回答",
        "history": [],
        "referenced_card_ids": ["C-V-001", "C-FAKE-999", "C-E-002"],
    })

    state = {
        "client_event": "CHAT",
        "current_phase": "READING",
        "user_mode": "chat",
        "event_data": {"message": "你好"},
        "node_outputs": {},
        "evidence_pack": {"learner_state": {}},
        "references": [
            {"card_id": "C-V-001", "card_type": "visualize", "short_text": "图"}
        ],
        "last_cards": [
            {"card_id": "C-E-002", "card_type": "error_diagnosis", "short_text": "错"}
        ],
    }

    result = await chat_node(state, llm_client=llm)

    assert result["node_outputs"]["chat"]["referenced_card_ids"] == ["C-V-001", "C-E-002"]


@pytest.mark.asyncio
async def test_chat_handles_empty_references_and_cards():
    captured = {}

    async def fake_generate_json(system_prompt, user_msg, **kwargs):
        captured["user"] = user_msg
        return {"content": "ok", "history": []}

    llm = MagicMock()
    llm.generate_json = AsyncMock(side_effect=fake_generate_json)

    state = {
        "client_event": "CHAT",
        "current_phase": "READING",
        "event_data": {"message": "你好"},
        "node_outputs": {},
        "evidence_pack": {"learner_state": {}},
        "references": [],
        "last_cards": [],
    }

    result = await chat_node(state, llm_client=llm)

    assert "用户显式引用的卡片" not in captured["user"]
    assert "最近卡片摘要" not in captured["user"]
    assert result["node_outputs"]["chat"]["referenced_card_ids"] == []


@pytest.mark.asyncio
async def test_chat_single_courseware_labelled_as_primary():
    """Single courseware bundle should be labelled as primary (用户引用的课件)."""
    captured = {}

    async def fake_generate_json(system_prompt, user_msg, **kwargs):
        captured["user"] = user_msg
        return {"content": "ok", "history": []}

    llm = MagicMock()
    llm.generate_json = AsyncMock(side_effect=fake_generate_json)

    state = {
        "client_event": "CHAT",
        "current_phase": "READING",
        "event_data": {"message": "@courseware:1 第3页讲了什么"},
        "node_outputs": {},
        "evidence_pack": {
            "learner_state": {},
            "coursewares": [
                {
                    "language_pack_id": 1,
                    "pack_name": "Python入门",
                    "chunks": [
                        {"text": "变量赋值", "page_number": 3}
                    ],
                }
            ],
        },
        "references": [],
        "last_cards": [],
    }

    await chat_node(state, llm_client=llm)

    assert "用户引用的课件 #1" in captured["user"]
    assert "旁证课件" not in captured["user"]


@pytest.mark.asyncio
async def test_chat_multi_courseware_labels_primary_and_side_evidence():
    """Multiple courseware bundles: first is primary, rest are side-evidence (旁证)."""
    captured = {}

    async def fake_generate_json(system_prompt, user_msg, **kwargs):
        captured["user"] = user_msg
        return {"content": "ok", "history": []}

    llm = MagicMock()
    llm.generate_json = AsyncMock(side_effect=fake_generate_json)

    state = {
        "client_event": "CHAT",
        "current_phase": "READING",
        "event_data": {"message": "@courseware:1 @courseware:2 对比一下"},
        "node_outputs": {},
        "evidence_pack": {
            "learner_state": {},
            "coursewares": [
                {
                    "language_pack_id": 1,
                    "pack_name": "Python入门",
                    "chunks": [{"text": "变量赋值", "page_number": 3}],
                },
                {
                    "language_pack_id": 2,
                    "pack_name": "数据结构",
                    "chunks": [{"text": "列表操作", "page_number": 7}],
                },
            ],
        },
        "references": [],
        "last_cards": [],
    }

    await chat_node(state, llm_client=llm)

    assert "用户引用的课件 #1" in captured["user"]
    assert "旁证课件 #2" in captured["user"]


@pytest.mark.asyncio
async def test_chat_propagates_user_mode_to_prompt():
    captured = {}

    async def fake_generate_json(system_prompt, user_msg, **kwargs):
        captured["user"] = user_msg
        return {"content": "ok", "history": []}

    llm = MagicMock()
    llm.generate_json = AsyncMock(side_effect=fake_generate_json)

    state = {
        "client_event": "CHAT",
        "current_phase": "CODING",
        "user_mode": "visualize",
        "event_data": {"message": "为什么我循环不对"},
        "node_outputs": {},
        "evidence_pack": {"learner_state": {}},
    }

    await chat_node(state, llm_client=llm)

    assert "用户当前 Mode: visualize" in captured["user"]
