package com.alethicode.service.languagepack.quality;

import com.alethicode.service.languagepack.LanguagePackProblemPackage;
import com.alethicode.service.languagepack.LanguagePackProblemPackage.Sample;
import com.alethicode.service.languagepack.LanguagePackProblemPackage.TestCase;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SamplesSynchronizerTest {

    private final SamplesSynchronizer synchronizer = new SamplesSynchronizer();

    @Test
    void shouldOverwriteSampleOutputWithReferenceActualOutput() {
        LanguagePackProblemPackage pkg = buildPkg(
                List.of(new TestCase("5", "78.5398")),
                List.of(new Sample("5", "WRONG_OUTPUT"))
        );
        SelfValidationReport report = passingReport(
                pkg,
                List.of(new SelfValidationCaseResult(
                        "1",
                        SelfValidationCaseResult.STATUS_AC,
                        "78.5398",
                        "78.5398",
                        "",
                        0
                ))
        );

        LanguagePackProblemPackage synced = synchronizer.synchronize(pkg, report);

        assertThat(synced.samples()).hasSize(1);
        assertThat(synced.samples().get(0).output()).isEqualTo("78.5398");
    }

    @Test
    void shouldNotChangeSampleWhenInputDoesNotMatchTestCase() {
        LanguagePackProblemPackage pkg = buildPkg(
                List.of(new TestCase("5", "78.5398")),
                List.of(new Sample("MISMATCH_INPUT", "ORIGINAL"))
        );
        SelfValidationReport report = passingReport(
                pkg,
                List.of(new SelfValidationCaseResult(
                        "1",
                        SelfValidationCaseResult.STATUS_AC,
                        "78.5398",
                        "78.5398",
                        "",
                        0
                ))
        );

        LanguagePackProblemPackage synced = synchronizer.synchronize(pkg, report);

        assertThat(synced.samples().get(0).output()).isEqualTo("ORIGINAL");
    }

    @Test
    void shouldReturnSamePackageWhenSelfValidationFailed() {
        LanguagePackProblemPackage pkg = buildPkg(
                List.of(new TestCase("5", "78.5398")),
                List.of(new Sample("5", "OLD"))
        );
        SelfValidationReport failing = new SelfValidationReport(
                "PPT2-1",
                false,
                List.of(),
                List.of(),
                ReferenceLintReport.empty(),
                Optional.of("self-validation failed"),
                Optional.empty(),
                Duration.ZERO
        );

        LanguagePackProblemPackage synced = synchronizer.synchronize(pkg, failing);

        assertThat(synced).isSameAs(pkg);
    }

    @Test
    void shouldKeepOriginalSamplesWhenAlreadyMatching() {
        LanguagePackProblemPackage pkg = buildPkg(
                List.of(new TestCase("5", "78.5398")),
                List.of(new Sample("5", "78.5398"))
        );
        SelfValidationReport report = passingReport(
                pkg,
                List.of(new SelfValidationCaseResult(
                        "1",
                        SelfValidationCaseResult.STATUS_AC,
                        "78.5398",
                        "78.5398",
                        "",
                        0
                ))
        );

        LanguagePackProblemPackage synced = synchronizer.synchronize(pkg, report);

        assertThat(synced).isSameAs(pkg);
    }

    private SelfValidationReport passingReport(LanguagePackProblemPackage pkg,
                                               List<SelfValidationCaseResult> cases) {
        return new SelfValidationReport(
                pkg.displayId(),
                true,
                cases,
                List.of(),
                ReferenceLintReport.empty(),
                Optional.empty(),
                Optional.empty(),
                Duration.ofMillis(50)
        );
    }

    private LanguagePackProblemPackage buildPkg(List<TestCase> testCases, List<Sample> samples) {
        return new LanguagePackProblemPackage(
                "PPT2-1",
                "title",
                "desc",
                "input",
                "output",
                samples,
                testCases,
                Map.of("Python3", "print(0)"),
                3000,
                256,
                "Low",
                List.of(),
                List.of(),
                List.of(),
                "",
                List.of(),
                null,
                "Python3",
                "print(0)"
        );
    }
}
