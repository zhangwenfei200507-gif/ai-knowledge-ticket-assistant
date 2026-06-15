package com.zwf.aisupport.service.impl;

import com.zwf.aisupport.dto.AiAskDTO;
import com.zwf.aisupport.mapper.KnowledgeArticleMapper;
import com.zwf.aisupport.service.AiAssistantService;
import com.zwf.aisupport.vo.AiAnswerVO;
import com.zwf.aisupport.entity.KnowledgeArticle;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AiAssistantServiceImpl implements AiAssistantService {

    private final KnowledgeArticleMapper knowledgeArticleMapper;

    public AiAssistantServiceImpl(KnowledgeArticleMapper knowledgeArticleMapper) {
        this.knowledgeArticleMapper = knowledgeArticleMapper;
    }

    @Override
    public AiAnswerVO ask(AiAskDTO aiAskDTO) {
        String keyword = extractKeyword(aiAskDTO.getQuestion());
        List<KnowledgeArticle> articles = knowledgeArticleMapper.searchPublishedArticles(keyword);

        if (articles.isEmpty()) {
            return buildFallbackAnswer();
        }

        KnowledgeArticle article = articles.get(0);

        AiAnswerVO answerVO = new AiAnswerVO();
        answerVO.setAnswer("Based on the knowledge base: " + article.getContent());
        answerVO.setSourceArticleId(article.getId());
        answerVO.setSourceTitle(article.getTitle());
        answerVO.setSuggestCreateTicket(false);
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
        answerVO.setAnswer("I could not find a reliable answer in the knowledge base. You may create a support ticket for further assistance.");
        answerVO.setSourceArticleId(null);
        answerVO.setSourceTitle(null);
        answerVO.setSuggestCreateTicket(true);
        return answerVO;
    }
}