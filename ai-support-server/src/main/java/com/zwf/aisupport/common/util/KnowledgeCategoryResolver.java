package com.zwf.aisupport.common.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class KnowledgeCategoryResolver {

    private static final Map<String, Map<String, Integer>> DOMAIN_SIGNALS = domainSignals();

    private KnowledgeCategoryResolver() {
    }

    public static String infer(String text) {
        return infer("General", text);
    }

    public static String infer(String fallbackCategory, String text) {
        List<DomainMatch> matches = rankDomains(text);
        if (matches.isEmpty()) {
            return normalizeFallback(fallbackCategory);
        }

        DomainMatch first = matches.get(0);
        if (matches.size() > 1 && first.score() == matches.get(1).score()) {
            return normalizeFallback(fallbackCategory);
        }
        return first.category();
    }

    /**
     * Returns every explicitly supported domain in descending confidence order.
     * Retrieval uses the whole set so a legitimate cross-domain question is not
     * forced into whichever keyword happened to be checked first.
     */
    public static List<String> inferCandidates(String text) {
        return rankDomains(text).stream()
                .filter(match -> match.score() >= 3)
                .map(DomainMatch::category)
                .toList();
    }

    private static List<DomainMatch> rankDomains(String text) {
        String normalized = " " + TextUtil.normalizeSearchText(text)
                .replaceAll("[^a-z0-9+#/_-]+", " ") + " ";
        if (normalized.isBlank()) {
            return List.of();
        }

        List<DomainMatch> matches = new ArrayList<>();
        DOMAIN_SIGNALS.forEach((category, signals) -> {
            int score = signals.entrySet().stream()
                    .filter(signal -> containsTerm(normalized, signal.getKey()))
                    .mapToInt(Map.Entry::getValue)
                    .sum();
            if (score > 0) {
                matches.add(new DomainMatch(category, score));
            }
        });
        return matches.stream()
                .sorted(Comparator.comparingInt(DomainMatch::score).reversed()
                        .thenComparing(DomainMatch::category))
                .toList();
    }

    private static boolean containsTerm(String normalizedText, String signal) {
        String normalizedSignal = TextUtil.normalizeSearchText(signal)
                .replaceAll("[^a-z0-9+#/_-]+", " ");
        return normalizedText.contains(" " + normalizedSignal + " ")
                || normalizedText.contains(" " + normalizedSignal + "/")
                || normalizedText.contains("/" + normalizedSignal + " ")
                || normalizedText.contains(" " + normalizedSignal + "-")
                || normalizedText.contains("-" + normalizedSignal + " ");
    }

    private static String normalizeFallback(String fallbackCategory) {
        return fallbackCategory == null || fallbackCategory.isBlank() ? "General" : fallbackCategory.trim();
    }

    private static Map<String, Map<String, Integer>> domainSignals() {
        Map<String, Map<String, Integer>> signals = new LinkedHashMap<>();
        signals.put("Auth / JWT", Map.ofEntries(
                Map.entry("jwt", 10), Map.entry("oauth", 10), Map.entry("oidc", 10),
                Map.entry("authentication", 5), Map.entry("authorization", 5), Map.entry("auth", 4),
                Map.entry("token", 3), Map.entry("login", 2), Map.entry("password", 2), Map.entry("session", 2)
        ));
        signals.put("Redis Cache", Map.ofEntries(
                Map.entry("redis", 10), Map.entry("keyspace", 6), Map.entry("sentinel", 6),
                Map.entry("cache", 3), Map.entry("eviction", 3), Map.entry("slowlog", 3)
        ));
        signals.put("PostgreSQL", Map.ofEntries(
                Map.entry("postgresql", 10), Map.entry("postgres", 10), Map.entry("psql", 8),
                Map.entry("logical replication", 6), Map.entry("database", 2), Map.entry("sql", 2), Map.entry("wal", 3)
        ));
        signals.put("Docker", Map.ofEntries(
                Map.entry("docker", 10), Map.entry("dockerfile", 10), Map.entry("compose", 8),
                Map.entry("buildkit", 8), Map.entry("container", 3), Map.entry("image", 2)
        ));
        signals.put("Kubernetes", Map.ofEntries(
                Map.entry("kubernetes", 10), Map.entry("k8s", 10), Map.entry("crashloopbackoff", 8),
                Map.entry("networkpolicy", 8), Map.entry("httproute", 8), Map.entry("ingress", 5),
                Map.entry("pod", 4), Map.entry("cluster", 2), Map.entry("admission", 3)
        ));
        signals.put("Incident SOP", Map.ofEntries(
                Map.entry("incident", 8), Map.entry("outage", 6), Map.entry("sev", 6),
                Map.entry("rollback", 4), Map.entry("postmortem", 5), Map.entry("mitigation", 3)
        ));
        signals.put("Ticket Rules", Map.ofEntries(
                Map.entry("ticket", 8), Map.entry("sla", 7), Map.entry("priority", 5),
                Map.entry("triage", 4), Map.entry("escalation", 3)
        ));
        signals.put("Customer Templates", Map.ofEntries(
                Map.entry("customer communication", 8), Map.entry("customer", 3),
                Map.entry("template", 4), Map.entry("reply", 3)
        ));
        return Map.copyOf(signals);
    }

    private record DomainMatch(String category, int score) {
    }
}
