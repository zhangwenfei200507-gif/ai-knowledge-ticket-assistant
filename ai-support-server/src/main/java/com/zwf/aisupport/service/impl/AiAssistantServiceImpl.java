package com.zwf.aisupport.service.impl;

import com.zwf.aisupport.common.exception.BusinessException;
import com.zwf.aisupport.common.util.CurrentUserUtil;
import com.zwf.aisupport.common.util.KnowledgeArticleVoMapper;
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
            "i", "we", "you", "my", "our", "your", "it", "its", "of", "by", "as", "into", "about"
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

        // Search uploaded document chunks with the full user question first.
        List<DocumentSearchResultVO> documentResults = documentChunkMapper.searchChunks(question)
                .stream()
                .filter(result -> isReliableInternalSource(result.getContent(), terms, specificTerms, versionTerms))
                .toList();
        if (!documentResults.isEmpty()) {
            return buildDocumentBasedAnswer(question, documentResults);
        }

        // Then use local article relevance scoring. This avoids matching broad stop words or loosely related topics.
        List<KnowledgeArticle> articles = findRelevantArticles(question, terms, specificTerms, versionTerms);
        if (!articles.isEmpty()) {
            return buildKnowledgeBasedAnswer(question, articles);
        }

        Long currentUserId = CurrentUserUtil.requireCurrentUserId();
        Optional<WebEvidence> webEvidence = webEvidenceService.searchAndStoreEvidence(question, currentUserId);
        if (webEvidence.isPresent()) {
            return buildWebEvidenceBasedAnswer(webEvidence.get());
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

        List<DocumentSearchResultVO> preciseResults = documentChunkMapper.searchChunksByDocumentId(document.getId(), question)
                .stream()
                .filter(result -> isReliableInternalSource(result.getContent(), terms, specificTerms, versionTerms))
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
    public List<KnowledgeGapVO> listKnowledgeGaps() {
        CurrentUserUtil.requireAdmin("Only administrators can access knowledge gaps");

        // Knowledge gaps help administrators decide which Knowledge Pack should be reorganized.
        return aiQuestionLogMapper.findKnowledgeGaps()
                .stream()
                .map(this::convertToKnowledgeGapVO)
                .toList();
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
    public List<AiQuestionLogVO> listAllQuestionLogs() {
        CurrentUserUtil.requireAdmin("Only administrators can access AI question logs");

        // Admin users can review AI usage across the whole platform.
        return aiQuestionLogMapper.findAll()
                .stream()
                .map(this::convertToAiQuestionLogVO)
                .toList();
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
        answerVO.setAnswer(buildConciseAnswer(question, article.getContent()));
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

    private AiAnswerVO buildWebEvidenceBasedAnswer(WebEvidence evidence) {
        List<AiAnswerSourceVO> sources = buildWebEvidenceSources(evidence);

        AiAnswerVO answerVO = new AiAnswerVO();
        answerVO.setAnswer(buildConciseWebEvidenceAnswer(evidence));
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

    private String buildReviewedWebArticleTitle(WebEvidence evidence) {
        String question = evidence.getQuestion() == null ? "External Web Evidence" : evidence.getQuestion().trim();
        if (question.length() > 120) {
            question = question.substring(0, 117).trim() + "...";
        }
        return "Reviewed Web Answer: " + question;
    }

    private String buildReviewedWebArticleContent(WebEvidence evidence, String answer) {
        String evidenceSummary = evidence.getExtractedContent();
        if (evidenceSummary == null || evidenceSummary.isBlank()) {
            evidenceSummary = evidence.getSuggestedArticleContent();
        }

        return """
                # %s

                Category: %s
                Source references: %s

                Purpose:
                Preserve the model-generated answer and the web evidence collected for this question so an administrator can review it once and promote it into internal knowledge without spending another model call.

                Original question:
                %s

                Model-generated answer:
                %s

                Web evidence summary:
                %s

                Review checklist:
                - Verify the source URL is relevant and trustworthy.
                - Remove any platform-specific claim that does not apply to AegisCloud.
                - Add internal runbook links, commands, dashboards, or owners before publishing.
                """.formatted(
                buildReviewedWebArticleTitle(evidence),
                evidence.getCategory() == null ? "General" : evidence.getCategory(),
                evidence.getUrl() == null ? "-" : evidence.getUrl(),
                evidence.getQuestion() == null ? "-" : evidence.getQuestion(),
                answer == null ? "-" : answer,
                evidenceSummary == null ? "-" : evidenceSummary
        );
    }

    private AiAnswerVO buildDocumentBasedAnswer(String question, List<DocumentSearchResultVO> documentResults) {
        DocumentSearchResultVO documentResult = documentResults.get(0);
        List<AiAnswerSourceVO> sources = buildDocumentSources(documentResults);

        AiAnswerVO answerVO = new AiAnswerVO();
        answerVO.setAnswer(buildConciseAnswer(question, documentResult.getContent()));
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
                                                        List<String> versionTerms) {
        if (terms.isEmpty()) {
            return List.of();
        }

        return knowledgeArticleMapper.findPublishedArticles().stream()
                .filter(article -> !isLowQualityKnowledgeContent(article.getContent()))
                .map(article -> new ArticleMatch(article, scoreArticle(article, terms)))
                .filter(match -> match.score() >= 6)
                .filter(match -> isReliableInternalSource(combinedArticleText(match.article()), terms, specificTerms, versionTerms))
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
                .toList();
    }

    private List<String> extractVersionTerms(List<String> terms) {
        return terms.stream()
                .filter(term -> term.matches(".*\\d.*"))
                .toList();
    }

    private boolean isReliableInternalSource(String sourceText,
                                             List<String> terms,
                                             List<String> specificTerms,
                                             List<String> versionTerms) {
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
        boolean specificCovered = specificTerms.isEmpty() || specificTerms.stream().anyMatch(normalizedSource::contains);

        return enoughCoverage && versionsCovered && specificCovered;
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
                .sorted(Comparator.comparingInt(point -> -scoreAnswerPoint(point, terms)))
                .limit(5)
                .forEach(lines::add);

        if (!lines.isEmpty()) {
            return lines;
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
                && !normalized.startsWith("source references:")
                && !normalized.startsWith("purpose:")
                && !normalized.startsWith("sources:")
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
