package com.zwf.aisupport.vo;

import lombok.Data;

@Data
public class AiAnswerVO {

    private String answer;
    private Long sourceArticleId;
    private String sourceTitle;
    private Boolean suggestCreateTicket;
}