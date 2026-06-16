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

    private Boolean suggestCreateTicket;
    private Long createdTicketId;
}