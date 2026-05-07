package com.alethicode.service.aitutor.parsons;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Reference solution → Parsons blocks 的切分器。
 *
 * <p>策略：</p>
 * <ul>
 *   <li>按物理行切分。空白行与纯注释行作为分隔符，不生成 block。</li>
 *   <li>缩进按每 4 空格 / 1 制表符为一级；不足 4 空格的混合缩进按 round-down。</li>
 *   <li>fading_state 选择：根据 {@link FadingDecision} 在「中段非函数签名 / 非 print 收尾」的候选块里
 *       依序挑选指定数量为 {@code FADED}，{@code fadingLevel=3} 再额外挑 1 个最深嵌套的为 {@code HIDDEN}。</li>
 *   <li>候选块不足时阶梯降级：实际渐隐数量取目标与候选数量的较小值；不抛错以保证发卡。</li>
 * </ul>
 *
 * <p>语言无关：默认按行切分，对所有题面参考代码可用。Python AST 微切分留作后续增强，
 * 当前 fadingLevel=3 通过隐藏更多块实现「认知负荷加大」的等价语义。</p>
 */
@Service
public class ParsonsTokenSegmenter {

    private static final Set<String> SIGNATURE_PREFIXES = new LinkedHashSet<>(List.of(
            "def ", "class ", "import ", "from ", "async def "
    ));

    public List<ParsonsBlock> segment(String referenceCode, FadingDecision decision) {
        if (referenceCode == null || referenceCode.isBlank()) {
            throw new IllegalArgumentException("reference code is blank");
        }
        List<RawLine> lines = parseLines(referenceCode);
        if (lines.size() < 2) {
            throw new IllegalArgumentException("reference code must contain at least 2 statements for parsons");
        }

        List<ParsonsBlock> blocks = new ArrayList<>(lines.size());
        for (int i = 0; i < lines.size(); i++) {
            RawLine ln = lines.get(i);
            blocks.add(new ParsonsBlock(
                    "B" + i,
                    ln.code,
                    ln.indent,
                    ParsonsBlock.FadingState.VISIBLE,
                    null
            ));
        }

        if (decision == null || decision.fadingLevel() <= 0) {
            return blocks;
        }

        List<Integer> fadeCandidates = pickFadeCandidates(blocks);
        int fadedTarget = Math.min(decision.fadedCount(), fadeCandidates.size());
        Set<Integer> fadedIndexes = new LinkedHashSet<>();
        for (int i = 0; i < fadedTarget; i++) {
            fadedIndexes.add(fadeCandidates.get(i));
        }
        // fading_level=3 额外把最深嵌套的候选块标 hidden
        Integer hiddenIndex = null;
        if (decision.fadingLevel() >= 3) {
            hiddenIndex = pickDeepestRemaining(blocks, fadedIndexes);
            if (hiddenIndex != null) {
                fadedIndexes.remove(hiddenIndex);
            }
        }

        List<ParsonsBlock> result = new ArrayList<>(blocks.size());
        for (int i = 0; i < blocks.size(); i++) {
            ParsonsBlock b = blocks.get(i);
            if (hiddenIndex != null && i == hiddenIndex) {
                result.add(new ParsonsBlock(b.id(), b.code(), b.indent(),
                        ParsonsBlock.FadingState.HIDDEN, fadeHintFor(b)));
            } else if (fadedIndexes.contains(i)) {
                result.add(new ParsonsBlock(b.id(), b.code(), b.indent(),
                        ParsonsBlock.FadingState.FADED, fadeHintFor(b)));
            } else {
                result.add(b);
            }
        }
        return result;
    }

    private List<Integer> pickFadeCandidates(List<ParsonsBlock> blocks) {
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < blocks.size(); i++) {
            if (i == 0 || i == blocks.size() - 1) {
                continue; // 保留首尾锚点
            }
            ParsonsBlock b = blocks.get(i);
            String trimmed = b.code().trim();
            if (isSignatureLine(trimmed)) {
                continue;
            }
            indices.add(i);
        }
        // 高缩进优先（核心控制流），稳定排序
        indices.sort((a, b) -> {
            int da = blocks.get(a).indent();
            int db = blocks.get(b).indent();
            if (da != db) return Integer.compare(db, da);
            return Integer.compare(a, b);
        });
        return indices;
    }

    private Integer pickDeepestRemaining(List<ParsonsBlock> blocks, Set<Integer> excluded) {
        int bestIdx = -1;
        int bestIndent = -1;
        for (int i = 1; i < blocks.size() - 1; i++) {
            if (excluded.contains(i)) continue;
            ParsonsBlock b = blocks.get(i);
            String trimmed = b.code().trim();
            if (isSignatureLine(trimmed)) continue;
            if (b.indent() > bestIndent) {
                bestIndent = b.indent();
                bestIdx = i;
            }
        }
        return bestIdx < 0 ? null : bestIdx;
    }

    private static boolean isSignatureLine(String trimmed) {
        for (String p : SIGNATURE_PREFIXES) {
            if (trimmed.startsWith(p)) return true;
        }
        return false;
    }

    private static String fadeHintFor(ParsonsBlock b) {
        String trimmed = b.code().trim();
        if (trimmed.startsWith("if ")) return "条件判断";
        if (trimmed.startsWith("for ")) return "循环遍历";
        if (trimmed.startsWith("while ")) return "循环条件";
        if (trimmed.startsWith("return")) return "函数返回";
        if (trimmed.startsWith("print")) return "结果输出";
        if (trimmed.contains("=") && !trimmed.contains("==")) return "变量赋值";
        return "关键步骤";
    }

    private List<RawLine> parseLines(String code) {
        List<RawLine> result = new ArrayList<>();
        for (String raw : code.split("\\r?\\n")) {
            if (raw == null) continue;
            int indent = countIndent(raw);
            String body = raw.substring(Math.min(raw.length(), countLeadingWhitespaceChars(raw)));
            String stripped = body.stripTrailing();
            if (stripped.isBlank()) continue;
            if (stripped.startsWith("#")) continue;
            result.add(new RawLine(stripped, indent));
        }
        return result;
    }

    private static int countIndent(String raw) {
        int spaces = 0;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == ' ') spaces++;
            else if (c == '\t') spaces += 4;
            else break;
        }
        return spaces / 4;
    }

    private static int countLeadingWhitespaceChars(String raw) {
        int n = 0;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == ' ' || c == '\t') n++;
            else break;
        }
        return n;
    }

    private record RawLine(String code, int indent) {
    }
}
