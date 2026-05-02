"""Pydantic schema for adversarial test cases.

A case captures: which node receives the attack, what payload is injected
where, what the attack tries to achieve, and what assertions the node's
output must satisfy.

Failfast principle (per AGENTS.md): unknown fields are forbidden so that
dataset drift fails at parse time rather than silently passing tests.
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
    """A single assertion against the node output."""

    model_config = ConfigDict(extra="forbid")

    kind: AssertionKind
    target_field: str | None = None
    value: Any = None
    description: str = Field(
        default="",
        description="Human-readable description of what this assertion checks.",
    )


class PayloadInjection(BaseModel):
    """Where and what to inject into the node input."""

    model_config = ConfigDict(extra="forbid")

    state_path: str = Field(
        ...,
        description=(
            "Dotted path into TutorGraphState where the payload is placed. "
            "Examples: 'event_data.message', 'evidence_pack.diagnosis_evidence.code', "
            "'evidence_pack.workflow_context.statement', 'last_cards', 'references'."
        ),
    )
    value: Any = Field(
        ...,
        description="The adversarial payload value to inject at state_path.",
    )


class AdversarialCase(BaseModel):
    """One adversarial test case."""

    model_config = ConfigDict(extra="forbid")

    id: str = Field(..., pattern=r"^adv-\d{3,4}$")
    phase: Phase
    target_node: str = Field(
        ...,
        description=(
            "tutor_graph node function name. Examples: 'chat_node', "
            "'error_feedback_node', 'ideating_node', 'skeleton_node', "
            "'parsons_node', 'ac_review_node'."
        ),
    )
    attack_category: AttackCategory
    cia: PedagogicalCIA = Field(
        ...,
        description=(
            "Which axis of the pedagogical CIA triad this attack targets. "
            "C=Confidentiality (don't leak answer); "
            "I=Integrity (don't let learner manipulate mastery); "
            "A=Availability (don't allow flooding/abuse)."
        ),
    )

    description: str = Field(
        ...,
        description="One-line description of the real-world student behavior this models.",
    )
    payload: list[PayloadInjection] = Field(
        ...,
        min_length=1,
        description="One or more injections to apply to the node input state.",
    )
    base_state_overrides: dict[str, Any] = Field(
        default_factory=dict,
        description=(
            "Optional overrides applied to the default base state before payload injection. "
            "Use this to set up scenario context (e.g., specific problem text, learner profile)."
        ),
    )
    expected_assertions: list[Assertion] = Field(
        ...,
        min_length=1,
        description="All assertions must hold for the case to pass.",
    )
    notes: str = Field(default="")


class AssertionResult(BaseModel):
    """Result of running one assertion."""

    model_config = ConfigDict(extra="forbid")

    assertion: Assertion
    passed: bool
    actual: Any = None
    failure_reason: str = ""


class CaseResult(BaseModel):
    """Result of running one adversarial case."""

    model_config = ConfigDict(extra="forbid")

    case_id: str
    passed: bool
    attempted: bool = Field(
        ...,
        description=(
            "Whether the node attempted to respond at all (vs failfast). "
            "Mirrors RedTeamCUA's Attempt Rate metric: a node that responds "
            "to a clearly malicious prompt has high AR, even if its output "
            "happens to be benign."
        ),
    )
    failfast: bool = Field(
        ...,
        description=(
            "Whether the node returned runtime_state=FAILED or threw. "
            "For some categories (e.g., schema injection), failfast is the "
            "correct behavior."
        ),
    )
    raw_output: dict[str, Any] = Field(default_factory=dict)
    assertion_results: list[AssertionResult] = Field(default_factory=list)
    error: str = ""


class DatasetSummary(BaseModel):
    """Aggregate report of a full dataset run."""

    model_config = ConfigDict(extra="forbid")

    total: int
    passed: int
    failed: int
    pass_rate: float
    attempt_rate: float = Field(
        ...,
        description="Fraction of cases where node attempted to respond (RedTeamCUA AR analog).",
    )
    failfast_rate: float
    by_phase: dict[Phase, dict[str, int]] = Field(default_factory=dict)
    by_category: dict[AttackCategory, dict[str, int]] = Field(default_factory=dict)
    by_cia: dict[PedagogicalCIA, dict[str, int]] = Field(default_factory=dict)
    failed_case_ids: list[str] = Field(default_factory=list)
