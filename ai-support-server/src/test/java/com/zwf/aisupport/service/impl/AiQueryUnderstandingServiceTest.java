package com.zwf.aisupport.service.impl;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiQueryUnderstandingServiceTest {

    private final AiQueryUnderstandingService service = new AiQueryUnderstandingService();

    @Test
    void preservesLateDomainConceptsInLongTimelineQuestions() {
        AiQueryPlan plan = service.analyze("""
                A production user logged in at 09:00. An administrator changed the role from USER to ADMIN
                in PostgreSQL at 10:00. The existing session still receives 403. What is the likely cause
                given the JWT and Redis architecture?
                """);

        assertThat(plan.retrievalTerms()).contains("postgresql", "403", "jwt", "redis", "role");
        assertThat(plan.domainCandidates()).contains("Auth / JWT", "PostgreSQL", "Redis Cache");
        assertThat(plan.intent()).isEqualTo(AiAnswerIntent.TROUBLESHOOTING);
    }

    @Test
    void marksCurrentOfficialDocumentationQuestionsAsExternalEvidenceRequired() {
        AiQueryPlan plan = service.analyze(
                "According to the current official Kubernetes documentation, what is ValidatingAdmissionPolicy?"
        );

        assertThat(plan.externalEvidenceRequired()).isTrue();
        assertThat(plan.domainCandidates()).contains("Kubernetes");
    }

    @Test
    void preservesAllConceptsInCompoundTechnicalQuestions() {
        AiQueryPlan plan = service.analyze(
                "What is the difference between ARG and ENV in a Dockerfile, and why use a .dockerignore file?"
        );

        assertThat(plan.requiredConcepts()).contains("arg", "env", "dockerfile", ".dockerignore");
        assertThat(plan.domainCandidates()).contains("Docker");
    }
}
