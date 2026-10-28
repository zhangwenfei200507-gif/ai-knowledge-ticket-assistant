package com.zwf.aisupport.service.impl;

import com.zwf.aisupport.common.util.TextUtil;
import com.zwf.aisupport.common.util.KnowledgeCategoryResolver;
import com.zwf.aisupport.vo.AiAnswerSourceVO;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class KnowledgeCoverageEvaluator {

    private static final Pattern WORD_SPLIT_PATTERN = Pattern.compile("[^a-z0-9+#./_-]+");
    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "the", "and", "or", "in", "on", "at", "to", "for", "from", "with", "of", "by"
    );

    public Coverage evaluate(String sourceText, AiQueryPlan plan) {
        String normalizedSource = TextUtil.safeLower(sourceText);
        if (normalizedSource.isBlank() || plan.requiredConcepts().isEmpty()) {
            return new Coverage(List.of(), plan.requiredConcepts(), plan.anchorConcepts(), 0);
        }

        List<String> covered = plan.requiredConcepts().stream()
                .filter(concept -> matchesConcept(normalizedSource, concept))
                .distinct()
                .toList();
        List<String> missing = plan.requiredConcepts().stream()
                .filter(concept -> !covered.contains(concept))
                .distinct()
                .toList();
        List<String> missingAnchors = plan.anchorConcepts().stream()
                .filter(concept -> !matchesConcept(normalizedSource, concept))
                .distinct()
                .toList();
        double score = (double) covered.size() / (double) plan.requiredConcepts().size();
        return new Coverage(covered, missing, missingAnchors, score);
    }

    public boolean isSufficient(String sourceText, AiQueryPlan plan) {
        return evaluate(sourceText, plan).sufficient();
    }

    /**
     * Evaluates the selected evidence as a set. A cross-domain question is complete only when
     * every requested domain contributes evidence and the combined evidence covers the concepts.
     */
    public EvidenceCoverage evaluateEvidence(List<AiAnswerSourceVO> sources, AiQueryPlan plan) {
        List<AiAnswerSourceVO> safeSources = sources == null ? List.of() : sources.stream()
                .filter(source -> source != null && source.getPreviewContent() != null && !source.getPreviewContent().isBlank())
                .toList();
        String combinedEvidence = safeSources.stream()
                .map(AiAnswerSourceVO::getPreviewContent)
                .distinct()
                .reduce((left, right) -> left + "\n\n" + right)
                .orElse("");
        Coverage conceptCoverage = evaluate(combinedEvidence, plan);

        Map<String, DomainCoverage> domains = new LinkedHashMap<>();
        for (String domain : plan.domainCandidates()) {
            List<AiAnswerSourceVO> domainSources = safeSources.stream()
                    .filter(source -> sourceCoversDomain(source, domain))
                    .toList();
            String domainEvidence = domainSources.stream()
                    .map(AiAnswerSourceVO::getPreviewContent)
                    .distinct()
                    .reduce((left, right) -> left + "\n\n" + right)
                    .orElse("");
            Coverage domainConcepts = evaluate(domainEvidence, plan);
            domains.put(domain, new DomainCoverage(
                    domain,
                    !domainSources.isEmpty(),
                    domainSources.size(),
                    domainConcepts.coveredConcepts(),
                    domainConcepts.score()
            ));
        }

        List<String> missingDomains = domains.values().stream()
                .filter(domain -> !domain.evidencePresent())
                .map(DomainCoverage::domain)
                .toList();
        boolean domainsCovered = plan.domainCandidates().isEmpty() || missingDomains.isEmpty();
        CoverageLevel level;
        if (!safeSources.isEmpty() && domainsCovered && conceptCoverage.sufficient()) {
            level = CoverageLevel.FULL;
        } else if (!safeSources.isEmpty()
                && (!conceptCoverage.coveredConcepts().isEmpty()
                || domains.values().stream().anyMatch(DomainCoverage::evidencePresent))) {
            level = CoverageLevel.PARTIAL;
        } else {
            level = CoverageLevel.NONE;
        }
        return new EvidenceCoverage(level, conceptCoverage, domains, missingDomains, safeSources.size());
    }

    /**
     * Candidate admission is intentionally weaker than final sufficiency. Each source only has to
     * contribute evidence to its own domain; final coverage is decided after sources are combined.
     */
    public boolean contributesEvidence(String sourceText, String sourceDomain, AiQueryPlan plan) {
        if (sourceText == null || sourceText.isBlank()) {
            return false;
        }
        Coverage coverage = evaluate(sourceText, plan);
        boolean domainRelevant = plan.domainCandidates().isEmpty()
                || plan.domainCandidates().stream().anyMatch(domain -> domainMatches(sourceDomain, domain)
                || domainMatches(KnowledgeCategoryResolver.infer(sourceText), domain));
        return domainRelevant
                && (!coverage.coveredConcepts().isEmpty()
                || plan.domainCandidates().stream().anyMatch(domain -> domainMatches(sourceDomain, domain)
                || domainMatches(KnowledgeCategoryResolver.infer(sourceText), domain)));
    }

    private boolean sourceCoversDomain(AiAnswerSourceVO source, String domain) {
        return domainMatches(source.getSourceDomain(), domain)
                || domainMatches(KnowledgeCategoryResolver.infer(source.getPreviewContent()), domain);
    }

    private boolean domainMatches(String sourceDomain, String requestedDomain) {
        if (sourceDomain == null || requestedDomain == null) {
            return false;
        }
        String source = TextUtil.safeLower(sourceDomain).replaceAll("[^a-z0-9]+", " ").trim();
        String requested = TextUtil.safeLower(requestedDomain).replaceAll("[^a-z0-9]+", " ").trim();
        return !source.isBlank() && !requested.isBlank()
                && (source.equals(requested) || source.contains(requested) || requested.contains(source));
    }

    public boolean matchesConcept(String normalizedSource, String concept) {
        if (normalizedSource == null || concept == null || concept.isBlank()) {
            return false;
        }
        String normalizedConcept = TextUtil.safeLower(concept);
        if (normalizedSource.contains(normalizedConcept)
                || matchesSingularPlural(normalizedSource, normalizedConcept)
                || matchesCommonWordVariant(normalizedSource, normalizedConcept)) {
            return true;
        }
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

    /**
     * Search text often uses a grammatical form different from the query (connect/connection,
     * fail/failure, run/running). Treat those forms as the same concept for coverage evaluation;
     * this prevents correct evidence from being discarded before answer composition.
     */
    private boolean matchesCommonWordVariant(String source, String concept) {
        Map<String, Set<String>> variants = Map.of(
                "connect", Set.of("connection", "connected", "connecting"),
                "connection", Set.of("connect", "connected", "connecting"),
                "fail", Set.of("fails", "failed", "failure", "failing"),
                "failure", Set.of("fail", "fails", "failed", "failing"),
                "run", Set.of("runs", "running", "ran"),
                "running", Set.of("run", "runs", "ran"),
                "use", Set.of("uses", "used", "using"),
                "build", Set.of("builds", "building", "built")
        );
        return variants.getOrDefault(concept, Set.of()).stream().anyMatch(source::contains);
    }

    private boolean matchesSingularPlural(String source, String concept) {
        if (concept.length() <= 3) {
            return false;
        }
        return concept.endsWith("s")
                ? source.contains(concept.substring(0, concept.length() - 1))
                : source.contains(concept + "s");
    }

    public record Coverage(List<String> coveredConcepts,
                           List<String> missingConcepts,
                           List<String> missingAnchors,
                           double score) {
        public boolean sufficient() {
            boolean anchorsCovered = missingAnchors.isEmpty()
                    || (coveredConcepts.size() >= 4 && missingAnchors.size() == 1 && score >= 0.75);
            return anchorsCovered && (missingConcepts.isEmpty() || score >= 0.75);
        }
    }

    public enum CoverageLevel {
        FULL,
        PARTIAL,
        NONE
    }

    public record DomainCoverage(String domain,
                                 boolean evidencePresent,
                                 int sourceCount,
                                 List<String> coveredConcepts,
                                 double conceptScore) {
    }

    public record EvidenceCoverage(CoverageLevel level,
                                   Coverage conceptCoverage,
                                   Map<String, DomainCoverage> domains,
                                   List<String> missingDomains,
                                   int sourceCount) {
        public boolean sufficient() {
            return level == CoverageLevel.FULL;
        }
    }
}
