"""Tests for card JSON Schema validation."""

import pytest

from app.nodes.schema_validation import validate_card_schema


class TestProblemGuideSchema:
    def test_valid(self):
        state = {
            "node_outputs": {
                "problem_guide": {
                    "problem_restatement": "题目描述",
                    "input_output_focus": "输入输出关注点",
                    "key_observation": "关键观察",
                    "starter_questions": ["问题1"],
                    "related_kcs": [],
                    "courseware_refs": [],
                }
            }
        }
        result = validate_card_schema(state)
        assert result.get("runtime_state") != "FAILED"

    def test_missing_required_field(self):
        state = {
            "node_outputs": {
                "problem_guide": {
                    "problem_restatement": "题目描述",
                }
            }
        }
        result = validate_card_schema(state)
        assert result["runtime_state"] == "FAILED"
        assert result["failure_bucket"] == "SCHEMA_VIOLATION"

    def test_stale_projection_visualize_metadata_is_stripped_before_validation(self):
        state = {
            "node_outputs": {
                "problem_guide": {
                    "problem_restatement": "题目描述",
                    "input_output_focus": "输入输出关注点",
                    "key_observation": "关键观察",
                    "starter_questions": ["问题1"],
                    "related_kcs": [],
                    "courseware_refs": [],
                    "visualize_card_id": "C-V-001",
                    "visualize": {
                        "intent": "flowchart",
                        "format": "mermaid",
                        "payload": "flowchart TD\nA-->B",
                    },
                    "visualize_failed": "dispatch failed",
                }
            }
        }
        result = validate_card_schema(state)
        assert result.get("runtime_state") != "FAILED"
        assert "visualize_card_id" not in result["node_outputs"]["problem_guide"]
        assert "visualize" not in result["node_outputs"]["problem_guide"]
        assert "visualize_failed" not in result["node_outputs"]["problem_guide"]


class TestErrorDiagnosisSchema:
    def test_valid(self):
        state = {
            "node_outputs": {
                "error_diagnosis": {
                    "root_cause": "索引越界",
                    "fix_direction": "检查循环范围",
                }
            }
        }
        result = validate_card_schema(state)
        assert result.get("runtime_state") != "FAILED"


class TestPostAcSchema:
    def test_valid(self):
        state = {
            "node_outputs": {
                "post_ac": {
                    "success_summary": "学生正确使用了循环",
                    "next_practice_direction": "尝试嵌套循环",
                    "courseware_refs": [],
                }
            }
        }
        result = validate_card_schema(state)
        assert result.get("runtime_state") != "FAILED"


class TestTransferProblemSchema:
    def test_valid(self):
        state = {
            "node_outputs": {
                "transfer": {
                    "title": "迁移题标题",
                    "description": "迁移题描述",
                }
            }
        }
        result = validate_card_schema(state)
        assert result.get("runtime_state") != "FAILED"


class TestAiReplySchema:
    def test_valid(self):
        state = {
            "node_outputs": {
                "chat": {
                    "content": "这是AI的回复",
                }
            }
        }
        result = validate_card_schema(state)
        assert result.get("runtime_state") != "FAILED"

    def test_valid_with_referenced_card_ids(self):
        state = {
            "node_outputs": {
                "chat": {
                    "content": "这张知识点回顾卡讲的是程序执行流程。",
                    "referenced_card_ids": ["C-K-12345678", "C-V-87654321"],
                }
            }
        }
        result = validate_card_schema(state)
        assert result.get("runtime_state") != "FAILED"


class TestIdeateAnalysisSchema:
    def test_null_optional_fields_are_removed_before_validation(self):
        state = {
            "node_outputs": {
                "ideate": {
                    "analysis": "先明确输入输出，再拆循环。",
                    "steps": ["确认 N", "生成偶数", "累加"],
                    "guiding_questions": ["range 的结束值该怎么写？"],
                    "misconception_alert": None,
                }
            }
        }

        result = validate_card_schema(state)

        assert result.get("runtime_state") != "FAILED"
        assert "misconception_alert" not in result["node_outputs"]["ideate"]


class TestKnowledgeReviewSchema:
    def test_valid(self):
        state = {
            "node_outputs": {
                "knowledge_review": {
                    "review_content": "知识点回顾内容",
                    "related_kcs": ["循环"],
                }
            }
        }
        result = validate_card_schema(state)
        assert result.get("runtime_state") != "FAILED"


class TestVisualizeSchema:
    def test_valid(self):
        state = {
            "node_outputs": {
                "visualize": {
                    "intent": "for_loop_trace",
                    "format": "mermaid",
                    "payload": "flowchart TD\nA-->B",
                    "alt_text": "for 循环流程图",
                    "source_role": "Yoshino",
                }
            }
        }
        result = validate_card_schema(state)
        assert result.get("runtime_state") != "FAILED"


class TestSkeletonCodeSchema:
    def test_missing_required_skeleton_field_fails(self):
        state = {
            "node_outputs": {
                "skeleton_code": {
                    "description": "先补全输入输出。",
                }
            }
        }
        result = validate_card_schema(state)
        assert result["runtime_state"] == "FAILED"
        assert result["failure_bucket"] == "SCHEMA_VIOLATION"


class TestParsonsProblemPassthrough:
    """Parsons card payload is generated and validated by Java backend.
    The Python graph side only transparently passes it through node_outputs['parsons'].
    Schema validation must NOT block parsons outputs."""

    def test_parsons_output_is_not_blocked_by_schema_validation(self):
        state = {
            "node_outputs": {
                "parsons": {
                    "parsons_session_id": "ps-abc123",
                    "fading_level": 2,
                    "blocks": [
                        {"id": "B0", "code": "a = 1", "indent": 0, "fading_state": "visible"},
                        {"id": "B1", "code": "print(a)", "indent": 0, "fading_state": "faded", "fade_hint": "输出"},
                    ],
                    "distractors": [],
                    "mastery_snapshot": {"routing": {}, "decision_at": "2026-04-28T00:00:00Z"},
                    "instructions": "拖拽排序",
                }
            }
        }
        result = validate_card_schema(state)
        assert result.get("runtime_state") != "FAILED"
        assert result["node_outputs"]["parsons"]["parsons_session_id"] == "ps-abc123"


class TestNoOutputsPassesValidation:
    def test_empty_outputs(self):
        state = {"node_outputs": {}}
        result = validate_card_schema(state)
        assert result.get("runtime_state") != "FAILED"
