package com.zwf.aisupport.vo;

import lombok.Data;

@Data
public class WebEvidenceSummaryVO {
    private long total;
    private long webSources;
    private long modelGenerated;
    private long needsReview;
    private long converted;
    private long rejected;
}
