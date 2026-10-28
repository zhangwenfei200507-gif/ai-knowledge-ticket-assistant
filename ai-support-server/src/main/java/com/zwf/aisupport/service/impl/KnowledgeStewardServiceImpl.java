package com.zwf.aisupport.service.impl;

import com.zwf.aisupport.common.exception.BusinessException;
import com.zwf.aisupport.common.util.CurrentUserUtil;
import com.zwf.aisupport.common.util.KnowledgeArticleVoMapper;
import com.zwf.aisupport.common.util.KnowledgeCategoryResolver;
import com.zwf.aisupport.common.util.PageRequestUtil;
import com.zwf.aisupport.common.util.TextUtil;
import com.zwf.aisupport.entity.AiQuestionLog;
import com.zwf.aisupport.entity.DocumentKnowledgeCandidate;
import com.zwf.aisupport.entity.KnowledgeArticle;
import com.zwf.aisupport.entity.KnowledgeStewardTask;
import com.zwf.aisupport.entity.WebEvidence;
import com.zwf.aisupport.mapper.AiQuestionLogMapper;
import com.zwf.aisupport.mapper.DocumentKnowledgeCandidateMapper;
import com.zwf.aisupport.mapper.KnowledgeArticleMapper;
import com.zwf.aisupport.mapper.KnowledgeStewardTaskMapper;
import com.zwf.aisupport.mapper.WebEvidenceMapper;
import com.zwf.aisupport.service.KnowledgeStewardService;
import com.zwf.aisupport.service.LlmGatewayService;
import com.zwf.aisupport.vo.KnowledgeArticleVO;
import com.zwf.aisupport.vo.KnowledgeStewardRunVO;
import com.zwf.aisupport.vo.KnowledgeStewardTaskVO;
import com.zwf.aisupport.vo.PageVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class KnowledgeStewardServiceImpl implements KnowledgeStewardService {

    private static final String STATUS_NEEDS_ADMIN_REVIEW = "NEEDS_ADMIN_REVIEW";
    private static final String ACTION_UPDATE_SECTION = "UPDATE_SECTION";
    private static final String ACTION_ADD_SECTION = "ADD_SECTION";
    private static final String ACTION_REJECT_LOW_QUALITY = "REJECT_LOW_QUALITY";
    private static final String ACTION_NEEDS_MANUAL_CLASSIFICATION = "NEEDS_MANUAL_CLASSIFICATION";

    private final WebEvidenceMapper webEvidenceMapper;
    private final DocumentKnowledgeCandidateMapper documentCandidateMapper;
    private final AiQuestionLogMapper aiQuestionLogMapper;
    private final KnowledgeArticleMapper knowledgeArticleMapper;
    private final KnowledgeStewardTaskMapper taskMapper;
    private final KnowledgePackReorganizer knowledgePackReorganizer;
    private final KnowledgeArticleChunkManager articleChunkManager;
    private final LlmGatewayService llmGatewayService;
    private final boolean llmEnabled;

    public KnowledgeStewardServiceImpl(WebEvidenceMapper webEvidenceMapper,
                                       DocumentKnowledgeCandidateMapper documentCandidateMapper,
                                       AiQuestionLogMapper aiQuestionLogMapper,
                                       KnowledgeArticleMapper knowledgeArticleMapper,
                                       KnowledgeStewardTaskMapper taskMapper,
                                       KnowledgePackReorganizer knowledgePackReorganizer,
                                       KnowledgeArticleChunkManager articleChunkManager,
                                       LlmGatewayService llmGatewayService,
                                       @Value("${app.ai.steward.llm-enabled:true}") boolean llmEnabled) {
        this.webEvidenceMapper = webEvidenceMapper;
        this.documentCandidateMapper = documentCandidateMapper;
        this.aiQuestionLogMapper = aiQuestionLogMapper;
        this.knowledgeArticleMapper = knowledgeArticleMapper;
        this.taskMapper = taskMapper;
        this.knowledgePackReorganizer = knowledgePackReorganizer;
        this.articleChunkManager = articleChunkManager;
        this.llmGatewayService = llmGatewayService;
        this.llmEnabled = llmEnabled;
    }

    @Override
    public KnowledgeStewardRunVO run(Integer limit) {
        CurrentUserUtil.requireAdmin("Only administrators can run the Knowledge Steward");
        Long currentUserId = CurrentUserUtil.requireCurrentUserId();
        int safeLimit = Math.max(1, Math.min(limit == null ? 20 : limit, 50));

        KnowledgeStewardRunVO run = new KnowledgeStewardRunVO();
        for (WebEvidence evidence : webEvidenceMapper.findPendingReviewLimited(safeLimit)) {
            run.setScannedWebEvidence(run.getScannedWebEvidence() + 1);
            createTaskIfUseful(buildCandidate(evidence), currentUserId, run);
        }
        for (DocumentKnowledgeCandidate candidate : documentCandidateMapper.findPendingReviewLimited(safeLimit)) {
            run.setScannedDocumentCandidates(run.getScannedDocumentCandidates() + 1);
            createTaskIfUseful(buildCandidate(candidate), currentUserId, run);
        }
        for (AiQuestionLog log : aiQuestionLogMapper.findStewardCandidates(safeLimit)) {
            run.setScannedQuestionLogs(run.getScannedQuestionLogs() + 1);
            createTaskIfUseful(buildCandidate(log), currentUserId, run);
        }
        return run;
    }

    @Override
    public PageVO<KnowledgeStewardTaskVO> listTasks(String keyword,
                                                    String status,
                                                    String sourceType,
                                                    String targetDomain,
                                                    Integer page,
                                                    Integer size) {
        CurrentUserUtil.requireAdmin("Only administrators can access Knowledge Steward tasks");
        int safePage = PageRequestUtil.page(page);
        int safeSize = PageRequestUtil.size(size);
        List<KnowledgeStewardTaskVO> items = taskMapper.findPage(
                        PageRequestUtil.optionalText(keyword),
                        PageRequestUtil.optionalText(status),
                        PageRequestUtil.optionalText(sourceType),
                        PageRequestUtil.optionalText(targetDomain),
                        PageRequestUtil.offset(safePage, safeSize),
                        safeSize
                )
                .stream()
                .map(this::toVO)
                .toList();
        long total = taskMapper.countPage(
                PageRequestUtil.optionalText(keyword),
                PageRequestUtil.optionalText(status),
                PageRequestUtil.optionalText(sourceType),
                PageRequestUtil.optionalText(targetDomain)
        );
        return PageVO.of(items, safePage, safeSize, total);
    }

    @Override
    public KnowledgeStewardTaskVO updateTask(Long id, KnowledgeStewardTaskVO updateVO) {
        CurrentUserUtil.requireAdmin("Only administrators can edit Knowledge Steward tasks");
        KnowledgeStewardTask task = requireTask(id);
        if (!STATUS_NEEDS_ADMIN_REVIEW.equals(task.getStatus())) {
            throw new BusinessException("Only pending steward tasks can be edited");
        }
        task.setProposalTitle(nonBlank(updateVO.getProposalTitle(), task.getProposalTitle()));
        task.setProposalContent(nonBlank(updateVO.getProposalContent(), task.getProposalContent()));
        task.setTargetDomain(nonBlank(updateVO.getTargetDomain(), task.getTargetDomain()));
        task.setTargetArticleId(updateVO.getTargetArticleId());
        task.setTargetArticleTitle(updateVO.getTargetArticleTitle());
        task.setTargetSection(nonBlank(updateVO.getTargetSection(), task.getTargetSection()));
        task.setRecommendedAction(nonBlank(updateVO.getRecommendedAction(), task.getRecommendedAction()));
        task.setWarnings(updateVO.getWarnings());
        taskMapper.updateProposal(task);
        return toVO(taskMapper.findById(id));
    }

    @Override
    @Transactional
    public KnowledgeArticleVO approve(Long id) {
        CurrentUserUtil.requireAdmin("Only administrators can approve Knowledge Steward tasks");
        Long currentUserId = CurrentUserUtil.requireCurrentUserId();
        KnowledgeStewardTask task = requireTask(id);
        if (!STATUS_NEEDS_ADMIN_REVIEW.equals(task.getStatus())) {
            throw new BusinessException("Only pending steward tasks can be approved");
        }
        if (ACTION_REJECT_LOW_QUALITY.equals(task.getRecommendedAction())
                || ACTION_NEEDS_MANUAL_CLASSIFICATION.equals(task.getRecommendedAction())) {
            throw new BusinessException("This task must be edited or rejected before approval");
        }
        if (isUnsafeProposal(task.getProposalContent())) {
            throw new BusinessException("The proposal still contains raw evidence or review artifacts");
        }

        KnowledgeArticle article = task.getTargetArticleId() == null
                ? knowledgePackReorganizer.reorganizeOrCreate(
                task.getProposalTitle(),
                task.getTargetDomain(),
                task.getProposalContent(),
                currentUserId,
                "Knowledge Steward approved " + task.getSourceType()
        )
                : knowledgePackReorganizer.reorganizeExistingById(
                task.getTargetArticleId(),
                task.getProposalTitle(),
                task.getTargetDomain(),
                task.getProposalContent(),
                "Knowledge Steward approved " + task.getSourceType()
        );
        articleChunkManager.rebuildArticleChunks(article);
        taskMapper.markApproved(task.getId(), currentUserId, article.getId());
        return KnowledgeArticleVoMapper.toVO(article);
    }

    @Override
    public void reject(Long id) {
        CurrentUserUtil.requireAdmin("Only administrators can reject Knowledge Steward tasks");
        KnowledgeStewardTask task = requireTask(id);
        if (!STATUS_NEEDS_ADMIN_REVIEW.equals(task.getStatus())) {
            throw new BusinessException("Only pending steward tasks can be rejected");
        }
        taskMapper.markRejected(task.getId(), CurrentUserUtil.requireCurrentUserId());
    }

    @Override
    public void archive(Long id) {
        CurrentUserUtil.requireAdmin("Only administrators can archive Knowledge Steward tasks");
        KnowledgeStewardTask task = requireTask(id);
        if ("APPROVED".equals(task.getStatus())) {
            throw new BusinessException("Approved tasks are part of the knowledge audit trail");
        }
        taskMapper.markArchived(task.getId(), CurrentUserUtil.requireCurrentUserId());
    }

    private void createTaskIfUseful(StewardCandidate candidate, Long currentUserId, KnowledgeStewardRunVO run) {
        if (candidate == null || candidate.sourceId() == null) {
            run.setSkippedLowQuality(run.getSkippedLowQuality() + 1);
            return;
        }
        if (taskMapper.countActiveBySource(candidate.sourceType(), candidate.sourceId()) > 0) {
            run.setSkippedExistingTasks(run.getSkippedExistingTasks() + 1);
            return;
        }

        KnowledgeStewardTask task = buildRuleBasedTask(candidate, currentUserId);
        if (ACTION_REJECT_LOW_QUALITY.equals(task.getRecommendedAction())) {
            run.setSkippedLowQuality(run.getSkippedLowQuality() + 1);
            return;
        }
        if (llmEnabled && llmGatewayService.isConfigured() && shouldUseLlm(task)) {
            llmGatewayService.generateKnowledgeStewardProposal(buildStewardPromptInput(candidate, task))
                    .map(this::cleanProposal)
                    .filter(proposal -> !proposal.isBlank())
                    .ifPresent(proposal -> {
                        task.setProposalContent(proposal);
                        task.setReasoning("Generated by Knowledge Steward LLM after rule-based filtering.");
                    });
            run.setLlmCallsAllowed(run.getLlmCallsAllowed() + 1);
        }
        taskMapper.insert(task);
        run.setCreatedTasks(run.getCreatedTasks() + 1);
    }

    private KnowledgeStewardTask buildRuleBasedTask(StewardCandidate candidate, Long currentUserId) {
        String category = KnowledgeCategoryResolver.infer(candidate.category(), candidate.combinedText());
        KnowledgeArticle target = findBestTarget(category, candidate.combinedText()).orElse(null);
        BigDecimal quality = sourceQuality(candidate);

        KnowledgeStewardTask task = new KnowledgeStewardTask();
        task.setSourceType(candidate.sourceType());
        task.setSourceId(candidate.sourceId());
        task.setSourceOrigin(candidate.sourceOrigin());
        task.setSourceTitle(TextUtil.limit(candidate.title(), 300));
        task.setSourceQuestion(candidate.question());
        task.setSourceExcerpt(TextUtil.limit(cleanEvidence(candidate.content()), 1200));
        task.setTargetDomain(category);
        task.setTargetArticleId(target == null ? null : target.getId());
        task.setTargetArticleTitle(target == null ? null : target.getTitle());
        task.setTargetSection(suggestSection(candidate.combinedText()));
        task.setDomainConfidence(confidence(category, candidate.combinedText()));
        task.setCoverageConfidence(new BigDecimal(target == null ? "0.60" : "0.78"));
        task.setSourceQuality(quality);
        task.setDuplicateRisk(new BigDecimal(target == null ? "0.20" : "0.55"));
        task.setRecommendedAction(recommendedAction(candidate, target, quality));
        task.setProposalTitle(TextUtil.limit(buildProposalTitle(category, candidate.title(), candidate.question()), 300));
        task.setProposalContent(buildLocalProposal(candidate));
        task.setReasoning(buildReason(candidate, target, quality));
        task.setWarnings(buildWarnings(candidate, target, quality));
        task.setStatus(STATUS_NEEDS_ADMIN_REVIEW);
        task.setCreatedBy(currentUserId);
        return task;
    }

    private StewardCandidate buildCandidate(WebEvidence evidence) {
        return new StewardCandidate(
                "WEB_EVIDENCE",
                evidence.getId(),
                evidence.getEvidenceOrigin(),
                evidence.getSuggestedArticleTitle() == null ? evidence.getTitle() : evidence.getSuggestedArticleTitle(),
                evidence.getQuestion(),
                evidence.getSuggestedArticleContent() == null ? evidence.getExtractedContent() : evidence.getSuggestedArticleContent(),
                evidence.getCategory(),
                evidence.getDomain()
        );
    }

    private StewardCandidate buildCandidate(DocumentKnowledgeCandidate candidate) {
        return new StewardCandidate(
                "DOCUMENT_CANDIDATE",
                candidate.getId(),
                "DOCUMENT",
                candidate.getTitle(),
                null,
                candidate.getContent(),
                candidate.getCategory(),
                null
        );
    }

    private StewardCandidate buildCandidate(AiQuestionLog log) {
        return new StewardCandidate(
                "AI_ANSWER_LOG",
                log.getId(),
                log.getSourceType(),
                log.getSourceTitle(),
                log.getQuestion(),
                log.getAnswer(),
                KnowledgeCategoryResolver.infer(log.getQuestion() + " " + log.getAnswer()),
                null
        );
    }

    private Optional<KnowledgeArticle> findBestTarget(String category, String text) {
        String normalizedText = TextUtil.normalizeSearchText(text);
        return knowledgeArticleMapper.findPublishedArticles().stream()
                .filter(article -> category != null && category.equalsIgnoreCase(article.getCategory()))
                .map(article -> new ArticleMatch(article, scoreArticle(article, normalizedText)))
                .filter(match -> match.score() >= 2)
                .max(Comparator.comparingInt(ArticleMatch::score))
                .map(ArticleMatch::article);
    }

    private int scoreArticle(KnowledgeArticle article, String normalizedText) {
        int score = 0;
        String articleText = TextUtil.normalizeSearchText(article.getTitle() + " " + article.getContent());
        for (String term : normalizedText.split("[^a-z0-9+#./_-]+")) {
            if (term.length() >= 5 && articleText.contains(term)) {
                score++;
            }
        }
        return score;
    }

    private record ArticleMatch(KnowledgeArticle article, int score) {
    }

    private String recommendedAction(StewardCandidate candidate, KnowledgeArticle target, BigDecimal quality) {
        if (quality.compareTo(new BigDecimal("0.45")) < 0) {
            return ACTION_REJECT_LOW_QUALITY;
        }
        if ("General".equalsIgnoreCase(KnowledgeCategoryResolver.infer(candidate.category(), candidate.combinedText()))) {
            return ACTION_NEEDS_MANUAL_CLASSIFICATION;
        }
        return target == null ? ACTION_ADD_SECTION : ACTION_UPDATE_SECTION;
    }

    private boolean shouldUseLlm(KnowledgeStewardTask task) {
        return !ACTION_REJECT_LOW_QUALITY.equals(task.getRecommendedAction())
                && task.getSourceQuality().compareTo(new BigDecimal("0.65")) >= 0;
    }

    private BigDecimal sourceQuality(StewardCandidate candidate) {
        String text = TextUtil.safeLower(candidate.combinedText());
        BigDecimal score = new BigDecimal("0.65");
        if (TextUtil.containsAny(text, "official", "docs.", "kubernetes.io", "docker.com", "postgresql.org", "redis.io", "spring.io")) {
            score = score.add(new BigDecimal("0.20"));
        }
        if (TextUtil.containsAny(text, "advertisement", "sponsored", "course", "bootcamp", "pricing", "sign up", "enroll")) {
            score = score.subtract(new BigDecimal("0.35"));
        }
        if (candidate.content() == null || candidate.content().length() < 120) {
            score = score.subtract(new BigDecimal("0.25"));
        }
        return score.max(BigDecimal.ZERO).min(BigDecimal.ONE);
    }

    private BigDecimal confidence(String category, String text) {
        if (category == null || "General".equalsIgnoreCase(category)) {
            return new BigDecimal("0.35");
        }
        String normalized = TextUtil.safeLower(text);
        return normalized.contains(category.toLowerCase(Locale.ROOT).split(" ")[0])
                ? new BigDecimal("0.88")
                : new BigDecimal("0.72");
    }

    private String suggestSection(String text) {
        String normalized = TextUtil.safeLower(text);
        if (TextUtil.containsAny(normalized, "error", "failure", "timeout", "diagnose", "troubleshoot", "check")) {
            return "Troubleshooting";
        }
        if (TextUtil.containsAny(normalized, "configure", "setting", "yaml", "property", "environment")) {
            return "Configuration";
        }
        if (TextUtil.containsAny(normalized, "should", "avoid", "recommend", "best practice")) {
            return "Best Practices";
        }
        return "Core Concepts";
    }

    private String buildLocalProposal(StewardCandidate candidate) {
        String section = suggestSection(candidate.combinedText());
        return ("## " + section + "\n\n" + formatKnowledgeBullets(cleanEvidence(candidate.content()))).trim();
    }

    private String formatKnowledgeBullets(String content) {
        List<String> points = cleanEvidence(content).lines()
                .map(String::trim)
                .filter(line -> line.length() >= 24)
                .filter(line -> !isUnsafeProposal(line))
                .limit(6)
                .map(line -> "- " + TextUtil.limit(line.replaceAll("^[-*]\\s*", ""), 260))
                .toList();
        if (points.isEmpty()) {
            return "- Add a concise, verified technical explanation before approval.";
        }
        return String.join("\n", points);
    }

    private String cleanEvidence(String content) {
        if (content == null) {
            return "";
        }
        return content
                .replaceAll("(?im)^\\s*#{1,6}\\s*", "")
                .replaceAll("(?im)^\\s*(Title|Purpose|Knowledge Candidate|Reviewed Model Answer|Reviewed Web Answer|Original question|Observed answer|Review checklist)\\s*:?.*$", "")
                .replaceAll("(?im)^\\s*-?\\s*https?://\\S+\\s*$", "")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private String cleanProposal(String proposal) {
        String cleaned = cleanEvidence(proposal);
        int changeStart = cleaned.toLowerCase(Locale.ROOT).indexOf("proposed article change:");
        if (changeStart >= 0) {
            cleaned = cleaned.substring(changeStart + "proposed article change:".length());
            int nextField = cleaned.toLowerCase(Locale.ROOT).indexOf("chunk regeneration notes:");
            if (nextField >= 0) {
                cleaned = cleaned.substring(0, nextField);
            }
        }
        return cleanEvidence(cleaned)
                .replaceAll("(?im)^\\s*Source references\\s*:?.*$", "")
                .trim();
    }

    private boolean isUnsafeProposal(String content) {
        String normalized = TextUtil.safeLower(content);
        return TextUtil.containsAny(normalized,
                "knowledge candidate", "reviewed model answer", "reviewed web answer",
                "original question", "observed answer", "review checklist",
                "advertisement", "sponsored", "course curriculum", "javascript", "window.", "document.");
    }

    private String buildStewardPromptInput(StewardCandidate candidate, KnowledgeStewardTask task) {
        String targetExcerpt = task.getTargetArticleId() == null
                ? "No existing article selected."
                : knowledgeArticleMapper.findById(task.getTargetArticleId()).getContent();
        return """
                Source type: %s
                Source origin: %s
                Source title: %s
                Source question: %s
                Evidence domain: %s
                Target domain: %s
                Target section: %s
                Target article excerpt:
                %s

                Evidence to review:
                %s
                """.formatted(
                candidate.sourceType(),
                candidate.sourceOrigin(),
                candidate.title(),
                candidate.question(),
                candidate.category(),
                task.getTargetDomain(),
                task.getTargetSection(),
                TextUtil.limit(targetExcerpt, 1800),
                TextUtil.limit(cleanEvidence(candidate.content()), 2600)
        );
    }

    private String buildProposalTitle(String category, String title, String question) {
        String base = title == null || title.isBlank() ? question : title;
        if (base == null || base.isBlank()) {
            base = category + " Knowledge Update";
        }
        return category + ": " + TextUtil.limit(base
                .replaceAll("(?i)^(reviewed|external evidence|document insight)\\s*:?\\s*", "")
                .replaceAll("(?i)\\b(runbooks?|playbooks?|sops?)\\b", "")
                .replaceAll("\\s+", " ")
                .trim(), 180);
    }

    private String buildReason(StewardCandidate candidate, KnowledgeArticle target, BigDecimal quality) {
        return "Source " + candidate.sourceType() + " has quality " + quality
                + " and maps to " + (target == null ? "a new or admin-selected Knowledge Pack" : target.getTitle()) + ".";
    }

    private String buildWarnings(StewardCandidate candidate, KnowledgeArticle target, BigDecimal quality) {
        if (quality.compareTo(new BigDecimal("0.60")) < 0) {
            return "Source quality is moderate. Administrator should verify accuracy before approval.";
        }
        if (target == null) {
            return "No strong existing target was found. Confirm whether this should update an existing pack before creating new knowledge.";
        }
        return "";
    }

    private KnowledgeStewardTask requireTask(Long id) {
        KnowledgeStewardTask task = taskMapper.findById(id);
        if (task == null) {
            throw new BusinessException("Knowledge Steward task not found");
        }
        return task;
    }

    private String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private KnowledgeStewardTaskVO toVO(KnowledgeStewardTask task) {
        KnowledgeStewardTaskVO vo = new KnowledgeStewardTaskVO();
        vo.setId(task.getId());
        vo.setSourceType(task.getSourceType());
        vo.setSourceId(task.getSourceId());
        vo.setSourceOrigin(task.getSourceOrigin());
        vo.setSourceTitle(task.getSourceTitle());
        vo.setSourceQuestion(task.getSourceQuestion());
        vo.setSourceExcerpt(task.getSourceExcerpt());
        vo.setRecommendedAction(task.getRecommendedAction());
        vo.setTargetDomain(task.getTargetDomain());
        vo.setTargetArticleId(task.getTargetArticleId());
        vo.setTargetArticleTitle(task.getTargetArticleTitle());
        vo.setTargetSection(task.getTargetSection());
        vo.setDomainConfidence(task.getDomainConfidence());
        vo.setCoverageConfidence(task.getCoverageConfidence());
        vo.setSourceQuality(task.getSourceQuality());
        vo.setDuplicateRisk(task.getDuplicateRisk());
        vo.setProposalTitle(task.getProposalTitle());
        vo.setProposalContent(task.getProposalContent());
        vo.setReasoning(task.getReasoning());
        vo.setWarnings(task.getWarnings());
        vo.setStatus(task.getStatus());
        vo.setCreatedBy(task.getCreatedBy());
        vo.setReviewedBy(task.getReviewedBy());
        vo.setConvertedArticleId(task.getConvertedArticleId());
        vo.setCreatedTime(task.getCreatedTime());
        vo.setReviewedTime(task.getReviewedTime());
        return vo;
    }

    private record StewardCandidate(String sourceType,
                                    Long sourceId,
                                    String sourceOrigin,
                                    String title,
                                    String question,
                                    String content,
                                    String category,
                                    String sourceDomain) {

        private String combinedText() {
            return String.join(" ",
                    title == null ? "" : title,
                    question == null ? "" : question,
                    content == null ? "" : content,
                    category == null ? "" : category,
                    sourceDomain == null ? "" : sourceDomain
            );
        }
    }
}
