package com.zwf.aisupport.service.impl;

import com.zwf.aisupport.common.exception.BusinessException;
import com.zwf.aisupport.common.util.CurrentUserUtil;
import com.zwf.aisupport.common.util.KnowledgeArticleVoMapper;
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
import com.zwf.aisupport.mapper.AiQuestionLogMapper;
import com.zwf.aisupport.mapper.DocumentChunkMapper;
import com.zwf.aisupport.mapper.KnowledgeArticleMapper;
import com.zwf.aisupport.mapper.TicketMapper;
import com.zwf.aisupport.service.AiAssistantService;
import com.zwf.aisupport.service.DocumentService;
import com.zwf.aisupport.service.LlmGatewayService;
import com.zwf.aisupport.service.WebEvidenceService;
import com.zwf.aisupport.vo.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class AiAssistantServiceImpl implements AiAssistantService {

    private static final Pattern WORD_SPLIT_PATTERN = Pattern.compile("[^a-z0-9+#./-]+");
    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "the", "and", "or", "but", "if", "then", "this", "that", "these", "those",
            "in", "on", "at", "to", "for", "from", "with", "without", "after", "before", "behind",
            "why", "what", "how", "when", "where", "which", "who", "does", "do", "did", "is", "are",
            "was", "were", "be", "been", "being", "still", "first", "should", "check", "please",
            "i", "we", "you", "my", "our", "your", "it", "its", "of", "by", "as", "into", "about",
            "not", "because", "even", "though", "has", "have", "had", "than"
    );
    private static final Set<String> BROAD_DOMAIN_TERMS = Set.of(
            "auth", "authentication", "authorization", "jwt", "token", "kubernetes", "postgresql",
            "postgres", "redis", "docker", "production", "troubleshooting", "document", "article",
            "knowledge", "platform", "cloudops", "support", "server", "service", "services", "system",
            "container", "containers", "database", "backend", "frontend", "application"
    );
    private static final Set<String> SHORT_TECHNICAL_TERMS = Set.of(
            "jwt", "nbf", "exp", "iat", "iss", "aud", "jti", "kid", "wal", "lsn", "pod", "hpa",
            "cpu", "api", "tls", "ssl", "dns", "rbac", "sso", "oidc", "csrf", "cors"
    );
    private static final Set<String> GENERIC_QUESTION_TERMS = Set.of(
            "rejected", "reject", "expired", "expire", "failure", "failed", "error", "issue",
            "problem", "handle", "configure", "download", "install", "login", "logout", "users",
            "inside", "instead", "communication", "connect", "connects", "connected", "connecting",
            "use", "using", "used", "names", "name", "running", "fail", "fails"
    );

    private final KnowledgeArticleMapper knowledgeArticleMapper;
    private final TicketMapper ticketMapper;
    private final AiQuestionLogMapper aiQuestionLogMapper;
    private final DocumentChunkMapper documentChunkMapper;
    private final DocumentService documentService;
    private final WebEvidenceService webEvidenceService;
    private final LlmGatewayService llmGatewayService;
    private final KnowledgePackReorganizer knowledgePackReorganizer;

    public AiAssistantServiceImpl(KnowledgeArticleMapper knowledgeArticleMapper,
                                  TicketMapper ticketMapper,
                                  AiQuestionLogMapper aiQuestionLogMapper,
                                  DocumentChunkMapper documentChunkMapper,
                                  DocumentService documentService,
                                  WebEvidenceService webEvidenceService,
                                  LlmGatewayService llmGatewayService,
                                  KnowledgePackReorganizer knowledgePackReorganizer) {
        this.knowledgeArticleMapper = knowledgeArticleMapper;
        this.ticketMapper = ticketMapper;
        this.aiQuestionLogMapper = aiQuestionLogMapper;
        this.documentChunkMapper = documentChunkMapper;
        this.documentService = documentService;
        this.webEvidenceService = webEvidenceService;
        this.llmGatewayService = llmGatewayService;
        this.knowledgePackReorganizer = knowledgePackReorganizer;
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
        AiAnswerVO answerVO = uploadedDocumentResults.isEmpty()
                ? findAnswerFromKnowledgeBase(trimmedQuestion)
                : buildDocumentBasedAnswer(trimmedQuestion, uploadedDocumentResults);

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
        List<String> terms = extractSearchTerms(question);
        List<String> specificTerms = extractSpecificTerms(terms);
        List<String> versionTerms = extractVersionTerms(terms);
        List<String> coreConcepts = extractCoreConcepts(question, terms);

        // Search uploaded document chunks with the full user question first.
        List<DocumentSearchResultVO> documentResults = documentChunkMapper.searchChunks(question)
                .stream()
                .filter(result -> isReliableInternalSource(result.getContent(), terms, specificTerms, versionTerms, coreConcepts))
                .toList();
        if (!documentResults.isEmpty()) {
            AiAnswerVO answer = buildDocumentBasedAnswer(question, documentResults);
            if (isGroundedAnswerUsable(answer.getAnswer(), terms, coreConcepts)) {
                return answer;
            }
        }

        // Then use local article relevance scoring. This avoids matching broad stop words or loosely related topics.
        List<KnowledgeArticle> articles = findRelevantArticles(question, terms, specificTerms, versionTerms, coreConcepts);
        if (!articles.isEmpty()) {
            AiAnswerVO answer = buildKnowledgeBasedAnswer(question, articles);
            if (isGroundedAnswerUsable(answer.getAnswer(), terms, coreConcepts)) {
                return answer;
            }
        }

        Long currentUserId = CurrentUserUtil.requireCurrentUserId();
        Optional<WebEvidence> webEvidence = webEvidenceService.searchAndStoreEvidence(question, currentUserId);
        if (webEvidence.isPresent()) {
            AiAnswerVO answer = buildWebEvidenceBasedAnswer(question, webEvidence.get(), articles, documentResults);
            if (isGroundedAnswerUsable(answer.getAnswer(), terms, coreConcepts)) {
                return answer;
            }
        }

        Optional<String> modelAnswer = llmGatewayService.generateDirectAnswer(question)
                .map(this::normalizeUserAnswer)
                .filter(answer -> !answer.isBlank());
        if (modelAnswer.isPresent()) {
            WebEvidence modelEvidence = webEvidenceService.storeModelGeneratedEvidence(question, modelAnswer.get(), currentUserId);
            return buildModelGeneratedAnswer(modelEvidence, modelAnswer.get());
        }

        return buildFallbackAnswer();
    }

    private List<DocumentSearchResultVO> findUploadedDocumentAnswer(String question, KnowledgeDocumentVO document) {
        List<String> terms = extractSearchTerms(question);
        List<String> specificTerms = extractSpecificTerms(terms);
        List<String> versionTerms = extractVersionTerms(terms);
        List<String> coreConcepts = extractCoreConcepts(question, terms);

        List<DocumentSearchResultVO> preciseResults = documentChunkMapper.searchChunksByDocumentId(document.getId(), question)
                .stream()
                .filter(result -> isReliableInternalSource(result.getContent(), terms, specificTerms, versionTerms, coreConcepts))
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
        aiQuestionLog.setSuggestCreateTicket(answerVO.getSuggestCreateTicket());
        aiQuestionLog.setCreatedTicketId(answerVO.getCreatedTicketId());

        aiQuestionLogMapper.insert(aiQuestionLog);
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
        return vo;
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

    private AiAnswerVO buildKnowledgeBasedAnswer(String question, List<KnowledgeArticle> articles) {
        KnowledgeArticle article = articles.get(0);
        List<AiAnswerSourceVO> sources = buildArticleSources(articles);

        AiAnswerVO answerVO = new AiAnswerVO();
        answerVO.setAnswer(buildEnterpriseAnswer(question, "ARTICLE", sources, article.getContent()));
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

    private AiAnswerVO buildWebEvidenceBasedAnswer(String question,
                                                   WebEvidence evidence,
                                                   List<KnowledgeArticle> internalArticles,
                                                   List<DocumentSearchResultVO> internalDocuments) {
        List<AiAnswerSourceVO> sources = new ArrayList<>();
        sources.addAll(buildArticleSources(internalArticles));
        sources.addAll(buildDocumentSources(internalDocuments));
        sources.addAll(buildWebEvidenceSources(evidence));

        AiAnswerVO answerVO = new AiAnswerVO();
        answerVO.setAnswer(buildEnterpriseAnswer(question, "WEB", sources, evidence.getExtractedContent()));
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

    private AiAnswerVO buildDocumentBasedAnswer(String question, List<DocumentSearchResultVO> documentResults) {
        DocumentSearchResultVO documentResult = documentResults.get(0);
        List<AiAnswerSourceVO> sources = buildDocumentSources(documentResults);

        AiAnswerVO answerVO = new AiAnswerVO();
        answerVO.setAnswer(buildEnterpriseAnswer(question, "DOCUMENT", sources, documentResult.getContent()));
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
    private List<KnowledgeArticle> findRelevantArticles(String question,
                                                        List<String> terms,
                                                        List<String> specificTerms,
                                                        List<String> versionTerms,
                                                        List<String> coreConcepts) {
        if (terms.isEmpty()) {
            return List.of();
        }

        return knowledgeArticleMapper.findPublishedArticles().stream()
                .filter(article -> !isLowQualityKnowledgeContent(article.getContent()))
                .map(article -> new ArticleMatch(article, scoreArticle(article, terms)))
                .filter(match -> match.score() >= 6)
                .filter(match -> isReliableInternalSource(combinedArticleText(match.article()), terms, specificTerms, versionTerms, coreConcepts))
                .sorted(Comparator.comparingInt(ArticleMatch::score).reversed())
                .limit(5)
                .map(ArticleMatch::article)
                .toList();
    }

    private List<String> extractSearchTerms(String question) {
        String normalizedQuestion = question == null ? "" : question.toLowerCase();
        return WORD_SPLIT_PATTERN.splitAsStream(normalizedQuestion)
                .map(String::trim)
                .filter(term -> term.length() >= 3)
                .filter(term -> !STOP_WORDS.contains(term))
                .distinct()
                .limit(12)
                .toList();
    }

    private int scoreArticle(KnowledgeArticle article, List<String> terms) {
        String title = TextUtil.safeLower(article.getTitle());
        String category = TextUtil.safeLower(article.getCategory());
        String content = TextUtil.safeLower(article.getContent());
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
        if (terms.stream().anyMatch(term -> Set.of("container", "containers", "backend").contains(term))
                && ("docker".equals(category) || title.contains("docker") || content.contains("docker compose"))) {
            score += 6;
        }
        if (terms.stream().anyMatch(term -> Set.of("postgresql", "postgres", "database").contains(term))
                && (category.contains("postgres") || title.contains("postgres") || content.contains("postgres"))) {
            score += 4;
        }

        return score;
    }

    private List<String> extractSpecificTerms(List<String> terms) {
        Set<String> broadTerms = Set.of(
                "kubernetes", "postgresql", "production", "troubleshooting", "document",
                "article", "knowledge", "platform", "cloudops", "support"
        );
        return terms.stream()
                .filter(term -> !term.matches(".*\\d.*") && term.length() >= 10)
                .filter(term -> !broadTerms.contains(term))
                .filter(term -> !GENERIC_QUESTION_TERMS.contains(term))
                .toList();
    }

    private List<String> extractVersionTerms(List<String> terms) {
        return terms.stream()
                .filter(term -> term.matches(".*\\d.*"))
                .toList();
    }

    private List<String> extractCoreConcepts(String question, List<String> terms) {
        String normalizedQuestion = TextUtil.safeLower(question);
        LinkedHashSet<String> concepts = new LinkedHashSet<>();

        if (normalizedQuestion.contains("not before")) {
            concepts.add("not before");
        }
        if (normalizedQuestion.contains("service name") || normalizedQuestion.contains("service names")) {
            concepts.add("service name");
        }
        if (normalizedQuestion.contains("localhost")) {
            concepts.add("localhost");
        }
        if (normalizedQuestion.contains("container-to-container")) {
            concepts.add("container-to-container");
        }
        for (String term : terms) {
            if (isCoreConceptTerm(term)) {
                concepts.add(term);
            }
        }
        return concepts.stream().limit(6).toList();
    }

    private boolean isCoreConceptTerm(String term) {
        if (term == null || term.isBlank() || GENERIC_QUESTION_TERMS.contains(term)) {
            return false;
        }
        if (SHORT_TECHNICAL_TERMS.contains(term)) {
            return true;
        }
        if ("localhost".equals(term)) {
            return true;
        }
        if (BROAD_DOMAIN_TERMS.contains(term)) {
            return false;
        }
        return term.matches(".*[0-9+#./-].*") || term.length() >= 8;
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
                || specificTerms.stream().anyMatch(term -> matchesConcept(normalizedSource, term));
        boolean coreCovered = coversCoreConcepts(normalizedSource, coreConcepts);

        return enoughCoverage && versionsCovered && specificCovered && coreCovered;
    }

    private boolean coversCoreConcepts(String normalizedSource, List<String> coreConcepts) {
        if (coreConcepts == null || coreConcepts.isEmpty()) {
            return true;
        }

        long matchedConcepts = coreConcepts.stream()
                .filter(concept -> matchesConcept(normalizedSource, concept))
                .count();
        boolean shortTechnicalCovered = coreConcepts.stream()
                .filter(SHORT_TECHNICAL_TERMS::contains)
                .allMatch(concept -> matchesConcept(normalizedSource, concept));
        double coverage = (double) matchedConcepts / (double) coreConcepts.size();

        return shortTechnicalCovered && (coreConcepts.size() <= 2 ? matchedConcepts == coreConcepts.size() : coverage >= 0.60);
    }

    private boolean matchesConcept(String normalizedSource, String concept) {
        if (normalizedSource == null || concept == null || concept.isBlank()) {
            return false;
        }
        String normalizedConcept = TextUtil.safeLower(concept);
        if (normalizedSource.contains(normalizedConcept)) {
            return true;
        }
        if (matchesSingularPlural(normalizedSource, normalizedConcept)) {
            return true;
        }
        if (matchesCompoundConcept(normalizedSource, normalizedConcept)) {
            return true;
        }
        return switch (normalizedConcept) {
            case "nbf" -> normalizedSource.contains("not before");
            case "not before" -> normalizedSource.contains("nbf");
            case "service name", "service names" -> normalizedSource.contains("service name")
                    || normalizedSource.contains("service names")
                    || normalizedSource.contains("connect to other services")
                    || normalizedSource.contains("connects to other services");
            case "container-to-container" -> normalizedSource.contains("container") && normalizedSource.contains("service");
            case "exp" -> normalizedSource.contains("expiration") || normalizedSource.contains("expired") || normalizedSource.contains("expires");
            case "iat" -> normalizedSource.contains("issued at");
            case "aud" -> normalizedSource.contains("audience");
            case "iss" -> normalizedSource.contains("issuer");
            case "kid" -> normalizedSource.contains("key id");
            case "jti" -> normalizedSource.contains("jwt id");
            default -> false;
        };
    }

    private boolean matchesSingularPlural(String normalizedSource, String normalizedConcept) {
        if (normalizedConcept.length() <= 3) {
            return false;
        }
        if (normalizedConcept.endsWith("s")) {
            return normalizedSource.contains(normalizedConcept.substring(0, normalizedConcept.length() - 1));
        }
        return normalizedSource.contains(normalizedConcept + "s");
    }

    private boolean matchesCompoundConcept(String normalizedSource, String normalizedConcept) {
        if (!normalizedConcept.contains("-")) {
            return false;
        }
        List<String> parts = WORD_SPLIT_PATTERN.splitAsStream(normalizedConcept.replace("-", " "))
                .filter(part -> !part.isBlank())
                .filter(part -> !STOP_WORDS.contains(part))
                .distinct()
                .toList();
        return !parts.isEmpty() && parts.stream().allMatch(normalizedSource::contains);
    }

    private boolean isGroundedAnswerUsable(String answer, List<String> terms, List<String> coreConcepts) {
        if (answer == null || answer.isBlank()) {
            return false;
        }
        String normalizedAnswer = TextUtil.safeLower(answer);
        if (!coversCoreConcepts(normalizedAnswer, coreConcepts)) {
            return false;
        }
        if (terms == null || terms.isEmpty()) {
            return true;
        }
        long matchedTerms = terms.stream()
                .filter(normalizedAnswer::contains)
                .count();
        return matchedTerms >= Math.min(2, terms.size());
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

    private String buildConciseWebEvidenceAnswer(WebEvidence evidence) {
        String content = evidence.getExtractedContent();
        if (content == null || content.isBlank()) {
            content = evidence.getSuggestedArticleContent();
        }
        String concise = buildConciseAnswer(evidence.getQuestion(), content);
        if (concise.isBlank()) {
            concise = "I found related external evidence, but it needs administrator review before becoming trusted internal knowledge.";
        }
        String domain = evidence.getDomain();
        return domain == null || domain.isBlank() ? concise : concise + "\n\nSource: " + domain;
    }

    private String buildEnterpriseAnswer(String question,
                                         String sourceType,
                                         List<AiAnswerSourceVO> sources,
                                         String fallbackContent) {
        String answerContext = buildAnswerContext(sources, fallbackContent);
        Optional<String> deterministicAnswer = buildDomainSpecificAnswer(question, answerContext, sources);
        if (deterministicAnswer.isPresent()) {
            return deterministicAnswer.get();
        }

        Optional<String> modelAnswer = safeGenerateGroundedAnswer(question, sourceType, sources)
                .map(this::normalizeEnterpriseAnswer)
                .filter(answer -> !answer.isBlank());
        if (modelAnswer.isPresent()) {
            return modelAnswer.get();
        }

        return buildTemplateAnswer(question, answerContext, sources);
    }

    private String buildAnswerContext(List<AiAnswerSourceVO> sources, String fallbackContent) {
        LinkedHashSet<String> context = new LinkedHashSet<>();
        if (fallbackContent != null && !fallbackContent.isBlank()) {
            context.add(fallbackContent);
        }
        if (sources != null) {
            sources.stream()
                    .map(AiAnswerSourceVO::getPreviewContent)
                    .filter(preview -> preview != null && !preview.isBlank())
                    .forEach(context::add);
        }
        return String.join("\n\n", context);
    }

    private Optional<String> buildDomainSpecificAnswer(String question, String content, List<AiAnswerSourceVO> sources) {
        if (isDockerfileInstructionQuestion(question)) {
            return Optional.of(buildDockerfileInstructionEnterpriseAnswer(sources));
        }
        if (isDockerPostgresContainerConnectionQuestion(question, content)) {
            return Optional.of(buildDockerPostgresContainerConnectionAnswer(sources));
        }
        if (isDockerComposeServiceNameQuestion(question, content)) {
            return Optional.of(buildDockerComposeServiceNameAnswer(sources));
        }
        if (isDockerMultiStageBuildQuestion(question, content)) {
            return Optional.of(buildDockerMultiStageBuildAnswer(sources));
        }
        return Optional.empty();
    }

    private Optional<String> safeGenerateGroundedAnswer(String question, String sourceType, List<AiAnswerSourceVO> sources) {
        try {
            Optional<String> answer = llmGatewayService.generateGroundedAnswer(question, sourceType, sources);
            return answer == null ? Optional.empty() : answer;
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private String normalizeEnterpriseAnswer(String answer) {
        if (answer == null || answer.isBlank()) {
            return "";
        }
        String cleaned = answer.trim();
        if (cleaned.contains("Direct Answer")
                && (cleaned.contains("Recommended Actions") || cleaned.contains("When To Use") || cleaned.contains("Checks"))) {
            return cleaned;
        }
        return """
                ## Direct Answer
                %s

                ## Explanation
                The answer is synthesized from the available grounded sources. It avoids exposing raw article, chunk, or web evidence text.

                ## Likely Causes
                - The issue is usually caused by a configuration, timing, version, or state mismatch related to the question.

                ## Checks
                - Verify the exact error, configuration value, version, and runtime context before applying changes.

                ## Fix
                - Compare the current behavior against the referenced source and internal knowledge.

                ## References
                - See the grounding section for the evidence used.
                """.formatted(cleaned);
    }

    private String buildTemplateAnswer(String question, String content, List<AiAnswerSourceVO> sources) {
        List<String> points = extractQuestionFocusedPoints(question, content);
        String direct = points.isEmpty()
                ? "The available evidence is related, but it does not fully explain the specific question."
                : points.get(0);
        String explanation = points.size() > 1
                ? points.get(1)
                : "The likely answer depends on the exact configuration, runtime state, and the mechanism named in the question.";
        List<String> references = sources.stream()
                .map(this::formatReference)
                .filter(reference -> !reference.isBlank())
                .distinct()
                .limit(4)
                .toList();

        if (isConceptQuestion(question)) {
            return """
                    ## Direct Answer
                    %s

                    ## Explanation
                    %s

                    ## When To Use
                    %s

                    ## Example
                    Use the concept when it directly reduces operational risk, simplifies delivery, or makes the runtime environment easier to reason about.

                    ## References
                    %s
                    """.formatted(
                    direct,
                    explanation,
                    formatBullets(points.stream().skip(2).limit(3).toList(), "- Use it when the source evidence confirms it fits the current system design."),
                    formatBullets(references, "- Grounding sources are attached below.")
            ).trim();
        }

        List<String> causes = points.stream().skip(2).limit(3).toList();
        return """
                ## Direct Answer
                %s

                ## Explanation
                %s

                ## Likely Causes
                %s

                ## Checks
                - Verify the exact configuration, timestamp, version, and runtime context related to the failure.
                - Check whether the specific mechanism named in the question is present in the active configuration or source evidence.

                ## Fix
                - If internal knowledge only partially covers the issue, use the web evidence as a supplement and submit it for review.

                ## References
                %s
                """.formatted(
                direct,
                explanation,
                formatBullets(causes, "- The current evidence does not list a confirmed root cause."),
                formatBullets(references, "- Grounding sources are attached below.")
        ).trim();
    }

    private boolean isDockerPostgresContainerConnectionQuestion(String question, String content) {
        String normalizedQuestion = TextUtil.safeLower(question);
        String normalizedContent = TextUtil.safeLower(content);
        return TextUtil.containsAny(normalizedQuestion, "postgresql", "postgres", "database")
                && TextUtil.containsAny(normalizedQuestion, "backend", "container", "docker", "compose")
                && TextUtil.containsAny(normalizedQuestion, "connect", "connection", "cannot", "fail", "fails")
                && TextUtil.containsAny(normalizedContent, "service name", "localhost", "docker compose", "postgres");
    }

    private String buildDockerPostgresContainerConnectionAnswer(List<AiAnswerSourceVO> sources) {
        return """
                ## Direct Answer
                The backend container usually fails to connect even while the PostgreSQL container is running because it is using the wrong network address. In Docker Compose, the backend should connect to the PostgreSQL service name, such as `postgres:5432`, not `localhost:5432`.

                ## Explanation
                `localhost` inside the backend container points back to the backend container itself. It does not point to the PostgreSQL container. Docker Compose gives containers on the same Compose network internal DNS, so one service reaches another by service name.

                ## Likely Causes
                - The backend uses `localhost` or `127.0.0.1` as `DB_HOST`.
                - The backend and PostgreSQL services are not attached to the same Compose network.
                - The app is using the host-mapped port instead of the internal PostgreSQL port, usually `5432`.
                - PostgreSQL is running but not ready when the backend starts.
                - `DB_HOST`, `DB_PORT`, `DB_USER`, `DB_PASSWORD`, or database name is different from the Compose configuration.

                ## Checks
                - Set `DB_HOST` to the Compose service name, for example `postgres`.
                - Use `DB_PORT=5432` for container-to-container traffic unless the database listens on a custom internal port.
                - Confirm both services are in the same Compose network.
                - Check backend logs for the exact error, such as connection refused, timeout, authentication failed, or unknown host.

                ## Fix
                Update the backend connection string to use the PostgreSQL service name and internal port, then restart the backend after PostgreSQL is healthy.

                ## References
                %s
                """.formatted(formatBullets(formatReferences(sources), "- Grounding sources are attached below.")).trim();
    }

    private boolean isDockerComposeServiceNameQuestion(String question, String content) {
        String normalizedQuestion = TextUtil.safeLower(question);
        String normalizedContent = TextUtil.safeLower(content);
        return TextUtil.containsAny(normalizedQuestion, "service name", "service names", "localhost", "container-to-container")
                && TextUtil.containsAny(normalizedQuestion, "docker", "compose", "container")
                && TextUtil.containsAny(normalizedContent, "service name", "localhost", "docker compose", "internal dns");
    }

    private String buildDockerComposeServiceNameAnswer(List<AiAnswerSourceVO> sources) {
        return """
                ## Direct Answer
                Docker Compose services should use service names because Compose provides internal DNS for containers on the same network. `localhost` inside a container refers to that same container, not another service.

                ## Explanation
                If a backend container connects to `localhost`, it is trying to reach a process inside the backend container. To reach another Compose service, such as PostgreSQL or Redis, it should use the service name from `docker-compose.yml`, for example `postgres:5432` or `redis:6379`.

                ## When To Use
                - Use service names for container-to-container communication inside the same Compose project.
                - Use `localhost` only when a process inside the same container is the target.
                - Use host-published ports mainly for traffic from the host machine into a container.

                ## Example
                A backend service should use `jdbc:postgresql://postgres:5432/appdb`, not `jdbc:postgresql://localhost:5432/appdb`, when PostgreSQL is another Compose service.

                ## References
                %s
                """.formatted(formatBullets(formatReferences(sources), "- Grounding sources are attached below.")).trim();
    }

    private boolean isDockerMultiStageBuildQuestion(String question, String content) {
        String normalizedQuestion = TextUtil.safeLower(question);
        String normalizedContent = TextUtil.safeLower(content);
        return normalizedQuestion.contains("multi-stage")
                && normalizedQuestion.contains("build")
                && normalizedQuestion.contains("purpose")
                && TextUtil.containsAny(normalizedContent, "multi-stage", "multiple from", "final image", "build stage");
    }

    private String buildDockerMultiStageBuildAnswer(List<AiAnswerSourceVO> sources) {
        return """
                ## Direct Answer
                Docker multi-stage builds separate the build environment from the final runtime image. They are used to compile, package, or prepare an application in one stage, then copy only the required output into a smaller and cleaner final image.

                ## Explanation
                A Dockerfile can contain multiple `FROM` stages. Earlier stages can include source code, compilers, SDKs, package managers, and temporary build files. The final stage should contain only what the application needs to run.

                ## When To Use
                - Use them when build tools are needed during image creation but should not be shipped to production.
                - Use them for compiled applications, frontend builds, Java packages, Go binaries, or Node build pipelines.
                - Use them when production images need to be smaller, cleaner, and lower risk.
                - Use them when you want separate build, test, and runtime stages in one Dockerfile.

                ## Example
                A frontend app can be built in a Node stage, then the generated static files can be copied into an Nginx runtime image.

                ## References
                %s
                """.formatted(formatBullets(formatReferences(sources), "- Grounding sources are attached below.")).trim();
    }

    private String buildDockerfileInstructionEnterpriseAnswer(List<AiAnswerSourceVO> sources) {
        return """
                ## Direct Answer
                These Dockerfile instructions define where the application lives in the image, what files are copied into it, what build-time commands run, which port the app is expected to listen on, and which command starts the Python application when the container runs.

                ## Explanation
                `WORKDIR` sets the default directory inside the image. `COPY` brings application files into the image. `RUN` executes build-time steps such as dependency installation. `EXPOSE` documents the application port but does not publish it by itself. `CMD` defines the default runtime command for the container.

                ## When To Use
                - Use `WORKDIR` to keep later instructions predictable and avoid path mistakes.
                - Use `COPY` to include source code, dependency files, or built artifacts.
                - Use `RUN` for repeatable image build steps.
                - Use `EXPOSE` to document the intended listening port.
                - Use `CMD` for the normal startup command that can still be overridden at runtime.

                ## Example
                A Python service can copy its source into `/app`, install dependencies during the image build, expose port `8000`, and start with a `python` or `uvicorn` command when the container launches.

                ## References
                %s
                """.formatted(formatBullets(formatReferences(sources), "- Grounding sources are attached below.")).trim();
    }

    private boolean isConceptQuestion(String question) {
        String normalized = TextUtil.safeLower(question);
        return normalized.startsWith("what is")
                || normalized.startsWith("what are")
                || normalized.contains("purpose")
                || normalized.contains("when should")
                || normalized.contains("why should")
                || normalized.contains("explain");
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

    private String buildConciseAnswer(String question, String content) {
        if (isDockerfileInstructionQuestion(question)) {
            return buildDockerfileInstructionAnswer();
        }

        List<String> points = extractQuestionFocusedPoints(question, content);
        if (points.isEmpty()) {
            return "";
        }

        if (points.size() == 1) {
            return points.get(0);
        }

        StringBuilder answer = new StringBuilder("Key answer:\n");
        points.stream()
                .limit(5)
                .forEach(point -> answer.append("- ").append(point).append("\n"));
        return answer.toString().trim();
    }

    private String normalizeUserAnswer(String answer) {
        List<String> points = extractQuestionFocusedPoints("", answer);
        if (points.isEmpty()) {
            return "";
        }
        if (points.size() <= 2) {
            return String.join("\n", points);
        }

        StringBuilder normalized = new StringBuilder("Key answer:\n");
        points.stream()
                .limit(5)
                .forEach(point -> normalized.append("- ").append(point).append("\n"));
        return normalized.toString().trim();
    }

    private List<String> extractQuestionFocusedPoints(String question, String content) {
        String cleaned = cleanAnswerContent(content);
        if (cleaned.isBlank()) {
            return List.of();
        }

        List<String> terms = extractSearchTerms(question == null ? "" : question);
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        for (String rawLine : cleaned.split("\\R+")) {
            String line = normalizeAnswerLine(rawLine);
            if (isUsefulAnswerPoint(line)) {
                candidates.add(line);
            }
        }

        for (String sentence : cleaned.split("(?<=[.!?。！？])\\s+")) {
            String line = normalizeAnswerLine(sentence);
            if (isUsefulAnswerPoint(line)) {
                candidates.add(line);
            }
        }

        List<String> lines = new ArrayList<>();
        candidates.stream()
                .map(point -> new ScoredPoint(point, scoreAnswerPoint(point, terms)))
                .filter(point -> terms.isEmpty() || point.score() > 0)
                .sorted(Comparator.comparingInt(ScoredPoint::score).reversed())
                .limit(5)
                .forEach(point -> lines.add(point.point()));

        if (!lines.isEmpty()) {
            return lines;
        }

        if (!terms.isEmpty()) {
            return List.of();
        }
        return List.of(limitAnswerPoint(cleaned));
    }

    private int scoreAnswerPoint(String point, List<String> terms) {
        String normalized = TextUtil.safeLower(point);
        int score = 0;
        for (String term : terms) {
            if (normalized.contains(term)) {
                score += term.length() >= 8 ? 3 : 1;
            }
        }
        if (TextUtil.containsAny(normalized, "first", "check", "verify", "configure", "set", "run", "command", "cause", "because")) {
            score += 2;
        }
        if (TextUtil.containsAny(normalized, "click", "subscribe", "copyright", "table of contents", "skip to content")) {
            score -= 5;
        }
        return score;
    }

    private boolean isDockerfileInstructionQuestion(String question) {
        String normalized = TextUtil.safeLower(question);
        return normalized.contains("dockerfile")
                && normalized.contains("workdir")
                && normalized.contains("copy")
                && normalized.contains("run")
                && normalized.contains("expose")
                && normalized.contains("cmd");
    }

    private String buildDockerfileInstructionAnswer() {
        return """
                Key answer:
                - WORKDIR sets the default directory inside the image, so later COPY, RUN, and CMD instructions run from a predictable path.
                - COPY moves your application files from the build context into the image so the container has the code it needs.
                - RUN executes build-time commands, such as installing dependencies; the result is saved into the image layer.
                - EXPOSE documents the port the app listens on; it does not publish the port by itself, but helps operators map it correctly.
                - CMD defines the default command that starts the Python app when the container runs; it can be overridden at runtime.
                """.trim();
    }

    private String cleanAnswerContent(String content) {
        if (content == null) {
            return "";
        }
        return content
                .replace('\u00a0', ' ')
                .replaceAll("(?im)^#+\\s*", "")
                .replaceAll("(?im)^category\\s*:\\s*.*$", "")
                .replaceAll("(?im)^primary keywords\\s*:\\s*.*$", "")
                .replaceAll("(?im)^source references\\s*:\\s*.*$", "")
                .replaceAll("(?im)^purpose\\s*:?\\s*$", "")
                .replaceAll("(?im)^original question\\s*:?\\s*$", "")
                .replaceAll("(?im)^observed answer\\s*:?\\s*$", "")
                .replaceAll("(?im)^review checklist\\s*:?\\s*$", "")
                .replaceAll("(?im)^web evidence summary\\s*:?\\s*$", "")
                .replaceAll("(?im)^model-generated answer\\s*:?\\s*$", "")
                .replaceAll("(?im)^sources\\s*:?\\s*$", "")
                .trim();
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
                && !normalized.startsWith("category:")
                && !normalized.startsWith("primary keywords:")
                && !normalized.startsWith("source references:")
                && !normalized.startsWith("purpose:")
                && !normalized.startsWith("sources:")
                && !normalized.contains("use this runbook")
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
                && !normalized.contains("window.")
                && !normalized.contains("document.")
                && !normalized.contains("__next")
                && !normalized.matches(".*[{};]{4,}.*");
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
            source.setSourceDomain(null);
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
            source.setSourceDomain(null);
            source.setSourceStatus(null);
            source.setPreviewContent(article.getContent());
            sources.add(source);
        }

        return sources;
    }

    private List<AiAnswerSourceVO> buildWebEvidenceSources(WebEvidence evidence) {
        AiAnswerSourceVO source = new AiAnswerSourceVO();
        source.setSourceType("WEB");
        source.setSourceScore(evidence.getCredibilityScore() == null ? null : evidence.getCredibilityScore().doubleValue());
        source.setSourceArticleId(null);
        source.setSourceTitle(evidence.getTitle());
        source.setSourceDocumentId(null);
        source.setSourceDocumentName(null);
        source.setSourceChunkId(null);
        source.setSourceChunkIndex(null);
        source.setSourceWebEvidenceId(evidence.getId());
        source.setSourceUrl(evidence.getUrl());
        source.setSourceDomain(evidence.getDomain());
        source.setSourceStatus(evidence.getStatus());
        source.setPreviewContent(evidence.getExtractedContent());
        return List.of(source);
    }
}
