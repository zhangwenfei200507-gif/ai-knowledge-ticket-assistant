package com.zwf.aisupport.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AiQuestionLog {

    private Long id;
    private Long userId;
    private String question;
    private String answer;
    private Long sourceArticleId;
    private String sourceTitle;
    private Boolean suggestCreateTicket;
    private Long createdTicketId;
    private String feedback;
    private String feedbackComment;
    private LocalDateTime feedbackTime;
    private LocalDateTime createdTime;
    private String sourceType;
    private Long sourceDocumentId;
    private String sourceDocumentName;
    private Long sourceChunkId;
    private Integer sourceChunkIndex;
    private String answerMode;
    private String answerEngine;
    private Boolean internalAttempted;
    private Boolean internalSufficient;
    private Boolean webAttempted;
    private Boolean webSucceeded;
    private Boolean webSufficient;
    private Boolean llmAttempted;
    private Boolean llmSucceeded;
    private String retrievalFailureReason;
    private Long totalLatencyMs;
    private String retrievalTraceJson;
    private String llmProvider;
    private String llmModel;
    private Integer llmInputTokens;
    private Integer llmOutputTokens;
    private Integer llmTotalTokens;
    private java.math.BigDecimal llmEstimatedCost;
    private String llmCurrency;
    private Boolean llmUsageEstimated;
    private Integer llmRetryCount;
    private Long llmLatencyMs;
    private String llmRequestStatus;
    private String llmErrorCategory;
}
