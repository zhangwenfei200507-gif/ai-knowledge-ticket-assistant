package com.zwf.aisupport.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class WebEvidenceSourceVO {

    private Long id;
    private String title;
    private String url;
    private String domain;
    private String excerpt;
    private BigDecimal credibilityScore;
    private Integer sourceRank;
    private String sourceType;
    private Boolean officialSource;
    private LocalDateTime publishedTime;
    private LocalDateTime retrievedTime;
    private String sourceStatus;
}
