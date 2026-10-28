package com.zwf.aisupport.service.impl;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeCoverageEvaluatorTest {

    private final KnowledgeCoverageEvaluator evaluator = new KnowledgeCoverageEvaluator();

    @Test
    void rejectsSameDomainEvidenceThatMissesTheCoreMechanism() {
        AiQueryPlan plan = new AiQueryPlan(
                "Why is a JWT rejected because of nbf?",
                List.of(),
                List.of("jwt", "nbf"),
                List.of("jwt", "nbf"),
                List.of("direct answer"),
                List.of("jwt", "nbf"),
                List.of("Auth / JWT"),
                false,
                AiAnswerIntent.TROUBLESHOOTING
        );

        KnowledgeCoverageEvaluator.Coverage coverage = evaluator.evaluate(
                "JWT authentication uses bearer tokens. A 403 response means authorization failed.",
                plan
        );

        assertThat(coverage.sufficient()).isFalse();
        assertThat(coverage.missingAnchors()).contains("nbf");
    }

    @Test
    void acceptsEvidenceThatCoversEveryRequiredConcept() {
        AiQueryPlan plan = new AiQueryPlan(
                "Why is a JWT rejected because of nbf?",
                List.of(),
                List.of("jwt", "nbf"),
                List.of("jwt", "nbf"),
                List.of("direct answer"),
                List.of("jwt", "nbf"),
                List.of("Auth / JWT"),
                false,
                AiAnswerIntent.TROUBLESHOOTING
        );

        KnowledgeCoverageEvaluator.Coverage coverage = evaluator.evaluate(
                "A JWT with an nbf claim must not be accepted before that timestamp; check clock skew.",
                plan
        );

        assertThat(coverage.sufficient()).isTrue();
        assertThat(coverage.missingConcepts()).isEmpty();
    }
}
