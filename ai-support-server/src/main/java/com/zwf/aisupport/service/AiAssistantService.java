package com.zwf.aisupport.service;

import com.zwf.aisupport.dto.AiAskDTO;
import com.zwf.aisupport.dto.AiQuestionLogFeedbackDTO;
import com.zwf.aisupport.vo.AiAnswerVO;
import com.zwf.aisupport.vo.AiDashboardStatsVO;
import com.zwf.aisupport.vo.AiQuestionLogVO;
import com.zwf.aisupport.vo.KnowledgeGapVO;

import java.util.List;

public interface AiAssistantService {

    AiAnswerVO ask(AiAskDTO aiAskDTO);

    AiAnswerVO askAndCreateTicket(AiAskDTO aiAskDTO);

    List<AiQuestionLogVO> listMyQuestionLogs();

    List<AiQuestionLogVO> listAllQuestionLogs();

    void submitQuestionLogFeedback(Long id, AiQuestionLogFeedbackDTO feedbackDTO);

    AiDashboardStatsVO getDashboardStats();

    List<KnowledgeGapVO> listKnowledgeGaps();
}