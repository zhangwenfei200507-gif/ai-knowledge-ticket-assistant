package com.zwf.aisupport.service.impl;

import com.zwf.aisupport.common.exception.BusinessException;
import com.zwf.aisupport.common.util.CurrentUserUtil;
import com.zwf.aisupport.common.util.KnowledgeArticleVoMapper;
import com.zwf.aisupport.common.util.KnowledgeCategoryResolver;
import com.zwf.aisupport.common.util.PageRequestUtil;
import com.zwf.aisupport.common.util.TextUtil;
import com.zwf.aisupport.dto.AiAskDTO;
import com.zwf.aisupport.dto.AiQuestionLogFeedbackDTO;
import com.zwf.aisupport.dto.KnowledgeArticleCreateDTO;
import com.zwf.aisupport.entity.AiQuestionLog;
import com.zwf.aisupport.entity.DocumentChunk;
import com.zwf.aisupport.entity.KnowledgeArticle;
import com.zwf.aisupport.entity.Ticket;
import com.zwf.aisupport.entity.WebEvidence;
import com.zwf.aisupport.entity.WebEvidenceSource;
import com.zwf.aisupport.mapper.AiQuestionLogMapper;
import com.zwf.aisupport.mapper.DocumentChunkMapper;
import com.zwf.aisupport.mapper.KnowledgeArticleChunkMapper;
import com.zwf.aisupport.mapper.KnowledgeArticleMapper;
import com.zwf.aisupport.mapper.TicketMapper;
import com.zwf.aisupport.service.AiAssistantService;
import com.zwf.aisupport.service.DocumentService;
import com.zwf.aisupport.service.LlmGatewayService;
import com.zwf.aisupport.service.WebEvidenceService;
import com.zwf.aisupport.service.model.LlmGenerationResult;
import com.zwf.aisupport.vo.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class AiAssistantServiceImpl implements AiAssistantService {

    private static final Logger log = LoggerFactory.getLogger(AiAssistantServiceImpl.class);

    private static final Set<String> BROAD_DOMAIN_TERMS = Set.of(
            "auth", "authentication", "authorization", "jwt", "token", "kubernetes", "postgresql",
            "postgres", "redis", "docker", "production", "troubleshooting", "document", "article",
            "knowledge", "platform", "cloudops", "support", "server", "service", "services", "system",
            "container", "containers", "database", "backend", "frontend", "application"
    );
    private static final Set<String> TECHNICAL_DOMAIN_TERMS = Set.of(
            "docker", "postgresql", "postgres", "redis", "kubernetes", "jwt", "token", "ticket"
    );
    private static final Set<String> GENERIC_QUESTION_TERMS = Set.of(
            "rejected", "reject", "expired", "expire", "failure", "failed", "error", "issue",
            "problem", "handle", "configure", "download", "install", "login", "logout", "users",
            "inside", "instead", "communication", "connect", "connects", "connected", "connecting",
            "use", "using", "used", "names", "name", "running", "fail", "fails", "decide", "choose",
            "classify", "classification", "return", "returns", "returned", "purpose", "updated",
            "update", "receives", "forbidden", "normal", "endpoint", "endpoints", "specific",
            "interesting", "interestingly", "directly", "production", "named", "called", "logged", "system",
            "architecture", "given", "likely", "root", "cause", "access", "analytics"
    );
    private final KnowledgeArticleMapper knowledgeArticleMapper;
    private final KnowledgeArticleChunkMapper knowledgeArticleChunkMapper;
    private final TicketMapper ticketMapper;
    private final AiQuestionLogMapper aiQuestionLogMapper;
    private final DocumentChunkMapper documentChunkMapper;
    private final DocumentService documentService;
    private final WebEvidenceService webEvidenceService;
    private final LlmGatewayService llmGatewayService;
    private final KnowledgePackReorganizer knowledgePackReorganizer;
    private final LlmUsagePolicy llmUsagePolicy;
    private final AiQueryUnderstandingService queryUnderstandingService;
    private final KnowledgeCoverageEvaluator coverageEvaluator;
    private final KnowledgeChunkRanker knowledgeChunkRanker;
    private final KnowledgeArticleChunkManager knowledgeArticleChunkManager;
    private final RetrievalTraceCodec retrievalTraceCodec;
    private final GroundedAnswerGuard groundedAnswerGuard = new GroundedAnswerGuard();

    public AiAssistantServiceImpl(KnowledgeArticleMapper knowledgeArticleMapper,
                                  KnowledgeArticleChunkMapper knowledgeArticleChunkMapper,
                                  TicketMapper ticketMapper,
                                  AiQuestionLogMapper aiQuestionLogMapper,
                                  DocumentChunkMapper documentChunkMapper,
                                  DocumentService documentService,
                                  WebEvidenceService webEvidenceService,
                                  LlmGatewayService llmGatewayService,
                                  KnowledgePackReorganizer knowledgePackReorganizer,
                                  LlmUsagePolicy llmUsagePolicy,
                                  AiQueryUnderstandingService queryUnderstandingService,
                                  KnowledgeCoverageEvaluator coverageEvaluator,
                                  KnowledgeChunkRanker knowledgeChunkRanker,
                                   KnowledgeArticleChunkManager knowledgeArticleChunkManager,
                                  RetrievalTraceCodec retrievalTraceCodec) {
        this.knowledgeArticleMapper = knowledgeArticleMapper;
        this.knowledgeArticleChunkMapper = knowledgeArticleChunkMapper;
        this.ticketMapper = ticketMapper;
        this.aiQuestionLogMapper = aiQuestionLogMapper;
        this.documentChunkMapper = documentChunkMapper;
        this.documentService = documentService;
        this.webEvidenceService = webEvidenceService;
        this.llmGatewayService = llmGatewayService;
        this.knowledgePackReorganizer = knowledgePackReorganizer;
        this.llmUsagePolicy = llmUsagePolicy;
        this.queryUnderstandingService = queryUnderstandingService;
        this.coverageEvaluator = coverageEvaluator;
        this.knowledgeChunkRanker = knowledgeChunkRanker;
        this.knowledgeArticleChunkManager = knowledgeArticleChunkManager;
        this.retrievalTraceCodec = retrievalTraceCodec;
    }

    @Override
    public AiAnswerVO ask(AiAskDTO aiAskDTO) {
        AiAnswerVO answerVO = findAnswerFromKnowledgeBase(aiAskDTO);

        // Record every AI question so the platform can review usage and improve knowledge later.
        saveQuestionLog(aiAskDTO.getQuestion(), answerVO);

        return answerVO;
    }

    @Override
    public AiAnswerVO askWithFile(String question, MultipartFile file) {
        if (question == null || question.isBlank()) {
            throw new BusinessException("Question cannot be blank");
        }

        String trimmedQuestion = question.trim();
        if (file == null || file.isEmpty()) {
            AiAskDTO askDTO = new AiAskDTO();
            askDTO.setQuestion(trimmedQuestion);
            return ask(askDTO);
        }

        KnowledgeDocumentVO uploadedDocument = documentService.upload(file, "User Upload");
        List<DocumentSearchResultVO> uploadedDocumentResults = findUploadedDocumentAnswer(trimmedQuestion, uploadedDocument);
        AiAnswerVO answerVO;
        if (uploadedDocumentResults.isEmpty()) {
            answerVO = findAnswerFromKnowledgeBase(trimmedQuestion);
        } else {
            RetrievalTraceState trace = new RetrievalTraceState();
            trace.start("QUERY_ANALYSIS");
            AiQueryPlan plan = queryUnderstandingService.analyze(trimmedQuestion);
            trace.finish("QUERY_ANALYSIS", "SUCCEEDED", "Question intent, domains, and required concepts identified", null);
            trace.internalAttempted = true;
            trace.internalSufficient = true;
            trace.finish("INTERNAL_RETRIEVAL", "SUCCEEDED", "Relevant chunks selected from the uploaded file", null);
            trace.finish("EVIDENCE_EVALUATION", "SUCCEEDED", "Uploaded-file evidence covers the question", null);
            trace.finish("WEB_RETRIEVAL", "SKIPPED", "Internal uploaded-file evidence is sufficient", null);
            trace.finish("LLM_FALLBACK", "SKIPPED", "Direct fallback is unnecessary", null);
            trace.start("ANSWER_COMPOSITION");
            answerVO = finalizeAnswer(
                    buildDocumentBasedAnswer(trimmedQuestion, plan, uploadedDocumentResults),
                    trace,
                    null
            );
        }

        saveQuestionLog(trimmedQuestion, answerVO);
        return answerVO;
    }

    @Override
    public AiAnswerVO askAndCreateTicket(AiAskDTO aiAskDTO) {
        AiAnswerVO answerVO = findAnswerFromKnowledgeBase(aiAskDTO);
        if (!Boolean.TRUE.equals(answerVO.getSuggestCreateTicket())) {
            saveQuestionLog(aiAskDTO.getQuestion(), answerVO);
            return answerVO;
        }

        Long currentUserId = CurrentUserUtil.requireCurrentUserId();

        Ticket ticket = new Ticket();
        ticket.setTitle(generateTicketTitle(aiAskDTO.getQuestion()));
        ticket.setDescription(aiAskDTO.getQuestion());
        ticket.setStatus("OPEN");
        ticket.setPriority(suggestPriority(aiAskDTO.getQuestion()));
        ticket.setCreatedBy(currentUserId);

        // Create a support ticket when the knowledge base cannot answer the user question.
        ticketMapper.insert(ticket);

        answerVO.setCreatedTicketId(ticket.getId());

        // Keep the AI answer and the created ticket connected for later review.
        saveQuestionLog(aiAskDTO.getQuestion(), answerVO);

        return answerVO;
    }

    private AiAnswerVO findAnswerFromKnowledgeBase(AiAskDTO aiAskDTO) {
        return findAnswerFromKnowledgeBase(aiAskDTO.getQuestion().trim());
    }

    private AiAnswerVO findAnswerFromKnowledgeBase(String question) {
        RetrievalTraceState trace = new RetrievalTraceState();
        trace.start("QUERY_ANALYSIS");
        AiQueryPlan contract = queryUnderstandingService.analyze(question);
        trace.finish("QUERY_ANALYSIS", "SUCCEEDED", "Question intent, domains, and required concepts identified", null);
        trace.internalAttempted = true;
        trace.start("INTERNAL_RETRIEVAL");
        List<String> terms = contract.retrievalTerms();
        List<String> specificTerms = queryUnderstandingService.extractSpecificTerms(terms);
        List<String> versionTerms = queryUnderstandingService.extractVersionTerms(terms);
        List<String> coreConcepts = contract.requiredConcepts();
        List<AiAnswerSourceVO> internalSources = new ArrayList<>();

        List<KnowledgeArticleChunkSearchResultVO> articleChunks = findRelevantArticleChunks(question, contract, terms, specificTerms, versionTerms, coreConcepts);
        if (!articleChunks.isEmpty()) {
            List<AiAnswerSourceVO> articleChunkSources = buildArticleChunkSources(articleChunks);
            internalSources.addAll(articleChunkSources);
            trace.start("EVIDENCE_EVALUATION");
            if (!contract.externalEvidenceRequired()
                    && hasSufficientCoverage(articleChunkSources, contract)) {
                trace.start("ANSWER_COMPOSITION");
                AiAnswerVO answer = buildArticleChunkBasedAnswer(question, contract, articleChunks);
                trace.llmAttempted = shouldAttemptGroundedSynthesis("ARTICLE", articleChunkSources);
                if (isGeneratedAnswerUsable(answer.getAnswer())) {
                    trace.internalSufficient = true;
                    trace.finish("INTERNAL_RETRIEVAL", "SUCCEEDED", "Internal article chunks selected", null);
                    trace.finish("EVIDENCE_EVALUATION", "SUCCEEDED", "Internal evidence fully covers the question", null);
                    return finalizeAnswer(answer, trace, null);
                }
            }
        }

        // Whole-article matching is retained only for installations that have not built semantic chunks yet.
        List<KnowledgeArticle> articles = List.of();
        if (!hasPublishedArticleChunks()) {
            articles = findRelevantArticles(question, contract, terms, specificTerms, versionTerms, coreConcepts);
            if (!articles.isEmpty()) {
                List<AiAnswerSourceVO> articleSources = buildArticleSources(articles);
                internalSources.addAll(articleSources);
                trace.start("EVIDENCE_EVALUATION");
                if (!contract.externalEvidenceRequired()
                        && hasSufficientCoverage(articleSources, contract)) {
                    trace.start("ANSWER_COMPOSITION");
                    AiAnswerVO answer = buildKnowledgeBasedAnswer(question, contract, articles);
                    trace.llmAttempted = shouldAttemptGroundedSynthesis("ARTICLE", articleSources);
                    if (isGeneratedAnswerUsable(answer.getAnswer())) {
                        trace.internalSufficient = true;
                        trace.finish("INTERNAL_RETRIEVAL", "SUCCEEDED", "Internal articles selected", null);
                        trace.finish("EVIDENCE_EVALUATION", "SUCCEEDED", "Internal evidence fully covers the question", null);
                        return finalizeAnswer(answer, trace, null);
                    }
                }
            }
        }

        // Search document chunks after curated articles. Uploaded-file Q&A still has its own document-first path.
        List<DocumentSearchResultVO> documentResults = documentChunkMapper.searchChunks(
                        question,
                        CurrentUserUtil.requireCurrentUserId(),
                        CurrentUserUtil.isAdmin()
                )
                .stream()
                .filter(result -> isReliableInternalSource(result.getContent(), terms, specificTerms, versionTerms, coreConcepts)
                        || hasSufficientCoverage(result.getContent(), contract))
                .toList();
        if (!documentResults.isEmpty()) {
            List<AiAnswerSourceVO> documentSources = buildDocumentSources(documentResults);
            internalSources.addAll(documentSources);
            trace.start("EVIDENCE_EVALUATION");
            if (!contract.externalEvidenceRequired()
                    && hasSufficientCoverage(documentSources, contract)) {
                trace.start("ANSWER_COMPOSITION");
                AiAnswerVO answer = buildDocumentBasedAnswer(question, contract, documentResults);
                trace.llmAttempted = shouldAttemptGroundedSynthesis("DOCUMENT", documentSources);
                if (isGeneratedAnswerUsable(answer.getAnswer())) {
                    trace.internalSufficient = true;
                    trace.finish("INTERNAL_RETRIEVAL", "SUCCEEDED", "Internal document chunks selected", null);
                    trace.finish("EVIDENCE_EVALUATION", "SUCCEEDED", "Internal evidence fully covers the question", null);
                    return finalizeAnswer(answer, trace, null);
                }
            }
        }

        Long currentUserId = CurrentUserUtil.requireCurrentUserId();
        trace.finish(
                "INTERNAL_RETRIEVAL",
                internalSources.isEmpty() ? "INSUFFICIENT" : "SUCCEEDED",
                internalSources.isEmpty() ? "No relevant internal evidence selected" : internalSources.size() + " internal sources selected",
                null
        );
        trace.finish("EVIDENCE_EVALUATION", "INSUFFICIENT", "Internal evidence does not fully cover the question", null);
        boolean webSearchApplicable = !queryUnderstandingService.isCreativeOrHypothetical(question);
        trace.webAttempted = webSearchApplicable;
        if (webSearchApplicable) {
            trace.start("WEB_RETRIEVAL");
        } else {
            trace.finish("WEB_RETRIEVAL", "SKIPPED", "Web retrieval is not applicable to creative or hypothetical questions", null);
        }
        Optional<WebEvidence> webEvidence = webSearchApplicable
                ? webEvidenceService.searchAndStoreEvidence(question, currentUserId)
                : Optional.empty();
        trace.webSucceeded = webEvidence.isPresent();
        if (webSearchApplicable) {
            trace.finish(
                    "WEB_RETRIEVAL",
                    webEvidence.isPresent() ? "SUCCEEDED" : "INSUFFICIENT",
                    webEvidence.isPresent() ? "External evidence collected" : "No acceptable external evidence found",
                    null
            );
        }
        if (webEvidence.isPresent()) {
            List<AiAnswerSourceVO> webSources = new ArrayList<>(internalSources);
            webSources.addAll(buildWebEvidenceSources(webEvidence.get()));
            trace.start("EVIDENCE_EVALUATION");
            if (hasSufficientCoverage(webSources, contract)) {
                trace.webSufficient = true;
                trace.finish("EVIDENCE_EVALUATION", "SUCCEEDED", "Combined internal and web evidence fully covers the question", null);
                trace.llmAttempted = shouldAttemptGroundedSynthesis("WEB", webSources);
                trace.start("ANSWER_COMPOSITION");
                AiAnswerVO answer = buildWebEvidenceBasedAnswer(question, contract, webEvidence.get(), internalSources);
                if (isGeneratedAnswerUsable(answer.getAnswer())) {
                    return finalizeAnswer(answer, trace, null);
                }
            }
        }

        boolean directFallbackAllowed = llmUsagePolicy.canUseDirectFallback(question);
        trace.llmAttempted = directFallbackAllowed;
        if (directFallbackAllowed) {
            trace.start("LLM_FALLBACK");
        } else {
            trace.finish("LLM_FALLBACK", "SKIPPED", "Direct model fallback is disabled by policy", null);
        }
        LlmGenerationResult directResult = directFallbackAllowed ? safeGenerateDirectAnswer(question) : null;
        Optional<String> modelAnswer = directResult == null
                ? Optional.empty()
                : directResult.answerOptional()
                .map(this::normalizeEnterpriseAnswer)
                .filter(answer -> !answer.isBlank())
                .filter(this::isAnswerPresentationClean)
                .filter(answer -> groundedAnswerGuard.containsOnlyVerifiedUrls(answer, List.of()))
                .map(groundedAnswerGuard::markUnverifiedFallback);
        if (modelAnswer.isPresent()) {
            trace.llmSucceeded = true;
            trace.finish("LLM_FALLBACK", "SUCCEEDED", "Model fallback returned a usable answer", null);
            WebEvidence modelEvidence = webEvidenceService.storeModelGeneratedEvidence(question, modelAnswer.get(), currentUserId);
            trace.start("ANSWER_COMPOSITION");
            AiAnswerVO answer = buildModelGeneratedAnswer(modelEvidence, modelAnswer.get());
            copyLlmResult(directResult, answer);
            return finalizeAnswer(answer, trace, null);
        }

        String failureReason;
        if (directFallbackAllowed && !llmGatewayService.isConfigured()) {
            failureReason = "LLM_NOT_CONFIGURED";
        } else if (directFallbackAllowed) {
            failureReason = "LLM_NO_USABLE_RESPONSE";
        } else if (webSearchApplicable && !trace.webSucceeded) {
            failureReason = "WEB_NO_EVIDENCE";
        } else if (trace.webSucceeded && !trace.webSufficient) {
            failureReason = "WEB_INSUFFICIENT_COVERAGE";
        } else {
            failureReason = "NO_SUFFICIENT_EVIDENCE";
        }
        if (directFallbackAllowed) {
            trace.finish("LLM_FALLBACK", "FAILED", "Model fallback did not return a usable answer", failureReason);
        }
        return finalizeAnswer(buildFallbackAnswer(), trace, failureReason);
    }

    private boolean shouldAttemptGroundedSynthesis(String sourceType, List<AiAnswerSourceVO> sources) {
        return llmGatewayService.isConfigured() && llmUsagePolicy.canUseGroundedSynthesis(sourceType, sources);
    }

    private AiAnswerVO finalizeAnswer(AiAnswerVO answer,
                                      RetrievalTraceState state,
                                      String failureReason) {
        if ("LLM".equals(answer.getAnswerEngine())) {
            state.llmAttempted = true;
            state.llmSucceeded = true;
        }
        AiRetrievalTraceVO trace = new AiRetrievalTraceVO();
        trace.setInternalAttempted(state.internalAttempted);
        trace.setInternalSufficient(state.internalSufficient);
        trace.setWebAttempted(state.webAttempted);
        trace.setWebSucceeded(state.webSucceeded);
        trace.setWebSufficient(state.webSufficient);
        trace.setLlmAttempted(state.llmAttempted);
        trace.setLlmSucceeded(state.llmSucceeded);
        trace.setFinalMode(answer.getAnswerMode());
        trace.setFailureReason(failureReason);
        state.finish(
                "ANSWER_COMPOSITION",
                isGeneratedAnswerUsable(answer.getAnswer()) ? "SUCCEEDED" : "FAILED",
                isGeneratedAnswerUsable(answer.getAnswer()) ? "Answer composed for the selected route" : "No usable answer could be composed",
                isGeneratedAnswerUsable(answer.getAnswer()) ? null : failureReason
        );
        trace.setTotalLatencyMs(state.totalLatencyMs());
        trace.setStages(state.stageViews());
        answer.setRetrievalTrace(trace);
        return answer;
    }

    private static final class RetrievalTraceState {
        private static final List<String> STAGE_ORDER = List.of(
                "QUERY_RECEIVED",
                "QUERY_ANALYSIS",
                "INTERNAL_RETRIEVAL",
                "EVIDENCE_EVALUATION",
                "WEB_RETRIEVAL",
                "LLM_FALLBACK",
                "ANSWER_COMPOSITION",
                "RESPONSE_PERSISTENCE"
        );

        private final LocalDateTime requestStartedAt = LocalDateTime.now();
        private final Map<String, MutableTraceStage> stages = new LinkedHashMap<>();
        private boolean internalAttempted;
        private boolean internalSufficient;
        private boolean webAttempted;
        private boolean webSucceeded;
        private boolean webSufficient;
        private boolean llmAttempted;
        private boolean llmSucceeded;

        private RetrievalTraceState() {
            finish("QUERY_RECEIVED", "SUCCEEDED", "Question accepted for routing", null);
        }

        private void start(String stage) {
            stages.computeIfAbsent(stage, ignored -> new MutableTraceStage()).start();
        }

        private void finish(String stage, String status, String summary, String errorCategory) {
            MutableTraceStage value = stages.computeIfAbsent(stage, ignored -> new MutableTraceStage());
            value.finish(status, summary, errorCategory);
        }

        private long totalLatencyMs() {
            return Math.max(0, Duration.between(requestStartedAt, LocalDateTime.now()).toMillis());
        }

        private List<AiRetrievalStageVO> stageViews() {
            return STAGE_ORDER.stream().map(stage -> {
                MutableTraceStage value = stages.get(stage);
                AiRetrievalStageVO view = new AiRetrievalStageVO();
                view.setStage(stage);
                if (value == null) {
                    view.setStatus("NOT_ATTEMPTED");
                    return view;
                }
                view.setStatus(value.status == null ? "NOT_ATTEMPTED" : value.status);
                view.setStartedAt(value.startedAt);
                view.setCompletedAt(value.completedAt);
                view.setDurationMs(value.durationMs());
                view.setResultSummary(value.summary);
                view.setErrorCategory(value.errorCategory);
                return view;
            }).toList();
        }
    }

    private static final class MutableTraceStage {
        private LocalDateTime startedAt;
        private LocalDateTime completedAt;
        private String status;
        private String summary;
        private String errorCategory;

        private void start() {
            if (startedAt == null) {
                startedAt = LocalDateTime.now();
            }
        }

        private void finish(String status, String summary, String errorCategory) {
            start();
            this.completedAt = LocalDateTime.now();
            this.status = status;
            this.summary = TextUtil.limit(summary, 240);
            this.errorCategory = errorCategory;
        }

        private Long durationMs() {
            if (startedAt == null || completedAt == null) {
                return null;
            }
            return Math.max(0, Duration.between(startedAt, completedAt).toMillis());
        }
    }

    private boolean hasPublishedArticleChunks() {
        try {
            return knowledgeArticleChunkMapper.countActivePublishedChunks() > 0;
        } catch (Exception exception) {
            log.warn("Could not check published knowledge chunk availability", exception);
            return false;
        }
    }

    private String combinedArticleText(List<KnowledgeArticle> articles) {
        return articles.stream()
                .map(this::combinedArticleText)
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    private String combinedDocumentText(List<DocumentSearchResultVO> documents) {
        return documents.stream()
                .map(DocumentSearchResultVO::getContent)
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    private String combinedAnswerSourceText(List<AiAnswerSourceVO> sources) {
        return sources.stream()
                .map(AiAnswerSourceVO::getPreviewContent)
                .filter(content -> content != null && !content.isBlank())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    private List<DocumentSearchResultVO> findUploadedDocumentAnswer(String question, KnowledgeDocumentVO document) {
        AiQueryPlan contract = queryUnderstandingService.analyze(question);
        List<String> terms = contract.retrievalTerms();
        List<String> specificTerms = queryUnderstandingService.extractSpecificTerms(terms);
        List<String> versionTerms = queryUnderstandingService.extractVersionTerms(terms);
        List<String> coreConcepts = contract.requiredConcepts();

        Long currentUserId = CurrentUserUtil.requireCurrentUserId();
        boolean admin = CurrentUserUtil.isAdmin();
        List<DocumentSearchResultVO> preciseResults = documentChunkMapper.searchChunksByDocumentId(
                        document.getId(), question, currentUserId, admin
                )
                .stream()
                .filter(result -> isReliableInternalSource(result.getContent(), terms, specificTerms, versionTerms, coreConcepts)
                        || hasSufficientCoverage(result.getContent(), contract))
                .toList();
        if (!preciseResults.isEmpty()) {
            return preciseResults;
        }

        if (isDocumentOverviewQuestion(question)) {
            return documentChunkMapper.findByDocumentId(document.getId())
                    .stream()
                    .limit(3)
                    .map(chunk -> convertUploadedChunkToSearchResult(document, chunk))
                    .toList();
        }

        return List.of();
    }

    private boolean isDocumentOverviewQuestion(String question) {
        String normalized = TextUtil.safeLower(question);
        return normalized.contains("summarize")
                || normalized.contains("summary")
                || normalized.contains("what is this document")
                || normalized.contains("what does this document")
                || normalized.contains("key points")
                || normalized.contains("main points")
                || normalized.contains("核心")
                || normalized.contains("总结")
                || normalized.contains("概括")
                || normalized.contains("这份文件")
                || normalized.contains("这个文件");
    }

    private DocumentSearchResultVO convertUploadedChunkToSearchResult(KnowledgeDocumentVO document, DocumentChunk chunk) {
        DocumentSearchResultVO result = new DocumentSearchResultVO();
        result.setChunkId(chunk.getId());
        result.setDocumentId(document.getId());
        result.setOriginalFilename(document.getOriginalFilename());
        result.setCategory(document.getCategory());
        result.setChunkIndex(chunk.getChunkIndex());
        result.setContent(chunk.getContent());
        result.setRelevanceScore(1.0);
        return result;
    }

    private void saveQuestionLog(String question, AiAnswerVO answerVO) {
        Long currentUserId = CurrentUserUtil.requireCurrentUserId();
        markResponsePersistenceSucceeded(answerVO.getRetrievalTrace());

        AiQuestionLog aiQuestionLog = new AiQuestionLog();
        aiQuestionLog.setUserId(currentUserId);
        aiQuestionLog.setQuestion(question);
        aiQuestionLog.setAnswer(answerVO.getAnswer());
        aiQuestionLog.setSourceType(answerVO.getSourceType());
        aiQuestionLog.setSourceDocumentId(answerVO.getSourceDocumentId());
        aiQuestionLog.setSourceDocumentName(answerVO.getSourceDocumentName());
        aiQuestionLog.setSourceChunkId(answerVO.getSourceChunkId());
        aiQuestionLog.setSourceChunkIndex(answerVO.getSourceChunkIndex());
        aiQuestionLog.setSourceArticleId(answerVO.getSourceArticleId());
        aiQuestionLog.setSourceTitle(resolveQuestionLogSourceTitle(answerVO));
        aiQuestionLog.setAnswerMode(answerVO.getAnswerMode());
        aiQuestionLog.setAnswerEngine(answerVO.getAnswerEngine());
        copyRetrievalTrace(answerVO.getRetrievalTrace(), aiQuestionLog);
        aiQuestionLog.setTotalLatencyMs(answerVO.getRetrievalTrace() == null ? null : answerVO.getRetrievalTrace().getTotalLatencyMs());
        aiQuestionLog.setRetrievalTraceJson(retrievalTraceCodec.encode(answerVO.getRetrievalTrace()));
        copyLlmResult(answerVO, aiQuestionLog);
        aiQuestionLog.setSuggestCreateTicket(answerVO.getSuggestCreateTicket());
        aiQuestionLog.setCreatedTicketId(answerVO.getCreatedTicketId());

        aiQuestionLogMapper.insert(aiQuestionLog);
    }

    private void markResponsePersistenceSucceeded(AiRetrievalTraceVO trace) {
        if (trace == null || trace.getStages() == null) {
            return;
        }
        trace.getStages().stream()
                .filter(stage -> "RESPONSE_PERSISTENCE".equals(stage.getStage()))
                .findFirst()
                .ifPresent(stage -> {
                    LocalDateTime now = LocalDateTime.now();
                    stage.setStatus("SUCCEEDED");
                    stage.setStartedAt(now);
                    stage.setCompletedAt(now);
                    stage.setDurationMs(0L);
                    stage.setResultSummary("Question log persisted");
                    stage.setErrorCategory(null);
                });
    }

    private void copyRetrievalTrace(AiRetrievalTraceVO trace, AiQuestionLog log) {
        if (trace == null) {
            return;
        }
        log.setInternalAttempted(trace.isInternalAttempted());
        log.setInternalSufficient(trace.isInternalSufficient());
        log.setWebAttempted(trace.isWebAttempted());
        log.setWebSucceeded(trace.isWebSucceeded());
        log.setWebSufficient(trace.isWebSufficient());
        log.setLlmAttempted(trace.isLlmAttempted());
        log.setLlmSucceeded(trace.isLlmSucceeded());
        log.setRetrievalFailureReason(trace.getFailureReason());
    }

    private void copyLlmResult(AiAnswerVO answer, AiQuestionLog log) {
        log.setLlmProvider(answer.getLlmProvider());
        log.setLlmModel(answer.getLlmModel());
        log.setLlmInputTokens(answer.getLlmInputTokens());
        log.setLlmOutputTokens(answer.getLlmOutputTokens());
        log.setLlmTotalTokens(answer.getLlmTotalTokens());
        log.setLlmEstimatedCost(answer.getLlmEstimatedCost());
        log.setLlmCurrency(answer.getLlmCurrency());
        log.setLlmUsageEstimated(answer.getLlmUsageEstimated());
        log.setLlmRetryCount(answer.getLlmRetryCount());
        log.setLlmLatencyMs(answer.getLlmLatencyMs());
        log.setLlmRequestStatus(answer.getLlmRequestStatus());
        log.setLlmErrorCategory(answer.getLlmErrorCategory());
    }

    private String resolveQuestionLogSourceTitle(AiAnswerVO answerVO) {
        if ("WEB".equals(answerVO.getSourceType()) && answerVO.getSourceDomain() != null) {
            return answerVO.getSourceTitle() + " (" + answerVO.getSourceDomain() + ")";
        }
        return answerVO.getSourceTitle();
    }
   @Override
    public List<AiQuestionLogVO> listMyQuestionLogs() {
        Long currentUserId = CurrentUserUtil.requireCurrentUserId();

        // Users can review their own AI question history.
        return aiQuestionLogMapper.findByUserId(currentUserId)
                .stream()
                .map(this::convertToAiQuestionLogVO)
                .toList();
    }
    @Override
    public AiDashboardStatsVO getDashboardStats() {
        CurrentUserUtil.requireAdmin("Only administrators can access AI dashboard stats");

        AiDashboardStatsVO statsVO = new AiDashboardStatsVO();
        statsVO.setTotalQuestions(aiQuestionLogMapper.countAll());
        statsVO.setAnsweredByKnowledgeBase(aiQuestionLogMapper.countAnsweredByInternalKnowledge());
        statsVO.setAnsweredByDocument(aiQuestionLogMapper.countAnsweredByDocument());
        statsVO.setAnsweredByArticle(aiQuestionLogMapper.countAnsweredByArticle());
        statsVO.setUnansweredQuestions(aiQuestionLogMapper.countUnansweredQuestions());
        statsVO.setCreatedTickets(aiQuestionLogMapper.countCreatedTickets());
        statsVO.setUsefulFeedbackCount(aiQuestionLogMapper.countUsefulFeedback());
        statsVO.setNotUsefulFeedbackCount(aiQuestionLogMapper.countNotUsefulFeedback());
        statsVO.setPendingFeedbackCount(aiQuestionLogMapper.countPendingFeedback());

        return statsVO;
    }
    @Override
    public PageVO<KnowledgeGapVO> listKnowledgeGaps(String keyword, Integer page, Integer size) {
        CurrentUserUtil.requireAdmin("Only administrators can access knowledge gaps");

        int safePage = PageRequestUtil.page(page);
        int safeSize = PageRequestUtil.size(size);
        List<KnowledgeGapVO> items = aiQuestionLogMapper.findKnowledgeGapsPage(
                        PageRequestUtil.optionalText(keyword),
                        PageRequestUtil.offset(safePage, safeSize),
                        safeSize
                )
                .stream()
                .map(this::convertToKnowledgeGapVO)
                .toList();
        long total = aiQuestionLogMapper.countKnowledgeGapsPage(PageRequestUtil.optionalText(keyword));
        return PageVO.of(items, safePage, safeSize, total);
    }
    @Override
    public KnowledgeArticleVO createArticleFromKnowledgeGap(Long logId, KnowledgeArticleCreateDTO createDTO) {
        CurrentUserUtil.requireAdmin("Only administrators can create articles from knowledge gaps");
        Long currentUserId = CurrentUserUtil.requireCurrentUserId();

        AiQuestionLog aiQuestionLog = aiQuestionLogMapper.findById(logId);
        if (aiQuestionLog == null || aiQuestionLog.getSourceArticleId() != null) {
            throw new BusinessException("Knowledge gap not found");
        }
        if (isLowQualityKnowledgeContent(createDTO.getContent())) {
            throw new BusinessException("Article content is still a placeholder or failed answer. Please replace it with a reviewed, useful solution before publishing.");
        }

        KnowledgeArticle article = knowledgePackReorganizer.reorganizeOrCreate(
                createDTO.getTitle(),
                createDTO.getCategory(),
                createDTO.getContent(),
                currentUserId,
                "Reviewed knowledge gap answer"
        );
        knowledgeArticleChunkManager.rebuildArticleChunks(article);
        aiQuestionLogMapper.linkKnowledgeArticle(logId, article.getId(), article.getTitle());

        return KnowledgeArticleVoMapper.toVO(article);
    }

    @Override
    public void deleteKnowledgeGap(Long logId) {
        CurrentUserUtil.requireAdmin("Only administrators can delete knowledge gaps");

        AiQuestionLog aiQuestionLog = aiQuestionLogMapper.findById(logId);
        if (aiQuestionLog == null || !"NONE".equals(aiQuestionLog.getSourceType())) {
            throw new BusinessException("Knowledge gap not found");
        }
        aiQuestionLogMapper.deleteById(logId);
    }

    private KnowledgeGapVO convertToKnowledgeGapVO(AiQuestionLog aiQuestionLog) {
        KnowledgeGapVO vo = new KnowledgeGapVO();
        vo.setLogId(aiQuestionLog.getId());
        vo.setUserId(aiQuestionLog.getUserId());
        vo.setQuestion(aiQuestionLog.getQuestion());
        vo.setAnswer(aiQuestionLog.getAnswer());
        vo.setSourceType(aiQuestionLog.getSourceType());
        vo.setCreatedTicketId(aiQuestionLog.getCreatedTicketId());
        vo.setFeedback(aiQuestionLog.getFeedback());
        vo.setFeedbackComment(aiQuestionLog.getFeedbackComment());
        vo.setCreatedTime(aiQuestionLog.getCreatedTime());
        return vo;
    }
    @Override
    public PageVO<AiQuestionLogVO> listAllQuestionLogs(String keyword, String sourceType, String feedback, Integer page, Integer size) {
        CurrentUserUtil.requireAdmin("Only administrators can access AI question logs");

        int safePage = PageRequestUtil.page(page);
        int safeSize = PageRequestUtil.size(size);
        List<AiQuestionLogVO> items = aiQuestionLogMapper.findPage(
                        PageRequestUtil.optionalText(keyword),
                        PageRequestUtil.optionalText(sourceType),
                        PageRequestUtil.optionalText(feedback),
                        PageRequestUtil.offset(safePage, safeSize),
                        safeSize
                )
                .stream()
                .map(this::convertToAiQuestionLogVO)
                .toList();
        long total = aiQuestionLogMapper.countPage(
                PageRequestUtil.optionalText(keyword),
                PageRequestUtil.optionalText(sourceType),
                PageRequestUtil.optionalText(feedback)
        );
        return PageVO.of(items, safePage, safeSize, total);
    }

    @Override
    public void deleteQuestionLog(Long id) {
        CurrentUserUtil.requireAdmin("Only administrators can delete AI question logs");

        AiQuestionLog aiQuestionLog = aiQuestionLogMapper.findById(id);
        if (aiQuestionLog == null) {
            throw new BusinessException("AI question log not found");
        }

        aiQuestionLogMapper.deleteById(id);
    }

    @Override
    public void submitQuestionLogFeedback(Long id, AiQuestionLogFeedbackDTO feedbackDTO) {
        Long currentUserId = CurrentUserUtil.requireCurrentUserId();

        AiQuestionLog aiQuestionLog = aiQuestionLogMapper.findById(id);
        if (aiQuestionLog == null || !currentUserId.equals(aiQuestionLog.getUserId())) {
            throw new BusinessException("AI question log not found");
        }

        // Users can mark whether an AI answer was useful for future quality review.
        aiQuestionLogMapper.updateFeedback(
                id,
                feedbackDTO.getFeedback(),
                feedbackDTO.getFeedbackComment()
        );
    }
    private AiQuestionLogVO convertToAiQuestionLogVO(AiQuestionLog aiQuestionLog) {
        AiQuestionLogVO vo = new AiQuestionLogVO();
        vo.setId(aiQuestionLog.getId());
        vo.setUserId(aiQuestionLog.getUserId());
        vo.setQuestion(aiQuestionLog.getQuestion());
        vo.setAnswer(aiQuestionLog.getAnswer());
        vo.setSourceType(aiQuestionLog.getSourceType());
        vo.setSourceDocumentId(aiQuestionLog.getSourceDocumentId());
        vo.setSourceDocumentName(aiQuestionLog.getSourceDocumentName());
        vo.setSourceChunkId(aiQuestionLog.getSourceChunkId());
        vo.setSourceChunkIndex(aiQuestionLog.getSourceChunkIndex());
        vo.setSourceArticleId(aiQuestionLog.getSourceArticleId());
        vo.setSourceTitle(aiQuestionLog.getSourceTitle());
        vo.setSuggestCreateTicket(aiQuestionLog.getSuggestCreateTicket());
        vo.setCreatedTicketId(aiQuestionLog.getCreatedTicketId());
        vo.setFeedback(aiQuestionLog.getFeedback());
        vo.setFeedbackComment(aiQuestionLog.getFeedbackComment());
        vo.setFeedbackTime(aiQuestionLog.getFeedbackTime());
        vo.setCreatedTime(aiQuestionLog.getCreatedTime());
        vo.setAnswerMode(aiQuestionLog.getAnswerMode());
        vo.setAnswerEngine(aiQuestionLog.getAnswerEngine());
        vo.setLlmProvider(aiQuestionLog.getLlmProvider());
        vo.setLlmModel(aiQuestionLog.getLlmModel());
        vo.setLlmInputTokens(aiQuestionLog.getLlmInputTokens());
        vo.setLlmOutputTokens(aiQuestionLog.getLlmOutputTokens());
        vo.setLlmTotalTokens(aiQuestionLog.getLlmTotalTokens());
        vo.setLlmEstimatedCost(aiQuestionLog.getLlmEstimatedCost());
        vo.setLlmCurrency(aiQuestionLog.getLlmCurrency());
        vo.setLlmUsageEstimated(aiQuestionLog.getLlmUsageEstimated());
        vo.setLlmRetryCount(aiQuestionLog.getLlmRetryCount());
        vo.setLlmLatencyMs(aiQuestionLog.getLlmLatencyMs());
        vo.setLlmRequestStatus(aiQuestionLog.getLlmRequestStatus());
        vo.setLlmErrorCategory(aiQuestionLog.getLlmErrorCategory());
        vo.setRetrievalTrace(toRetrievalTrace(aiQuestionLog));
        return vo;
    }

    private AiRetrievalTraceVO toRetrievalTrace(AiQuestionLog log) {
        AiRetrievalTraceVO storedTrace = retrievalTraceCodec.decode(log.getRetrievalTraceJson());
        if (storedTrace != null) {
            return storedTrace;
        }
        AiRetrievalTraceVO trace = new AiRetrievalTraceVO();
        trace.setInternalAttempted(Boolean.TRUE.equals(log.getInternalAttempted()));
        trace.setInternalSufficient(Boolean.TRUE.equals(log.getInternalSufficient()));
        trace.setWebAttempted(Boolean.TRUE.equals(log.getWebAttempted()));
        trace.setWebSucceeded(Boolean.TRUE.equals(log.getWebSucceeded()));
        trace.setWebSufficient(Boolean.TRUE.equals(log.getWebSufficient()));
        trace.setLlmAttempted(Boolean.TRUE.equals(log.getLlmAttempted()));
        trace.setLlmSucceeded(Boolean.TRUE.equals(log.getLlmSucceeded()));
        trace.setFinalMode(log.getAnswerMode());
        trace.setFailureReason(log.getRetrievalFailureReason());
        trace.setTotalLatencyMs(log.getTotalLatencyMs());
        trace.setStages(List.of());
        return trace;
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

    private AiAnswerVO buildKnowledgeBasedAnswer(String question, AiQueryPlan contract, List<KnowledgeArticle> articles) {
        KnowledgeArticle article = articles.get(0);
        List<AiAnswerSourceVO> sources = buildArticleSources(articles);

        AiAnswerVO answerVO = new AiAnswerVO();
        AnswerComposition composition = buildEnterpriseAnswer(question, contract, "ARTICLE", sources, article.getContent());
        answerVO.setAnswer(composition.text());
        answerVO.setAnswerEngine(composition.engine());
        copyLlmResult(composition.llmResult(), answerVO);
        answerVO.setAnswerMode("INTERNAL_RAG");
        answerVO.setSourceType("ARTICLE");
        answerVO.setSourceScore(null);
        answerVO.setSourceArticleId(article.getId());
        answerVO.setSourceTitle(article.getTitle());
        answerVO.setSourceDocumentId(null);
        answerVO.setSourceDocumentName(null);
        answerVO.setSourceChunkId(null);
        answerVO.setSourceChunkIndex(null);
        answerVO.setSuggestCreateTicket(false);
        answerVO.setCreatedTicketId(null);
        answerVO.setSources(sources);
        return answerVO;
    }

    private AiAnswerVO buildArticleChunkBasedAnswer(String question,
                                                    AiQueryPlan contract,
                                                    List<KnowledgeArticleChunkSearchResultVO> chunks) {
        KnowledgeArticleChunkSearchResultVO topChunk = chunks.get(0);
        List<AiAnswerSourceVO> sources = buildArticleChunkSources(chunks);

        AiAnswerVO answerVO = new AiAnswerVO();
        AnswerComposition composition = buildEnterpriseAnswer(question, contract, "ARTICLE", sources, topChunk.getContent());
        answerVO.setAnswer(composition.text());
        answerVO.setAnswerEngine(composition.engine());
        copyLlmResult(composition.llmResult(), answerVO);
        answerVO.setAnswerMode("INTERNAL_RAG");
        answerVO.setSourceType("ARTICLE");
        answerVO.setSourceScore(topChunk.getRelevanceScore());
        answerVO.setSourceArticleId(topChunk.getArticleId());
        answerVO.setSourceTitle(topChunk.getArticleTitle());
        answerVO.setSourceDocumentId(null);
        answerVO.setSourceDocumentName(null);
        answerVO.setSourceChunkId(topChunk.getChunkId());
        answerVO.setSourceChunkIndex(topChunk.getChunkIndex());
        answerVO.setSuggestCreateTicket(false);
        answerVO.setCreatedTicketId(null);
        answerVO.setSources(sources);
        return answerVO;
    }

    private AiAnswerVO buildWebEvidenceBasedAnswer(String question,
                                                   AiQueryPlan contract,
                                                   WebEvidence evidence,
                                                   List<AiAnswerSourceVO> internalSources) {
        List<AiAnswerSourceVO> sources = new ArrayList<>(internalSources);
        sources.addAll(buildWebEvidenceSources(evidence));

        AiAnswerVO answerVO = new AiAnswerVO();
        AnswerComposition composition = buildEnterpriseAnswer(question, contract, "WEB", sources, evidence.getExtractedContent());
        answerVO.setAnswer(composition.text());
        answerVO.setAnswerEngine(composition.engine());
        copyLlmResult(composition.llmResult(), answerVO);
        answerVO.setAnswerMode("LLM".equals(composition.engine()) ? "AI_ASSISTED_WEB" : "WEB_SEARCH");
        answerVO.setSourceType("WEB");
        answerVO.setSourceScore(evidence.getCredibilityScore() == null ? null : evidence.getCredibilityScore().doubleValue());
        answerVO.setSourceArticleId(null);
        answerVO.setSourceTitle(evidence.getTitle());
        answerVO.setSourceDocumentId(null);
        answerVO.setSourceDocumentName(null);
        answerVO.setSourceChunkId(null);
        answerVO.setSourceChunkIndex(null);
        answerVO.setSourceWebEvidenceId(evidence.getId());
        answerVO.setSourceUrl(evidence.getUrl());
        answerVO.setSourceDomain(evidence.getDomain());
        answerVO.setSourceStatus(evidence.getStatus());
        answerVO.setSuggestCreateTicket(false);
        answerVO.setCreatedTicketId(null);
        answerVO.setSources(sources);
        return answerVO;
    }

    private AiAnswerVO buildModelGeneratedAnswer(WebEvidence evidence, String answer) {
        AiAnswerVO answerVO = new AiAnswerVO();
        answerVO.setAnswer(answer);
        answerVO.setAnswerEngine("LLM");
        answerVO.setAnswerMode("AI_ONLY");
        answerVO.setSourceType("LLM");
        answerVO.setSourceScore(evidence.getCredibilityScore() == null ? null : evidence.getCredibilityScore().doubleValue());
        answerVO.setSourceArticleId(null);
        answerVO.setSourceTitle(evidence.getTitle());
        answerVO.setSourceDocumentId(null);
        answerVO.setSourceDocumentName(null);
        answerVO.setSourceChunkId(null);
        answerVO.setSourceChunkIndex(null);
        answerVO.setSourceWebEvidenceId(evidence.getId());
        answerVO.setSourceUrl(null);
        answerVO.setSourceDomain(evidence.getDomain());
        answerVO.setSourceStatus(evidence.getStatus());
        answerVO.setSuggestCreateTicket(false);
        answerVO.setCreatedTicketId(null);
        answerVO.setSources(buildWebEvidenceSources(evidence));
        return answerVO;
    }

    private AiAnswerVO buildDocumentBasedAnswer(String question, AiQueryPlan contract, List<DocumentSearchResultVO> documentResults) {
        DocumentSearchResultVO documentResult = documentResults.get(0);
        List<AiAnswerSourceVO> sources = buildDocumentSources(documentResults);

        AiAnswerVO answerVO = new AiAnswerVO();
        AnswerComposition composition = buildEnterpriseAnswer(question, contract, "DOCUMENT", sources, documentResult.getContent());
        answerVO.setAnswer(composition.text());
        answerVO.setAnswerEngine(composition.engine());
        copyLlmResult(composition.llmResult(), answerVO);
        answerVO.setAnswerMode("INTERNAL_RAG");
        answerVO.setSourceType("DOCUMENT");
        answerVO.setSourceScore(documentResult.getRelevanceScore());
        answerVO.setSourceArticleId(null);
        answerVO.setSourceTitle(null);
        answerVO.setSourceDocumentId(documentResult.getDocumentId());
        answerVO.setSourceDocumentName(documentResult.getOriginalFilename());
        answerVO.setSourceChunkId(documentResult.getChunkId());
        answerVO.setSourceChunkIndex(documentResult.getChunkIndex());
        answerVO.setSuggestCreateTicket(false);
        answerVO.setCreatedTicketId(null);
        answerVO.setSources(sources);
        return answerVO;
    }

    private List<KnowledgeArticleChunkSearchResultVO> findRelevantArticleChunks(String question,
                                                                                AiQueryPlan contract,
                                                                                List<String> terms,
                                                                                List<String> specificTerms,
                                                                                List<String> versionTerms,
                                                                                List<String> coreConcepts) {
        if (question == null || question.isBlank() || terms.isEmpty()) {
            return List.of();
        }

        String searchExpression = terms.stream()
                .map(term -> term.replace("\"", ""))
                .filter(term -> !term.isBlank())
                .distinct()
                .reduce((left, right) -> left + " OR " + right)
                .orElse(question);
        try {
            List<KnowledgeArticleChunkSearchResultVO> candidates = new ArrayList<>();
            if (contract.domainCandidates().isEmpty()) {
                candidates.addAll(knowledgeArticleChunkMapper.searchChunks(searchExpression, null, 24));
            } else {
                for (String category : contract.domainCandidates()) {
                    candidates.addAll(knowledgeArticleChunkMapper.searchChunks(searchExpression, category, 16));
                }
            }

            Map<Long, KnowledgeArticleChunkSearchResultVO> deduplicated = new LinkedHashMap<>();
            candidates.stream()
                    .filter(result -> result != null && result.getChunkId() != null)
                    .filter(result -> contract.domainCandidates().isEmpty()
                            || contract.domainCandidates().stream().anyMatch(domain -> domain.equalsIgnoreCase(result.getCategory())))
                    .filter(result -> !isLowQualityKnowledgeContent(result.getContent()))
                    .filter(result -> hasMeaningfulConceptOverlap(result, contract))
                    .forEach(result -> deduplicated.putIfAbsent(result.getChunkId(), result));

            Map<Long, List<KnowledgeArticleChunkSearchResultVO>> byArticle = deduplicated.values().stream()
                    .collect(java.util.stream.Collectors.groupingBy(
                            KnowledgeArticleChunkSearchResultVO::getArticleId,
                            LinkedHashMap::new,
                            java.util.stream.Collectors.toList()
                    ));
            if (byArticle.isEmpty()) {
                return List.of();
            }
            return knowledgeChunkRanker.select(byArticle, contract);
        } catch (Exception exception) {
            log.warn("Internal article chunk retrieval failed for question: {}", TextUtil.limit(question, 160), exception);
            return List.of();
        }
    }

    private boolean hasMeaningfulConceptOverlap(KnowledgeArticleChunkSearchResultVO result, AiQueryPlan contract) {
        String source = result.getSectionTitle() + " " + result.getConceptTags() + " " + result.getContent();
        return coverageEvaluator.contributesEvidence(source, result.getCategory(), contract);
    }

    private List<KnowledgeArticle> findRelevantArticles(String question,
                                                        AiQueryPlan contract,
                                                        List<String> terms,
                                                        List<String> specificTerms,
                                                        List<String> versionTerms,
                                                        List<String> coreConcepts) {
        if (terms.isEmpty()) {
            return List.of();
        }

        List<ArticleMatch> matches = knowledgeArticleMapper.findPublishedArticles().stream()
                .filter(article -> !isLowQualityKnowledgeContent(article.getContent()))
                .filter(article -> contract.domainCandidates().isEmpty()
                        || contract.domainCandidates().stream().anyMatch(domain -> domain.equalsIgnoreCase(article.getCategory())))
                .map(article -> new ArticleMatch(article, scoreArticle(article, terms)))
                .filter(match -> match.score() >= 6)
                .filter(match -> contract.domainCandidates().size() > 1
                        ? hasMeaningfulConceptOverlap(combinedArticleText(match.article()), match.article().getCategory(), contract)
                        : hasSufficientCoverage(combinedArticleText(match.article()), contract))
                .sorted(Comparator.comparingInt(ArticleMatch::score).reversed())
                .toList();
        if (contract.domainCandidates().size() <= 1) {
            return matches.stream().limit(5).map(ArticleMatch::article).toList();
        }

        LinkedHashMap<Long, KnowledgeArticle> selected = new LinkedHashMap<>();
        for (String domain : contract.domainCandidates()) {
            matches.stream()
                    .filter(match -> domain.equalsIgnoreCase(match.article().getCategory()))
                    .findFirst()
                    .ifPresent(match -> selected.putIfAbsent(match.article().getId(), match.article()));
        }
        matches.stream()
                .map(ArticleMatch::article)
                .forEach(article -> selected.putIfAbsent(article.getId(), article));
        return selected.values().stream().limit(6).toList();
    }

    private boolean hasMeaningfulConceptOverlap(String source, String sourceDomain, AiQueryPlan contract) {
        return coverageEvaluator.contributesEvidence(source, sourceDomain, contract);
    }

    private int scoreArticle(KnowledgeArticle article, List<String> terms) {
        String title = TextUtil.safeLower(article.getTitle());
        String category = TextUtil.safeLower(article.getCategory());
        String content = TextUtil.safeLower(article.getContent());
        String inferredCategory = TextUtil.safeLower(KnowledgeCategoryResolver.infer(String.join(" ", terms)));
        int score = 0;

        for (String term : terms) {
            if (title.contains(term)) {
                score += 5;
            }
            if (category.contains(term)) {
                score += 3;
            }
            if (content.contains(term)) {
                score += 1;
            }
        }
        if (!inferredCategory.isBlank()
                && (category.contains(inferredCategory) || inferredCategory.contains(category))) {
            score += 6;
        }

        return score;
    }

    private boolean isReliableInternalSource(String sourceText,
                                             List<String> terms,
                                             List<String> specificTerms,
                                             List<String> versionTerms,
                                             List<String> coreConcepts) {
        if (terms.isEmpty()) {
            return false;
        }

        String normalizedSource = TextUtil.safeLower(sourceText);
        long matchedTerms = terms.stream()
                .filter(normalizedSource::contains)
                .count();
        double coverage = (double) matchedTerms / (double) terms.size();

        boolean enoughCoverage = terms.size() <= 3
                ? matchedTerms >= Math.min(2, terms.size())
                : coverage >= 0.35 || matchedTerms >= 4;
        boolean versionsCovered = versionTerms.stream().allMatch(normalizedSource::contains);
        boolean specificCovered = specificTerms.isEmpty()
                || specificTerms.stream().anyMatch(term -> coverageEvaluator.matchesConcept(normalizedSource, term));
        boolean coreCovered = coversCoreConcepts(normalizedSource, coreConcepts);

        return enoughCoverage && versionsCovered && specificCovered && coreCovered;
    }

    private boolean coversCoreConcepts(String normalizedSource, List<String> coreConcepts) {
        if (coreConcepts == null || coreConcepts.isEmpty()) {
            return true;
        }

        long matchedConcepts = coreConcepts.stream()
                .filter(concept -> coverageEvaluator.matchesConcept(normalizedSource, concept))
                .count();
        double coverage = (double) matchedConcepts / (double) coreConcepts.size();

        return coreConcepts.size() <= 2 ? matchedConcepts == coreConcepts.size() : coverage >= 0.60;
    }

    boolean isGeneratedAnswerUsable(String answer) {
        return answer != null && !answer.isBlank() && isAnswerPresentationClean(answer);
    }

    private boolean isAnswerPresentationClean(String answer) {
        String normalized = TextUtil.safeLower(answer);
        return !normalized.contains("primary keywords")
                && !normalized.contains("source references")
                && !normalized.contains("knowledge candidate")
                && !normalized.contains("observed answer")
                && !normalized.contains("review this content before publishing")
                && !normalized.contains("uploaded document chunks remain retrieval evidence")
                && !normalized.contains("consolidated reviewed internal knowledge")
                && !normalized.contains("use this runbook")
                && !normalized.contains("use this playbook")
                && !normalized.contains("use this guide")
                && !normalized.contains("runbook runbook")
                && !normalized.contains("sop sop")
                && !normalized.contains("window.")
                && !normalized.contains("document.")
                && !normalized.contains("__next")
                && !normalized.matches("(?s).*[{};]{6,}.*");
    }

    private String combinedArticleText(KnowledgeArticle article) {
        return TextUtil.safeLower(article.getTitle()) + " "
                + TextUtil.safeLower(article.getCategory()) + " "
                + TextUtil.safeLower(article.getContent());
    }

    private boolean isLowQualityKnowledgeContent(String content) {
        String normalized = TextUtil.safeLower(content);
        return normalized.contains("i could not find a reliable answer")
                || normalized.contains("add concrete checks, commands, dashboards, or logs")
                || normalized.contains("add the final reviewed answer here before publishing")
                || normalized.contains("document a reliable internal answer for a repeated ai miss");
    }

    private record ArticleMatch(KnowledgeArticle article, int score) {
    }

    private record ScoredPoint(String point, int score) {
    }

    private record ConceptSummary(String directAnswer, String explanation, List<String> whenToUse) {
    }

    private boolean hasSufficientCoverage(String sourceText, AiQueryPlan contract) {
        return coverageEvaluator.isSufficient(sourceText, contract);
    }

    private boolean hasSufficientCoverage(List<AiAnswerSourceVO> sources, AiQueryPlan contract) {
        return coverageEvaluator.evaluateEvidence(sources, contract).sufficient();
    }

    private AnswerComposition buildEnterpriseAnswer(String question,
                                                     AiQueryPlan contract,
                                                     String sourceType,
                                                     List<AiAnswerSourceVO> sources,
                                                     String fallbackContent) {
        List<AiAnswerSourceVO> answerSources = sanitizeSourcesForAnswer(sources);
        List<AiAnswerSourceVO> llmSources = llmGatewayService.isConfigured()
                && llmUsagePolicy.canUseGroundedSynthesis(sourceType, answerSources)
                ? llmUsagePolicy.selectSourcesForGroundedSynthesis(answerSources)
                : List.of();
        LlmGenerationResult llmResult = llmSources.isEmpty()
                ? null
                : safeGenerateGroundedAnswer(question, sourceType, llmSources);
        Optional<String> modelAnswer = llmResult == null ? Optional.empty() : llmResult.answerOptional()
                .map(this::normalizeEnterpriseAnswer)
                .filter(answer -> !answer.isBlank())
                .filter(this::isAnswerPresentationClean)
                .filter(answer -> groundedAnswerGuard.containsOnlyVerifiedUrls(
                        answer,
                        answerSources.stream().map(AiAnswerSourceVO::getSourceUrl).toList()))
                .map(answer -> groundedAnswerGuard.replaceReferencesWithVerifiedSources(
                        answer,
                        formatReferences(answerSources)));
        if (modelAnswer.isPresent()) {
            return new AnswerComposition(modelAnswer.get(), "LLM", llmResult);
        }

        String answerContext = buildAnswerContext(answerSources, fallbackContent);
        return new AnswerComposition(buildTemplateAnswer(contract, answerContext, sources), "DETERMINISTIC", llmResult);
    }

    private record AnswerComposition(String text, String engine, LlmGenerationResult llmResult) {
    }

    private String buildAnswerContext(List<AiAnswerSourceVO> sources, String fallbackContent) {
        LinkedHashSet<String> context = new LinkedHashSet<>();
        if (fallbackContent != null && !fallbackContent.isBlank()) {
            context.add(cleanAnswerContent(fallbackContent));
        }
        if (sources != null) {
            sources.stream()
                    .map(AiAnswerSourceVO::getPreviewContent)
                    .filter(preview -> preview != null && !preview.isBlank())
                    .map(this::cleanAnswerContent)
                    .filter(preview -> !preview.isBlank())
                    .forEach(context::add);
        }
        return String.join("\n\n", context);
    }

    private List<AiAnswerSourceVO> sanitizeSourcesForAnswer(List<AiAnswerSourceVO> sources) {
        if (sources == null || sources.isEmpty()) {
            return List.of();
        }
        List<AiAnswerSourceVO> sanitized = new ArrayList<>();
        for (AiAnswerSourceVO source : sources) {
            AiAnswerSourceVO copy = new AiAnswerSourceVO();
            copy.setSourceType(source.getSourceType());
            copy.setSourceScore(source.getSourceScore());
            copy.setSourceArticleId(source.getSourceArticleId());
            copy.setSourceTitle(source.getSourceTitle());
            copy.setSourceDocumentId(source.getSourceDocumentId());
            copy.setSourceDocumentName(source.getSourceDocumentName());
            copy.setSourceChunkId(source.getSourceChunkId());
            copy.setSourceChunkIndex(source.getSourceChunkIndex());
            copy.setSourceWebEvidenceId(source.getSourceWebEvidenceId());
            copy.setSourceUrl(source.getSourceUrl());
            copy.setSourceDomain(source.getSourceDomain());
            copy.setSourceStatus(source.getSourceStatus());
            copy.setPreviewContent(cleanAnswerContent(source.getPreviewContent()));
            sanitized.add(copy);
        }
        return sanitized;
    }

    private LlmGenerationResult safeGenerateGroundedAnswer(String question, String sourceType, List<AiAnswerSourceVO> sources) {
        try {
            LlmGenerationResult result = llmGatewayService.generateGroundedAnswerResult(question, sourceType, sources);
            return result == null
                    ? LlmGenerationResult.skipped("OpenAI", null, com.zwf.aisupport.service.model.LlmErrorCategory.LLM_NO_USABLE_RESPONSE, "No LLM result")
                    : result;
        } catch (Exception exception) {
            log.warn("Grounded LLM synthesis failed: {}", exception.getClass().getSimpleName());
            return LlmGenerationResult.skipped("OpenAI", null, com.zwf.aisupport.service.model.LlmErrorCategory.LLM_PROVIDER_ERROR, "LLM invocation failed");
        }
    }

    private LlmGenerationResult safeGenerateDirectAnswer(String question) {
        try {
            LlmGenerationResult result = llmGatewayService.generateDirectAnswerResult(question);
            return result == null
                    ? LlmGenerationResult.skipped("OpenAI", null, com.zwf.aisupport.service.model.LlmErrorCategory.LLM_NO_USABLE_RESPONSE, "No LLM result")
                    : result;
        } catch (Exception exception) {
            log.warn("Direct LLM fallback failed: {}", exception.getClass().getSimpleName());
            return LlmGenerationResult.skipped("OpenAI", null, com.zwf.aisupport.service.model.LlmErrorCategory.LLM_PROVIDER_ERROR, "LLM invocation failed");
        }
    }

    private void copyLlmResult(LlmGenerationResult result, AiAnswerVO answer) {
        if (result == null) {
            return;
        }
        answer.setLlmProvider(result.provider());
        answer.setLlmModel(result.model());
        answer.setLlmInputTokens(result.inputTokens());
        answer.setLlmOutputTokens(result.outputTokens());
        answer.setLlmTotalTokens(result.totalTokens());
        answer.setLlmEstimatedCost(result.estimatedCost());
        answer.setLlmCurrency(result.currency());
        answer.setLlmUsageEstimated(result.usageEstimated());
        answer.setLlmRetryCount(result.retryCount());
        answer.setLlmLatencyMs(result.latencyMs());
        answer.setLlmRequestStatus(result.requestStatus());
        answer.setLlmErrorCategory(result.errorCategory() == null ? null : result.errorCategory().name());
    }

    private String normalizeEnterpriseAnswer(String answer) {
        if (answer == null || answer.isBlank()) {
            return "";
        }
        String cleaned = answer.trim();
        if (TextUtil.safeLower(cleaned).contains("direct answer")) {
            return cleaned;
        }
        return "## Direct Answer\n" + cleaned;
    }

    String buildTemplateAnswer(AiQueryPlan contract, String content, List<AiAnswerSourceVO> sources) {
        List<String> points = extractQuestionFocusedPoints(contract, content);
        if (points.isEmpty()) {
            points = extractGroundedFallbackPoints(contract, content);
        }
        if (points.isEmpty()) {
            return "";
        }
        AiAnswerIntent intent = contract.intent();
        List<String> references = formatReferences(sources);

        if (intent == AiAnswerIntent.DECISION) {
            return buildDecisionAnswer(contract, points, references);
        }
        if (intent == AiAnswerIntent.CONCEPT) {
            return buildConceptAnswer(contract, content, points, references);
        }
        if (intent == AiAnswerIntent.PROCEDURE) {
            return buildProcedureAnswer(contract, points, references);
        }

        List<String> actionPoints = points.stream()
                .filter(this::isProceduralAnswerPoint)
                .map(this::rewritePointAsAction)
                .distinct()
                .limit(5)
                .toList();
        List<String> factualPoints = points.stream()
                .filter(point -> !isProceduralAnswerPoint(point))
                .map(this::rewritePointAsAnswer)
                .distinct()
                .toList();
        String direct = factualPoints.isEmpty() ? rewritePointAsAnswer(points.get(0)) : factualPoints.get(0);
        direct = ensureImportantConceptsVisible(contract, direct);
        String explanation = factualPoints.stream().skip(1).findFirst().orElse(direct);
        List<String> causes = factualPoints.stream().skip(2).limit(3).toList();
        return """
                ## Direct Answer
                %s

                ## Explanation
                %s

                ## Common Causes
                %s

                ## Recommended Actions
                %s

                ## References
                %s
                """.formatted(
                direct,
                explanation,
                 formatBullets(causes, "- No grounded cause was established."),
                 formatBullets(actionPoints, "- No grounded action was established."),
                 formatBullets(references, "- No reference was attached.")
        ).trim();
    }

    private List<String> extractGroundedFallbackPoints(AiQueryPlan contract, String content) {
        String cleaned = cleanAnswerContent(content);
        if (cleaned.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(cleaned.split("\\R+|(?<=[.!?])\\s+"))
                .map(this::normalizeAnswerLine)
                .filter(this::isUsefulAnswerPoint)
                .filter(line -> !isAnswerNoiseLine(line))
                .filter(this::isAnswerPresentationClean)
                .filter(line -> contract.requiredConcepts().stream()
                        .anyMatch(concept -> coverageEvaluator.matchesConcept(TextUtil.safeLower(line), concept)))
                .distinct()
                .sorted(Comparator.comparingInt((String line) -> scoreAnswerPoint(line, contract.retrievalTerms())).reversed())
                .limit(6)
                .toList();
    }

    private String buildDecisionAnswer(AiQueryPlan contract, List<String> points, List<String> references) {
        List<String> criteria = points.stream()
                .map(this::rewritePointAsAnswer)
                .filter(point -> !point.isBlank())
                .limit(6)
                .toList();
        String direct = rewritePointAsAnswer(criteria.get(0));
        direct = ensureImportantConceptsVisible(contract, direct);
        List<String> application = criteria.stream().skip(1).limit(5).toList();
        return """
                ## Direct Answer
                %s

                ## Explanation
                %s

                ## Common Causes
                %s

                ## Recommended Actions
                %s

                ## References
                %s
                """.formatted(
                direct,
                 formatBullets(criteria, "- No grounded decision criteria were found."),
                 "- No separate cause or trade-off was established by the grounded source.",
                 formatBullets(application, "- No grounded application guidance was found."),
                 formatBullets(references, "- No reference was attached.")
        ).trim();
    }

    private String buildConceptAnswer(AiQueryPlan contract, String content, List<String> points, List<String> references) {
        ConceptSummary summary = summarizeConcept(contract, content, points);
        return """
                ## Direct Answer
                %s

                ## Explanation
                %s

                ## Common Causes
                - The source does not identify a separate failure cause for this concept.

                ## Recommended Actions
                %s

                ## References
                %s
                """.formatted(
                summary.directAnswer(),
                summary.explanation(),
                 formatBullets(summary.whenToUse(), "- No grounded usage guidance was found."),
                 formatBullets(references, "- No reference was attached.")
        ).trim();
    }

    private String buildProcedureAnswer(AiQueryPlan contract, List<String> points, List<String> references) {
        String direct = rewritePointAsAnswer(points.get(0));
        direct = ensureImportantConceptsVisible(contract, direct);
        List<String> steps = points.stream().limit(5).map(this::rewritePointAsAction).toList();
        return """
                ## Direct Answer
                %s

                ## Explanation
                The available internal evidence describes the following sequence.

                ## Common Causes
                - The source does not establish a separate cause beyond the question context.

                ## Recommended Actions
                %s

                ## References
                %s
                """.formatted(
                direct,
                 formatBullets(steps, "- No grounded steps were found."),
                 formatBullets(references, "- No reference was attached.")
        ).trim();
    }

    private ConceptSummary summarizeConcept(AiQueryPlan contract, String content, List<String> points) {
        List<String> cleanPoints = points.stream()
                .map(this::rewritePointAsAnswer)
                .filter(point -> !point.isBlank())
                .filter(point -> !isProceduralAnswerPoint(point))
                .distinct()
                .limit(8)
                .toList();
        List<String> groundedPoints = cleanPoints.isEmpty()
                ? points.stream().map(this::rewritePointAsAnswer).filter(point -> !point.isBlank()).toList()
                : cleanPoints;
        String initialDirect = groundedPoints.get(0);
        String direct = ensureImportantConceptsVisible(contract, initialDirect);
        String explanation = groundedPoints.stream()
                .skip(1)
                .filter(point -> !sameMeaningForTemplate(point, direct))
                .findFirst()
                .orElse(direct);
        List<String> usage = groundedPoints.size() > 2
                ? groundedPoints.stream()
                .skip(2)
                .filter(point -> !sameMeaningForTemplate(point, direct))
                .filter(point -> !sameMeaningForTemplate(point, explanation))
                .limit(4)
                .toList()
                : List.of("The cited source does not provide a separate usage recommendation.");
        return new ConceptSummary(direct, explanation, usage);
    }

    private String ensureImportantConceptsVisible(AiQueryPlan contract, String sentence) {
        if (contract == null || sentence == null || sentence.isBlank()) {
            return sentence == null ? "" : sentence;
        }
        String normalizedSentence = TextUtil.safeLower(sentence);
        List<String> missing = contract.requiredConcepts().stream()
                .filter(term -> isCoreAnswerTerm(contract.originalQuestion(), term))
                .filter(term -> !coverageEvaluator.matchesConcept(normalizedSentence, term))
                .limit(3)
                .toList();
        if (missing.isEmpty()) {
            return sentence;
        }
        String prefix = missing.stream()
                .map(concept -> displayConcept(contract.originalQuestion(), concept))
                .distinct()
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
        if (prefix.isBlank()) {
            return sentence;
        }
        String cleaned = sentence.trim();
        return prefix + ": " + Character.toLowerCase(cleaned.charAt(0)) + cleaned.substring(1);
    }

    private boolean isCoreAnswerTerm(String question, String term) {
        return TECHNICAL_DOMAIN_TERMS.contains(term)
                || term.length() >= 6
                || term.matches(".*[0-9+#./_-].*");
    }

    private String displayConcept(String question, String concept) {
        if (question != null) {
            var matcher = Pattern.compile("(?<![A-Za-z0-9])" + Pattern.quote(concept) + "(?![A-Za-z0-9])", Pattern.CASE_INSENSITIVE)
                    .matcher(question);
            if (matcher.find()) {
                return matcher.group();
            }
        }
        return concept.isEmpty() ? concept : Character.toUpperCase(concept.charAt(0)) + concept.substring(1);
    }

    private boolean isProceduralAnswerPoint(String point) {
        String normalized = TextUtil.safeLower(point);
        return normalized.matches("^(check|verify|confirm|validate|inspect|restart|read)\\b.*")
                || normalized.startsWith("useful command");
    }

    private boolean sameMeaningForTemplate(String left, String right) {
        String normalizedLeft = TextUtil.safeLower(left).replaceAll("[^a-z0-9]+", " ").trim();
        String normalizedRight = TextUtil.safeLower(right).replaceAll("[^a-z0-9]+", " ").trim();
        return !normalizedLeft.isBlank()
                && !normalizedRight.isBlank()
                && (normalizedLeft.equals(normalizedRight)
                || normalizedLeft.contains(normalizedRight)
                || normalizedRight.contains(normalizedLeft));
    }

    private String rewritePointAsAnswer(String point) {
        if (point == null || point.isBlank()) {
            return "";
        }
        String rewritten = point.trim()
                .replaceAll("(?i)^getting started\\s+", "")
                .replaceAll("(?i)^for example,?\\s+", "")
                .replaceAll("(?i)^use this runbook when\\s+", "This applies when ")
                .replaceAll("(?i)\\s+", " ")
                .trim();
        if (!rewritten.endsWith(".") && !rewritten.endsWith("!") && !rewritten.endsWith("?")) {
            rewritten = rewritten + ".";
        }
        return rewritten;
    }

    private String rewritePointAsAction(String point) {
        String rewritten = rewritePointAsAnswer(point);
        if (rewritten.isBlank()) {
            return "";
        }
        String normalized = TextUtil.safeLower(rewritten);
        return rewritten;
    }

    private List<String> formatReferences(List<AiAnswerSourceVO> sources) {
        return sources.stream()
                .map(this::formatReference)
                .filter(reference -> !reference.isBlank())
                .distinct()
                .limit(4)
                .toList();
    }

    private String formatReference(AiAnswerSourceVO source) {
        if (source == null) {
            return "";
        }
        if ("WEB".equals(source.getSourceType())) {
            String domain = source.getSourceDomain() == null || source.getSourceDomain().isBlank()
                    ? "web evidence"
                    : source.getSourceDomain();
            return source.getSourceUrl() == null || source.getSourceUrl().isBlank()
                    ? domain
                    : domain + " - " + source.getSourceUrl();
        }
        if ("DOCUMENT".equals(source.getSourceType())) {
            return source.getSourceDocumentName() == null ? "uploaded document" : source.getSourceDocumentName();
        }
        if ("ARTICLE".equals(source.getSourceType())) {
            return "Internal knowledge base";
        }
        return "";
    }

    private String formatBullets(List<String> values, String fallback) {
        if (values == null || values.isEmpty()) {
            return fallback;
        }
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            builder.append("- ").append(value.replaceFirst("^[-*]\\s*", "")).append("\n");
        }
        return builder.toString().trim();
    }

    private List<String> extractQuestionFocusedPoints(AiQueryPlan contract, String content) {
        String cleaned = cleanAnswerContent(content);
        if (cleaned.isBlank()) {
            return List.of();
        }

        List<String> terms = answerFocusTerms(contract);
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        for (String rawLine : cleaned.split("\\R+")) {
            String line = normalizeAnswerLine(rawLine);
            if (isUsefulAnswerPoint(line) && isAnswerPresentationClean(line)) {
                candidates.add(line);
            }
        }

        for (String sentence : cleaned.split("(?<=[.!?。！？])\\s+")) {
            String line = normalizeAnswerLine(sentence);
            if (isUsefulAnswerPoint(line) && isAnswerPresentationClean(line)) {
                candidates.add(line);
            }
        }

        List<String> lines = new ArrayList<>();
        candidates.stream()
                .map(point -> new ScoredPoint(point, scoreAnswerPoint(point, terms)))
                .filter(point -> isAllowedAnswerPointForQuestion(point.point(), contract))
                .filter(point -> terms.isEmpty() || point.score() >= minimumAnswerPointScore(terms))
                .sorted(Comparator.comparingInt(ScoredPoint::score).reversed())
                .limit(6)
                .forEach(point -> lines.add(point.point()));

        if (!lines.isEmpty()) {
            return lines;
        }

        if (!terms.isEmpty()) {
            return List.of();
        }
        return List.of(limitAnswerPoint(cleaned));
    }

    private List<String> answerFocusTerms(AiQueryPlan contract) {
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        if (contract != null) {
            contract.requiredConcepts().stream()
                    .filter(term -> !GENERIC_QUESTION_TERMS.contains(term))
                    .forEach(terms::add);
            contract.retrievalTerms().stream()
                    .filter(term -> !BROAD_DOMAIN_TERMS.contains(term) || TECHNICAL_DOMAIN_TERMS.contains(term))
                    .filter(term -> !GENERIC_QUESTION_TERMS.contains(term))
                    .filter(term -> term.length() >= 4 || term.matches(".*[0-9+#./_-].*"))
                    .forEach(terms::add);
        }
        return terms.stream().limit(12).toList();
    }

    private int minimumAnswerPointScore(List<String> terms) {
        return terms == null || terms.size() <= 2 ? 1 : 2;
    }

    private int scoreAnswerPoint(String point, List<String> terms) {
        String normalized = TextUtil.safeLower(point);
        if (isAnswerNoiseLine(point)) {
            return -20;
        }
        int score = 0;
        for (String term : terms) {
            if (coverageEvaluator.matchesConcept(normalized, term)) {
                score += term.length() >= 8 ? 3 : 1;
            }
        }
        if (TextUtil.containsAny(normalized,
                "means", "refers to", "different", "difference", "because", "caused by",
                "first", "check", "verify", "configure", "set", "run", "command", "cause",
                "should", "must", "use", "when", "instead", "avoid")) {
            score += 2;
        }
        if (TextUtil.containsAny(normalized,
                "click", "subscribe", "copyright", "table of contents", "skip to content",
                "source references", "primary keywords", "runbook", "playbook", "sop", "https://")) {
            score -= 8;
        }
        return score;
    }

    private boolean isAllowedAnswerPointForQuestion(String point, AiQueryPlan contract) {
        String normalized = TextUtil.safeLower(point);
        List<String> terms = answerFocusTerms(contract);
        return !containsUnaskedSideConcept(normalized, terms);
    }

    private boolean containsUnaskedSideConcept(String normalizedPoint, List<String> terms) {
        if (terms == null || terms.isEmpty()) {
            return false;
        }
        long matched = terms.stream().filter(term -> coverageEvaluator.matchesConcept(normalizedPoint, term)).count();
        return matched == 0;
    }

    private String cleanAnswerContent(String content) {
        if (content == null) {
            return "";
        }
        String normalized = content
                .replace('\u00a0', ' ')
                .replaceAll("(?is)```.*?```", " ")
                .replaceAll("(?im)^\\s*#{1,6}\\s+.*\\b(runbook|sop|playbook|knowledge pack|operations knowledge pack)\\b.*$", "")
                .replaceAll("(?im)^\\s*#{1,6}\\s+", "")
                .replaceAll("(?im)^category\\s*:\\s*.*$", "")
                .replaceAll("(?im)^primary keywords\\s*:\\s*.*$", "")
                .replaceAll("(?im)^source references\\s*:?\\s*$", "")
                .replaceAll("(?im)^\\s*-?\\s*https?://\\S+\\s*$", "")
                .replaceAll("(?im)^purpose\\s*:?\\s*$", "")
                .replaceAll("(?im)^use this when\\s+.*$", "")
                .replaceAll("(?im)^use this (playbook|guide|runbook) when\\s+.*$", "")
                .replaceAll("(?im)^.*\\b(consolidated reviewed internal knowledge|uploaded document chunks remain retrieval evidence)\\b.*$", "")
                .replaceAll("(?im)^original question\\s*:?\\s*$", "")
                .replaceAll("(?im)^observed answer\\s*:?\\s*$", "")
                .replaceAll("(?im)^knowledge candidate\\s*:?\\s*$", "")
                .replaceAll("(?im)^review checklist\\s*:?\\s*$", "")
                .replaceAll("(?im)^web evidence summary\\s*:?\\s*$", "")
                .replaceAll("(?im)^model-generated answer\\s*:?\\s*$", "")
                .replaceAll("(?im)^sources\\s*:?\\s*$", "")
                .replaceAll("(?im)^source\\s*:\\s*.*$", "")
                .trim();
        StringBuilder cleaned = new StringBuilder();
        for (String rawLine : normalized.split("\\R+")) {
            String line = rawLine.trim();
            if (!line.isBlank() && !isAnswerNoiseLine(line)) {
                cleaned.append(line).append("\n");
            }
        }
        return cleaned.toString().trim();
    }

    private String normalizeAnswerLine(String rawLine) {
        if (rawLine == null) {
            return "";
        }
        String line = rawLine
                .replaceAll("^\\s*[-*•]+\\s*", "")
                .replaceAll("^\\s*\\d+[.)]\\s*", "")
                .replaceAll("\\s+", " ")
                .trim();
        return limitAnswerPoint(line);
    }

    private boolean isUsefulAnswerPoint(String line) {
        String normalized = TextUtil.safeLower(line);
        return line.length() >= 24
                && !isAnswerNoiseLine(line)
                && !normalized.startsWith("category:")
                && !normalized.startsWith("primary keywords:")
                && !normalized.startsWith("source references:")
                && !normalized.startsWith("purpose:")
                && !normalized.startsWith("sources:")
                && !normalized.startsWith("this knowledge pack covers")
                && !normalized.contains("explains the durable concepts")
                && !normalized.contains("operational checks that support teams need")
                && !normalized.contains("recommended response for users")
                && !normalized.contains("keep one concept per section")
                && !normalized.contains("final article readable")
                && !normalized.contains("closest existing knowledge pack")
                && !normalized.contains("verified operational concept")
                && !normalized.contains("use this runbook")
                && !normalized.contains("use this playbook")
                && !normalized.contains("use this guide")
                && !normalized.contains("consolidated reviewed internal knowledge")
                && !normalized.contains("uploaded document chunks remain retrieval evidence")
                && !normalized.matches(".*\\brunbook\\s+runbook\\b.*")
                && !normalized.contains("capture externally discovered evidence")
                && !normalized.contains("preserve the model-generated answer")
                && !normalized.contains("review this content before publishing")
                && !normalized.contains("saved for administrator review")
                && !normalized.contains("add internal system names")
                && !normalized.contains("remove any platform-specific claim")
                && !normalized.contains("validate the source url")
                && !normalized.contains("skip to content")
                && !normalized.contains("table of contents")
                && !normalized.contains("was this page helpful")
                && !normalized.contains("docker build --target")
                && !normalized.contains("stop at a named stage")
                && !normalized.contains("window.")
                && !normalized.contains("document.")
                && !normalized.contains("__next")
                && !normalized.matches(".*[{};]{4,}.*");
    }

    private boolean isAnswerNoiseLine(String line) {
        String normalized = TextUtil.safeLower(line);
        return normalized.isBlank()
                || normalized.matches("^#{1,6}\\s+.*$")
                || normalized.matches("^https?://\\S+$")
                || normalized.matches("^[-*]?\\s*https?://\\S+$")
                || isLikelySectionHeading(line)
                || normalized.matches("^(title|category|purpose|primary keywords|source references|sources|original question|observed answer)\\s*:?.*$")
                || normalized.contains("knowledge candidate")
                || normalized.contains("review checklist")
                || normalized.contains("capture externally discovered evidence")
                || normalized.contains("review this content before publishing")
                || normalized.contains("saved for administrator review")
                || normalized.contains("consolidated reviewed internal knowledge")
                || normalized.contains("uploaded document chunks remain retrieval evidence")
                || normalized.startsWith("this knowledge pack covers")
                || normalized.contains("explains the durable concepts")
                || normalized.contains("operational checks that support teams need")
                || normalized.contains("recommended response for users")
                || normalized.contains("keep one concept per section")
                || normalized.contains("final article readable")
                || normalized.contains("closest existing knowledge pack")
                || normalized.contains("verified operational concept")
                || normalized.startsWith("use this when")
                || normalized.contains("use this runbook")
                || normalized.contains("use this playbook")
                || normalized.contains("use this guide")
                || normalized.matches(".*\\b(runbook|playbook|sop)\\s+\\1\\b.*")
                || normalized.contains("skip to content")
                || normalized.contains("table of contents")
                || normalized.contains("was this page helpful")
                || normalized.contains("advertisement")
                || normalized.contains("sponsored")
                || normalized.contains("course curriculum")
                || normalized.contains("window.")
                || normalized.contains("document.")
                || normalized.contains("__next")
                || normalized.matches(".*[{};]{4,}.*");
    }

    private boolean isLikelySectionHeading(String line) {
        String normalized = TextUtil.safeLower(line);
        if (line.length() > 80 || line.contains(".") || line.contains("?") || line.contains("`")) {
            return false;
        }
        if (TextUtil.containsAny(normalized,
                "overview", "purpose", "impact model", "network model", "volume model",
                "fast triage checklist", "checklist", "useful commands", "common root causes",
                "recovery actions", "escalate when", "escalate immediately when",
                "prevention controls", "priority model", "priority rules", "routing rules",
                "sla model", "customer update template", "resolution template")) {
            return true;
        }
        return line.matches("^[A-Z][A-Za-z0-9 /&()_-]{2,80}$")
                && line.split("\\s+").length <= 7;
    }

    private String limitAnswerPoint(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() <= 220) {
            return trimmed;
        }
        return trimmed.substring(0, 217).trim() + "...";
    }

    private AiAnswerVO buildFallbackAnswer() {
        AiAnswerVO answerVO = new AiAnswerVO();
        answerVO.setAnswer("I do not have enough reliable knowledge to answer this yet. Please create a support ticket or add reviewed knowledge for this topic.");
        answerVO.setAnswerMode("NONE");
        answerVO.setSourceType("NONE");
        answerVO.setSourceScore(null);
        answerVO.setSourceArticleId(null);
        answerVO.setSourceTitle(null);
        answerVO.setSourceDocumentId(null);
        answerVO.setSourceDocumentName(null);
        answerVO.setSourceChunkId(null);
        answerVO.setSourceChunkIndex(null);
        answerVO.setSourceWebEvidenceId(null);
        answerVO.setSourceUrl(null);
        answerVO.setSourceDomain(null);
        answerVO.setSourceStatus(null);
        answerVO.setSuggestCreateTicket(true);
        answerVO.setCreatedTicketId(null);
        answerVO.setSources(new ArrayList<>());
        return answerVO;
    }
    private List<AiAnswerSourceVO> buildDocumentSources(List<DocumentSearchResultVO> documentResults) {
        List<AiAnswerSourceVO> sources = new ArrayList<>();

        for (DocumentSearchResultVO result : documentResults) {
            AiAnswerSourceVO source = new AiAnswerSourceVO();
            source.setSourceType("DOCUMENT");
            source.setSourceScore(result.getRelevanceScore());
            source.setSourceArticleId(null);
            source.setSourceTitle(null);
            source.setSourceDocumentId(result.getDocumentId());
            source.setSourceDocumentName(result.getOriginalFilename());
            source.setSourceChunkId(result.getChunkId());
            source.setSourceChunkIndex(result.getChunkIndex());
            source.setSourceWebEvidenceId(null);
            source.setSourceUrl(null);
            source.setSourceDomain(result.getCategory());
            source.setSourceStatus(null);
            source.setPreviewContent(result.getContent());
            sources.add(source);
        }

        return sources;
    }
    private List<AiAnswerSourceVO> buildArticleSources(List<KnowledgeArticle> articles) {
        List<AiAnswerSourceVO> sources = new ArrayList<>();

        for (KnowledgeArticle article : articles) {
            AiAnswerSourceVO source = new AiAnswerSourceVO();
            source.setSourceType("ARTICLE");
            source.setSourceScore(null);
            source.setSourceArticleId(article.getId());
            source.setSourceTitle(article.getTitle());
            source.setSourceDocumentId(null);
            source.setSourceDocumentName(null);
            source.setSourceChunkId(null);
            source.setSourceChunkIndex(null);
            source.setSourceWebEvidenceId(null);
            source.setSourceUrl(null);
            source.setSourceDomain(article.getCategory());
            source.setSourceStatus(null);
            source.setPreviewContent(article.getContent());
            sources.add(source);
        }

        return sources;
    }

    private List<AiAnswerSourceVO> buildArticleChunkSources(List<KnowledgeArticleChunkSearchResultVO> chunks) {
        List<AiAnswerSourceVO> sources = new ArrayList<>();

        for (KnowledgeArticleChunkSearchResultVO chunk : chunks) {
            AiAnswerSourceVO source = new AiAnswerSourceVO();
            source.setSourceType("ARTICLE");
            source.setSourceScore(chunk.getRelevanceScore());
            source.setSourceArticleId(chunk.getArticleId());
            source.setSourceTitle(chunk.getArticleTitle());
            source.setSourceDocumentId(null);
            source.setSourceDocumentName(null);
            source.setSourceChunkId(chunk.getChunkId());
            source.setSourceChunkIndex(chunk.getChunkIndex());
            source.setSourceWebEvidenceId(null);
            source.setSourceUrl(null);
            source.setSourceDomain(chunk.getCategory());
            source.setSourceStatus(null);
            source.setPreviewContent("## " + chunk.getSectionTitle() + "\n\n" + chunk.getContent());
            sources.add(source);
        }

        return sources;
    }

    private List<AiAnswerSourceVO> buildWebEvidenceSources(WebEvidence evidence) {
        if (evidence.getSources() != null && !evidence.getSources().isEmpty()) {
            return evidence.getSources().stream()
                    .map(item -> buildWebEvidenceSource(evidence, item))
                    .toList();
        }
        return List.of(buildWebEvidenceSource(evidence, null));
    }

    private AiAnswerSourceVO buildWebEvidenceSource(WebEvidence evidence, WebEvidenceSource item) {
        AiAnswerSourceVO source = new AiAnswerSourceVO();
        source.setSourceType("WEB");
        java.math.BigDecimal credibility = item == null ? evidence.getCredibilityScore() : item.getCredibilityScore();
        source.setSourceScore(credibility == null ? null : credibility.doubleValue());
        source.setSourceArticleId(null);
        source.setSourceTitle(item == null ? evidence.getTitle() : item.getTitle());
        source.setSourceDocumentId(null);
        source.setSourceDocumentName(null);
        source.setSourceChunkId(null);
        source.setSourceChunkIndex(null);
        source.setSourceWebEvidenceId(evidence.getId());
        source.setSourceUrl(item == null ? evidence.getUrl() : item.getUrl());
        source.setSourceDomain(item == null ? evidence.getDomain() : item.getDomain());
        source.setSourceStatus(evidence.getStatus());
        source.setPreviewContent(item == null ? evidence.getExtractedContent() : item.getExcerpt());
        return source;
    }
}
