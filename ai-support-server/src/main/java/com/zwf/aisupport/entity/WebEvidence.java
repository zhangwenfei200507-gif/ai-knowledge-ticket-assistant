package com.zwf.aisupport.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class WebEvidence {

    private Long id;
    private String question;
    private String query;
    private String title;
    private String url;
    private String domain;
    private String snippet;
    private String extractedContent;
    private String suggestedArticleTitle;
    private String suggestedArticleContent;
    private String category;
    private BigDecimal credibilityScore;
    private String evidenceOrigin;
    private String status;
    private Long createdBy;
    private Long reviewedBy;
    private Long convertedArticleId;
    private LocalDateTime createdTime;
    private LocalDateTime reviewedTime;
}
