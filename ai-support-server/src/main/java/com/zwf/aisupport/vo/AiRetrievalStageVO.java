package com.zwf.aisupport.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AiRetrievalStageVO {

    private String stage;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private Long durationMs;
    private String resultSummary;
    private String errorCategory;
}
