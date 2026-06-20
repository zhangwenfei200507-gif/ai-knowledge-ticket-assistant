package com.zwf.aisupport.service.impl;

import com.zwf.aisupport.common.exception.BusinessException;
import com.zwf.aisupport.common.util.CurrentUserUtil;
import com.zwf.aisupport.dto.AiAskDTO;
import com.zwf.aisupport.dto.AiQuestionLogFeedbackDTO;
import com.zwf.aisupport.dto.KnowledgeArticleCreateDTO;
import com.zwf.aisupport.entity.AiQuestionLog;
import com.zwf.aisupport.entity.KnowledgeArticle;
import com.zwf.aisupport.entity.Ticket;
import com.zwf.aisupport.entity.WebEvidence;
import com.zwf.aisupport.mapper.AiQuestionLogMapper;
import com.zwf.aisupport.mapper.DocumentChunkMapper;
import com.zwf.aisupport.mapper.KnowledgeArticleMapper;
import com.zwf.aisupport.mapper.TicketMapper;
import com.zwf.aisupport.service.AiAssistantService;
import com.zwf.aisupport.service.WebEvidenceService;
import com.zwf.aisupport.vo.*;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
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
    private final WebEvidenceService webEvidenceService;

    public AiAssistantServiceImpl(KnowledgeArticleMapper knowledgeArticleMapper,
                                  TicketMapper ticketMapper,
                                  AiQuestionLogMapper aiQuestionLogMapper,
                                  DocumentChunkMapper documentChunkMapper,
                                  WebEvidenceService webEvidenceService) {
        this.knowledgeArticleMapper = knowledgeArticleMapper;
        this.ticketMapper = ticketMapper;
        this.aiQuestionLogMapper = aiQuestionLogMapper;
        this.documentChunkMapper = documentChunkMapper;
        this.webEvidenceService = webEvidenceService;
    }

    @Override
    public AiAnswerVO ask(AiAskDTO aiAskDTO) {
        AiAnswerVO answerVO = findAnswerFromKnowledgeBase(aiAskDTO);

        // Record every AI question so the platform can review usage and improve knowledge later.
        saveQuestionLog(aiAskDTO.getQuestion(), answerVO);

        return answerVO;
    }

    @Override
    public AiAnswerVO askAndCreateTicket(AiAskDTO aiAskDTO) {
        AiAnswerVO answerVO = findAnswerFromKnowledgeBase(aiAskDTO);
        if (!Boolean.TRUE.equals(answerVO.getSuggestCreateTicket())) {
            saveQuestionLog(aiAskDTO.getQuestion(), answerVO);
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

        // Keep the AI answer and the created ticket connected for later review.
        saveQuestionLog(aiAskDTO.getQuestion(), answerVO);

        return answerVO;
    }

    private AiAnswerVO findAnswerFromKnowledgeBase(AiAskDTO aiAskDTO) {
        String question = aiAskDTO.getQuestion().trim();
        List<String> terms = extractSearchTerms(question);
        List<String> specificTerms = extractSpecificTerms(terms);
        List<String> versionTerms = extractVersionTerms(terms);

        // Search uploaded document chunks with the full user question first.
        List<DocumentSearchResultVO> documentResults = documentChunkMapper.searchChunks(question)
                .stream()
                .filter(result -> isReliableInternalSource(result.getContent(), terms, specificTerms, versionTerms))
                .toList();
        if (!documentResults.isEmpty()) {
            return buildDocumentBasedAnswer(documentResults);
        }

        // Then use local article relevance scoring. This avoids matching broad stop words or loosely related topics.
        List<KnowledgeArticle> articles = findRelevantArticles(question, terms, specificTerms, versionTerms);
        if (!articles.isEmpty()) {
            return buildKnowledgeBasedAnswer(articles);
        }

        Long currentUserId = CurrentUserUtil.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException("User is not authenticated");
        }
        Optional<WebEvidence> webEvidence = webEvidenceService.searchAndStoreEvidence(question, currentUserId);
        if (webEvidence.isPresent()) {
            return buildWebEvidenceBasedAnswer(webEvidence.get());
        }

        return buildFallbackAnswer();
    }

    private void saveQuestionLog(String question, AiAnswerVO answerVO) {
        Long currentUserId = CurrentUserUtil.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException("User is not authenticated");
        }

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
        Long currentUserId = CurrentUserUtil.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException("User is not authenticated");
        }

        // Users can review their own AI question history.
        return aiQuestionLogMapper.findByUserId(currentUserId)
                .stream()
                .map(this::convertToAiQuestionLogVO)
                .toList();
    }
    @Override
    public AiDashboardStatsVO getDashboardStats() {
        String role = CurrentUserUtil.getCurrentUserRole();
        if (!"ADMIN".equals(role)) {
            throw new BusinessException("Only administrators can access AI dashboard stats");
        }

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
        String role = CurrentUserUtil.getCurrentUserRole();
        if (!"ADMIN".equals(role)) {
            throw new BusinessException("Only administrators can access knowledge gaps");
        }

        // Knowledge gaps help administrators decide what new articles should be added.
        return aiQuestionLogMapper.findKnowledgeGaps()
                .stream()
                .map(this::convertToKnowledgeGapVO)
                .toList();
    }
    @Override
    public KnowledgeArticleVO createArticleFromKnowledgeGap(Long logId, KnowledgeArticleCreateDTO createDTO) {
        String role = CurrentUserUtil.getCurrentUserRole();
        if (!"ADMIN".equals(role)) {
            throw new BusinessException("Only administrators can create articles from knowledge gaps");
        }

        Long currentUserId = CurrentUserUtil.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException("User is not authenticated");
        }

        AiQuestionLog aiQuestionLog = aiQuestionLogMapper.findById(logId);
        if (aiQuestionLog == null || aiQuestionLog.getSourceArticleId() != null) {
            throw new BusinessException("Knowledge gap not found");
        }

        KnowledgeArticle article = new KnowledgeArticle();
        article.setTitle(createDTO.getTitle());
        article.setContent(createDTO.getContent());
        article.setCategory(createDTO.getCategory());
        article.setCreatedBy(currentUserId);
        article.setStatus("PUBLISHED");

        // Convert an unanswered AI question into reusable knowledge for future answers.
        knowledgeArticleMapper.insert(article);
        aiQuestionLogMapper.linkKnowledgeArticle(logId, article.getId(), article.getTitle());

        return convertToKnowledgeArticleVO(article);
    }

    private KnowledgeArticleVO convertToKnowledgeArticleVO(KnowledgeArticle article) {
        KnowledgeArticleVO vo = new KnowledgeArticleVO();
        vo.setId(article.getId());
        vo.setTitle(article.getTitle());
        vo.setContent(article.getContent());
        vo.setCategory(article.getCategory());
        vo.setCreatedBy(article.getCreatedBy());
        vo.setStatus(article.getStatus());
        vo.setCreatedTime(article.getCreatedTime());
        vo.setUpdatedTime(article.getUpdatedTime());
        return vo;
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
        String role = CurrentUserUtil.getCurrentUserRole();
        if (!"ADMIN".equals(role)) {
            throw new BusinessException("Only administrators can access AI question logs");
        }

        // Admin users can review AI usage across the whole platform.
        return aiQuestionLogMapper.findAll()
                .stream()
                .map(this::convertToAiQuestionLogVO)
                .toList();
    }
    @Override
    public void submitQuestionLogFeedback(Long id, AiQuestionLogFeedbackDTO feedbackDTO) {
        Long currentUserId = CurrentUserUtil.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException("User is not authenticated");
        }

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

    private AiAnswerVO buildKnowledgeBasedAnswer(List<KnowledgeArticle> articles) {
        KnowledgeArticle article = articles.get(0);

        AiAnswerVO answerVO = new AiAnswerVO();
        answerVO.setAnswer("Based on the knowledge base: " + article.getContent());
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
        answerVO.setSources(buildArticleSources(articles));
        return answerVO;
    }

    private AiAnswerVO buildWebEvidenceBasedAnswer(WebEvidence evidence) {
        AiAnswerVO answerVO = new AiAnswerVO();
        answerVO.setAnswer("""
                I could not find a reliable internal article or uploaded document for this question, so I checked external web evidence.

                External evidence from %s:
                %s

                This evidence has been saved for administrator review. Once approved, it can become internal knowledge for future answers.
                """.formatted(evidence.getDomain(), evidence.getSuggestedArticleContent()));
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
        answerVO.setSources(buildWebEvidenceSources(evidence));
        return answerVO;
    }

    private AiAnswerVO buildDocumentBasedAnswer(List<DocumentSearchResultVO> documentResults) {
        DocumentSearchResultVO documentResult = documentResults.get(0);

        AiAnswerVO answerVO = new AiAnswerVO();
        answerVO.setAnswer("Based on the uploaded document " + documentResult.getOriginalFilename() + ": " + documentResult.getContent());
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
        answerVO.setSources(buildDocumentSources(documentResults));
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
        String title = safeLower(article.getTitle());
        String category = safeLower(article.getCategory());
        String content = safeLower(article.getContent());
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

        String normalizedSource = safeLower(sourceText);
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
        return safeLower(article.getTitle()) + " "
                + safeLower(article.getCategory()) + " "
                + safeLower(article.getContent());
    }

    private String safeLower(String value) {
        return value == null ? "" : value.toLowerCase();
    }

    private record ArticleMatch(KnowledgeArticle article, int score) {
    }

    private AiAnswerVO buildFallbackAnswer() {
        AiAnswerVO answerVO = new AiAnswerVO();
        answerVO.setAnswer("I could not find a reliable answer in uploaded documents, internal knowledge articles, or external web evidence. A support ticket can be created for further assistance.");
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
