package com.zwf.aisupport.service.impl;

import com.zwf.aisupport.common.exception.BusinessException;
import com.zwf.aisupport.common.util.CurrentUserUtil;
import com.zwf.aisupport.common.util.KnowledgeCategoryResolver;
import com.zwf.aisupport.common.util.TextUtil;
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

@Service
public class DocumentServiceImpl implements DocumentService {

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;
    private static final int TARGET_CHUNK_TOKENS = 800;
    private static final int APPROX_CHARS_PER_TOKEN = 4;
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
        Long currentUserId = CurrentUserUtil.requireCurrentUserId();

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
        String category = KnowledgeCategoryResolver.infer(document.getCategory(), document.getOriginalFilename() + " " + chunk.getContent());
        DocumentKnowledgeCandidate candidate = new DocumentKnowledgeCandidate();
        candidate.setDocumentId(document.getId());
        candidate.setChunkId(chunk.getId());
        candidate.setTitle(buildCandidateTitle(category, chunk.getContent()));
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
        String articleText = TextUtil.normalizeSearchText(article.getTitle() + " " + article.getCategory() + " " + article.getContent());
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
        String normalized = TextUtil.normalizeSearchText(text);
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

    private String buildCandidateTitle(String category, String content) {
        String topic = firstUsefulSentence(content);
        if (topic.length() > 70) {
            topic = topic.substring(0, 67).trim() + "...";
        }
        return TextUtil.limit(category + " Knowledge Insight - " + topic, 180);
    }

    private String buildCandidateContent(KnowledgeDocument document, DocumentChunk chunk, String category) {
        List<String> points = extractOperationalPoints(chunk.getContent());
        StringBuilder builder = new StringBuilder();
        builder.append("# ").append(buildCandidateTitle(category, chunk.getContent())).append("\n\n")
                .append("Category: ").append(category).append("\n")
                .append("\n## Core Knowledge\n");

        for (String point : points) {
            builder.append("- ").append(point).append("\n");
        }

        return builder.toString().trim();
    }

    private List<String> extractOperationalPoints(String content) {
        List<String> points = new ArrayList<>();
        for (String rawSentence : content.split("(?<=[.!?。！？])\\s+|\\R+")) {
            String sentence = normalizeLine(rawSentence);
            if (isOperationalSentence(sentence) && !points.contains(sentence)) {
                points.add(TextUtil.limit(sentence, 220));
            }
            if (points.size() >= 5) {
                return points;
            }
        }

        if (!points.isEmpty()) {
            return points;
        }
        return List.of(TextUtil.limit(normalizeLine(content), 220));
    }

    private int scoreLearningValue(KnowledgeDocument document, DocumentChunk chunk) {
        String text = TextUtil.normalizeSearchText(document.getCategory() + " " + document.getOriginalFilename() + " " + chunk.getContent());
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
        String normalized = TextUtil.normalizeSearchText(sentence);
        return sentence.length() >= 24
                && TextUtil.containsAny(normalized,
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

    private int countMatches(String text, String... terms) {
        int score = 0;
        for (String term : terms) {
            if (text.contains(term)) {
                score++;
            }
        }
        return score;
    }

    private String normalizeLine(String value) {
        return value == null ? "" : value
                .replaceAll("^\\s*[-*•]+\\s*", "")
                .replaceAll("^\\s*\\d+[.)]\\s*", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private record CandidateDraft(DocumentChunk chunk, int score) {
    }

    private record SuggestedArticleTarget(Long articleId) {
    }

    private record ArticleTargetScore(KnowledgeArticle article, int score) {
    }
    @Override
    public void archive(Long documentId) {
        CurrentUserUtil.requireAdmin("Only administrators can archive documents");

        KnowledgeDocument document = knowledgeDocumentMapper.findById(documentId);
        if (document == null || !"ACTIVE".equals(document.getStatus())) {
            throw new BusinessException("Document not found");
        }

        // Archive outdated documents so they are no longer used as AI answer sources.
        knowledgeDocumentMapper.archive(documentId);
    }
    @Override
    public List<DocumentCategoryStatsVO> getCategoryStats() {
        CurrentUserUtil.requireAdmin("Only administrators can access document category stats");

        // Document category stats help administrators understand how uploaded knowledge is organized.
        return knowledgeDocumentMapper.countActiveDocumentsByCategory();
    }
    private List<String> splitIntoChunks(String content) {
        String normalizedContent = content == null ? "" : content.trim();
        if (normalizedContent.isBlank()) {
            throw new BusinessException("Document content cannot be blank");
        }

        List<String> chunks = new ArrayList<>();
        List<String> semanticUnits = splitIntoSemanticUnits(normalizedContent);

        StringBuilder currentChunk = new StringBuilder();
        for (String unit : semanticUnits) {
            String cleanedUnit = unit.trim();
            if (cleanedUnit.isBlank()) {
                continue;
            }

            if (estimateTokens(currentChunk.length() + cleanedUnit.length()) <= TARGET_CHUNK_TOKENS) {
                appendUnit(currentChunk, cleanedUnit);
                continue;
            }

            if (!currentChunk.isEmpty()) {
                chunks.add(currentChunk.toString().trim());
                currentChunk.setLength(0);
            }

            if (estimateTokens(cleanedUnit.length()) <= TARGET_CHUNK_TOKENS) {
                appendUnit(currentChunk, cleanedUnit);
                continue;
            }

            chunks.addAll(splitOversizedTopic(cleanedUnit));
        }

        if (!currentChunk.isEmpty()) {
            chunks.add(currentChunk.toString().trim());
        }

        return chunks;
    }

    private List<String> splitIntoSemanticUnits(String content) {
        List<String> units = new ArrayList<>();
        String[] paragraphs = content.split("\\R\\s*\\R");
        StringBuilder currentUnit = new StringBuilder();

        for (String paragraph : paragraphs) {
            String cleanedParagraph = paragraph.trim();
            if (cleanedParagraph.isBlank()) {
                continue;
            }

            if (isHeading(cleanedParagraph) && !currentUnit.isEmpty()) {
                units.add(currentUnit.toString().trim());
                currentUnit.setLength(0);
            }
            appendUnit(currentUnit, cleanedParagraph);
        }

        if (!currentUnit.isEmpty()) {
            units.add(currentUnit.toString().trim());
        }
        return units.isEmpty() ? List.of(content) : units;
    }

    private List<String> splitOversizedTopic(String topic) {
        List<String> chunks = new ArrayList<>();
        StringBuilder currentChunk = new StringBuilder();
        for (String paragraph : topic.split("\\R\\s*\\R")) {
            String cleanedParagraph = paragraph.trim();
            if (cleanedParagraph.isBlank()) {
                continue;
            }

            if (estimateTokens(cleanedParagraph.length()) > TARGET_CHUNK_TOKENS) {
                if (!currentChunk.isEmpty()) {
                    chunks.add(currentChunk.toString().trim());
                    currentChunk.setLength(0);
                }
                chunks.addAll(splitOversizedParagraph(cleanedParagraph));
                continue;
            }

            if (estimateTokens(currentChunk.length() + cleanedParagraph.length()) > TARGET_CHUNK_TOKENS && !currentChunk.isEmpty()) {
                chunks.add(currentChunk.toString().trim());
                currentChunk.setLength(0);
            }
            appendUnit(currentChunk, cleanedParagraph);
        }

        if (!currentChunk.isEmpty()) {
            chunks.add(currentChunk.toString().trim());
        }
        return chunks;
    }

    private List<String> splitOversizedParagraph(String paragraph) {
        List<String> chunks = new ArrayList<>();
        StringBuilder currentChunk = new StringBuilder();
        for (String sentence : paragraph.split("(?<=[.!?])\\s+")) {
            String cleanedSentence = sentence.trim();
            if (cleanedSentence.isBlank()) {
                continue;
            }

            if (estimateTokens(currentChunk.length() + cleanedSentence.length()) > TARGET_CHUNK_TOKENS && !currentChunk.isEmpty()) {
                chunks.add(currentChunk.toString().trim());
                currentChunk.setLength(0);
            }
            appendUnit(currentChunk, cleanedSentence);
        }

        if (!currentChunk.isEmpty()) {
            chunks.add(currentChunk.toString().trim());
        }
        return chunks.isEmpty() ? List.of(paragraph) : chunks;
    }

    private boolean isHeading(String paragraph) {
        return paragraph.matches("^#{1,6}\\s+.+")
                || paragraph.matches("^[A-Z][A-Za-z0-9 /_-]{2,80}:?$");
    }

    private void appendUnit(StringBuilder builder, String unit) {
        if (!builder.isEmpty()) {
            builder.append("\n\n");
        }
        builder.append(unit);
    }

    private int estimateTokens(int characterCount) {
        return (int) Math.ceil((double) Math.max(0, characterCount) / APPROX_CHARS_PER_TOKEN);
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
