package com.zwf.aisupport.service.model;

import java.math.BigDecimal;
import java.util.Optional;

public record LlmGenerationResult(String answer,
                                  String provider,
                                  String model,
                                  Integer inputTokens,
                                  Integer outputTokens,
                                  Integer totalTokens,
                                  BigDecimal estimatedCost,
                                  String currency,
                                  boolean usageEstimated,
                                  int retryCount,
                                  long latencyMs,
                                  String requestStatus,
                                  LlmErrorCategory errorCategory,
                                  String safeErrorMessage) {

    public Optional<String> answerOptional() {
        return Optional.ofNullable(answer).filter(value -> !value.isBlank());
    }

    public boolean succeeded() {
        return "SUCCEEDED".equals(requestStatus) && answer != null && !answer.isBlank();
    }

    public static LlmGenerationResult skipped(String provider,
                                              String model,
                                              LlmErrorCategory category,
                                              String message) {
        return new LlmGenerationResult(
                null, provider, model, null, null, null, null, "USD", false,
                0, 0, "FAILED", category, message
        );
    }
}
