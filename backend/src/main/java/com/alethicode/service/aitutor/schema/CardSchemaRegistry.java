package com.alethicode.service.aitutor.schema;

import com.alethicode.service.aitutor.contract.CardType;

import java.util.List;
import java.util.Map;

public class CardSchemaRegistry {

    private final Map<CardType, List<String>> requiredFields = Map.ofEntries(
            Map.entry(CardType.PROBLEM_GUIDE, List.of("plain_task", "problem_explanation", "input_translation", "output_translation", "approach_direction", "warmup_question", "courseware_refs")),
            Map.entry(CardType.IDEATE_ANALYSIS, List.of("understood_as", "step_plan", "has_logic_gap", "logic_gap_hint", "confidence_level")),
            Map.entry(CardType.FADED_EXAMPLE, List.of("scaffold_level", "mastery_snapshot", "fade_ratio", "steps", "student_blanks", "validation_status", "step_feedback")),
            Map.entry(CardType.ERROR_DIAGNOSIS, List.of("error_taxonomy", "root_cause", "what_program_is_doing", "expected_behavior", "fix_direction", "related_kcs")),
            Map.entry(CardType.EXECUTION_TRACE_EXPLAINER, List.of("status", "input_sample", "steps", "divergence_step", "failure_reason")),
            Map.entry(CardType.POST_AC, List.of("celebration", "what_you_learned", "key_success_point", "transfer_tip", "one_improvement", "recommended_review", "next_practice_direction")),
            Map.entry(CardType.TRANSFER_PROBLEM, List.of("title", "description", "input_description", "output_description", "hint", "reference_solution_code", "samples", "test_cases", "target_kcs")),
            Map.entry(CardType.KNOWLEDGE_REVIEW, List.of("reply", "kc_focus", "mastery_snapshot", "courseware_refs")),
            Map.entry(CardType.AI_REPLY, List.of("history")),
            Map.entry(CardType.VISUALIZE, List.of("intent", "format", "payload")),
            Map.entry(CardType.PARSONS_PROBLEM, List.of(
                    "parsons_session_id",
                    "fading_level",
                    "blocks",
                    "distractors",
                    "mastery_snapshot",
                    "instructions"
            ))
    );
    private final Map<CardType, List<String>> allowEmptyListFields = Map.ofEntries(
            Map.entry(CardType.PROBLEM_GUIDE, List.of("courseware_refs")),
            Map.entry(CardType.FADED_EXAMPLE, List.of("step_feedback")),
            Map.entry(CardType.EXECUTION_TRACE_EXPLAINER, List.of("steps")),
            Map.entry(CardType.KNOWLEDGE_REVIEW, List.of("courseware_refs")),
            Map.entry(CardType.PARSONS_PROBLEM, List.of("distractors"))
    );

    public List<String> requiredFields(CardType cardType) {
        return requiredFields.getOrDefault(cardType, List.of());
    }

    public boolean allowEmptyList(CardType cardType, String field) {
        return allowEmptyListFields.getOrDefault(cardType, List.of()).contains(field);
    }
}
