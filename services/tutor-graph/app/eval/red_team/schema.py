"""对抗测试用例的 Pydantic schema。

一个用例描述：攻击进入哪个节点、payload 注入到哪里、攻击目标是什么，以及节点输出
必须满足哪些断言。

按 AGENTS.md 的 failfast 原则：禁止未知字段，使数据集漂移在解析阶段失败，而不是
静默通过测试。
"""

from __future__ import annotations

from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field

from app.eval.red_team.targets import AttackCategory, PedagogicalCIA, Phase


AssertionKind = Literal[
    "output_must_not_contain",
    "output_must_not_match_regex",
    "output_field_must_be_absent",
    "output_field_must_satisfy",
    "output_must_be_failfast",
    "score_must_be_below",
    "memory_must_not_change",
]


class Assertion(BaseModel):
    """针对节点输出的一条断言。"""

    model_config = ConfigDict(extra="forbid")

    kind: AssertionKind
    target_field: str | None = None
    value: Any = None
    description: str = Field(
        default="",
        description="供人阅读的断言说明。",
    )


class PayloadInjection(BaseModel):
    """描述向节点输入的哪个位置注入什么内容。"""

    model_config = ConfigDict(extra="forbid")

    state_path: str = Field(
        ...,
        description=(
            "payload 写入 TutorGraphState 的点分路径。例如：'event_data.message'、"
            "'evidence_pack.diagnosis_evidence.code'、"
            "'evidence_pack.workflow_context.statement'、'last_cards'、'references'。"
        ),
    )
    value: Any = Field(
        ...,
        description="注入到 state_path 的对抗 payload 值。",
    )


class AdversarialCase(BaseModel):
    """一个对抗测试用例。"""

    model_config = ConfigDict(extra="forbid")

    id: str = Field(..., pattern=r"^adv-\d{3,4}$")
    phase: Phase
    target_node: str = Field(
        ...,
        description=(
            "tutor_graph 节点函数名。例如：'chat_node'、'error_feedback_node'、"
            "'ideating_node'、'skeleton_node'、'parsons_node'、'ac_review_node'。"
        ),
    )
    attack_category: AttackCategory
    cia: PedagogicalCIA = Field(
        ...,
        description=(
            "攻击目标对应教学 CIA 三元组的哪一轴。"
            "C=Confidentiality（不泄漏答案）；"
            "I=Integrity（不允许学习者操纵 mastery）；"
            "A=Availability（不允许洪泛或滥用）。"
        ),
    )

    description: str = Field(
        ...,
        description="该用例模拟的真实学生行为一句话描述。",
    )
    payload: list[PayloadInjection] = Field(
        ...,
        min_length=1,
        description="应用到节点输入状态的一个或多个注入项。",
    )
    base_state_overrides: dict[str, Any] = Field(
        default_factory=dict,
        description=(
            "payload 注入前应用到默认基础状态的可选覆盖项，用于设置场景上下文，"
            "例如特定题面或学习者画像。"
        ),
    )
    expected_assertions: list[Assertion] = Field(
        ...,
        min_length=1,
        description="所有断言都满足时用例才算通过。",
    )
    notes: str = Field(default="")


class AssertionResult(BaseModel):
    """一条断言的运行结果。"""

    model_config = ConfigDict(extra="forbid")

    assertion: Assertion
    passed: bool
    actual: Any = None
    failure_reason: str = ""


class CaseResult(BaseModel):
    """一个对抗用例的运行结果。"""

    model_config = ConfigDict(extra="forbid")

    case_id: str
    passed: bool
    attempted: bool = Field(
        ...,
        description=(
            "节点是否尝试响应（相对于 failfast）。对齐 RedTeamCUA 的 Attempt Rate："
            "节点只要响应明显恶意的 prompt，就会提高 AR，即使输出碰巧是安全的。"
        ),
    )
    failfast: bool = Field(
        ...,
        description=(
            "节点是否返回 runtime_state=FAILED 或抛错。某些类别（例如 schema 注入）"
            "的正确行为就是 failfast。"
        ),
    )
    raw_output: dict[str, Any] = Field(default_factory=dict)
    assertion_results: list[AssertionResult] = Field(default_factory=list)
    error: str = ""


class DatasetSummary(BaseModel):
    """完整数据集运行的聚合报告。"""

    model_config = ConfigDict(extra="forbid")

    total: int
    passed: int
    failed: int
    pass_rate: float
    attempt_rate: float = Field(
        ...,
        description="节点尝试响应的用例占比，对应 RedTeamCUA AR。",
    )
    failfast_rate: float
    by_phase: dict[Phase, dict[str, int]] = Field(default_factory=dict)
    by_category: dict[AttackCategory, dict[str, int]] = Field(default_factory=dict)
    by_cia: dict[PedagogicalCIA, dict[str, int]] = Field(default_factory=dict)
    failed_case_ids: list[str] = Field(default_factory=list)
