package com.zwf.aisupport.vo;

import lombok.Data;

@Data
public class AiAnswerSourceVO {

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

    private String previewContent;
}
