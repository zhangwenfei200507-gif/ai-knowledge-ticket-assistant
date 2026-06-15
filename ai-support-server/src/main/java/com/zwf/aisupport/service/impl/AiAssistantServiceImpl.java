package com.zwf.aisupport.service.impl;

import com.zwf.aisupport.common.exception.BusinessException;
import com.zwf.aisupport.common.util.CurrentUserUtil;
import com.zwf.aisupport.dto.AiAskDTO;
import com.zwf.aisupport.entity.KnowledgeArticle;
import com.zwf.aisupport.entity.Ticket;
import com.zwf.aisupport.mapper.KnowledgeArticleMapper;
import com.zwf.aisupport.mapper.TicketMapper;
import com.zwf.aisupport.service.AiAssistantService;
import com.zwf.aisupport.vo.AiAnswerVO;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AiAssistantServiceImpl implements AiAssistantService {

    private final KnowledgeArticleMapper knowledgeArticleMapper;
    private final TicketMapper ticketMapper;

    public AiAssistantServiceImpl(KnowledgeArticleMapper knowledgeArticleMapper,
                                  TicketMapper ticketMapper) {
        this.knowledgeArticleMapper = knowledgeArticleMapper;
        this.ticketMapper = ticketMapper;
    }

    @Override
    public AiAnswerVO ask(AiAskDTO aiAskDTO) {
        String keyword = extractKeyword(aiAskDTO.getQuestion());
        List<KnowledgeArticle> articles = knowledgeArticleMapper.searchPublishedArticles(keyword);

        if (articles.isEmpty()) {
            return buildFallbackAnswer();
        }

        return buildKnowledgeBasedAnswer(articles.get(0));
    }

    @Override
    public AiAnswerVO askAndCreateTicket(AiAskDTO aiAskDTO) {
        AiAnswerVO answerVO = ask(aiAskDTO);
        if (!Boolean.TRUE.equals(answerVO.getSuggestCreateTicket())) {
            return answerVO;
        }

        Long currentUserId = CurrentUserUtil.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException("User is not authenticated");
        }

        Ticket ticket = new Ticket();
        ticket.setTitle(generateTicketTitle(aiAskDTO.getQuestion()));
        ticket.setDescription(aiAskDTO.getQuestion());
        ticket.setStatus("OPEN");
        ticket.setPriority(suggestPriority(aiAskDTO.getQuestion()));
        ticket.setCreatedBy(currentUserId);

        // Create a support ticket when the knowledge base cannot answer the user question.
        ticketMapper.insert(ticket);

        answerVO.setCreatedTicketId(ticket.getId());
        return answerVO;
    }

    private String generateTicketTitle(String question) {
        String trimmedQuestion = question.trim();
        if (trimmedQuestion.length() <= 90) {
            return "AI: " + trimmedQuestion;
        }

        // Keep generated ticket titles short enough for the database title limit.
        return "AI: " + trimmedQuestion.substring(0, 87) + "...";
    }

    private String suggestPriority(String question) {
        String normalizedQuestion = question.toLowerCase();

        if (normalizedQuestion.contains("production")
                || normalizedQuestion.contains("urgent")
                || normalizedQuestion.contains("down")
                || normalizedQuestion.contains("cannot login")
                || normalizedQuestion.contains("security")) {
            return "HIGH";
        }

        if (normalizedQuestion.contains("question")
                || normalizedQuestion.contains("how to")
                || normalizedQuestion.contains("guide")) {
            return "LOW";
        }

        // Default to MEDIUM when the assistant cannot confidently classify urgency.
        return "MEDIUM";
    }

    private AiAnswerVO buildKnowledgeBasedAnswer(KnowledgeArticle article) {
        AiAnswerVO answerVO = new AiAnswerVO();
        answerVO.setAnswer("Based on the knowledge base: " + article.getContent());
        answerVO.setSourceArticleId(article.getId());
        answerVO.setSourceTitle(article.getTitle());
        answerVO.setSuggestCreateTicket(false);
        answerVO.setCreatedTicketId(null);
        return answerVO;
    }

    private String extractKeyword(String question) {
        String normalizedQuestion = question.trim().toLowerCase();

        if (normalizedQuestion.contains("password")) {
            return "password";
        }
        if (normalizedQuestion.contains("email")) {
            return "email";
        }
        if (normalizedQuestion.contains("login")) {
            return "login";
        }

        // Use the original question as a simple keyword before semantic retrieval is added.
        return question.trim();
    }

    private AiAnswerVO buildFallbackAnswer() {
        AiAnswerVO answerVO = new AiAnswerVO();
        answerVO.setAnswer("I could not find a reliable answer in the knowledge base. A support ticket can be created for further assistance.");
        answerVO.setSourceArticleId(null);
        answerVO.setSourceTitle(null);
        answerVO.setSuggestCreateTicket(true);
        answerVO.setCreatedTicketId(null);
        return answerVO;
    }
}