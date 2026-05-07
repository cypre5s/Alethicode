package com.alethicode.service.aitutor.parsons;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ParsonsTokenSegmenter 单元测试：
 * 行级切分、缩进映射、空白与注释过滤、首尾锚点保留、faded/hidden 选择、
 * 候选不足时的阶梯降级、空 / 单行参考代码的 failfast。
 */
class ParsonsTokenSegmenterTest {

    private final ParsonsTokenSegmenter segmenter = new ParsonsTokenSegmenter();

    @Test
    void segmentSplitsByLineAndStripsBlankAndComment() {
        String code = """
                a = int(input())
                # 这一行是注释，要丢
                
                b = a + 1
                print(b)
                """;
        List<ParsonsBlock> blocks = segmenter.segment(code, new FadingDecision(0, 0, 0));
        assertThat(blocks).hasSize(3);
        assertThat(blocks).extracting(ParsonsBlock::code)
                .containsExactly("a = int(input())", "b = a + 1", "print(b)");
        assertThat(blocks).extracting(ParsonsBlock::indent).containsOnly(0);
        assertThat(blocks).extracting(ParsonsBlock::id).containsExactly("B0", "B1", "B2");
        assertThat(blocks).extracting(ParsonsBlock::fadingState)
                .containsOnly(ParsonsBlock.FadingState.VISIBLE);
    }

    @Test
    void segmentMapsTabIndentToFourSpaceLevels() {
        String code = "for i in range(3):\n\tprint(i)\n";
        List<ParsonsBlock> blocks = segmenter.segment(code, new FadingDecision(0, 0, 0));
        assertThat(blocks).hasSize(2);
        assertThat(blocks.get(0).indent()).isZero();
        assertThat(blocks.get(1).indent()).isEqualTo(1);
    }

    @Test
    void segmentLevel1FadesOneNonAnchorNonSignatureLine() {
        String code = """
                def solve():
                    n = int(input())
                    total = 0
                    for i in range(n):
                        total += i
                    print(total)
                """;
        FadingDecision decision = new FadingDecision(1, 1, 1);
        List<ParsonsBlock> blocks = segmenter.segment(code, decision);

        Map<ParsonsBlock.FadingState, Long> counts = blocks.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        ParsonsBlock::fadingState, java.util.stream.Collectors.counting()));
        assertThat(counts.getOrDefault(ParsonsBlock.FadingState.FADED, 0L)).isEqualTo(1L);
        assertThat(counts.getOrDefault(ParsonsBlock.FadingState.HIDDEN, 0L)).isZero();
        ParsonsBlock first = blocks.get(0);
        ParsonsBlock last = blocks.get(blocks.size() - 1);
        assertThat(first.fadingState()).isEqualTo(ParsonsBlock.FadingState.VISIBLE);
        assertThat(last.fadingState()).isEqualTo(ParsonsBlock.FadingState.VISIBLE);
        for (ParsonsBlock b : blocks) {
            String trimmed = b.code().trim();
            if (trimmed.startsWith("def ")) {
                assertThat(b.fadingState())
                        .as("def 签名行不应被 faded")
                        .isEqualTo(ParsonsBlock.FadingState.VISIBLE);
            }
        }
    }

    @Test
    void segmentLevel3MarksDeepestRemainingBlockAsHidden() {
        String code = """
                def solve():
                    n = int(input())
                    total = 0
                    for i in range(n):
                        if i % 2 == 0:
                            total += i
                        else:
                            total -= i
                    return total
                """;
        List<ParsonsBlock> blocks = segmenter.segment(code, new FadingDecision(3, 3, 3));
        long hidden = blocks.stream()
                .filter(b -> b.fadingState() == ParsonsBlock.FadingState.HIDDEN)
                .count();
        long faded = blocks.stream()
                .filter(b -> b.fadingState() == ParsonsBlock.FadingState.FADED)
                .count();
        assertThat(hidden).isEqualTo(1L);
        assertThat(faded).isEqualTo(3L);
        ParsonsBlock hiddenBlock = blocks.stream()
                .filter(b -> b.fadingState() == ParsonsBlock.FadingState.HIDDEN)
                .findFirst().orElseThrow();
        assertThat(hiddenBlock.indent())
                .as("hidden 应该挑剩余候选中最深嵌套的行")
                .isEqualTo(2);
    }

    @Test
    void segmentDegradesWhenCandidatesAreInsufficient() {
        String code = """
                a = 1
                b = 2
                """;
        List<ParsonsBlock> blocks = segmenter.segment(code, new FadingDecision(2, 2, 2));
        assertThat(blocks).hasSize(2);
        assertThat(blocks).extracting(ParsonsBlock::fadingState)
                .containsOnly(ParsonsBlock.FadingState.VISIBLE);
    }

    @Test
    void segmentRejectsEmptyOrSingleLineReferenceCode() {
        assertThatThrownBy(() -> segmenter.segment("", new FadingDecision(0, 0, 0)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> segmenter.segment("only_one_line()\n", new FadingDecision(0, 0, 0)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
