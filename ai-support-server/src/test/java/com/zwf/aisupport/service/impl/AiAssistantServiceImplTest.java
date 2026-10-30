package com.zwf.aisupport.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zwf.aisupport.dto.AiAskDTO;
import com.zwf.aisupport.config.AiRoutingProperties;
import com.zwf.aisupport.entity.KnowledgeArticle;
import com.zwf.aisupport.entity.WebEvidence;
import com.zwf.aisupport.mapper.AiQuestionLogMapper;
import com.zwf.aisupport.mapper.DocumentChunkMapper;
import com.zwf.aisupport.mapper.KnowledgeArticleChunkMapper;
import com.zwf.aisupport.mapper.KnowledgeArticleMapper;
import com.zwf.aisupport.mapper.TicketMapper;
import com.zwf.aisupport.security.CurrentUser;
import com.zwf.aisupport.service.DocumentService;
import com.zwf.aisupport.service.KnowledgeStewardService;
import com.zwf.aisupport.service.LlmGatewayService;
import com.zwf.aisupport.service.WebEvidenceService;
import com.zwf.aisupport.vo.AiAnswerVO;
import com.zwf.aisupport.vo.KnowledgeArticleChunkSearchResultVO;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiAssistantServiceImplTest {

    @Mock
    private KnowledgeArticleMapper knowledgeArticleMapper;

    @Mock
    private KnowledgeArticleChunkMapper knowledgeArticleChunkMapper;

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
    private KnowledgeStewardService knowledgeStewardService;

    @Mock
    private KnowledgePackReorganizer knowledgePackReorganizer;

    @Mock
    private KnowledgeArticleChunkManager knowledgeArticleChunkManager;

    private AiAssistantServiceImpl aiAssistantService;

    @BeforeEach
    void setUp() {
        aiAssistantService = new AiAssistantServiceImpl(
                knowledgeArticleMapper,
                knowledgeArticleChunkMapper,
                ticketMapper,
                aiQuestionLogMapper,
                documentChunkMapper,
                documentService,
                webEvidenceService,
                llmGatewayService,
                knowledgePackReorganizer,
                new LlmUsagePolicy(new AiRoutingProperties()),
                knowledgeStewardService,
                new AiQueryUnderstandingService(),
                new KnowledgeCoverageEvaluator(),
                new KnowledgeChunkRanker(new KnowledgeCoverageEvaluator()),
                knowledgeArticleChunkManager,
                new RetrievalTraceCodec(new ObjectMapper())
        );
        lenient().when(knowledgeArticleChunkMapper.searchChunks(any(), any(), anyInt())).thenReturn(List.of());
        authenticateAsAdmin();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void askFallsBackToWebWhenArticleMissesCoreConcept() {
        String question = "Why is my JWT rejected because of the nbf (Not Before) claim even though it has not expired?";
        when(documentChunkMapper.searchChunks(question, 4L, true)).thenReturn(List.of());
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
        when(knowledgeArticleMapper.findPublishedArticles()).thenReturn(List.of(dockerComposeArticle()));

        AiAskDTO askDTO = new AiAskDTO();
        askDTO.setQuestion(question);

        AiAnswerVO answer = aiAssistantService.ask(askDTO);

        assertThat(answer.getSourceType())
                .as("answer=%s trace=%s", answer.getAnswer(), answer.getRetrievalTrace())
                .isEqualTo("ARTICLE");
        assertThat(answer.getAnswer()).containsIgnoringCase("localhost");
        assertThat(answer.getAnswer()).containsIgnoringCase("service name");
        assertThat(answer.getAnswer()).containsIgnoringCase("container");
        assertCleanUserAnswer(answer.getAnswer());
        verify(webEvidenceService, never()).searchAndStoreEvidence(any(), any());
        verify(llmGatewayService, never()).generateGroundedAnswer(any(), any(), any());
        verify(aiQuestionLogMapper).insert(any());
    }

    @Test
    void askDoesNotUseDirectLlmFallbackForOutOfDomainQuestions() {
        String question = "Write a poem about summer vacation";

        AiAskDTO askDTO = new AiAskDTO();
        askDTO.setQuestion(question);

        AiAnswerVO answer = aiAssistantService.ask(askDTO);

        assertThat(answer.getSourceType()).isEqualTo("NONE");
        verify(llmGatewayService, never()).generateDirectAnswer(any());
        verify(aiQuestionLogMapper).insert(any());
    }

    @Test
    void askSynthesizesDockerPostgresConnectionAnswerWithoutRunbookNoise() {
        String question = "Why does my backend container fail to connect to PostgreSQL even though the database container is running?";
        when(knowledgeArticleMapper.findPublishedArticles()).thenReturn(List.of(postgresRunbookArticle(), dockerComposeArticle()));

        AiQueryPlan plan = new AiQueryUnderstandingService().analyze(question);
        String groundedDraft = aiAssistantService.buildTemplateAnswer(
                plan,
                postgresRunbookArticle().getContent() + "\n" + dockerComposeArticle().getContent(),
                List.of()
        );
        assertThat(groundedDraft).as("deterministic grounded draft").isNotBlank();
        assertThat(groundedDraft)
                .as("deterministic grounded draft=%s", groundedDraft)
                .doesNotContainIgnoringCase("use this runbook", "primary keywords", "runbook runbook");
        assertThat(aiAssistantService.isGeneratedAnswerUsable(groundedDraft))
                .as("deterministic grounded draft=%s", groundedDraft)
                .isTrue();

        AiAskDTO askDTO = new AiAskDTO();
        askDTO.setQuestion(question);

        AiAnswerVO answer = aiAssistantService.ask(askDTO);

        assertThat(answer.getSourceType())
                .as("answer=%s trace=%s", answer.getAnswer(), answer.getRetrievalTrace())
                .isEqualTo("NONE");
        assertThat(answer.getAnswer()).containsIgnoringCase("enough reliable knowledge");
        assertThat(answer.getRetrievalTrace()).isNotNull();
        verify(webEvidenceService).searchAndStoreEvidence(question, 4L);
        verify(aiQuestionLogMapper).insert(any());
    }

    @Test
    void askSynthesizesDockerMultiStageWebAnswerAsConceptExplanation() {
        String question = "What is the purpose of Docker multi-stage builds, and when should they be used?";
        when(documentChunkMapper.searchChunks(question, 4L, true)).thenReturn(List.of());
        when(knowledgeArticleMapper.findPublishedArticles()).thenReturn(List.of());
        when(webEvidenceService.searchAndStoreEvidence(question, 4L)).thenReturn(Optional.of(multiStageWebEvidence(question)));

        AiAskDTO askDTO = new AiAskDTO();
        askDTO.setQuestion(question);

        AiAnswerVO answer = aiAssistantService.ask(askDTO);

        assertThat(answer.getSourceType()).isEqualTo("WEB");
        assertThat(answer.getAnswer()).containsIgnoringCase("runtime");
        assertThat(answer.getAnswer()).containsIgnoringCase("Recommended Actions");
        assertThat(answer.getAnswer()).containsIgnoringCase("final image");
        assertThat(answer.getAnswer()).containsIgnoringCase("Common Causes");
        assertThat(answer.getAnswer()).doesNotContainIgnoringCase("docker build --target");
        assertCleanUserAnswer(answer.getAnswer());
        verify(webEvidenceService).searchAndStoreEvidence(question, 4L);
        verify(aiQuestionLogMapper).insert(any());
    }

    @Test
    void askTreatsHowDoesImproveQuestionAsConceptExplanation() {
        String question = "How does Docker BuildKit improve image build performance?";
        when(documentChunkMapper.searchChunks(question, 4L, true)).thenReturn(List.of());
        when(knowledgeArticleMapper.findPublishedArticles()).thenReturn(List.of());
        when(webEvidenceService.searchAndStoreEvidence(question, 4L)).thenReturn(Optional.of(buildKitWebEvidence(question)));

        AiAskDTO askDTO = new AiAskDTO();
        askDTO.setQuestion(question);

        AiAnswerVO answer = aiAssistantService.ask(askDTO);

        assertThat(answer.getSourceType()).isEqualTo("WEB");
        assertThat(answer.getAnswer()).containsIgnoringCase("build");
        assertThat(answer.getAnswer()).containsIgnoringCase("parallel");
        assertThat(answer.getAnswer()).containsIgnoringCase("cache");
        assertThat(answer.getAnswer()).containsIgnoringCase("Recommended Actions");
        assertThat(answer.getAnswer()).doesNotContainIgnoringCase("Likely Causes");
        assertThat(answer.getAnswer()).doesNotContainIgnoringCase("Checks");
        assertThat(answer.getAnswer()).doesNotContainIgnoringCase("Fix");
        assertThat(answer.getAnswer()).doesNotContainIgnoringCase("Verify the exact configuration");
        assertCleanUserAnswer(answer.getAnswer());
        verify(webEvidenceService).searchAndStoreEvidence(question, 4L);
        verify(aiQuestionLogMapper).insert(any());
    }

    @Test
    void askUsesInternalTicketPriorityKnowledgeWithoutFallingBackToNone() {
        String question = "How should I determine whether a support ticket is P1, P2, or P3?";
        when(knowledgeArticleMapper.findPublishedArticles()).thenReturn(List.of(ticketPriorityArticle()));

        AiAskDTO askDTO = new AiAskDTO();
        askDTO.setQuestion(question);

        AiAnswerVO answer = aiAssistantService.ask(askDTO);

        assertThat(answer.getSourceType()).isEqualTo("ARTICLE");
        assertThat(answer.getAnswer()).containsIgnoringCase("P1");
        assertThat(answer.getAnswer()).containsIgnoringCase("P2");
        assertThat(answer.getAnswer()).containsIgnoringCase("P3");
        assertThat(answer.getAnswer()).containsIgnoringCase("Direct Answer");
        assertThat(answer.getAnswer()).doesNotContainIgnoringCase("I do not have enough reliable knowledge");
        assertCleanUserAnswer(answer.getAnswer());
        verify(webEvidenceService, never()).searchAndStoreEvidence(any(), any());
        verify(aiQuestionLogMapper).insert(any());
    }

    @Test
    void askAnswersCompoundConceptQuestionFromWebWithoutFallingBackToNone() {
        String question = "What is the difference between ARG and ENV in a Dockerfile? why should I use a .dockerignore file when building Docker images?";
        when(documentChunkMapper.searchChunks(question, 4L, true)).thenReturn(List.of());
        when(knowledgeArticleMapper.findPublishedArticles()).thenReturn(List.of());
        when(webEvidenceService.searchAndStoreEvidence(question, 4L)).thenReturn(Optional.of(dockerBuildBasicsWebEvidence(question)));

        AiAskDTO askDTO = new AiAskDTO();
        askDTO.setQuestion(question);

        AiAnswerVO answer = aiAssistantService.ask(askDTO);

        assertThat(answer.getSourceType()).isEqualTo("WEB");
        assertThat(answer.getAnswer()).containsIgnoringCase("ARG");
        assertThat(answer.getAnswer()).containsIgnoringCase("ENV");
        assertThat(answer.getAnswer()).containsIgnoringCase(".dockerignore");
        assertThat(answer.getAnswer()).containsIgnoringCase("build context");
        assertThat(answer.getAnswer()).containsIgnoringCase("Recommended Actions");
        assertThat(answer.getAnswer()).doesNotContainIgnoringCase("I do not have enough reliable knowledge");
        assertCleanUserAnswer(answer.getAnswer());
        verify(webEvidenceService).searchAndStoreEvidence(question, 4L);
        verify(aiQuestionLogMapper).insert(any());
    }

    @Test
    void askSynthesizesDockerfileInstructionAnswerWithoutKeyAnswerFragment() {
        String question = "What is the purpose of the WORKDIR, COPY, RUN, EXPOSE, and CMD instructions in this Dockerfile, and how do they help build and run the Python application inside a container?";
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

    @Test
    void askTreatsGatewayHttpRouteAsConceptNotTicketRoutingDecision() {
        String question = "What is Kubernetes Gateway API HTTPRoute and when should it replace Ingress?";
        when(knowledgeArticleChunkMapper.searchChunks(any(), any(), anyInt())).thenReturn(List.of(gatewayChunk(), genericKubernetesChunk()));

        AiAskDTO askDTO = new AiAskDTO();
        askDTO.setQuestion(question);

        AiAnswerVO answer = aiAssistantService.ask(askDTO);

        assertThat(answer.getSourceType()).isEqualTo("ARTICLE");
        assertThat(answer.getAnswer()).containsIgnoringCase("HTTPRoute");
        assertThat(answer.getAnswer()).containsIgnoringCase("Gateway API");
        assertThat(answer.getAnswer()).containsIgnoringCase("Ingress");
        assertThat(answer.getAnswer()).doesNotContainIgnoringCase("Decision Criteria");
        assertThat(answer.getAnswer()).doesNotContainIgnoringCase("user impact");
        assertCleanUserAnswer(answer.getAnswer());
        verify(webEvidenceService, never()).searchAndStoreEvidence(any(), any());
        verify(aiQuestionLogMapper).insert(any());
    }

    @Test
    void askDoesNotLeakDockerTemplateIntoRedisConceptAnswer() {
        String question = "How does Redis client-side caching with server-assisted tracking work?";
        when(knowledgeArticleChunkMapper.searchChunks(any(), any(), anyInt())).thenReturn(List.of(genericRedisChunk(), redisClientCachingChunk()));

        AiAskDTO askDTO = new AiAskDTO();
        askDTO.setQuestion(question);

        AiAnswerVO answer = aiAssistantService.ask(askDTO);

        assertThat(answer.getSourceType()).isEqualTo("ARTICLE");
        assertThat(answer.getAnswer()).containsIgnoringCase("client-side caching");
        assertThat(answer.getAnswer()).containsIgnoringCase("tracking");
        assertThat(answer.getAnswer()).containsIgnoringCase("invalidation");
        assertThat(answer.getAnswer()).doesNotContainIgnoringCase("Dockerfile");
        assertThat(answer.getAnswer()).doesNotContainIgnoringCase("Compose");
        assertCleanUserAnswer(answer.getAnswer());
        verify(webEvidenceService, never()).searchAndStoreEvidence(any(), any());
        verify(aiQuestionLogMapper).insert(any());
    }

    @Test
    void answersRedisAvailabilityFromInternalChunkWithoutWebFallback() {
        String question = "What are the main troubleshooting steps when Redis becomes unavailable in a production environment?";
        when(knowledgeArticleChunkMapper.searchChunks(any(), any(), anyInt())).thenReturn(List.of(genericRedisChunk()));

        AiAskDTO askDTO = new AiAskDTO();
        askDTO.setQuestion(question);

        AiAnswerVO answer = aiAssistantService.ask(askDTO);

        assertThat(answer.getAnswerMode()).isEqualTo("INTERNAL_RAG");
        assertThat(answer.getSourceType()).isEqualTo("ARTICLE");
        assertThat(answer.getAnswer()).containsIgnoringCase("Redis");
        assertThat(answer.getAnswer()).containsIgnoringCase("healthy");
        assertThat(answer.getAnswer()).doesNotContainIgnoringCase("enough reliable knowledge");
        assertCleanUserAnswer(answer.getAnswer());
        verify(webEvidenceService, never()).searchAndStoreEvidence(any(), any());
        verify(llmGatewayService, never()).generateDirectAnswer(any());
        verify(aiQuestionLogMapper).insert(any());
    }

    @Test
    void askPrioritizesRoleChangeTimelineOverUnaskedAuthSideConcepts() {
        String question = """
                We have a production user named alice. She logged into the system at 09:00 AM.
                At 10:00 AM, the admin updated her role from USER to ADMIN directly in PostgreSQL.
                At 10:15 AM, she receives 403 on GET /api/admin/analytics but can access GET /api/tickets/my.
                What is the most likely root cause given our JWT and Redis architecture?
                """;
        when(knowledgeArticleMapper.findPublishedArticles()).thenReturn(List.of(authRoleChangeArticleWithDpopNoise()));

        AiAskDTO askDTO = new AiAskDTO();
        askDTO.setQuestion(question);

        AiAnswerVO answer = aiAssistantService.ask(askDTO);

        assertThat(answer.getSourceType()).isEqualTo("NONE");
        assertThat(answer.getAnswer()).containsIgnoringCase("enough reliable knowledge");
        assertThat(answer.getRetrievalTrace()).isNotNull();
        verify(webEvidenceService).searchAndStoreEvidence(any(), any());
        verify(llmGatewayService, never()).generateGroundedAnswer(any(), any(), any());
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

    private KnowledgeArticle authRoleChangeArticleWithDpopNoise() {
        KnowledgeArticle article = new KnowledgeArticle();
        article.setId(16L);
        article.setTitle("Auth / JWT Knowledge Pack");
        article.setCategory("Auth / JWT");
        article.setContent("""
                403 means the request is authenticated but not authorized.
                A valid USER token must not access ADMIN endpoints.
                JWT claims are captured when the token is issued, so changing a user's database role later does not change an already issued token.
                If a user is promoted from USER to ADMIN, ask the user to sign out and sign in again so the frontend stores a fresh token with the updated role.
                Redis stores whether the login token is still active, but it does not rewrite JWT role claims after database role changes.
                DPoP is a proof-of-possession mechanism where the client creates a request-specific proof JWT.
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

    private KnowledgeArticle ticketPriorityArticle() {
        KnowledgeArticle article = new KnowledgeArticle();
        article.setId(15L);
        article.setTitle("CloudOps Ticket Operations Knowledge Pack");
        article.setCategory("Ticket Rules");
        article.setContent("""
                Category: Ticket Rules
                Primary keywords: ticket, priority, sla, routing, owner, p1, p2, p3, escalation
                Purpose
                Use this playbook when support must decide severity, owner, and user-facing communication.
                Priority rules
                P1: Production outage, data loss risk, authentication outage, security-sensitive issue, or broad customer impact.
                P2: Major feature degraded, repeated team-level issue, document upload unavailable for a group, or important workaround required.
                P3: Single-user issue, informational request, minor UI problem, or non-urgent knowledge request.
                SLA model
                P1: immediate response and frequent updates.
                P2: same business day response.
                P3: normal queue handling.
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

    private WebEvidence buildKitWebEvidence(String question) {
        WebEvidence evidence = new WebEvidence();
        evidence.setId(33L);
        evidence.setQuestion(question);
        evidence.setTitle("Docker BuildKit");
        evidence.setDomain("docs.docker.com");
        evidence.setUrl("https://docs.docker.com/build/buildkit/");
        evidence.setCredibilityScore(new BigDecimal("0.95"));
        evidence.setStatus("NEEDS_REVIEW");
        evidence.setExtractedContent("""
                BuildKit provides improved functionality and improves builds' performance over the legacy builder.
                BuildKit uses a fully concurrent build graph solver.
                It can run build steps in parallel when possible and optimize out commands that do not have an impact on the final result.
                BuildKit can also improve cache behavior for repeated Docker image builds.
                """);
        return evidence;
    }

    private WebEvidence dockerBuildBasicsWebEvidence(String question) {
        WebEvidence evidence = new WebEvidence();
        evidence.setId(34L);
        evidence.setQuestion(question);
        evidence.setTitle("Dockerfile ARG ENV and Dockerignore");
        evidence.setDomain("docs.docker.com");
        evidence.setUrl("https://docs.docker.com/reference/dockerfile/");
        evidence.setCredibilityScore(new BigDecimal("0.95"));
        evidence.setStatus("NEEDS_REVIEW");
        evidence.setExtractedContent("""
                ARG defines a variable that users can pass at build-time to the builder with the docker build command.
                ENV sets environment variables that are available to subsequent Dockerfile instructions and can persist in containers created from the image.
                A .dockerignore file excludes files and directories from the build context before it is sent to the builder.
                Excluding unnecessary files can make builds faster, improve cache behavior, and avoid sending local secrets or generated artifacts.
                """);
        return evidence;
    }

    private KnowledgeArticleChunkSearchResultVO gatewayChunk() {
        KnowledgeArticleChunkSearchResultVO chunk = new KnowledgeArticleChunkSearchResultVO();
        chunk.setChunkId(91L);
        chunk.setArticleId(31L);
        chunk.setArticleTitle("CloudOps Kubernetes Knowledge Pack");
        chunk.setCategory("Kubernetes");
        chunk.setChunkIndex(2);
        chunk.setSectionTitle("Gateway API and HTTPRoute");
        chunk.setSectionType("CONCEPT");
        chunk.setConceptTags("kubernetes, gateway api, httproute, ingress, routing");
        chunk.setContent("""
                Kubernetes Gateway API separates infrastructure-level traffic entry points from application-level routing rules.
                Gateway represents where traffic enters the cluster, while HTTPRoute defines how HTTP requests are matched and forwarded to Services.
                Gateway API should replace Ingress when teams need richer routing, cross-namespace attachment rules, and clearer platform/application ownership boundaries.
                HTTPRoute must be attached to an allowed Gateway and must point to valid backend Services.
                """);
        chunk.setRelevanceScore(0.20);
        return chunk;
    }

    private KnowledgeArticleChunkSearchResultVO genericKubernetesChunk() {
        KnowledgeArticleChunkSearchResultVO chunk = new KnowledgeArticleChunkSearchResultVO();
        chunk.setChunkId(92L);
        chunk.setArticleId(31L);
        chunk.setArticleTitle("CloudOps Kubernetes Knowledge Pack");
        chunk.setCategory("Kubernetes");
        chunk.setChunkIndex(0);
        chunk.setSectionTitle("Overview");
        chunk.setSectionType("CONCEPT");
        chunk.setConceptTags("kubernetes, pod, service");
        chunk.setContent("""
                This Knowledge Pack covers Kubernetes support operations.
                Readiness controls whether a pod receives traffic and liveness controls whether Kubernetes restarts a container.
                Keep dashboards separated for application health and infrastructure health.
                """);
        chunk.setRelevanceScore(0.90);
        return chunk;
    }

    private KnowledgeArticleChunkSearchResultVO redisClientCachingChunk() {
        KnowledgeArticleChunkSearchResultVO chunk = new KnowledgeArticleChunkSearchResultVO();
        chunk.setChunkId(93L);
        chunk.setArticleId(35L);
        chunk.setArticleTitle("CloudOps Redis Cache Knowledge Pack");
        chunk.setCategory("Redis Cache");
        chunk.setChunkIndex(3);
        chunk.setSectionTitle("Client-Side Caching");
        chunk.setSectionType("CONCEPT");
        chunk.setConceptTags("redis, client-side caching, server-assisted tracking, invalidation");
        chunk.setContent("""
                Redis client-side caching lets clients cache read results locally while Redis tracks which keys were read.
                Server-assisted tracking sends invalidation messages when cached keys change, so clients know cached values are no longer valid.
                Use it for high-read data where latency reduction matters and the application can handle invalidations correctly.
                """);
        chunk.setRelevanceScore(0.25);
        return chunk;
    }

    private KnowledgeArticleChunkSearchResultVO genericRedisChunk() {
        KnowledgeArticleChunkSearchResultVO chunk = new KnowledgeArticleChunkSearchResultVO();
        chunk.setChunkId(94L);
        chunk.setArticleId(35L);
        chunk.setArticleTitle("CloudOps Redis Cache Knowledge Pack");
        chunk.setCategory("Redis Cache");
        chunk.setChunkIndex(0);
        chunk.setSectionTitle("Troubleshooting");
        chunk.setSectionType("TROUBLESHOOTING");
        chunk.setConceptTags("redis, latency, memory");
        chunk.setContent("""
                Redis failures often appear as login problems, slow API responses, missing session state, background job delays, or repeated retries.
                Check whether Redis is healthy, restarting, or out of memory.
                """);
        chunk.setRelevanceScore(0.95);
        return chunk;
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
        assertThat(answer).doesNotContainIgnoringCase("Use this playbook");
        assertThat(answer).doesNotContainIgnoringCase("Category:");
        assertThat(answer).doesNotContainIgnoringCase("Knowledge Candidate");
        assertThat(answer).doesNotContainIgnoringCase("Observed answer");
        assertThat(answer).doesNotContainIgnoringCase("Review this content before publishing");
    }
}
