package com.alethicode.service.languagepack.quality;

import com.alethicode.service.languagepack.quality.TitleDedupV2Service.DedupAction;
import com.alethicode.service.languagepack.quality.TitleDedupV2Service.DedupCandidate;
import com.alethicode.service.languagepack.quality.TitleDedupV2Service.DedupResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TitleDedupV2ServiceTest {

    private final TitleDedupV2Service service = new TitleDedupV2Service();

    @Test
    void shouldKeepBothWhenSameSourceTitleButDifferentDescription() {
        DedupCandidate first = new DedupCandidate(
                "PPT5-9", "举例：成绩统计", "用 A B F 三档统计学生成绩",
                "ppt5-grade", 12, 13
        );
        DedupCandidate second = new DedupCandidate(
                "PPT5-10", "举例：成绩统计", "用百分位带格式化输出统计成绩",
                "ppt5-grade", 14, 15
        );

        List<DedupResult> results = service.dedup(List.of(first, second));

        assertThat(results).hasSize(2);
        assertThat(results.get(0).action()).isEqualTo(DedupAction.RENAMED_VARIANT);
        assertThat(results.get(1).action()).isEqualTo(DedupAction.RENAMED_VARIANT);
        assertThat(results.get(0).title()).isEqualTo("举例：成绩统计 V1");
        assertThat(results.get(1).title()).isEqualTo("举例：成绩统计 V2");
    }

    @Test
    void shouldDropDuplicateWhenBothSourceTitleAndDescriptionAreIdentical() {
        DedupCandidate earlier = new DedupCandidate(
                "PPT5-9", "举例：成绩统计", "完全相同的题面",
                "ppt5-grade", 12, 13
        );
        DedupCandidate later = new DedupCandidate(
                "PPT5-10", "举例：成绩统计", "完全相同的题面",
                "ppt5-grade", 14, 15
        );

        List<DedupResult> results = service.dedup(List.of(earlier, later));

        assertThat(results.get(0).action()).isEqualTo(DedupAction.KEPT);
        assertThat(results.get(1).action()).isEqualTo(DedupAction.DROPPED_DUPLICATE);
        assertThat(results.get(1).reason()).contains("duplicate");
    }

    @Test
    void shouldKeepEarlierPageRangeWhenDuplicatesAreOutOfOrder() {
        DedupCandidate later = new DedupCandidate(
                "PPT5-10", "举例：成绩统计", "完全相同的题面",
                "ppt5-grade", 14, 15
        );
        DedupCandidate earlier = new DedupCandidate(
                "PPT5-9", "举例：成绩统计", "完全相同的题面",
                "ppt5-grade", 12, 13
        );

        List<DedupResult> results = service.dedup(List.of(later, earlier));

        DedupResult laterResult = results.get(0);
        DedupResult earlierResult = results.get(1);
        assertThat(laterResult.action()).isEqualTo(DedupAction.DROPPED_DUPLICATE);
        assertThat(earlierResult.action()).isEqualTo(DedupAction.KEPT);
    }

    @Test
    void shouldKeepBothWhenSourceTitlesDiffer() {
        DedupCandidate a = new DedupCandidate(
                "PPT2-1", "圆面积计算", "求圆面积",
                "ppt2-circle", 1, 2
        );
        DedupCandidate b = new DedupCandidate(
                "PPT3-1", "字符串拼接", "拼接字符串",
                "ppt3-concat", 3, 4
        );

        List<DedupResult> results = service.dedup(List.of(a, b));

        assertThat(results).extracting(DedupResult::action)
                .containsExactly(DedupAction.KEPT, DedupAction.KEPT);
    }

    @Test
    void shouldRenameThirdVariantToV3() {
        DedupCandidate a = new DedupCandidate(
                "P1", "举例：成绩统计", "题面一",
                "stat", 1, 2
        );
        DedupCandidate b = new DedupCandidate(
                "P2", "举例：成绩统计", "题面二",
                "stat", 3, 4
        );
        DedupCandidate c = new DedupCandidate(
                "P3", "举例：成绩统计", "题面三",
                "stat", 5, 6
        );

        List<DedupResult> results = service.dedup(List.of(a, b, c));

        assertThat(results.get(0).title()).isEqualTo("举例：成绩统计 V1");
        assertThat(results.get(1).title()).isEqualTo("举例：成绩统计 V2");
        assertThat(results.get(2).title()).isEqualTo("举例：成绩统计 V3");
    }

    @Test
    void shouldHandleEmptyAndNullCandidatesGracefully() {
        assertThat(service.dedup(null)).isEmpty();
        assertThat(service.dedup(List.of())).isEmpty();
    }

    @Test
    void shouldNormalizeDescriptionForComparingMd5() {
        TitleDedupV2Service explicit = new TitleDedupV2Service();
        String a = explicit.normalizeDescription("Hello World\u3000Lorem  ipsum");
        String b = explicit.normalizeDescription("hello world lorem ipsum");
        assertThat(a).isEqualTo(b);
    }
}
