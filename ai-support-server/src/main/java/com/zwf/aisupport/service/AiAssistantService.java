package com.zwf.aisupport.service;

import com.zwf.aisupport.dto.AiAskDTO;
import com.zwf.aisupport.dto.AiQuestionLogFeedbackDTO;
import com.zwf.aisupport.dto.KnowledgeArticleCreateDTO;
import com.zwf.aisupport.vo.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface AiAssistantService {

    AiAnswerVO ask(AiAskDTO aiAskDTO);

    AiAnswerVO askWithFile(String question, MultipartFile file);

    AiAnswerVO askAndCreateTicket(AiAskDTO aiAskDTO);

    List<AiQuestionLogVO> listMyQuestionLogs();

    List<AiQuestionLogVO> listAllQuestionLogs();

    void deleteQuestionLog(Long id);

    void submitQuestionLogFeedback(Long id, AiQuestionLogFeedbackDTO feedbackDTO);

    AiDashboardStatsVO getDashboardStats();

    List<KnowledgeGapVO> listKnowledgeGaps();

    KnowledgeArticleVO createArticleFromKnowledgeGap(Long logId, KnowledgeArticleCreateDTO createDTO);

    void deleteKnowledgeGap(Long logId);
}
