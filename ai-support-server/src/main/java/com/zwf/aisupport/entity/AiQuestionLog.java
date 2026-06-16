package com.zwf.aisupport.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AiQuestionLog {

    private Long id;
    private Long userId;
    private String question;
    private String answer;
    private Long sourceArticleId;
    private String sourceTitle;
    private Boolean suggestCreateTicket;
    private Long createdTicketId;
    private String feedback;
    private String feedbackComment;
    private LocalDateTime feedbackTime;
    private LocalDateTime createdTime;
    private String sourceType;
    private Long sourceDocumentId;
    private String sourceDocumentName;
    private Long sourceChunkId;
    private Integer sourceChunkIndex;
}