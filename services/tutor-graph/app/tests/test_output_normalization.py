"""测试 tutor 节点输出规约。"""

from __future__ import annotations

from unittest.mock import AsyncMock

import pytest

from app.nodes.ac_review import ac_review_node
from app.nodes.knowledge_review import knowledge_review_node
from app.nodes.reading import problem_guide_node


@pytest.mark.asyncio
async def test_problem_guide_prefers_evidence_courseware_hits_when_llm_returns_strings():
    llm = AsyncMock()
    llm.generate_json.return_value = {
        "problem_restatement": "先理解输入和输出。",
        "input_output_focus": "输入一个数字，输出结果。",
        "key_observation": "先别急着写代码。",
        "starter_questions": ["这题要你输出什么？"],
        "courseware_refs": ["第一章：输入输出.pptx"],
    }
    evidence_ref = {
        "document_id": "doc-1",
        "page_no": 3,
        "title": "输入输出",
        "preview": "读取一个整数并输出平方",
    }
    state = {
        "language": "Python3",
        "evidence_pack": {
            "workflow_context": {},
            "courseware_hits": {"hits": [evidence_ref]},
        },
    }

    result = await problem_guide_node(state, llm_client=llm)

    assert result["node_outputs"]["problem_guide"]["courseware_refs"] == [evidence_ref]


@pytest.mark.asyncio
async def test_ac_review_normalizes_string_courseware_refs_to_objects():
    llm = AsyncMock()
    llm.generate_json.return_value = {
        "success_summary": "你已经把核心思路写对了。",
        "next_practice_direction": "再练一题输入输出。",
        "courseware_refs": ["第二章：Python 语言基础.pptx"],
    }
    state = {
        "language": "Python3",
        "evidence_pack": {
            "diagnosis_evidence": {},
            "learner_state": {},
            "courseware_hits": {"hits": []},
        },
    }

    result = await ac_review_node(state, llm_client=llm)
    refs = result["node_outputs"]["post_ac"]["courseware_refs"]

    assert refs == [{
        "title": "第二章：Python 语言基础.pptx",
        "page_title": "第二章：Python 语言基础.pptx",
        "preview": "第二章：Python 语言基础.pptx",
    }]


@pytest.mark.asyncio
async def test_knowledge_review_normalizes_string_courseware_refs_to_objects():
    llm = AsyncMock()
    llm.generate_json.return_value = {
        "review_content": "先把 range 的边界记牢。",
        "related_kcs": ["range-边界"],
        "courseware_refs": ["循环基础讲义"],
        "practice_suggestions": ["手动模拟一次循环变量变化"],
    }
    state = {
        "evidence_pack": {
            "learner_state": {"weak_kcs": ["range-边界"]},
            "courseware_hits": {"hits": []},
        },
    }

    result = await knowledge_review_node(state, llm_client=llm)
    refs = result["node_outputs"]["knowledge_review"]["courseware_refs"]

    assert refs == [{
        "title": "循环基础讲义",
        "page_title": "循环基础讲义",
        "preview": "循环基础讲义",
    }]


@pytest.mark.asyncio
async def test_knowledge_review_uses_weak_kcs_from_learner_state():
    llm = AsyncMock()
    llm.generate_json.return_value = {
        "review_content": "先复习字符串下标边界。",
        "related_kcs": ["字符串处理方法"],
        "courseware_refs": [],
        "practice_suggestions": ["写 3 个边界样例逐个推演"],
    }
    state = {
        "evidence_pack": {
            "learner_state": {
                "weak_kcs": ["字符串处理方法"],
                "mastery_by_kc": {"字符串处理方法": 0.42, "字符串索引与切片": 0.55},
            },
            "courseware_hits": {"hits": []},
        },
    }

    await knowledge_review_node(state, llm_client=llm)
    user_message = llm.generate_json.await_args.args[1]

    assert "薄弱知识点: ['字符串处理方法']" in user_message
