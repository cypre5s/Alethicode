package com.alethicode.service.languagepack.quality;

import com.alethicode.util.HashUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 题目 title 去重 v2：把"光看 source_title 同名"升级为
 * 「{@code (description_md5, source_title)} 双键 + 同名变体自动加 V1/V2 后缀」。
 *
 * <p>对应设计稿 D9。两个题包在同一 language_pack 里：</p>
 * <ul>
 *   <li>双键完全一致 → 视为重复，保留 page_range 在前的、删后者；</li>
 *   <li>{@code description_md5} 同但 {@code source_title} 不同 → 保留两者（题面完全一样但来源不同的情况几乎不出现，按设计保守保留）；</li>
 *   <li>{@code source_title} 同但 {@code description_md5} 不同 → 题面有差异但同名（PPT5-9 / PPT5-10），保留两者并自动加 V1/V2 后缀。</li>
 * </ul>
 */
@Service
public class TitleDedupV2Service {

    private static final Logger log = LoggerFactory.getLogger(TitleDedupV2Service.class);

    public List<DedupResult> dedup(List<DedupCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        Map<String, List<DedupResult>> bySignature = new LinkedHashMap<>();
        Map<String, List<DedupResult>> bySourceTitle = new LinkedHashMap<>();
        List<DedupResult> output = new ArrayList<>();

        for (DedupCandidate candidate : candidates) {
            String descriptionMd5 = HashUtils.sha256(normalizeDescription(candidate.description()));
            String sourceTitle = candidate.sourceTitle() == null ? "" : candidate.sourceTitle().strip();
            String signatureKey = descriptionMd5 + "|" + sourceTitle;

            List<DedupResult> sameSig = bySignature.get(signatureKey);
            if (sameSig != null && !sameSig.isEmpty()) {
                DedupResult existing = sameSig.get(0);
                if (pageRangeStartsBefore(candidate, existing.candidate())) {
                    DedupResult dropped = new DedupResult(
                            existing.candidate(),
                            existing.candidate().title(),
                            DedupAction.DROPPED_DUPLICATE,
                            "duplicate of (" + candidate.displayId() + "); kept earlier page_range",
                            descriptionMd5,
                            signatureKey
                    );
                    int idx = output.indexOf(existing);
                    if (idx >= 0) {
                        output.set(idx, dropped);
                    }
                    sameSig.set(0, dropped);
                    DedupResult kept = new DedupResult(
                            candidate,
                            candidate.title(),
                            DedupAction.KEPT,
                            "",
                            descriptionMd5,
                            signatureKey
                    );
                    output.add(kept);
                    sameSig.add(kept);
                } else {
                    DedupResult dropped = new DedupResult(
                            candidate,
                            candidate.title(),
                            DedupAction.DROPPED_DUPLICATE,
                            "duplicate of (" + existing.candidate().displayId() + "); existing has earlier page_range",
                            descriptionMd5,
                            signatureKey
                    );
                    output.add(dropped);
                    sameSig.add(dropped);
                }
                continue;
            }

            List<DedupResult> sameTitle = bySourceTitle.get(sourceTitle);
            String renamed = candidate.title();
            DedupAction action = DedupAction.KEPT;
            String reason = "";
            if (sameTitle != null && !sameTitle.isEmpty() && !sourceTitle.isEmpty()) {
                int variantIndex = sameTitle.size();
                if (variantIndex == 1) {
                    DedupResult first = sameTitle.get(0);
                    if (first.action() == DedupAction.KEPT && !first.title().contains(" V")) {
                        DedupResult relabel = new DedupResult(
                                first.candidate(),
                                first.title() + " V1",
                                DedupAction.RENAMED_VARIANT,
                                "auto-suffix V1 to disambiguate from later variants",
                                first.descriptionMd5(),
                                first.signatureKey()
                        );
                        int idx = output.indexOf(first);
                        if (idx >= 0) {
                            output.set(idx, relabel);
                        }
                        sameTitle.set(0, relabel);
                        bySignature.get(first.signatureKey()).set(0, relabel);
                    }
                }
                renamed = candidate.title() + " V" + (variantIndex + 1);
                action = DedupAction.RENAMED_VARIANT;
                reason = "auto-suffix V" + (variantIndex + 1) + " to disambiguate variants of '"
                        + sourceTitle + "'";
            }

            DedupResult result = new DedupResult(
                    candidate,
                    renamed,
                    action,
                    reason,
                    descriptionMd5,
                    signatureKey
            );
            output.add(result);
            bySignature.computeIfAbsent(signatureKey, k -> new ArrayList<>()).add(result);
            if (!sourceTitle.isEmpty()) {
                bySourceTitle.computeIfAbsent(sourceTitle, k -> new ArrayList<>()).add(result);
            }
        }

        return output;
    }

    private boolean pageRangeStartsBefore(DedupCandidate a, DedupCandidate b) {
        Integer pa = a.pageRangeStart();
        Integer pb = b.pageRangeStart();
        if (pa == null && pb == null) {
            return false;
        }
        if (pa == null) {
            return false;
        }
        if (pb == null) {
            return true;
        }
        return pa < pb;
    }

    /**
     * 题面归一化：删除 hint / common_mistakes 等辅助字段后做 NFKC + 多空白合并 + 大小写归一。
     */
    String normalizeDescription(String rawDescription) {
        if (rawDescription == null || rawDescription.isBlank()) {
            return "";
        }
        String body = rawDescription;
        String normalized = Normalizer.normalize(body, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .strip();
        return normalized;
    }

    /**
     * 输入候选：除了 displayId/title，还需要 description（用于 md5 双键）、
     * sourceTitle（用于命中变体）、pageRangeStart（用于决定保留哪一个）。
     */
    public record DedupCandidate(
            String displayId,
            String title,
            String description,
            String sourceTitle,
            Integer pageRangeStart,
            Integer pageRangeEnd
    ) {}

    public record DedupResult(
            DedupCandidate candidate,
            String title,
            DedupAction action,
            String reason,
            String descriptionMd5,
            String signatureKey
    ) {}

    public enum DedupAction {
        /** 该题包保留入库。 */
        KEPT,
        /** 与已有题包 description+source_title 双键完全一致，丢弃。 */
        DROPPED_DUPLICATE,
        /** 与已有题包 source_title 同名但题面不同，自动加 V1/V2/... 后缀。 */
        RENAMED_VARIANT
    }
}
