package com.zwf.aisupport.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class KnowledgeGapVO {

    private Long logId;
    private Long userId;
    private String question;
    private String answer;
    private String sourceType;
    private String category;
    private Long createdTicketId;
    private String feedback;
    private String feedbackComment;
    private LocalDateTime createdTime;
}
