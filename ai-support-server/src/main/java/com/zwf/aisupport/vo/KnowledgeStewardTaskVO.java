package com.zwf.aisupport.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class KnowledgeStewardTaskVO {

    private Long id;
    private String sourceType;
    private Long sourceId;
    private String sourceOrigin;
    private String sourceTitle;
    private String sourceQuestion;
    private String sourceExcerpt;
    private String recommendedAction;
    private String targetDomain;
    private Long targetArticleId;
    private String targetArticleTitle;
    private String targetSection;
    private BigDecimal domainConfidence;
    private BigDecimal coverageConfidence;
    private BigDecimal sourceQuality;
    private BigDecimal duplicateRisk;
    private String proposalTitle;
    private String proposalContent;
    private String reasoning;
    private String warnings;
    private String status;
    private Long createdBy;
    private Long reviewedBy;
    private Long convertedArticleId;
    private LocalDateTime createdTime;
    private LocalDateTime reviewedTime;
}
