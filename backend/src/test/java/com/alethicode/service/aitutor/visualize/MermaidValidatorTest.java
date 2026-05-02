package com.alethicode.service.aitutor.visualize;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MermaidValidatorTest {

    private final MermaidValidator validator = new MermaidValidator();

    @Test
    void acceptsFlowchartTd() {
        assertThatCode(() -> validator.validate("flowchart TD\n  A --> B"))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsQuotedFlowchartLabelsWithParentheses() {
        assertThatCode(() -> validator.validate("""
                flowchart LR
                  Start["Start loop for range(5)"]
                  Start --> I0["i=0: print(0)"]
                  I0 --> End["End loop"]
                """))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsGraphTdAsLegacyAlias() {
        assertThatCode(() -> validator.validate("graph TD\n  A --> B\n  B --> C"))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsGraphLrAsLegacyAlias() {
        assertThatCode(() -> validator.validate("graph LR\n  start --> finish"))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsAllGraphDirections() {
        for (String direction : new String[]{"TD", "LR", "TB", "BT", "RL"}) {
            assertThatCode(() -> validator.validate("graph " + direction + "\n  A --> B"))
                    .as("graph %s", direction)
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void acceptsSequenceDiagram() {
        assertThatCode(() -> validator.validate("sequenceDiagram\n  Alice->>Bob: hi"))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsStateDiagramV2() {
        assertThatCode(() -> validator.validate("stateDiagram-v2\n  [*] --> Idle"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsEmptyPayload() {
        assertThatThrownBy(() -> validator.validate(""))
                .isInstanceOf(VisualizeValidationException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void rejectsUnsupportedHeader() {
        assertThatThrownBy(() -> validator.validate("pie title Tasks\n  \"todo\" : 1"))
                .isInstanceOf(VisualizeValidationException.class)
                .hasMessageContaining("flowchart|graph");
    }

    @Test
    void rejectsUnquotedFlowchartLabelsWithParentheses() {
        assertThatThrownBy(() -> validator.validate("""
                flowchart LR
                  Start[Start loop for range(5)]
                  Start --> I0[i=0: print(0)]
                """))
                .isInstanceOf(VisualizeValidationException.class)
                .hasMessageContaining("quote flowchart labels");
    }

    @Test
    void rejectsForbiddenSubgraphKeyword() {
        assertThatThrownBy(() -> validator.validate("graph TD\nsubgraph foo\nend"))
                .isInstanceOf(VisualizeValidationException.class)
                .hasMessageContaining("forbidden keyword");
    }

    @Test
    void rejectsScriptInjection() {
        assertThatThrownBy(() -> validator.validate("flowchart TD\n  A[<script>alert(1)</script>]"))
                .isInstanceOf(VisualizeValidationException.class)
                .hasMessageContaining("forbidden fragment");
    }

    @Test
    void rejectsExceedingLineCap() {
        StringBuilder builder = new StringBuilder("graph TD\n");
        for (int i = 0; i < 60; i++) {
            builder.append("  N").append(i).append(" --> N").append(i + 1).append('\n');
        }
        assertThatThrownBy(() -> validator.validate(builder.toString()))
                .isInstanceOf(VisualizeValidationException.class)
                .hasMessageContaining("exceeds limit");
    }
}
