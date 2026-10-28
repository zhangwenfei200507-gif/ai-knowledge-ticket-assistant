package com.zwf.aisupport.service.impl;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GroundedAnswerGuardTest {

    private final GroundedAnswerGuard guard = new GroundedAnswerGuard();

    @Test
    void replacesModelReferencesWithVerifiedSources() {
        String answer = "## Direct Answer\nUse the service name.\n\n## References\n- https://made-up.example/fix";

        String result = guard.replaceReferencesWithVerifiedSources(
                answer,
                List.of("docs.docker.com - https://docs.docker.com/compose/how-tos/networking/")
        );

        assertThat(result).doesNotContain("made-up.example");
        assertThat(result).contains("docs.docker.com/compose/how-tos/networking/");
    }

    @Test
    void acceptsOnlyUrlsPresentInRetrievedSources() {
        String verifiedUrl = "https://docs.docker.com/compose/how-tos/networking/";
        assertThat(guard.containsOnlyVerifiedUrls("See " + verifiedUrl + ".", List.of(verifiedUrl))).isTrue();
        assertThat(guard.containsOnlyVerifiedUrls("See https://invented.example/fix", List.of(verifiedUrl))).isFalse();
        assertThat(guard.containsOnlyVerifiedUrls("No URLs are needed here.", List.of())).isTrue();
    }

    @Test
    void marksModelOnlyFallbackAsUnverifiedWithoutAddingGenericCauses() {
        String result = guard.markUnverifiedFallback("## Direct Answer\nThe model's concise answer.");

        assertThat(result).contains("Unverified general-model guidance");
        assertThat(result).doesNotContain("Likely Causes", "configuration, timing, version, or state mismatch");
    }
}
