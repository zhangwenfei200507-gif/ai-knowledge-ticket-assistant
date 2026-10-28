package com.zwf.aisupport.vo;

import lombok.Data;

@Data
public class AiAnswerVO {

    private String answer;

    /** Distinguishes a local grounded template from a successful model synthesis. */
    private String answerEngine;
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

    /** Observable route: INTERNAL_RAG, WEB_SEARCH, AI_ASSISTED_WEB, AI_ONLY, or NONE. */
    private String answerMode;

    private AiRetrievalTraceVO retrievalTrace;

    private String sourceType;
    private Double sourceScore;

    private Long sourceArticleId;
    private String sourceTitle;

    private Long sourceDocumentId;
    private String sourceDocumentName;
    private Long sourceChunkId;
    private Integer sourceChunkIndex;

    private Long sourceWebEvidenceId;
    private String sourceUrl;
    private String sourceDomain;
    private String sourceStatus;

    private Boolean suggestCreateTicket;
    private Long createdTicketId;
    private java.util.List<AiAnswerSourceVO> sources;
}
