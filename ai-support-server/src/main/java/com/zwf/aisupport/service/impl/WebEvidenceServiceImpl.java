package com.zwf.aisupport.service.impl;

import com.zwf.aisupport.common.exception.BusinessException;
import com.zwf.aisupport.common.util.CurrentUserUtil;
import com.zwf.aisupport.dto.WebEvidenceSearchDTO;
import com.zwf.aisupport.entity.KnowledgeArticle;
import com.zwf.aisupport.entity.WebEvidence;
import com.zwf.aisupport.mapper.KnowledgeArticleMapper;
import com.zwf.aisupport.mapper.WebEvidenceMapper;
import com.zwf.aisupport.service.WebEvidenceService;
import com.zwf.aisupport.vo.KnowledgeArticleVO;
import com.zwf.aisupport.vo.WebEvidenceVO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
public class WebEvidenceServiceImpl implements WebEvidenceService {

    private final WebSearchClient webSearchClient;
    private final WebEvidenceMapper webEvidenceMapper;
    private final KnowledgeArticleMapper knowledgeArticleMapper;
    private final KnowledgePackReorganizer knowledgePackReorganizer;

    public WebEvidenceServiceImpl(WebSearchClient webSearchClient,
                                  WebEvidenceMapper webEvidenceMapper,
                                  KnowledgeArticleMapper knowledgeArticleMapper,
                                  KnowledgePackReorganizer knowledgePackReorganizer) {
        this.webSearchClient = webSearchClient;
        this.webEvidenceMapper = webEvidenceMapper;
        this.knowledgeArticleMapper = knowledgeArticleMapper;
        this.knowledgePackReorganizer = knowledgePackReorganizer;
    }

    @Override
    public Optional<WebEvidence> searchAndStoreEvidence(String question, Long userId) {
        Optional<WebEvidence> evidence = webSearchClient.search(question, userId);
        evidence.ifPresent(webEvidenceMapper::insert);
        return evidence.map(savedEvidence -> {
            WebEvidence reloaded = webEvidenceMapper.findById(savedEvidence.getId());
            return reloaded == null ? savedEvidence : reloaded;
        });
    }

    @Override
    public WebEvidence storeModelGeneratedEvidence(String question, String answer, Long userId) {
        WebEvidence evidence = new WebEvidence();
        evidence.setQuestion(question);
        evidence.setQuery(question);
        evidence.setTitle(limit("Model Answer: " + question, 300));
        evidence.setUrl(null);
        evidence.setDomain("OpenAI model");
        evidence.setSnippet(limit(answer, 1200));
        evidence.setExtractedContent(answer);
        evidence.setSuggestedArticleTitle(limit("Reviewed Model Answer: " + question, 150));
        evidence.setSuggestedArticleContent(buildModelSuggestedArticle(question, answer));
        evidence.setCategory(inferCategory(question + " " + answer));
        evidence.setCredibilityScore(new BigDecimal("0.70"));
        evidence.setEvidenceOrigin("LLM_GENERATED");
        evidence.setStatus("NEEDS_REVIEW");
        evidence.setCreatedBy(userId);
        webEvidenceMapper.insert(evidence);
        WebEvidence reloaded = webEvidenceMapper.findById(evidence.getId());
        return reloaded == null ? evidence : reloaded;
    }

    @Override
    public WebEvidence updateSuggestedArticle(Long id, String suggestedArticleTitle, String suggestedArticleContent) {
        WebEvidence evidence = requireEvidence(id);
        webEvidenceMapper.updateSuggestedArticle(
                id,
                suggestedArticleTitle == null || suggestedArticleTitle.isBlank()
                        ? evidence.getSuggestedArticleTitle()
                        : suggestedArticleTitle,
                suggestedArticleContent == null || suggestedArticleContent.isBlank()
                        ? evidence.getSuggestedArticleContent()
                        : suggestedArticleContent
        );
        WebEvidence updated = webEvidenceMapper.findById(id);
        return updated == null ? evidence : updated;
    }

    @Override
    public WebEvidenceVO searchManually(WebEvidenceSearchDTO searchDTO) {
        ensureAdmin();
        Long currentUserId = requireCurrentUserId();
        WebEvidence evidence = searchAndStoreEvidence(searchDTO.getQuestion().trim(), currentUserId)
                .orElseThrow(() -> new BusinessException("No reliable external evidence was found for this question"));
        return convertToVO(evidence);
    }

    @Override
    public List<WebEvidenceVO> listAll() {
        ensureAdmin();
        return webEvidenceMapper.findAll().stream()
                .map(this::convertToVO)
                .toList();
    }

    @Override
    public KnowledgeArticleVO approveToArticle(Long id) {
        ensureAdmin();
        Long currentUserId = requireCurrentUserId();
        WebEvidence evidence = requireEvidence(id);
        if ("CONVERTED".equals(evidence.getStatus()) && evidence.getConvertedArticleId() != null) {
            KnowledgeArticle article = knowledgeArticleMapper.findById(evidence.getConvertedArticleId());
            if (article != null) {
                return convertToKnowledgeArticleVO(article);
            }
        }
        if ("REJECTED".equals(evidence.getStatus())) {
            throw new BusinessException("Rejected evidence cannot be converted to an article");
        }
        if (isLowQualityKnowledgeContent(evidence.getSuggestedArticleContent())) {
            throw new BusinessException("Evidence is not useful enough to publish as internal knowledge");
        }

        KnowledgeArticle article = knowledgePackReorganizer.reorganizeOrCreate(
                evidence.getSuggestedArticleTitle(),
                evidence.getCategory(),
                evidence.getSuggestedArticleContent(),
                currentUserId,
                "Reviewed " + (evidence.getEvidenceOrigin() == null ? "external evidence" : evidence.getEvidenceOrigin())
        );
        webEvidenceMapper.markConverted(evidence.getId(), currentUserId, article.getId());
        return convertToKnowledgeArticleVO(article);
    }

    @Override
    public void reject(Long id) {
        ensureAdmin();
        WebEvidence evidence = requireEvidence(id);
        if ("CONVERTED".equals(evidence.getStatus())) {
            throw new BusinessException("Converted evidence cannot be rejected");
        }
        webEvidenceMapper.markRejected(evidence.getId(), requireCurrentUserId());
    }

    private WebEvidence requireEvidence(Long id) {
        WebEvidence evidence = webEvidenceMapper.findById(id);
        if (evidence == null) {
            throw new BusinessException("Web evidence not found");
        }
        return evidence;
    }

    private void ensureAdmin() {
        if (!"ADMIN".equals(CurrentUserUtil.getCurrentUserRole())) {
            throw new BusinessException("Only administrators can manage web evidence");
        }
    }

    private Long requireCurrentUserId() {
        Long currentUserId = CurrentUserUtil.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException("User is not authenticated");
        }
        return currentUserId;
    }

    private WebEvidenceVO convertToVO(WebEvidence evidence) {
        WebEvidenceVO vo = new WebEvidenceVO();
        vo.setId(evidence.getId());
        vo.setQuestion(evidence.getQuestion());
        vo.setQuery(evidence.getQuery());
        vo.setTitle(evidence.getTitle());
        vo.setUrl(evidence.getUrl());
        vo.setDomain(evidence.getDomain());
        vo.setSnippet(evidence.getSnippet());
        vo.setExtractedContent(evidence.getExtractedContent());
        vo.setSuggestedArticleTitle(evidence.getSuggestedArticleTitle());
        vo.setSuggestedArticleContent(evidence.getSuggestedArticleContent());
        vo.setCategory(evidence.getCategory());
        vo.setCredibilityScore(evidence.getCredibilityScore());
        vo.setEvidenceOrigin(evidence.getEvidenceOrigin());
        vo.setStatus(evidence.getStatus());
        vo.setCreatedBy(evidence.getCreatedBy());
        vo.setReviewedBy(evidence.getReviewedBy());
        vo.setConvertedArticleId(evidence.getConvertedArticleId());
        vo.setCreatedTime(evidence.getCreatedTime());
        vo.setReviewedTime(evidence.getReviewedTime());
        return vo;
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

    private String buildModelSuggestedArticle(String question, String answer) {
        String category = inferCategory(question + " " + answer);
        return """
                # Reviewed Model Answer: %s

                Category: %s

                ## Overview
                This draft captures a model-generated fallback after internal knowledge and web search did not produce reliable evidence. It must be verified against official documentation or internal runbooks before it can become trusted knowledge.

                ## Core Answer
                %s

                ## Knowledge Pack Placement
                - Target Knowledge Pack: %s
                - Reorganization policy: update the closest existing Knowledge Pack first; create a new pack only if no suitable pack exists.
                - Chunk policy: rebuild retrieval chunks after approval so each chunk contains one independently understandable knowledge point.

                ## Review Checklist
                - Verify every technical claim against official docs, internal runbooks, or reproducible commands.
                - Add AegisCloud-specific configuration names, dashboards, owners, escalation paths, and version constraints where relevant.
                - Remove generic advice that does not apply to the production environment.
                - Integrate duplicate concepts into the existing article instead of appending repeated text.

                ## References
                - Source type: LLM_GENERATED
                - Source reference: OpenAI model fallback
                - Original question: %s
                """.formatted(limit(question, 120), category, normalizeArticleBullets(answer), category, question);
    }

    private String normalizeArticleBullets(String answer) {
        if (answer == null || answer.isBlank()) {
            return "- Add the reviewed technical answer before publishing.";
        }
        List<String> points = answer.lines()
                .map(String::trim)
                .filter(line -> line.length() >= 12)
                .map(line -> line.replaceAll("^[-*]\\s*", ""))
                .limit(6)
                .map(line -> "- " + limit(line, 260))
                .toList();
        if (points.isEmpty()) {
            return "- " + limit(answer.replaceAll("\\s+", " ").trim(), 260);
        }
        return String.join("\n", points);
    }

    private String inferCategory(String text) {
        String normalized = text == null ? "" : text.toLowerCase();
        if (containsAny(normalized, "jwt", "token", "login", "auth", "password", "session")) return "Auth / JWT";
        if (containsAny(normalized, "redis", "cache")) return "Redis Cache";
        if (containsAny(normalized, "postgres", "postgresql", "database", "sql")) return "PostgreSQL";
        if (containsAny(normalized, "docker", "container", "compose")) return "Docker";
        if (containsAny(normalized, "kubernetes", "k8s", "pod", "cluster")) return "Kubernetes";
        if (containsAny(normalized, "incident", "outage", "sev", "rollback")) return "Incident SOP";
        if (containsAny(normalized, "ticket", "sla", "priority", "escalation")) return "Ticket Rules";
        if (containsAny(normalized, "customer", "template", "reply")) return "Customer Templates";
        return "General";
    }

    private boolean containsAny(String text, String... terms) {
        for (String term : terms) {
            if (text.contains(term)) {
                return true;
            }
        }
        return false;
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength).trim();
    }

    private boolean isLowQualityKnowledgeContent(String content) {
        String normalized = content == null ? "" : content.toLowerCase();
        return normalized.contains("i could not find a reliable answer")
                || normalized.contains("add concrete checks, commands, dashboards, or logs")
                || normalized.contains("add the final reviewed answer here before publishing");
    }
}
