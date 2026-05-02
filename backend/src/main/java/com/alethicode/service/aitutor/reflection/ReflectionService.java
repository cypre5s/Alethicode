package com.alethicode.service.aitutor.reflection;

import com.alethicode.service.aitutor.contract.CardType;

import java.util.Map;

/**
 * Producer-Critic quality gate: evaluates LLM-generated output against evidence,
 * and optionally refines it if the Critic flags issues.
 */
public interface ReflectionService {

    /**
     * Runs a Critic pass on a generated card and, if it fails, a single Refine pass.
     *
     * @param cardType       determines which Critic rubric to apply
     * @param evidence       original evidence/context fed to the Producer
     * @param initialOutput  the card payload produced by the first LLM call
     * @param maxRounds      max number of Critic→Refine cycles (typically 1-2)
     * @return the accepted output (original if Critic passes, refined otherwise)
     */
    ReflectionResult reflectAndRefine(
            CardType cardType,
            Map<String, Object> evidence,
            Map<String, Object> initialOutput,
            int maxRounds
    );
}
