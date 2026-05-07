"""测试显式 SKELETON 工作流节点。"""

from __future__ import annotations

from unittest.mock import AsyncMock

import pytest

from app.nodes.ideating import ideating_node
from app.nodes.skeleton import skeleton_node


@pytest.mark.asyncio
async def test_skeleton_node_emits_dedicated_skeleton_code_output():
    llm = AsyncMock()
    llm.generate_json.return_value = {
        "description": "先保留输入输出和两个 TODO。",
        "skeleton": "n = int(input())\n# TODO: 读取剩余输入\n# TODO: 输出结果",
    }
    state = {
        "language": "Python3",
        "client_event": "SKELETON",
        "evidence_pack": {
            "workflow_context": {
                "statement": "给定一个整数 n，输出答案。",
            },
        },
    }

    result = await skeleton_node(state, llm_client=llm)

    assert "skeleton_code" in result["node_outputs"]
    assert result["node_outputs"]["skeleton_code"]["skeleton"].startswith("n = int(input())")
    assert "ideate" not in result["node_outputs"]


@pytest.mark.asyncio
async def test_skeleton_node_includes_full_problem_context_in_prompt():
    llm = AsyncMock()
    llm.generate_json.return_value = {
        "description": "为偶数求和保留 TODO。",
        "skeleton": "n = int(input())\ntotal = 0\n# TODO: 遍历偶数并累加\nprint(total)",
    }
    state = {
        "language": "Python3",
        "client_event": "SKELETON",
        "evidence_pack": {
            "workflow_context": {
                "title": "自然数偶数之和",
                "statement": "计算 1 到 N 范围内所有偶数自然数的和。",
                "input_description": "输入一个整数 N。",
                "output_description": "输出偶数和。",
                "samples": [{"input": "4", "output": "6"}],
            },
        },
    }

    await skeleton_node(state, llm_client=llm)

    user_msg = llm.generate_json.await_args.args[1]
    assert "题目标题: 自然数偶数之和" in user_msg
    assert "输入说明: 输入一个整数 N。" in user_msg
    assert "输出说明: 输出偶数和。" in user_msg
    assert "样例: [{'input': '4', 'output': '6'}]" in user_msg


@pytest.mark.asyncio
async def test_skeleton_node_fails_fast_when_problem_statement_missing():
    llm = AsyncMock()
    llm.generate_json.return_value = {
        "description": "当前题目信息缺失，无法生成代码骨架。",
        "skeleton": "# 请先提供题目描述",
    }
    state = {
        "language": "Python3",
        "client_event": "SKELETON",
        "evidence_pack": {"workflow_context": {}},
    }

    result = await skeleton_node(state, llm_client=llm)

    assert result["runtime_state"] == "FAILED"
    assert result["failure_bucket"] == "INSUFFICIENT_EVIDENCE"
    assert "workflow_context.statement required" in result["last_error"]
    assert "skeleton_code" not in result.get("node_outputs", {})
    llm.generate_json.assert_not_awaited()


@pytest.mark.asyncio
async def test_skeleton_node_rejects_off_topic_machine_learning_scaffold():
    llm = AsyncMock()
    llm.generate_json.return_value = {
        "description": "本练习主题为鸢尾花种类预测。",
        "skeleton": (
            "from sklearn.datasets import load_iris\n"
            "from sklearn.neighbors import KNeighborsClassifier\n"
            "iris = load_iris()\n"
            "# TODO: 训练 KNN 分类器"
        ),
    }
    state = {
        "language": "Python3",
        "client_event": "SKELETON",
        "evidence_pack": {
            "workflow_context": {
                "title": "自然数偶数之和",
                "statement": "编写程序，计算 1 到 N 范围内所有偶数自然数的和。",
                "input_description": "输入一个整数 N。",
                "output_description": "输出偶数和。",
            },
        },
    }

    result = await skeleton_node(state, llm_client=llm)

    assert result["runtime_state"] == "FAILED"
    assert result["failure_bucket"] == "SCHEMA_VIOLATION"
    assert "not grounded in current problem" in result["last_error"]
    assert "skeleton_code" not in result.get("node_outputs", {})


@pytest.mark.asyncio
async def test_ideating_node_no_longer_treats_magic_skeleton_text_as_special_branch():
    llm = AsyncMock()
    llm.generate_json.return_value = {
        "analysis": "先把输入、状态和输出拆开。",
        "steps": ["确认输入", "设计状态", "决定输出"],
        "guiding_questions": ["你准备记录什么中间量？"],
    }
    state = {
        "language": "Python3",
        "client_event": "IDEATING",
        "event_data": {
            "thought_text": "__generate_skeleton__",
        },
        "evidence_pack": {
            "workflow_context": {
                "statement": "给定一个整数 n，输出答案。",
            },
            "learner_state": {},
        },
    }

    result = await ideating_node(state, llm_client=llm)

    assert "ideate" in result["node_outputs"]
    assert "skeleton_code" not in result["node_outputs"]
    prompt = llm.generate_json.await_args.args[0]
    assert "学生正在思考解题思路" in prompt
    assert "学生请求生成代码骨架" not in prompt
