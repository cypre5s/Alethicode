package com.alethicode.service.languagepack.quality;

import com.alethicode.service.languagepack.LanguagePackProblemPackage;
import com.alethicode.service.languagepack.LanguagePackProblemPackage.Sample;
import com.alethicode.service.languagepack.LanguagePackProblemPackage.TestCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用 reference solution 跑出的 actualOutput 覆盖 LLM 生成的 sample.output。
 *
 * <p>设计稿 D6：sample.output 不允许由 LLM 单独写，必须等于 reference(sample.input)。
 * 来源数据由 {@link ReferenceSolutionSelfValidator} 已经计算的 case 结果提供，
 * Synchronizer 只做"对账+覆盖"，不再触发额外 judge 调用，避免 init 链路时间膨胀。</p>
 */
@Service
public class SamplesSynchronizer {

    private static final Logger log = LoggerFactory.getLogger(SamplesSynchronizer.class);

    /**
     * 用 self-validation 报告里的 actualOutput 同步覆盖 sample.output。
     * 没有匹配的 sample.input → 保留原 sample（fail-fast 由上游 SelfValidator 抛出）。
     *
     * @return 新的题包；samples 中 output 已与 reference 一致
     */
    public LanguagePackProblemPackage synchronize(LanguagePackProblemPackage pkg,
                                                  SelfValidationReport report) {
        if (pkg == null || report == null) {
            throw new IllegalArgumentException("pkg / report 不能为空");
        }
        if (!report.allPassed()) {
            log.debug("self-validation 未通过，跳过 samples 同步：{}", pkg.displayId());
            return pkg;
        }

        List<TestCase> testCases = pkg.testCases() == null ? List.of() : pkg.testCases();
        List<Sample> samples = pkg.samples() == null ? List.of() : pkg.samples();
        if (samples.isEmpty() || testCases.isEmpty()) {
            return pkg;
        }

        Map<String, String> inputToActual = new LinkedHashMap<>();
        List<SelfValidationCaseResult> caseResults = report.testCaseResults();
        for (int i = 0; i < testCases.size() && i < caseResults.size(); i++) {
            inputToActual.put(testCases.get(i).input(), caseResults.get(i).actualOutput());
        }

        List<Sample> synced = new ArrayList<>(samples.size());
        boolean changed = false;
        for (Sample sample : samples) {
            String actual = inputToActual.get(sample.input());
            if (actual == null) {
                synced.add(sample);
                continue;
            }
            if (!actual.equals(sample.output())) {
                synced.add(new Sample(sample.input(), actual));
                changed = true;
            } else {
                synced.add(sample);
            }
        }

        if (!changed) {
            return pkg;
        }
        return pkg.withOverwrittenOutputs(List.copyOf(synced), List.copyOf(testCases));
    }
}
