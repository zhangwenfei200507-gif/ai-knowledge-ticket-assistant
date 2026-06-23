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
import static org.mockito.Mockito.never;
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
        assertCleanUserAnswer(answer.getAnswer());
        verify(webEvidenceService).searchAndStoreEvidence(question, 4L);
        verify(aiQuestionLogMapper).insert(any());
    }

    @Test
    void askAnswersDockerComposeServiceNameQuestionFromArticle() {
        String question = "Why should Docker Compose services use service names instead of localhost for container-to-container communication?";
        when(documentChunkMapper.searchChunks(question)).thenReturn(List.of());
        when(knowledgeArticleMapper.findPublishedArticles()).thenReturn(List.of(dockerComposeArticle()));

        AiAskDTO askDTO = new AiAskDTO();
        askDTO.setQuestion(question);

        AiAnswerVO answer = aiAssistantService.ask(askDTO);

        assertThat(answer.getSourceType()).isEqualTo("ARTICLE");
        assertThat(answer.getAnswer()).containsIgnoringCase("localhost");
        assertThat(answer.getAnswer()).containsIgnoringCase("service name");
        assertThat(answer.getAnswer()).containsIgnoringCase("container");
        assertCleanUserAnswer(answer.getAnswer());
        verify(webEvidenceService, never()).searchAndStoreEvidence(any(), any());
        verify(aiQuestionLogMapper).insert(any());
    }

    @Test
    void askSynthesizesDockerPostgresConnectionAnswerWithoutRunbookNoise() {
        String question = "Why does my backend container fail to connect to PostgreSQL even though the database container is running?";
        when(documentChunkMapper.searchChunks(question)).thenReturn(List.of());
        when(knowledgeArticleMapper.findPublishedArticles()).thenReturn(List.of(postgresRunbookArticle(), dockerComposeArticle()));

        AiAskDTO askDTO = new AiAskDTO();
        askDTO.setQuestion(question);

        AiAnswerVO answer = aiAssistantService.ask(askDTO);

        assertThat(answer.getSourceType()).isEqualTo("ARTICLE");
        assertThat(answer.getAnswer()).containsIgnoringCase("service name");
        assertThat(answer.getAnswer()).containsIgnoringCase("localhost");
        assertThat(answer.getAnswer()).containsIgnoringCase("DB_HOST");
        assertCleanUserAnswer(answer.getAnswer());
        verify(webEvidenceService, never()).searchAndStoreEvidence(any(), any());
        verify(aiQuestionLogMapper).insert(any());
    }

    @Test
    void askSynthesizesDockerMultiStageWebAnswerAsConceptExplanation() {
        String question = "What is the purpose of Docker multi-stage builds, and when should they be used?";
        when(documentChunkMapper.searchChunks(question)).thenReturn(List.of());
        when(knowledgeArticleMapper.findPublishedArticles()).thenReturn(List.of());
        when(webEvidenceService.searchAndStoreEvidence(question, 4L)).thenReturn(Optional.of(multiStageWebEvidence(question)));

        AiAskDTO askDTO = new AiAskDTO();
        askDTO.setQuestion(question);

        AiAnswerVO answer = aiAssistantService.ask(askDTO);

        assertThat(answer.getSourceType()).isEqualTo("WEB");
        assertThat(answer.getAnswer()).containsIgnoringCase("runtime image");
        assertThat(answer.getAnswer()).containsIgnoringCase("When To Use");
        assertThat(answer.getAnswer()).containsIgnoringCase("smaller");
        assertThat(answer.getAnswer()).doesNotContainIgnoringCase("Common Causes");
        assertThat(answer.getAnswer()).doesNotContainIgnoringCase("docker build --target");
        assertCleanUserAnswer(answer.getAnswer());
        verify(webEvidenceService).searchAndStoreEvidence(question, 4L);
        verify(aiQuestionLogMapper).insert(any());
    }

    @Test
    void askSynthesizesDockerfileInstructionAnswerWithoutKeyAnswerFragment() {
        String question = "What is the purpose of the WORKDIR, COPY, RUN, EXPOSE, and CMD instructions in this Dockerfile, and how do they help build and run the Python application inside a container?";
        when(documentChunkMapper.searchChunks(question)).thenReturn(List.of());
        when(knowledgeArticleMapper.findPublishedArticles()).thenReturn(List.of(dockerfileArticle()));

        AiAskDTO askDTO = new AiAskDTO();
        askDTO.setQuestion(question);

        AiAnswerVO answer = aiAssistantService.ask(askDTO);

        assertThat(answer.getSourceType()).isEqualTo("ARTICLE");
        assertThat(answer.getAnswer()).containsIgnoringCase("WORKDIR");
        assertThat(answer.getAnswer()).containsIgnoringCase("COPY");
        assertThat(answer.getAnswer()).containsIgnoringCase("EXPOSE");
        assertThat(answer.getAnswer()).doesNotContainIgnoringCase("Key answer");
        assertCleanUserAnswer(answer.getAnswer());
        verify(webEvidenceService, never()).searchAndStoreEvidence(any(), any());
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

    private KnowledgeArticle dockerComposeArticle() {
        KnowledgeArticle article = new KnowledgeArticle();
        article.setId(12L);
        article.setTitle("CloudOps Docker Knowledge Pack");
        article.setCategory("Docker");
        article.setContent("""
                Docker Compose Networking
                Inside Docker Compose, containers should usually connect to other services by service name,
                not localhost. localhost inside a container refers to that container itself.
                Service names are resolved by Docker Compose DNS on the shared Compose network.
                For example, an app container should connect to postgres:5432 instead of localhost:5432.
                Backend containers should connect to PostgreSQL through the PostgreSQL service name and internal port.
                """);
        article.setStatus("PUBLISHED");
        return article;
    }

    private KnowledgeArticle postgresRunbookArticle() {
        KnowledgeArticle article = new KnowledgeArticle();
        article.setId(13L);
        article.setTitle("PostgreSQL Connection and Timeout Runbook");
        article.setCategory("PostgreSQL");
        article.setContent("""
                Use this runbook when the application cannot connect to PostgreSQL.
                Primary keywords: postgresql, postgres, database, connection timeout, max_connections.
                Confirm the database container or server is running.
                Check the connection string, port, credentials, and database readiness.
                """);
        article.setStatus("PUBLISHED");
        return article;
    }

    private KnowledgeArticle dockerfileArticle() {
        KnowledgeArticle article = new KnowledgeArticle();
        article.setId(14L);
        article.setTitle("Dockerfile Instruction Knowledge Pack");
        article.setCategory("Docker");
        article.setContent("""
                Dockerfile instructions define the application image.
                WORKDIR sets the default path for later commands.
                COPY places the Python application files inside the image.
                RUN installs dependencies during build time.
                EXPOSE documents the port used by the application.
                CMD defines the default command that starts the Python application in the container.
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

    private WebEvidence multiStageWebEvidence(String question) {
        WebEvidence evidence = new WebEvidence();
        evidence.setId(32L);
        evidence.setQuestion(question);
        evidence.setTitle("Docker Multi-stage Builds");
        evidence.setDomain("docs.docker.com");
        evidence.setUrl("https://docs.docker.com/build/building/multi-stage/");
        evidence.setCredibilityScore(new BigDecimal("0.95"));
        evidence.setStatus("NEEDS_REVIEW");
        evidence.setExtractedContent("""
                With multi-stage builds, you use multiple FROM statements in your Dockerfile.
                Each FROM instruction can use a different base image, and each begins a new stage of the build.
                You can selectively copy artifacts from one stage to another, leaving behind everything you do not want in the final image.
                The final image can contain only the runtime artifacts instead of the full build toolchain.
                The docker build --target build command can stop at a named stage for debugging.
                """);
        return evidence;
    }

    private void authenticateAsAdmin() {
        CurrentUser currentUser = new CurrentUser(4L, "admin", "ADMIN");
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(currentUser, null);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private void assertCleanUserAnswer(String answer) {
        assertThat(answer).doesNotContainIgnoringCase("Use this runbook");
        assertThat(answer).doesNotContainIgnoringCase("Primary keywords");
        assertThat(answer).doesNotContainIgnoringCase("Purpose:");
        assertThat(answer).doesNotContainIgnoringCase("Category:");
        assertThat(answer).doesNotContainIgnoringCase("Knowledge Candidate");
        assertThat(answer).doesNotContainIgnoringCase("Observed answer");
        assertThat(answer).doesNotContainIgnoringCase("Review this content before publishing");
    }
}
