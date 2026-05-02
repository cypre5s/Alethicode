from __future__ import annotations

import pytest
from unittest.mock import AsyncMock

from app.nodes.diagnosis import error_feedback_node


@pytest.mark.asyncio
async def test_error_feedback_prompt_includes_problem_text():
    llm = AsyncMock()
    llm.generate_json = AsyncMock(return_value={
        "root_cause": "没有按题面保留四位小数输出。",
        "what_program_is_doing": "程序计算了面积，但输出格式不匹配。",
        "expected_behavior": "使用题目要求的 3.1415 并保留四位小数。",
        "fix_direction": "检查输出格式。",
        "related_kcs": ["格式化输出"],
        "error_pattern": "output_format",
        "is_recurring": False,
        "encouragement": "先对齐题面要求。",
    })

    state = {
        "language": "Python3",
        "evidence_pack": {
            "workflow_context": {
                "title": "圆面积计算",
                "statement": "输入圆半径，圆周率取 3.1415，输出面积。",
                "input_description": "一行一个浮点数 radius。",
                "output_description": "输出 area，保留 4 位小数。",
            },
            "diagnosis_evidence": {
                "result": "Wrong Answer",
                "code": "radius=float(input())\npi=3.1415\narea=pi*radius*radius\nprint(area)",
                "language": "Python3",
                "err_info": "答案错误",
            },
            "learner_state": {},
        },
    }

    await error_feedback_node(state, llm_client=llm)

    _, user_msg = llm.generate_json.await_args.args[:2]
    assert "题目标题: 圆面积计算" in user_msg
    assert "题目正文: 输入圆半径，圆周率取 3.1415，输出面积。" in user_msg
    assert "输入说明: 一行一个浮点数 radius。" in user_msg
    assert "输出说明: 输出 area，保留 4 位小数。" in user_msg
