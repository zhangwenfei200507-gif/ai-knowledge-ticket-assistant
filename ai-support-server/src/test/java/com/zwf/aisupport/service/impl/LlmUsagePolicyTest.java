package com.zwf.aisupport.service.impl;

import com.zwf.aisupport.config.AiRoutingProperties;
import com.zwf.aisupport.vo.AiAnswerSourceVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LlmUsagePolicyTest {

    @Test
    void selectSourcesLimitsCountAndPreviewSize() {
        AiRoutingProperties properties = new AiRoutingProperties();
        properties.setMaxGroundedSources(2);
        properties.setMaxPreviewCharsPerSource(20);
        LlmUsagePolicy policy = new LlmUsagePolicy(properties);

        List<AiAnswerSourceVO> selected = policy.selectSourcesForGroundedSynthesis(List.of(
                source("WEB", 0.99, "web evidence should be shorter than this long raw page text"),
                source("ARTICLE", 0.10, "article evidence ".repeat(30)),
                source("DOCUMENT", 0.50, "document evidence should also be considered")
        ));

        assertThat(selected).hasSize(2);
        assertThat(selected.get(0).getSourceType()).isEqualTo("ARTICLE");
        assertThat(selected.get(0).getPreviewContent()).hasSizeLessThanOrEqualTo(300);
    }

    @Test
    void directFallbackRejectsOversizedQuestions() {
        AiRoutingProperties properties = new AiRoutingProperties();
        properties.setMaxQuestionCharsForDirectFallback(120);
        LlmUsagePolicy policy = new LlmUsagePolicy(properties);

        assertThat(policy.canUseDirectFallback("Why is Redis slow?")).isTrue();
        assertThat(policy.canUseDirectFallback("x".repeat(121))).isFalse();
    }

    @Test
    void groundedSynthesisAllowsGroundedInternalAndWebSources() {
        LlmUsagePolicy policy = new LlmUsagePolicy(new AiRoutingProperties());

        assertThat(policy.canUseGroundedSynthesis("WEB", List.of(source("WEB", 0.90, "official web evidence")))).isTrue();
        assertThat(policy.canUseGroundedSynthesis("ARTICLE", List.of(source("ARTICLE", 0.90, "internal knowledge")))).isTrue();
        assertThat(policy.canUseGroundedSynthesis("DOCUMENT", List.of(source("DOCUMENT", 0.90, "uploaded document")))).isTrue();
    }

    @Test
    void directFallbackRequiresProjectDomainQuestion() {
        LlmUsagePolicy policy = new LlmUsagePolicy(new AiRoutingProperties());

        assertThat(policy.canUseDirectFallback("Why does my PostgreSQL connection timeout?")).isTrue();
        assertThat(policy.canUseDirectFallback("Write a poem about summer vacation")).isFalse();
    }

    private AiAnswerSourceVO source(String type, Double score, String preview) {
        AiAnswerSourceVO source = new AiAnswerSourceVO();
        source.setSourceType(type);
        source.setSourceScore(score);
        source.setPreviewContent(preview);
        return source;
    }
}
