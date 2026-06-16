package com.zwf.aisupport.service.impl;

import com.zwf.aisupport.common.exception.BusinessException;
import com.zwf.aisupport.common.util.CurrentUserUtil;
import com.zwf.aisupport.mapper.AiQuestionLogMapper;
import com.zwf.aisupport.mapper.KnowledgeArticleMapper;
import com.zwf.aisupport.mapper.TicketMapper;
import com.zwf.aisupport.service.AdminDashboardService;
import com.zwf.aisupport.vo.AdminDashboardOverviewVO;
import org.springframework.stereotype.Service;

@Service
public class AdminDashboardServiceImpl implements AdminDashboardService {

    private final TicketMapper ticketMapper;
    private final KnowledgeArticleMapper knowledgeArticleMapper;
    private final AiQuestionLogMapper aiQuestionLogMapper;

    public AdminDashboardServiceImpl(TicketMapper ticketMapper,
                                     KnowledgeArticleMapper knowledgeArticleMapper,
                                     AiQuestionLogMapper aiQuestionLogMapper) {
        this.ticketMapper = ticketMapper;
        this.knowledgeArticleMapper = knowledgeArticleMapper;
        this.aiQuestionLogMapper = aiQuestionLogMapper;
    }

    @Override
    public AdminDashboardOverviewVO getOverview() {
        String role = CurrentUserUtil.getCurrentUserRole();
        if (!"ADMIN".equals(role)) {
            throw new BusinessException("Only administrators can access dashboard overview");
        }

        AdminDashboardOverviewVO overviewVO = new AdminDashboardOverviewVO();
        overviewVO.setTotalTickets(ticketMapper.countAll());
        overviewVO.setOpenTickets(ticketMapper.countOpenTickets());
        overviewVO.setResolvedTickets(ticketMapper.countResolvedTickets());
        overviewVO.setKnowledgeArticles(knowledgeArticleMapper.countPublishedArticles());
        overviewVO.setDocumentAnswers(aiQuestionLogMapper.countAnsweredByDocument());
        overviewVO.setArticleAnswers(aiQuestionLogMapper.countAnsweredByArticle());
        overviewVO.setAiQuestions(aiQuestionLogMapper.countAll());
        overviewVO.setAiCreatedTickets(aiQuestionLogMapper.countCreatedTickets());
        overviewVO.setKnowledgeGaps(aiQuestionLogMapper.countKnowledgeGaps());

        return overviewVO;
    }
}