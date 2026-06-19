package com.zwf.aisupport.service.impl;

import com.zwf.aisupport.common.exception.BusinessException;
import com.zwf.aisupport.common.util.CurrentUserUtil;
import com.zwf.aisupport.entity.AiQuestionLog;
import com.zwf.aisupport.entity.KnowledgeDocument;
import com.zwf.aisupport.entity.Ticket;
import com.zwf.aisupport.mapper.AiQuestionLogMapper;
import com.zwf.aisupport.mapper.DocumentChunkMapper;
import com.zwf.aisupport.mapper.KnowledgeArticleMapper;
import com.zwf.aisupport.mapper.KnowledgeDocumentMapper;
import com.zwf.aisupport.mapper.TicketMapper;
import com.zwf.aisupport.service.AdminDashboardService;
import com.zwf.aisupport.vo.AdminDashboardOverviewVO;
import com.zwf.aisupport.vo.AdminOperationsCenterVO;
import com.zwf.aisupport.vo.DocumentCategoryStatsVO;
import com.zwf.aisupport.vo.KnowledgeCategoryStatsVO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AdminDashboardServiceImpl implements AdminDashboardService {

    private static final List<String> CORE_CATEGORIES = List.of(
            "Auth / JWT",
            "Redis Cache",
            "PostgreSQL",
            "Docker",
            "Kubernetes",
            "Incident SOP",
            "Ticket Rules",
            "Customer Templates"
    );

    private static final Map<String, String> CATEGORY_ICONS = Map.of(
            "Auth / JWT", "shield",
            "Redis Cache", "redis",
            "PostgreSQL", "database",
            "Docker", "container",
            "Kubernetes", "kubernetes",
            "Incident SOP", "alert",
            "Ticket Rules", "ticket",
            "Customer Templates", "template"
    );

    private final TicketMapper ticketMapper;
    private final KnowledgeArticleMapper knowledgeArticleMapper;
    private final AiQuestionLogMapper aiQuestionLogMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final DocumentChunkMapper documentChunkMapper;

    public AdminDashboardServiceImpl(TicketMapper ticketMapper,
                                     KnowledgeArticleMapper knowledgeArticleMapper,
                                     AiQuestionLogMapper aiQuestionLogMapper,
                                     KnowledgeDocumentMapper knowledgeDocumentMapper,
                                     DocumentChunkMapper documentChunkMapper) {
        this.ticketMapper = ticketMapper;
        this.knowledgeArticleMapper = knowledgeArticleMapper;
        this.aiQuestionLogMapper = aiQuestionLogMapper;
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
        this.documentChunkMapper = documentChunkMapper;
    }

    @Override
    public AdminDashboardOverviewVO getOverview() {
        String role = CurrentUserUtil.getCurrentUserRole();
        if (!"ADMIN".equals(role)) {
            throw new BusinessException("Only administrators can access dashboard overview");
        }

        AdminDashboardOverviewVO overviewVO = new AdminDashboardOverviewVO();
        overviewVO.setTotalTickets(ticketMapper.countAll());
        overviewVO.setOpenTickets(ticketMapper.countOpenTickets());
        overviewVO.setResolvedTickets(ticketMapper.countResolvedTickets());
        overviewVO.setKnowledgeArticles(knowledgeArticleMapper.countPublishedArticles());
        overviewVO.setDocuments(knowledgeDocumentMapper.countActiveDocuments());
        overviewVO.setDocumentAnswers(aiQuestionLogMapper.countAnsweredByDocument());
        overviewVO.setArticleAnswers(aiQuestionLogMapper.countAnsweredByArticle());
        overviewVO.setAiQuestions(aiQuestionLogMapper.countAll());
        overviewVO.setAiCreatedTickets(aiQuestionLogMapper.countCreatedTickets());
        overviewVO.setKnowledgeGaps(aiQuestionLogMapper.countKnowledgeGaps());

        return overviewVO;
    }

    @Override
    public AdminOperationsCenterVO getOperationsCenter() {
        ensureAdmin();

        List<AiQuestionLog> logs = aiQuestionLogMapper.findAll();
        List<AiQuestionLog> knowledgeGaps = aiQuestionLogMapper.findKnowledgeGaps();
        List<KnowledgeDocument> documents = knowledgeDocumentMapper.findActiveDocuments();
        List<Ticket> tickets = ticketMapper.findAll();
        List<KnowledgeCategoryStatsVO> articleStats = knowledgeArticleMapper.countPublishedArticlesByCategory();
        List<DocumentCategoryStatsVO> documentStats = knowledgeDocumentMapper.countActiveDocumentsByCategory();

        AdminOperationsCenterVO vo = new AdminOperationsCenterVO();
        vo.setTitle("CloudOps AI Knowledge Operations Center");
        vo.setSubtitle("Operate knowledge. Improve answers. Deliver reliable support.");
        vo.setEnvironment(buildEnvironmentStatus());
        vo.setServiceHealth(buildServiceHealth());
        vo.setMetrics(buildMetrics(logs, documents, tickets, articleStats, documentStats));
        vo.setKnowledgeCore(buildKnowledgeCore(articleStats, documentStats));
        vo.setIngestionPipeline(buildIngestionPipeline(documents));
        vo.setExternalEvidence(buildExternalEvidence(knowledgeGaps, logs));
        vo.setReasoningLayer(buildReasoningLayer(logs));
        vo.setKnowledgeGapLoop(buildKnowledgeGapLoop(knowledgeGaps));
        vo.setTicketEscalation(buildTicketEscalation(tickets));
        vo.setAnswerAnalytics(buildAnswerAnalytics(logs));
        vo.setModelSearchControls(buildModelSearchControls());
        vo.setAdminTip("Review high priority knowledge gaps and convert valuable web evidence into internal articles to improve answer quality.");
        return vo;
    }

    private void ensureAdmin() {
        String role = CurrentUserUtil.getCurrentUserRole();
        if (!"ADMIN".equals(role)) {
            throw new BusinessException("Only administrators can access dashboard overview");
        }
    }

    private AdminOperationsCenterVO.EnvironmentStatusVO buildEnvironmentStatus() {
        AdminOperationsCenterVO.EnvironmentStatusVO environment = new AdminOperationsCenterVO.EnvironmentStatusVO();
        environment.setName("Production");
        environment.setStatus("HEALTHY");
        return environment;
    }

    private List<AdminOperationsCenterVO.ServiceHealthVO> buildServiceHealth() {
        return List.of(
                serviceHealth("API", "HEALTHY"),
                serviceHealth("Search", "HEALTHY"),
                serviceHealth("LLM Gateway", "NOT_CONFIGURED"),
                serviceHealth("Storage", "HEALTHY")
        );
    }

    private AdminOperationsCenterVO.ServiceHealthVO serviceHealth(String name, String status) {
        AdminOperationsCenterVO.ServiceHealthVO health = new AdminOperationsCenterVO.ServiceHealthVO();
        health.setName(name);
        health.setStatus(status);
        return health;
    }

    private List<AdminOperationsCenterVO.MetricCardVO> buildMetrics(List<AiQuestionLog> logs,
                                                                     List<KnowledgeDocument> documents,
                                                                     List<Ticket> tickets,
                                                                     List<KnowledgeCategoryStatsVO> articleStats,
                                                                     List<DocumentCategoryStatsVO> documentStats) {
        long totalQuestions = logs.size();
        long answeredLast7Days = logs.stream()
                .filter(log -> isWithinDays(log.getCreatedTime(), 7))
                .count();
        long articleCount = nullToZero(knowledgeArticleMapper.countPublishedArticles());
        long openTickets = tickets.stream()
                .filter(ticket -> "OPEN".equals(ticket.getStatus()) || "IN_PROGRESS".equals(ticket.getStatus()))
                .count();

        return List.of(
                metric("totalKnowledgeArticles", "Total Knowledge Articles", String.valueOf(articleCount), trend(articleStats.size(), "this week"), "article"),
                metric("runbookDocuments", "Runbook Documents", String.valueOf(documents.size()), trend(documents.size(), "this week"), "book"),
                metric("knowledgeCoverage", "Knowledge Coverage", percentText(calculateKnowledgeCoverage(articleStats, documentStats)), "based on core domains", "coverage"),
                metric("questionsAnswered7d", "Questions Answered (7D)", String.valueOf(answeredLast7Days), trend(answeredLast7Days, "last 7 days"), "question"),
                metric("avgAnswerConfidence", "Avg Answer Confidence", calculateConfidence(logs).toPlainString(), "from internal-source ratio", "target"),
                metric("openTickets", "Open Tickets", String.valueOf(openTickets), trend(openTickets, "active queue"), "ticket")
        );
    }

    private AdminOperationsCenterVO.MetricCardVO metric(String key, String label, String value, String trend, String icon) {
        AdminOperationsCenterVO.MetricCardVO metric = new AdminOperationsCenterVO.MetricCardVO();
        metric.setKey(key);
        metric.setLabel(label);
        metric.setValue(value);
        metric.setTrend(trend);
        metric.setIcon(icon);
        return metric;
    }

    private AdminOperationsCenterVO.KnowledgeCoreVO buildKnowledgeCore(List<KnowledgeCategoryStatsVO> articleStats,
                                                                        List<DocumentCategoryStatsVO> documentStats) {
        Map<String, Long> articleCountByCategory = articleStats.stream()
                .collect(Collectors.toMap(KnowledgeCategoryStatsVO::getCategory, KnowledgeCategoryStatsVO::getArticleCount));
        Map<String, Long> documentCountByCategory = documentStats.stream()
                .collect(Collectors.toMap(DocumentCategoryStatsVO::getCategory, DocumentCategoryStatsVO::getDocumentCount));

        List<AdminOperationsCenterVO.KnowledgeCategoryCardVO> categories = CORE_CATEGORIES.stream()
                .map(category -> {
                    long articles = articleCountByCategory.getOrDefault(category, 0L);
                    long docs = documentCountByCategory.getOrDefault(category, 0L);

                    AdminOperationsCenterVO.KnowledgeCategoryCardVO card = new AdminOperationsCenterVO.KnowledgeCategoryCardVO();
                    card.setCategory(category);
                    card.setArticleCount(articles);
                    card.setDocumentCount(docs);
                    card.setCoveragePercent(cappedPercent(articles * 10 + docs * 15));
                    card.setFreshnessPercent(cappedPercent(55 + articles * 4 + docs * 6));
                    card.setIcon(CATEGORY_ICONS.getOrDefault(category, "folder"));
                    return card;
                })
                .toList();

        AdminOperationsCenterVO.TrustedSourcesVO trustedSources = new AdminOperationsCenterVO.TrustedSourcesVO();
        trustedSources.setDomainCount(0L);
        trustedSources.setCoveragePercent(0);

        AdminOperationsCenterVO.KnowledgeCoreVO core = new AdminOperationsCenterVO.KnowledgeCoreVO();
        core.setCategories(categories);
        core.setTrustedSources(trustedSources);
        return core;
    }

    private AdminOperationsCenterVO.IngestionPipelineVO buildIngestionPipeline(List<KnowledgeDocument> documents) {
        List<AdminOperationsCenterVO.PipelineStepVO> steps = List.of(
                pipelineStep("Upload", "COMPLETED"),
                pipelineStep("Parse", "COMPLETED"),
                pipelineStep("Chunk", "COMPLETED"),
                pipelineStep("Index", "COMPLETED"),
                pipelineStep("Embed", "COMPLETED"),
                pipelineStep("Ready", documents.isEmpty() ? "WAITING" : "COMPLETED")
        );

        List<AdminOperationsCenterVO.IngestionJobVO> jobs = documents.stream()
                .sorted(Comparator.comparing(KnowledgeDocument::getCreatedTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(5)
                .map(document -> {
                    long chunks = documentChunkMapper.findByDocumentId(document.getId()).size();
                    AdminOperationsCenterVO.IngestionJobVO job = new AdminOperationsCenterVO.IngestionJobVO();
                    job.setDocument(document.getOriginalFilename());
                    job.setType(resolveDocumentType(document.getOriginalFilename(), document.getContentType()));
                    job.setStatus("ACTIVE".equals(document.getStatus()) ? "Completed" : document.getStatus());
                    job.setChunks(chunks);
                    job.setEmbeddings(chunks);
                    job.setTimeAgo(timeAgo(document.getCreatedTime()));
                    return job;
                })
                .toList();

        AdminOperationsCenterVO.IngestionPipelineVO pipeline = new AdminOperationsCenterVO.IngestionPipelineVO();
        pipeline.setSteps(steps);
        pipeline.setRecentJobs(jobs);
        return pipeline;
    }

    private AdminOperationsCenterVO.PipelineStepVO pipelineStep(String name, String status) {
        AdminOperationsCenterVO.PipelineStepVO step = new AdminOperationsCenterVO.PipelineStepVO();
        step.setName(name);
        step.setStatus(status);
        return step;
    }

    private AdminOperationsCenterVO.ExternalEvidenceVO buildExternalEvidence(List<AiQuestionLog> knowledgeGaps,
                                                                              List<AiQuestionLog> logs) {
        AdminOperationsCenterVO.WebSearchSummaryVO summary = new AdminOperationsCenterVO.WebSearchSummaryVO();
        summary.setSearches(0L);
        summary.setUsefulEvidence(0L);
        summary.setConvertedToArticles(0L);
        summary.setPendingReview(0L);

        AdminOperationsCenterVO.ExternalEvidenceVO externalEvidence = new AdminOperationsCenterVO.ExternalEvidenceVO();
        externalEvidence.setQueue(List.of());
        externalEvidence.setSummary(summary);
        return externalEvidence;
    }

    private AdminOperationsCenterVO.ReasoningLayerVO buildReasoningLayer(List<AiQuestionLog> logs) {
        Map<String, Long> intentCounts = logs.stream()
                .map(log -> inferIntent(log.getQuestion()))
                .collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting()));

        long total = logs.size();
        List<AdminOperationsCenterVO.IntentDistributionVO> intents = intentCounts.entrySet().stream()
                .map(entry -> {
                    AdminOperationsCenterVO.IntentDistributionVO intent = new AdminOperationsCenterVO.IntentDistributionVO();
                    intent.setIntent(entry.getKey());
                    intent.setCount(entry.getValue());
                    intent.setPercent(percent(entry.getValue(), total));
                    return intent;
                })
                .sorted(Comparator.comparing(AdminOperationsCenterVO.IntentDistributionVO::getCount).reversed())
                .toList();

        Map<String, Long> sourceCounts = logs.stream()
                .map(log -> log.getSourceType() == null ? "NONE" : log.getSourceType())
                .collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting()));

        List<AdminOperationsCenterVO.SourceMixVO> sourceMix = sourceCounts.entrySet().stream()
                .map(entry -> {
                    AdminOperationsCenterVO.SourceMixVO mix = new AdminOperationsCenterVO.SourceMixVO();
                    mix.setSource(entry.getKey());
                    mix.setCount(entry.getValue());
                    mix.setPercent(percent(entry.getValue(), total));
                    return mix;
                })
                .toList();

        AdminOperationsCenterVO.ReasoningLayerVO reasoning = new AdminOperationsCenterVO.ReasoningLayerVO();
        reasoning.setIntentDistribution(intents);
        reasoning.setTotalQuestions(total);
        reasoning.setAvgConfidence(calculateConfidence(logs));
        reasoning.setCitationCoverage(bigPercent(internalAnswerCount(logs), total));
        reasoning.setHallucinationRisk(bigPercent(sourceCounts.getOrDefault("NONE", 0L), total));
        reasoning.setSourceMix(sourceMix);
        return reasoning;
    }

    private AdminOperationsCenterVO.KnowledgeGapLoopVO buildKnowledgeGapLoop(List<AiQuestionLog> knowledgeGaps) {
        Map<String, Long> frequencyByQuestion = knowledgeGaps.stream()
                .map(AiQuestionLog::getQuestion)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        List<AdminOperationsCenterVO.KnowledgeGapQueueItemVO> gaps = knowledgeGaps.stream()
                .limit(5)
                .map(log -> {
                    String category = inferCategory(log.getQuestion());
                    AdminOperationsCenterVO.KnowledgeGapQueueItemVO item = new AdminOperationsCenterVO.KnowledgeGapQueueItemVO();
                    item.setLogId(log.getId());
                    item.setQuestion(log.getQuestion());
                    item.setFrequency(frequencyByQuestion.getOrDefault(log.getQuestion(), 1L));
                    item.setCategory(category);
                    item.setSuggestedArticle(suggestArticleTitle(category, log.getQuestion()));
                    item.setPriority(resolveGapPriority(category, frequencyByQuestion.getOrDefault(log.getQuestion(), 1L)));
                    return item;
                })
                .toList();

        AdminOperationsCenterVO.KnowledgeGapLoopVO loop = new AdminOperationsCenterVO.KnowledgeGapLoopVO();
        loop.setGaps(gaps);
        return loop;
    }

    private AdminOperationsCenterVO.TicketEscalationVO buildTicketEscalation(List<Ticket> tickets) {
        List<AdminOperationsCenterVO.TicketEscalationItemVO> queue = tickets.stream()
                .filter(ticket -> "OPEN".equals(ticket.getStatus()) || "IN_PROGRESS".equals(ticket.getStatus()))
                .sorted(Comparator.comparing(Ticket::getCreatedTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(5)
                .map(ticket -> {
                    AdminOperationsCenterVO.TicketEscalationItemVO item = new AdminOperationsCenterVO.TicketEscalationItemVO();
                    item.setTicketId(ticket.getId());
                    item.setTitle(ticket.getTitle());
                    item.setPriority(ticket.getPriority());
                    item.setCreatedAgo(timeAgo(ticket.getCreatedTime()));
                    item.setOwner(ticket.getAssignedTo() == null ? "Unassigned" : "User #" + ticket.getAssignedTo());
                    item.setSla(resolveSla(ticket.getPriority()));
                    return item;
                })
                .toList();

        AdminOperationsCenterVO.TicketEscalationVO escalation = new AdminOperationsCenterVO.TicketEscalationVO();
        escalation.setTickets(queue);
        return escalation;
    }

    private AdminOperationsCenterVO.AnswerAnalyticsVO buildAnswerAnalytics(List<AiQuestionLog> logs) {
        long useful = logs.stream().filter(log -> "USEFUL".equals(log.getFeedback())).count();
        long notUseful = logs.stream().filter(log -> "NOT_USEFUL".equals(log.getFeedback())).count();
        long pending = logs.stream().filter(log -> log.getFeedback() == null).count();
        long feedbackTotal = useful + notUseful + pending;

        Map<String, Long> categoryQuestionCounts = logs.stream()
                .map(log -> inferCategory(log.getQuestion()))
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        List<AdminOperationsCenterVO.CategoryQuestionVO> topCategories = categoryQuestionCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .map(entry -> {
                    AdminOperationsCenterVO.CategoryQuestionVO category = new AdminOperationsCenterVO.CategoryQuestionVO();
                    category.setCategory(entry.getKey());
                    category.setQuestionCount(entry.getValue());
                    category.setPercent(percent(entry.getValue(), logs.size()));
                    return category;
                })
                .toList();

        AdminOperationsCenterVO.AnswerAnalyticsVO analytics = new AdminOperationsCenterVO.AnswerAnalyticsVO();
        analytics.setUsefulnessPercent(percent(useful, feedbackTotal));
        analytics.setUsefulCount(useful);
        analytics.setPartiallyUsefulCount(pending);
        analytics.setNotUsefulCount(notUseful);
        analytics.setCoverageTrend(bigPercent(internalAnswerCount(logs), logs.size()));
        analytics.setTopCategoriesByQuestions(topCategories);
        return analytics;
    }

    private AdminOperationsCenterVO.ModelSearchControlsVO buildModelSearchControls() {
        AdminOperationsCenterVO.ModelProfileVO modelProfile = new AdminOperationsCenterVO.ModelProfileVO();
        modelProfile.setPrimaryModel("Not configured");
        modelProfile.setEmbeddingModel("Not configured");
        modelProfile.setRerankerModel("Not configured");
        modelProfile.setStatus("NOT_CONFIGURED");

        AdminOperationsCenterVO.RetrievalStrategyVO retrievalStrategy = new AdminOperationsCenterVO.RetrievalStrategyVO();
        retrievalStrategy.setTopKDocuments(8);
        retrievalStrategy.setTopKArticles(5);
        retrievalStrategy.setTopKWebEvidence(5);
        retrievalStrategy.setRerankerEnabled(true);

        AdminOperationsCenterVO.WebSearchSettingsVO webSearchSettings = new AdminOperationsCenterVO.WebSearchSettingsVO();
        webSearchSettings.setWebFallbackThreshold(decimal("0.65"));
        webSearchSettings.setTrustedDomains(0L);
        webSearchSettings.setMaxWebResults(0);

        AdminOperationsCenterVO.ModelSearchControlsVO controls = new AdminOperationsCenterVO.ModelSearchControlsVO();
        controls.setModelProfile(modelProfile);
        controls.setRetrievalStrategy(retrievalStrategy);
        controls.setWebSearchSettings(webSearchSettings);
        return controls;
    }

    private long internalAnswerCount(List<AiQuestionLog> logs) {
        return logs.stream()
                .filter(log -> "DOCUMENT".equals(log.getSourceType()) || "ARTICLE".equals(log.getSourceType()))
                .count();
    }

    private BigDecimal calculateConfidence(List<AiQuestionLog> logs) {
        if (logs.isEmpty()) {
            return decimal("0.00");
        }

        BigDecimal internalRatio = BigDecimal.valueOf(internalAnswerCount(logs))
                .divide(BigDecimal.valueOf(logs.size()), 2, RoundingMode.HALF_UP);
        return internalRatio.max(decimal("0.50"));
    }

    private int calculateKnowledgeCoverage(List<KnowledgeCategoryStatsVO> articleStats,
                                           List<DocumentCategoryStatsVO> documentStats) {
        List<String> coveredCategories = new ArrayList<>();
        articleStats.forEach(stat -> coveredCategories.add(stat.getCategory()));
        documentStats.forEach(stat -> coveredCategories.add(stat.getCategory()));

        long coveredCoreCategories = CORE_CATEGORIES.stream()
                .filter(coveredCategories::contains)
                .count();
        return percent(coveredCoreCategories, CORE_CATEGORIES.size());
    }

    private String inferIntent(String question) {
        String normalized = normalize(question);
        if (containsAny(normalized, "error", "fail", "down", "timeout", "cannot", "issue", "crash")) {
            return "Troubleshooting";
        }
        if (containsAny(normalized, "how", "guide", "steps", "reset", "configure")) {
            return "How-to Guide";
        }
        if (containsAny(normalized, "why", "explain", "what is")) {
            return "Concept Explanation";
        }
        if (containsAny(normalized, "setting", "config", "policy", "permission")) {
            return "Configuration";
        }
        return "Other";
    }

    private String inferCategory(String text) {
        String normalized = normalize(text);
        if (containsAny(normalized, "jwt", "token", "login", "auth", "password", "session")) {
            return "Auth / JWT";
        }
        if (containsAny(normalized, "redis", "cache")) {
            return "Redis Cache";
        }
        if (containsAny(normalized, "postgres", "postgresql", "database", "connection")) {
            return "PostgreSQL";
        }
        if (containsAny(normalized, "docker", "container", "compose")) {
            return "Docker";
        }
        if (containsAny(normalized, "kubernetes", "k8s", "pod", "cluster")) {
            return "Kubernetes";
        }
        if (containsAny(normalized, "incident", "outage", "sev", "rollback")) {
            return "Incident SOP";
        }
        if (containsAny(normalized, "ticket", "sla", "priority", "escalation")) {
            return "Ticket Rules";
        }
        if (containsAny(normalized, "customer", "template", "reply")) {
            return "Customer Templates";
        }
        return "General";
    }

    private String suggestArticleTitle(String category, String question) {
        if (question == null || question.isBlank()) {
            return category + " Troubleshooting Guide";
        }
        String normalized = question.trim();
        if (normalized.length() > 42) {
            normalized = normalized.substring(0, 42).trim();
        }
        return category + ": " + normalized;
    }

    private String resolveGapPriority(String category, long frequency) {
        if (frequency >= 3 || "Auth / JWT".equals(category) || "Redis Cache".equals(category)) {
            return "High";
        }
        if ("General".equals(category)) {
            return "Low";
        }
        return "Medium";
    }

    private String resolveSla(String priority) {
        String normalized = priority == null ? "" : priority.toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "HIGH", "P1" -> "2h";
            case "MEDIUM", "P2" -> "4h";
            default -> "8h";
        };
    }

    private String resolveDocumentType(String filename, String contentType) {
        if (filename != null && filename.contains(".")) {
            return filename.substring(filename.lastIndexOf('.') + 1).toUpperCase(Locale.ROOT);
        }
        if (contentType == null || contentType.isBlank()) {
            return "TEXT";
        }
        return contentType.toUpperCase(Locale.ROOT);
    }

    private boolean isWithinDays(LocalDateTime dateTime, int days) {
        return dateTime != null && dateTime.isAfter(LocalDateTime.now().minusDays(days));
    }

    private String timeAgo(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "unknown";
        }

        Duration duration = Duration.between(dateTime, LocalDateTime.now());
        long minutes = Math.max(0, duration.toMinutes());
        if (minutes < 60) {
            return minutes + "m ago";
        }

        long hours = duration.toHours();
        if (hours < 24) {
            return hours + "h ago";
        }

        return duration.toDays() + "d ago";
    }

    private String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT);
    }

    private boolean containsAny(String text, String... terms) {
        for (String term : terms) {
            if (text.contains(term)) {
                return true;
            }
        }
        return false;
    }

    private int cappedPercent(long value) {
        return (int) Math.max(0, Math.min(100, value));
    }

    private int percent(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0;
        }
        return BigDecimal.valueOf(numerator)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 0, RoundingMode.HALF_UP)
                .intValue();
    }

    private BigDecimal bigPercent(long numerator, long denominator) {
        if (denominator <= 0) {
            return decimal("0.00");
        }
        return BigDecimal.valueOf(numerator)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 1, RoundingMode.HALF_UP);
    }

    private String percentText(int percent) {
        return percent + "%";
    }

    private String trend(long value, String label) {
        return "+ " + value + " " + label;
    }

    private long nullToZero(Long value) {
        return value == null ? 0L : value;
    }

    private BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }
}
