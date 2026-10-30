package com.zwf.aisupport.service.impl;

import com.zwf.aisupport.entity.WebEvidence;
import com.zwf.aisupport.entity.KnowledgeArticle;
import com.zwf.aisupport.entity.KnowledgeStewardTask;
import com.zwf.aisupport.common.exception.BusinessException;
import com.zwf.aisupport.mapper.AiQuestionLogMapper;
import com.zwf.aisupport.mapper.DocumentKnowledgeCandidateMapper;
import com.zwf.aisupport.mapper.KnowledgeArticleMapper;
import com.zwf.aisupport.mapper.KnowledgeStewardTaskMapper;
import com.zwf.aisupport.mapper.WebEvidenceMapper;
import com.zwf.aisupport.security.CurrentUser;
import com.zwf.aisupport.service.LlmGatewayService;
import com.zwf.aisupport.vo.KnowledgeStewardRunVO;
import com.zwf.aisupport.vo.AiAnswerVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeStewardServiceImplTest {

    @Mock
    private WebEvidenceMapper webEvidenceMapper;

    @Mock
    private DocumentKnowledgeCandidateMapper documentCandidateMapper;

    @Mock
    private AiQuestionLogMapper aiQuestionLogMapper;

    @Mock
    private KnowledgeArticleMapper knowledgeArticleMapper;

    @Mock
    private KnowledgeStewardTaskMapper taskMapper;

    @Mock
    private KnowledgePackReorganizer knowledgePackReorganizer;

    @Mock
    private KnowledgeArticleChunkManager articleChunkManager;

    @Mock
    private LlmGatewayService llmGatewayService;

    private KnowledgeStewardServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new KnowledgeStewardServiceImpl(
                webEvidenceMapper,
                documentCandidateMapper,
                aiQuestionLogMapper,
                knowledgeArticleMapper,
                taskMapper,
                knowledgePackReorganizer,
                articleChunkManager,
                llmGatewayService,
                false
        );
        authenticateAsAdmin();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void runCreatesReviewTaskFromPendingWebEvidenceWithoutPublishing() {
        WebEvidence evidence = new WebEvidence();
        evidence.setId(20L);
        evidence.setEvidenceOrigin("WEB_SEARCH");
        evidence.setQuestion("What is Redis keyspace notification and when should it be enabled?");
        evidence.setTitle("Redis Keyspace Notifications");
        evidence.setDomain("redis.io");
        evidence.setCategory("Redis Cache");
        evidence.setCredibilityScore(new BigDecimal("0.96"));
        evidence.setSuggestedArticleTitle("Redis Keyspace Notifications");
        evidence.setSuggestedArticleContent("""
                Redis keyspace notifications publish events about changes to keys through Pub/Sub channels.
                They should be enabled selectively because high-volume event streams can add overhead.
                Use this for cache invalidation, expiration handling, and selected operational monitoring.
                """);
        evidence.setStatus("NEEDS_REVIEW");

        when(webEvidenceMapper.findPendingReviewLimited(10)).thenReturn(List.of(evidence));
        when(documentCandidateMapper.findPendingReviewLimited(10)).thenReturn(List.of());
        when(aiQuestionLogMapper.findStewardCandidates(10)).thenReturn(List.of());
        when(taskMapper.countActiveBySource("WEB_EVIDENCE", 20L)).thenReturn(0L);
        when(knowledgeArticleMapper.findPublishedArticles()).thenReturn(List.of());

        KnowledgeStewardRunVO run = service.run(10);

        assertThat(run.getCreatedTasks()).isEqualTo(1);
        assertThat(run.getScannedWebEvidence()).isEqualTo(1);
        verify(knowledgePackReorganizer, never()).reorganizeOrCreate(any(), any(), any(), any(), any());
        verify(articleChunkManager, never()).rebuildArticleChunks(any());

        ArgumentCaptor<com.zwf.aisupport.entity.KnowledgeStewardTask> taskCaptor =
                ArgumentCaptor.forClass(com.zwf.aisupport.entity.KnowledgeStewardTask.class);
        verify(taskMapper).insert(taskCaptor.capture());
        assertThat(taskCaptor.getValue().getSourceType()).isEqualTo("WEB_EVIDENCE");
        assertThat(taskCaptor.getValue().getTargetDomain()).isEqualTo("Redis Cache");
        assertThat(taskCaptor.getValue().getStatus()).isEqualTo("NEEDS_ADMIN_REVIEW");
        assertThat(taskCaptor.getValue().getProposalContent()).contains("Redis keyspace notifications");
    }

    @Test
    void rejectsApprovalWhenTargetDomainDoesNotMatchKnowledgePack() {
        KnowledgeStewardTask task = new KnowledgeStewardTask();
        task.setId(31L);
        task.setStatus("NEEDS_ADMIN_REVIEW");
        task.setTargetArticleId(99L);
        task.setTargetDomain("Docker");
        task.setProposalTitle("Docker Networking");
        task.setProposalContent("Docker containers use service discovery for network communication.");
        task.setRecommendedAction("UPDATE_SECTION");

        KnowledgeArticle article = new KnowledgeArticle();
        article.setId(99L);
        article.setCategory("Redis Cache");
        article.setStatus("PUBLISHED");

        when(taskMapper.findById(31L)).thenReturn(task);
        when(knowledgeArticleMapper.findById(99L)).thenReturn(article);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.approve(31L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("The target Knowledge Pack domain must match the proposal domain");
        verify(knowledgePackReorganizer, never()).reorganizeExistingById(any(), any(), any(), any(), any());
    }

    @Test
    void queuesWebOrAiOnlyEvidenceWithoutCallingTheModelAgain() {
        WebEvidence evidence = new WebEvidence();
        evidence.setId(41L);
        evidence.setEvidenceOrigin("LLM_GENERATED");
        evidence.setStatus("NEEDS_REVIEW");
        evidence.setQuestion("What is Redis keyspace notification?");
        evidence.setTitle("Redis Keyspace Notifications");
        evidence.setDomain("OpenAI model");
        evidence.setCategory("Redis Cache");
        evidence.setExtractedContent("Redis keyspace notifications publish events when keys change.");
        evidence.setSuggestedArticleContent("## Core Concepts\n\nRedis keyspace notifications publish events when keys change.");

        AiAnswerVO answer = new AiAnswerVO();
        answer.setSourceType("LLM");
        answer.setSourceWebEvidenceId(41L);

        when(webEvidenceMapper.findById(41L)).thenReturn(evidence);
        when(taskMapper.countActiveBySource("WEB_EVIDENCE", 41L)).thenReturn(0L);
        when(knowledgeArticleMapper.findPublishedArticles()).thenReturn(List.of());

        service.queueAnswerCandidate(answer, 4L);

        verify(taskMapper).insert(any());
        verify(llmGatewayService, never()).generateKnowledgeStewardProposal(any());
    }

    private void authenticateAsAdmin() {
        CurrentUser currentUser = new CurrentUser(4L, "admin", "ADMIN");
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(currentUser, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
