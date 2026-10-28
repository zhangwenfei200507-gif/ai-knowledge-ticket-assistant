package com.zwf.aisupport.vo;

import lombok.Data;

import java.util.List;

@Data
public class AiRetrievalTraceVO {

    private boolean internalAttempted;
    private boolean internalSufficient;
    private boolean webAttempted;
    private boolean webSucceeded;
    private boolean webSufficient;
    private boolean llmAttempted;
    private boolean llmSucceeded;
    private String finalMode;
    private String failureReason;
    private Long totalLatencyMs;
    private List<AiRetrievalStageVO> stages;
}
