package com.zwf.aisupport.vo;

import lombok.Data;

@Data
public class AiDashboardStatsVO {

    private Long totalQuestions;
    private Long answeredByKnowledgeBase;
    private Long answeredByDocument;
    private Long answeredByArticle;
    private Long unansweredQuestions;
    private Long createdTickets;
    private Long usefulFeedbackCount;
    private Long notUsefulFeedbackCount;
    private Long pendingFeedbackCount;
}