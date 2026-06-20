package com.zwf.aisupport.vo;

import lombok.Data;

@Data
public class AiAnswerVO {

    private String answer;

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
