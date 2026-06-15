package com.zwf.aisupport.vo;

import lombok.Data;

@Data
public class AiDashboardStatsVO {

    private Long totalQuestions;
    private Long answeredByKnowledgeBase;
    private Long createdTickets;
    private Long usefulFeedbackCount;
    private Long notUsefulFeedbackCount;
    private Long pendingFeedbackCount;
}