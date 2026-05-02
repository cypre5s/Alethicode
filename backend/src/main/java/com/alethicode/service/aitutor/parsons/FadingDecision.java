package com.alethicode.service.aitutor.parsons;

/**
 * AdaptiveFadingPolicy 输出的渐退决策。
 *
 * @param fadingLevel       0/1/2/3，0=全部 visible，3=micro AST 切分
 * @param fadedCount        需要把多少个 block 渲染为 faded（学生能看到提示但需补全）
 * @param distractorCount   需要附加多少个干扰块
 */
public record FadingDecision(int fadingLevel, int fadedCount, int distractorCount) {
}
