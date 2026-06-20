package com.zwf.aisupport.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class DocumentKnowledgeCandidate {

    private Long id;
    private Long documentId;
    private Long chunkId;
    private String title;
    private String content;
    private String category;
    private BigDecimal importanceScore;
    private String status;
    private Long createdBy;
    private Long reviewedBy;
    private Long convertedArticleId;
    private LocalDateTime createdTime;
    private LocalDateTime reviewedTime;
}
