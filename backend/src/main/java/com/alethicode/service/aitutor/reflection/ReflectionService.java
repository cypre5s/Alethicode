package com.alethicode.service.aitutor.reflection;

import com.alethicode.service.aitutor.contract.CardType;

import java.util.Map;

/**
 * Producer-Critic 质量门禁，用证据评估并按需修正 LLM 输出。
 */
public interface ReflectionService {

    /**
     * 对生成卡片执行 Critic 检查，失败时执行一次 Refine。
     *
     * @param cardType 用于选择 Critic Rubric 的卡片类型
     * @param evidence 传给 Producer 的原始证据与上下文
     * @param initialOutput 首次 LLM 生成的卡片载荷
     * @param maxRounds 最大 Critic→Refine 轮数
     * @return 被接受的输出，可能是原始输出或修正后输出
     */
    ReflectionResult reflectAndRefine(
            CardType cardType,
            Map<String, Object> evidence,
            Map<String, Object> initialOutput,
            int maxRounds
    );
}
