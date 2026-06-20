package com.zwf.aisupport.service.impl;

import com.zwf.aisupport.common.exception.BusinessException;
import com.zwf.aisupport.common.util.CurrentUserUtil;
import com.zwf.aisupport.common.util.UploadedTextExtractor;
import com.zwf.aisupport.entity.DocumentChunk;
import com.zwf.aisupport.entity.DocumentKnowledgeCandidate;
import com.zwf.aisupport.entity.KnowledgeArticle;
import com.zwf.aisupport.entity.KnowledgeDocument;
import com.zwf.aisupport.mapper.DocumentChunkMapper;
import com.zwf.aisupport.mapper.DocumentKnowledgeCandidateMapper;
import com.zwf.aisupport.mapper.KnowledgeArticleMapper;
import com.zwf.aisupport.mapper.KnowledgeDocumentMapper;
import com.zwf.aisupport.service.DocumentService;
import com.zwf.aisupport.vo.DocumentCategoryStatsVO;
import com.zwf.aisupport.vo.DocumentChunkVO;
import com.zwf.aisupport.vo.DocumentSearchResultVO;
import com.zwf.aisupport.vo.KnowledgeDocumentVO;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class DocumentServiceImpl implements DocumentService {

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;
    private static final int MAX_CHUNK_LENGTH = 800;
    private static final int MAX_LEARNING_CANDIDATES_PER_DOCUMENT = 3;

    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final DocumentChunkMapper documentChunkMapper;
    private final DocumentKnowledgeCandidateMapper candidateMapper;
    private final KnowledgeArticleMapper knowledgeArticleMapper;

    public DocumentServiceImpl(KnowledgeDocumentMapper knowledgeDocumentMapper,
                               DocumentChunkMapper documentChunkMapper,
                               DocumentKnowledgeCandidateMapper candidateMapper,
                               KnowledgeArticleMapper knowledgeArticleMapper) {
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
        this.documentChunkMapper = documentChunkMapper;
        this.candidateMapper = candidateMapper;
        this.knowledgeArticleMapper = knowledgeArticleMapper;
    }

    @Override
    public KnowledgeDocumentVO upload(MultipartFile file, String category) {
        Long currentUserId = CurrentUserUtil.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException("User is not authenticated");
        }

        UploadedTextExtractor.ExtractedText extractedText = UploadedTextExtractor.extract(file, MAX_FILE_SIZE, "document");
        List<String> chunks = splitIntoChunks(extractedText.content());

        KnowledgeDocument document = new KnowledgeDocument();
        document.setOriginalFilename(extractedText.filename());
        document.setContentType(resolveContentType(file, extractedText.fileType()));
        document.setFileSize(file.getSize());
        document.setCategory(category);
        document.setUploadedBy(currentUserId);
        document.setStatus("ACTIVE");

        // Save document metadata first so chunks can reference the generated document id.
        knowledgeDocumentMapper.insert(document);

        List<DocumentChunk> savedChunks = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            DocumentChunk chunk = new DocumentChunk();
            chunk.setDocumentId(document.getId());
            chunk.setChunkIndex(i);
            chunk.setContent(chunks.get(i));
            documentChunkMapper.insert(chunk);
            savedChunks.add(chunk);
        }
        createLearningCandidates(document, savedChunks, currentUserId);

        return convertToKnowledgeDocumentVO(document);
    }

    @Override
    public List<KnowledgeDocumentVO> listDocuments() {
        return knowledgeDocumentMapper.findActiveDocuments()
                .stream()
                .map(this::convertToKnowledgeDocumentVO)
                .toList();
    }

    @Override
    public List<DocumentChunkVO> listChunks(Long documentId) {
        KnowledgeDocument document = knowledgeDocumentMapper.findById(documentId);
        if (document == null || !"ACTIVE".equals(document.getStatus())) {
            throw new BusinessException("Document not found");
        }

        return documentChunkMapper.findByDocumentId(documentId)
                .stream()
                .map(this::convertToDocumentChunkVO)
                .toList();
    }
    @Override
    public List<DocumentSearchResultVO> searchChunks(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            throw new BusinessException("Search keyword cannot be blank");
        }

        // Search document chunks first by keyword before adding semantic retrieval later.
        return documentChunkMapper.searchChunks(keyword.trim());
    }

    private void createLearningCandidates(KnowledgeDocument document, List<DocumentChunk> chunks, Long currentUserId) {
        chunks.stream()
                .map(chunk -> new CandidateDraft(chunk, scoreLearningValue(document, chunk)))
                .filter(draft -> draft.score() >= 7)
                .sorted(Comparator.comparingInt(CandidateDraft::score).reversed())
                .limit(MAX_LEARNING_CANDIDATES_PER_DOCUMENT)
                .forEach(draft -> candidateMapper.insert(buildCandidate(document, draft.chunk(), draft.score(), currentUserId)));
    }

    private DocumentKnowledgeCandidate buildCandidate(KnowledgeDocument document,
                                                      DocumentChunk chunk,
                                                      int score,
                                                      Long currentUserId) {
        String category = inferCategory(document.getCategory(), document.getOriginalFilename() + " " + chunk.getContent());
        DocumentKnowledgeCandidate candidate = new DocumentKnowledgeCandidate();
        candidate.setDocumentId(document.getId());
        candidate.setChunkId(chunk.getId());
        candidate.setTitle(buildCandidateTitle(document.getOriginalFilename(), category, chunk.getContent()));
        candidate.setContent(buildCandidateContent(document, chunk, category));
        candidate.setCategory(category);
        candidate.setImportanceScore(new BigDecimal(score).movePointLeft(1).min(new BigDecimal("0.99")));
        SuggestedArticleTarget target = findSuggestedArticleTarget(category, chunk.getContent());
        candidate.setSuggestedArticleId(target.articleId());
        candidate.setSuggestedAction(target.articleId() == null ? "CREATE_KNOWLEDGE_PACK" : "REORGANIZE_KNOWLEDGE");
        candidate.setStatus("NEEDS_REVIEW");
        candidate.setCreatedBy(currentUserId);
        return candidate;
    }

    private SuggestedArticleTarget findSuggestedArticleTarget(String category, String content) {
        List<String> terms = extractTerms(category + " " + content);
        return knowledgeArticleMapper.findPublishedArticles().stream()
                .map(article -> new ArticleTargetScore(article, scoreArticleTarget(article, category, terms)))
                .filter(score -> score.score() >= 6)
                .max(Comparator.comparingInt(ArticleTargetScore::score))
                .map(score -> new SuggestedArticleTarget(score.article().getId()))
                .orElse(new SuggestedArticleTarget(null));
    }

    private int scoreArticleTarget(KnowledgeArticle article, String category, List<String> terms) {
        String articleText = normalizeSearchText(article.getTitle() + " " + article.getCategory() + " " + article.getContent());
        int score = 0;
        if (category != null && article.getCategory() != null && category.equalsIgnoreCase(article.getCategory())) {
            score += 5;
        }
        for (String term : terms) {
            if (articleText.contains(term)) {
                score++;
            }
        }
        return score;
    }

    private List<String> extractTerms(String text) {
        String normalized = normalizeSearchText(text);
        List<String> terms = new ArrayList<>();
        for (String term : normalized.split("[^a-z0-9+#./-]+")) {
            if (term.length() >= 4 && !terms.contains(term)) {
                terms.add(term);
            }
            if (terms.size() >= 12) {
                break;
            }
        }
        return terms;
    }

    private String buildCandidateTitle(String filename, String category, String content) {
        String topic = firstUsefulSentence(content);
        if (topic.length() > 70) {
            topic = topic.substring(0, 67).trim() + "...";
        }
        return limit("Knowledge Candidate: " + category + " - " + topic, 180);
    }

    private String buildCandidateContent(KnowledgeDocument document, DocumentChunk chunk, String category) {
        List<String> points = extractOperationalPoints(chunk.getContent());
        StringBuilder builder = new StringBuilder();
        builder.append("# ").append(buildCandidateTitle(document.getOriginalFilename(), category, chunk.getContent())).append("\n\n")
                .append("Category: ").append(category).append("\n")
                .append("Source document: ").append(document.getOriginalFilename()).append("\n")
                .append("Source chunk: ").append(chunk.getChunkIndex()).append("\n\n")
                .append("Knowledge to reorganize:\n");

        for (String point : points) {
            builder.append("- ").append(point).append("\n");
        }

        builder.append("\nKnowledge architecture checklist:\n")
                .append("- Place this knowledge in the closest existing Knowledge Pack before creating a new pack.\n")
                .append("- Rewrite the affected section so the new knowledge is naturally integrated, not appended.\n")
                .append("- Remove duplicate or low-density wording before publishing.\n")
                .append("- Reorganize retrieval chunks after the Knowledge Pack changes.\n");

        return builder.toString().trim();
    }

    private List<String> extractOperationalPoints(String content) {
        List<String> points = new ArrayList<>();
        for (String rawSentence : content.split("(?<=[.!?。！？])\\s+|\\R+")) {
            String sentence = normalizeLine(rawSentence);
            if (isOperationalSentence(sentence) && !points.contains(sentence)) {
                points.add(limit(sentence, 220));
            }
            if (points.size() >= 5) {
                return points;
            }
        }

        if (!points.isEmpty()) {
            return points;
        }
        return List.of(limit(normalizeLine(content), 220));
    }

    private int scoreLearningValue(KnowledgeDocument document, DocumentChunk chunk) {
        String text = normalizeSearchText(document.getCategory() + " " + document.getOriginalFilename() + " " + chunk.getContent());
        int score = 0;
        score += countMatches(text, "production", "incident", "outage", "down", "failure", "error", "timeout", "latency") * 3;
        score += countMatches(text, "verify", "check", "diagnose", "troubleshoot", "rollback", "restart", "configure") * 2;
        score += countMatches(text, "redis", "postgres", "postgresql", "docker", "kubernetes", "jwt", "token", "cache", "database") * 2;
        score += countMatches(text, "command", "sql", "log", "metric", "dashboard", "alert", "sla", "owner") * 2;
        if (chunk.getContent() != null && chunk.getContent().length() >= 180) {
            score += 2;
        }
        if (chunk.getContent() != null && chunk.getContent().length() >= 400) {
            score += 1;
        }
        return score;
    }

    private boolean isOperationalSentence(String sentence) {
        String normalized = normalizeSearchText(sentence);
        return sentence.length() >= 24
                && containsAny(normalized,
                "check", "verify", "confirm", "diagnose", "configure", "restart", "rollback",
                "redis", "postgres", "postgresql", "docker", "kubernetes", "jwt", "token",
                "production", "incident", "error", "timeout", "log", "metric", "alert", "sla");
    }

    private String firstUsefulSentence(String content) {
        for (String rawSentence : content.split("(?<=[.!?。！？])\\s+|\\R+")) {
            String sentence = normalizeLine(rawSentence);
            if (sentence.length() >= 18) {
                return sentence;
            }
        }
        return "Uploaded document knowledge";
    }

    private String inferCategory(String fallbackCategory, String text) {
        String normalized = normalizeSearchText(text);
        if (containsAny(normalized, "jwt", "token", "login", "auth", "password", "session")) return "Auth / JWT";
        if (containsAny(normalized, "redis", "cache")) return "Redis Cache";
        if (containsAny(normalized, "postgres", "postgresql", "database", "sql")) return "PostgreSQL";
        if (containsAny(normalized, "docker", "container", "compose")) return "Docker";
        if (containsAny(normalized, "kubernetes", "k8s", "pod", "cluster")) return "Kubernetes";
        if (containsAny(normalized, "incident", "outage", "sev", "rollback")) return "Incident SOP";
        if (containsAny(normalized, "ticket", "sla", "priority", "escalation")) return "Ticket Rules";
        if (containsAny(normalized, "customer", "template", "reply")) return "Customer Templates";
        return fallbackCategory == null || fallbackCategory.isBlank() ? "General" : fallbackCategory;
    }

    private int countMatches(String text, String... terms) {
        int score = 0;
        for (String term : terms) {
            if (text.contains(term)) {
                score++;
            }
        }
        return score;
    }

    private boolean containsAny(String text, String... terms) {
        for (String term : terms) {
            if (text.contains(term)) {
                return true;
            }
        }
        return false;
    }

    private String normalizeSearchText(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private String normalizeLine(String value) {
        return value == null ? "" : value
                .replaceAll("^\\s*[-*•]+\\s*", "")
                .replaceAll("^\\s*\\d+[.)]\\s*", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength).trim();
    }

    private record CandidateDraft(DocumentChunk chunk, int score) {
    }

    private record SuggestedArticleTarget(Long articleId) {
    }

    private record ArticleTargetScore(KnowledgeArticle article, int score) {
    }
    @Override
    public void archive(Long documentId) {
        String role = CurrentUserUtil.getCurrentUserRole();
        if (!"ADMIN".equals(role)) {
            throw new BusinessException("Only administrators can archive documents");
        }

        KnowledgeDocument document = knowledgeDocumentMapper.findById(documentId);
        if (document == null || !"ACTIVE".equals(document.getStatus())) {
            throw new BusinessException("Document not found");
        }

        // Archive outdated documents so they are no longer used as AI answer sources.
        knowledgeDocumentMapper.archive(documentId);
    }
    @Override
    public List<DocumentCategoryStatsVO> getCategoryStats() {
        String role = CurrentUserUtil.getCurrentUserRole();
        if (!"ADMIN".equals(role)) {
            throw new BusinessException("Only administrators can access document category stats");
        }

        // Document category stats help administrators understand how uploaded knowledge is organized.
        return knowledgeDocumentMapper.countActiveDocumentsByCategory();
    }
    private List<String> splitIntoChunks(String content) {
        String normalizedContent = content == null ? "" : content.trim();
        if (normalizedContent.isBlank()) {
            throw new BusinessException("Document content cannot be blank");
        }

        List<String> chunks = new ArrayList<>();
        String[] paragraphs = normalizedContent.split("\\R\\s*\\R");

        StringBuilder currentChunk = new StringBuilder();
        for (String paragraph : paragraphs) {
            String cleanedParagraph = paragraph.trim();
            if (cleanedParagraph.isBlank()) {
                continue;
            }

            if (currentChunk.length() + cleanedParagraph.length() > MAX_CHUNK_LENGTH) {
                if (!currentChunk.isEmpty()) {
                    chunks.add(currentChunk.toString().trim());
                    currentChunk.setLength(0);
                }
            }

            if (cleanedParagraph.length() > MAX_CHUNK_LENGTH) {
                chunks.add(cleanedParagraph.substring(0, MAX_CHUNK_LENGTH));
                String remaining = cleanedParagraph.substring(MAX_CHUNK_LENGTH).trim();
                if (!remaining.isBlank()) {
                    currentChunk.append(remaining).append("\n\n");
                }
            } else {
                currentChunk.append(cleanedParagraph).append("\n\n");
            }
        }

        if (!currentChunk.isEmpty()) {
            chunks.add(currentChunk.toString().trim());
        }

        return chunks;
    }

    private String resolveContentType(MultipartFile file, String fileType) {
        if (file.getContentType() != null && !file.getContentType().isBlank()) {
            return file.getContentType();
        }
        return fileType;
    }

    private KnowledgeDocumentVO convertToKnowledgeDocumentVO(KnowledgeDocument document) {
        KnowledgeDocumentVO vo = new KnowledgeDocumentVO();
        vo.setId(document.getId());
        vo.setOriginalFilename(document.getOriginalFilename());
        vo.setContentType(document.getContentType());
        vo.setFileSize(document.getFileSize());
        vo.setCategory(document.getCategory());
        vo.setUploadedBy(document.getUploadedBy());
        vo.setStatus(document.getStatus());
        vo.setCreatedTime(document.getCreatedTime());
        vo.setUpdatedTime(document.getUpdatedTime());
        return vo;
    }

    private DocumentChunkVO convertToDocumentChunkVO(DocumentChunk chunk) {
        DocumentChunkVO vo = new DocumentChunkVO();
        vo.setId(chunk.getId());
        vo.setDocumentId(chunk.getDocumentId());
        vo.setChunkIndex(chunk.getChunkIndex());
        vo.setContent(chunk.getContent());
        vo.setCreatedTime(chunk.getCreatedTime());
        return vo;
    }
}
