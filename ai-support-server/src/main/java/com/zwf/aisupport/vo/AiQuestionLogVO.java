package com.zwf.aisupport.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AiQuestionLogVO {

    private Long id;
    private Long userId;
    private String question;
    private String answer;
    private Long sourceArticleId;
    private String sourceTitle;
    private Boolean suggestCreateTicket;
    private Long createdTicketId;
    private LocalDateTime createdTime;
}