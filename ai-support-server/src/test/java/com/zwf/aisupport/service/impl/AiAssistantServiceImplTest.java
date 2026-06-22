package com.zwf.aisupport.service.impl;

import com.zwf.aisupport.dto.AiAskDTO;
import com.zwf.aisupport.entity.KnowledgeArticle;
import com.zwf.aisupport.entity.WebEvidence;
import com.zwf.aisupport.mapper.AiQuestionLogMapper;
import com.zwf.aisupport.mapper.DocumentChunkMapper;
import com.zwf.aisupport.mapper.KnowledgeArticleMapper;
import com.zwf.aisupport.mapper.TicketMapper;
import com.zwf.aisupport.security.CurrentUser;
import com.zwf.aisupport.service.DocumentService;
import com.zwf.aisupport.service.LlmGatewayService;
import com.zwf.aisupport.service.WebEvidenceService;
import com.zwf.aisupport.vo.AiAnswerVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiAssistantServiceImplTest {

    @Mock
    private KnowledgeArticleMapper knowledgeArticleMapper;

    @Mock
    private TicketMapper ticketMapper;

    @Mock
    private AiQuestionLogMapper aiQuestionLogMapper;

    @Mock
    private DocumentChunkMapper documentChunkMapper;

    @Mock
    private DocumentService documentService;

    @Mock
    private WebEvidenceService webEvidenceService;

    @Mock
    private LlmGatewayService llmGatewayService;

    @Mock
    private KnowledgePackReorganizer knowledgePackReorganizer;

    private AiAssistantServiceImpl aiAssistantService;

    @BeforeEach
    void setUp() {
        aiAssistantService = new AiAssistantServiceImpl(
                knowledgeArticleMapper,
                ticketMapper,
                aiQuestionLogMapper,
                documentChunkMapper,
                documentService,
                webEvidenceService,
                llmGatewayService,
                knowledgePackReorganizer
        );
        authenticateAsAdmin();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void askFallsBackToWebWhenArticleMissesCoreConcept() {
        String question = "Why is my JWT rejected because of the nbf (Not Before) claim even though it has not expired?";
        when(documentChunkMapper.searchChunks(question)).thenReturn(List.of());
        when(knowledgeArticleMapper.findPublishedArticles()).thenReturn(List.of(articleWithoutNbf()));
        when(webEvidenceService.searchAndStoreEvidence(question, 4L)).thenReturn(Optional.of(nbfWebEvidence(question)));

        AiAskDTO askDTO = new AiAskDTO();
        askDTO.setQuestion(question);

        AiAnswerVO answer = aiAssistantService.ask(askDTO);

        assertThat(answer.getSourceType()).isEqualTo("WEB");
        assertThat(answer.getAnswer()).containsIgnoringCase("nbf");
        assertThat(answer.getAnswer()).containsIgnoringCase("not before");
        verify(webEvidenceService).searchAndStoreEvidence(question, 4L);
        verify(aiQuestionLogMapper).insert(any());
    }

    private KnowledgeArticle articleWithoutNbf() {
        KnowledgeArticle article = new KnowledgeArticle();
        article.setId(11L);
        article.setTitle("Auth / JWT Knowledge Pack");
        article.setCategory("Auth / JWT");
        article.setContent("""
                JWT token rejected runbook.
                A 401 means the request is not authenticated.
                Check bearer token formatting and Spring Security resource server configuration.
                """);
        article.setStatus("PUBLISHED");
        return article;
    }

    private WebEvidence nbfWebEvidence(String question) {
        WebEvidence evidence = new WebEvidence();
        evidence.setId(31L);
        evidence.setQuestion(question);
        evidence.setTitle("JWT Not Before Claim");
        evidence.setDomain("jwt.io");
        evidence.setUrl("https://jwt.io/introduction");
        evidence.setCredibilityScore(new BigDecimal("0.88"));
        evidence.setStatus("NEEDS_REVIEW");
        evidence.setExtractedContent("""
                The nbf claim means Not Before.
                A JWT must not be accepted before the time in the nbf claim.
                This is separate from the exp claim, so a token can be unexpired but still rejected because it is not valid yet.
                Check clock skew between the token issuer and resource server.
                """);
        return evidence;
    }

    private void authenticateAsAdmin() {
        CurrentUser currentUser = new CurrentUser(4L, "admin", "ADMIN");
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(currentUser, null);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
