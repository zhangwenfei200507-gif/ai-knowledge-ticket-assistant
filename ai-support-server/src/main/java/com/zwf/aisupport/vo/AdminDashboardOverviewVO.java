package com.zwf.aisupport.vo;

import lombok.Data;

@Data
public class AdminDashboardOverviewVO {

    private Long totalTickets;
    private Long openTickets;
    private Long resolvedTickets;
    private Long knowledgeArticles;
    private Long aiQuestions;
    private Long aiCreatedTickets;
    private Long knowledgeGaps;
    private Long documentAnswers;
    private Long articleAnswers;
}