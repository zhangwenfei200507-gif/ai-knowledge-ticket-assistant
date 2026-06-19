package com.zwf.aisupport.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class AdminOperationsCenterVO {

    private String title;
    private String subtitle;
    private EnvironmentStatusVO environment;
    private List<ServiceHealthVO> serviceHealth;
    private List<MetricCardVO> metrics;
    private KnowledgeCoreVO knowledgeCore;
    private IngestionPipelineVO ingestionPipeline;
    private ExternalEvidenceVO externalEvidence;
    private ReasoningLayerVO reasoningLayer;
    private KnowledgeGapLoopVO knowledgeGapLoop;
    private TicketEscalationVO ticketEscalation;
    private AnswerAnalyticsVO answerAnalytics;
    private ModelSearchControlsVO modelSearchControls;
    private String adminTip;

    @Data
    public static class EnvironmentStatusVO {
        private String name;
        private String status;
    }

    @Data
    public static class ServiceHealthVO {
        private String name;
        private String status;
    }

    @Data
    public static class MetricCardVO {
        private String key;
        private String label;
        private String value;
        private String trend;
        private String trendType;
        private String detail;
        private String icon;
    }

    @Data
    public static class KnowledgeCoreVO {
        private List<KnowledgeCategoryCardVO> categories;
        private TrustedSourcesVO trustedSources;
    }

    @Data
    public static class KnowledgeCategoryCardVO {
        private String category;
        private Long articleCount;
        private Long documentCount;
        private Integer coveragePercent;
        private Integer freshnessPercent;
        private String icon;
    }

    @Data
    public static class TrustedSourcesVO {
        private Long domainCount;
        private Integer coveragePercent;
    }

    @Data
    public static class IngestionPipelineVO {
        private List<PipelineStepVO> steps;
        private List<IngestionJobVO> recentJobs;
    }

    @Data
    public static class PipelineStepVO {
        private String name;
        private String status;
    }

    @Data
    public static class IngestionJobVO {
        private String document;
        private String type;
        private String status;
        private Long chunks;
        private Long embeddings;
        private String timeAgo;
    }

    @Data
    public static class ExternalEvidenceVO {
        private List<WebEvidenceQueueItemVO> queue;
        private WebSearchSummaryVO summary;
    }

    @Data
    public static class WebEvidenceQueueItemVO {
        private Long logId;
        private String queryTopic;
        private String topDomain;
        private BigDecimal credibility;
        private String status;
    }

    @Data
    public static class WebSearchSummaryVO {
        private Long searches;
        private Long usefulEvidence;
        private Long convertedToArticles;
        private Long pendingReview;
    }

    @Data
    public static class ReasoningLayerVO {
        private List<IntentDistributionVO> intentDistribution;
        private Long totalQuestions;
        private BigDecimal avgConfidence;
        private BigDecimal citationCoverage;
        private BigDecimal hallucinationRisk;
        private List<SourceMixVO> sourceMix;
    }

    @Data
    public static class IntentDistributionVO {
        private String intent;
        private Long count;
        private Integer percent;
    }

    @Data
    public static class SourceMixVO {
        private String source;
        private Long count;
        private Integer percent;
    }

    @Data
    public static class KnowledgeGapLoopVO {
        private List<KnowledgeGapQueueItemVO> gaps;
    }

    @Data
    public static class KnowledgeGapQueueItemVO {
        private Long logId;
        private String question;
        private Long frequency;
        private String category;
        private String suggestedArticle;
        private String priority;
    }

    @Data
    public static class TicketEscalationVO {
        private List<TicketEscalationItemVO> tickets;
    }

    @Data
    public static class TicketEscalationItemVO {
        private Long ticketId;
        private String title;
        private String priority;
        private String createdAgo;
        private String owner;
        private String sla;
    }

    @Data
    public static class AnswerAnalyticsVO {
        private Integer usefulnessPercent;
        private Long usefulCount;
        private Long partiallyUsefulCount;
        private Long notUsefulCount;
        private BigDecimal coverageTrend;
        private List<CategoryQuestionVO> topCategoriesByQuestions;
    }

    @Data
    public static class CategoryQuestionVO {
        private String category;
        private Long questionCount;
        private Integer percent;
    }

    @Data
    public static class ModelSearchControlsVO {
        private ModelProfileVO modelProfile;
        private RetrievalStrategyVO retrievalStrategy;
        private WebSearchSettingsVO webSearchSettings;
    }

    @Data
    public static class ModelProfileVO {
        private String primaryModel;
        private String embeddingModel;
        private String rerankerModel;
        private String status;
    }

    @Data
    public static class RetrievalStrategyVO {
        private Integer topKDocuments;
        private Integer topKArticles;
        private Integer topKWebEvidence;
        private Boolean rerankerEnabled;
    }

    @Data
    public static class WebSearchSettingsVO {
        private BigDecimal webFallbackThreshold;
        private Long trustedDomains;
        private Integer maxWebResults;
    }
}
