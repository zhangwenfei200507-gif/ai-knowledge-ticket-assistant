package com.zwf.aisupport.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zwf.aisupport.common.util.KnowledgeCategoryResolver;
import com.zwf.aisupport.common.util.TextUtil;
import com.zwf.aisupport.entity.WebEvidence;
import com.zwf.aisupport.entity.WebEvidenceSource;
import com.zwf.aisupport.service.WebSearchProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class WebSearchClient {

    private static final Logger log = LoggerFactory.getLogger(WebSearchClient.class);

    private static final int MAX_CONTENT_LENGTH = 5000;
    private static final int MAX_PAGE_RESPONSE_BYTES = 1_500_000;
    private static final Pattern RESULT_LINK_PATTERN = Pattern.compile(
            "<a[^>]+class=\"result__a\"[^>]+href=\"([^\"]+)\"[^>]*>(.*?)</a>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern RESULT_SNIPPET_PATTERN = Pattern.compile(
            "<a[^>]+class=\"result__snippet\"[^>]*>(.*?)</a>|<div[^>]+class=\"result__snippet\"[^>]*>(.*?)</div>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final AiQueryUnderstandingService queryUnderstandingService;
    private final WebSearchProvider serperSearchProvider;
    private final ExternalUrlPolicy externalUrlPolicy = new ExternalUrlPolicy();

    public WebSearchClient(ObjectMapper objectMapper,
                           AiQueryUnderstandingService queryUnderstandingService,
                           WebSearchProvider serperSearchProvider) {
        this.objectMapper = objectMapper;
        this.queryUnderstandingService = queryUnderstandingService;
        this.serperSearchProvider = serperSearchProvider;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public Optional<WebEvidence> search(String question, Long userId) {
        List<WebEvidence> evidenceCandidates = new ArrayList<>();
        for (String query : buildQueries(question)) {
            List<WebEvidence> providerResults = searchSerper(question, query, userId);
            if (providerResults.isEmpty()) {
                Optional<WebEvidence> instantAnswer = searchDuckDuckGoInstantAnswer(question, query, userId);
                if (instantAnswer.isPresent()) {
                    evidenceCandidates.add(instantAnswer.get());
                } else {
                    searchDuckDuckGoHtml(question, query, userId).ifPresent(evidenceCandidates::add);
                }
            } else {
                evidenceCandidates.addAll(providerResults);
            }
            evidenceCandidates = deduplicateEvidence(evidenceCandidates);
            if (hasStrongOfficialCoverage(question, evidenceCandidates)) {
                break;
            }
        }

        if (evidenceCandidates.isEmpty()) {
            return Optional.empty();
        }

        List<WebEvidence> ranked = deduplicateEvidence(evidenceCandidates).stream()
                .sorted(Comparator.comparingInt((WebEvidence evidence) -> sourceQualityScore(evidence.getUrl(), evidence.getTitle(), evidence.getExtractedContent())).reversed())
                .limit(3)
                .toList();
        WebEvidence merged = mergeEvidence(question, ranked, userId);
        merged.setSources(toEvidenceSources(ranked));
        return Optional.of(merged);
    }

    private List<WebEvidenceSource> toEvidenceSources(List<WebEvidence> evidence) {
        List<WebEvidenceSource> sources = new ArrayList<>();
        for (int index = 0; index < evidence.size(); index++) {
            WebEvidence item = evidence.get(index);
            if (item.getUrl() == null || item.getUrl().isBlank()) {
                continue;
            }
            WebEvidenceSource source = new WebEvidenceSource();
            source.setTitle(item.getTitle());
            source.setUrl(item.getUrl());
            source.setDomain(item.getDomain());
            source.setExcerpt(TextUtil.limit(item.getExtractedContent(), 1800));
            source.setCredibilityScore(item.getCredibilityScore());
            source.setSourceRank(index + 1);
            source.setSourceType(isOfficialDocumentation(item.getUrl()) ? "OFFICIAL_DOCUMENTATION" : "TECHNICAL_SOURCE");
            source.setOfficialSource(isOfficialDocumentation(item.getUrl()));
            source.setRetrievedTime(LocalDateTime.now());
            source.setSourceStatus("RETRIEVED");
            sources.add(source);
        }
        return sources;
    }

    private List<WebEvidence> deduplicateEvidence(List<WebEvidence> evidence) {
        Map<String, WebEvidence> byUrl = new java.util.LinkedHashMap<>();
        for (WebEvidence item : evidence) {
            if (item != null && item.getUrl() != null && !item.getUrl().isBlank()) {
                byUrl.putIfAbsent(item.getUrl(), item);
            }
        }
        return new ArrayList<>(byUrl.values());
    }

    private List<WebEvidence> searchSerper(String question, String query, Long userId) {
        WebSearchProvider.ProviderResult providerResult = serperSearchProvider.search(query, 8);
        if (!providerResult.succeeded()) {
            if (providerResult.safeErrorMessage() != null) {
                log.warn("Search provider {} unavailable for query '{}': {}",
                        providerResult.provider(), TextUtil.limit(query, 120), providerResult.safeErrorMessage());
            }
            return List.of();
        }

        List<WebEvidence> evidence = new ArrayList<>();
        for (WebSearchProvider.SearchResult item : providerResult.results()) {
            if (evidence.size() >= 3) {
                break;
            }
            String title = clean(item.title());
            String url = item.url();
            String snippet = clean(item.snippet());
            String extracted = fetchReadableText(url).orElse(snippet);
            if (!extracted.isBlank()
                    && hasQuestionCoverage(query, title + " " + snippet + " " + extracted)
                    && isAcceptableEvidenceSource(url, title, snippet, extracted)) {
                evidence.add(buildEvidence(question, query, title, url, snippet, extracted, userId));
            }
        }
        return evidence;
    }

    private Optional<WebEvidence> searchDuckDuckGoInstantAnswer(String question, String query, Long userId) {
        try {
            String url = "https://api.duckduckgo.com/?format=json&no_html=1&skip_disambig=1&q="
                    + URLEncoder.encode(query, StandardCharsets.UTF_8);
            String body = get(url);
            JsonNode root = objectMapper.readTree(body);
            String abstractText = clean(root.path("AbstractText").asText(""));
            String abstractUrl = root.path("AbstractURL").asText("");
            String heading = root.path("Heading").asText("");

            if (!abstractText.isBlank() && !abstractUrl.isBlank()
                    && hasQuestionCoverage(query, abstractText)
                    && isAcceptableEvidenceSource(abstractUrl, heading, abstractText, abstractText)) {
                return Optional.of(buildEvidence(
                        question,
                        query,
                        blankToDefault(heading, shortTitle(question)),
                        abstractUrl,
                        abstractText,
                        abstractText,
                        userId
                ));
            }

            List<JsonNode> relatedTopics = new ArrayList<>();
            root.path("RelatedTopics").forEach(node -> {
                if (node.has("Text") && node.has("FirstURL")) {
                    relatedTopics.add(node);
                }
            });

            if (!relatedTopics.isEmpty()) {
                JsonNode best = relatedTopics.get(0);
                String text = clean(best.path("Text").asText(""));
                String firstUrl = best.path("FirstURL").asText("");
                if (!text.isBlank() && !firstUrl.isBlank()
                        && hasQuestionCoverage(query, text)
                        && isAcceptableEvidenceSource(firstUrl, shortTitle(text), text, text)) {
                    return Optional.of(buildEvidence(
                            question,
                            query,
                            shortTitle(text),
                            firstUrl,
                            text,
                            text,
                            userId
                    ));
                }
            }
        } catch (Exception exception) {
            log.debug("DuckDuckGo instant-answer search failed for query '{}': {}", TextUtil.limit(query, 120), exception.getMessage());
            return Optional.empty();
        }
        return Optional.empty();
    }

    private Optional<WebEvidence> searchDuckDuckGoHtml(String question, String query, Long userId) {
        try {
            String searchUrl = "https://html.duckduckgo.com/html/?q="
                    + URLEncoder.encode(query, StandardCharsets.UTF_8);
            String body = get(searchUrl);
            List<SearchCandidate> candidates = parseCandidates(body);
            for (SearchCandidate candidate : candidates) {
                if (candidate.url().isBlank() || !candidate.url().startsWith("http")) {
                    continue;
                }
                String extracted = fetchReadableText(candidate.url())
                        .orElse(candidate.snippet());
                if (!extracted.isBlank()
                        && hasQuestionCoverage(query, candidate.title() + " " + candidate.snippet() + " " + extracted)
                        && isAcceptableEvidenceSource(candidate.url(), candidate.title(), candidate.snippet(), extracted)) {
                    return Optional.of(buildEvidence(
                            question,
                            query,
                            candidate.title(),
                            candidate.url(),
                            candidate.snippet(),
                            extracted,
                            userId
                    ));
                }
            }
        } catch (Exception exception) {
            log.debug("DuckDuckGo HTML search failed for query '{}': {}", TextUtil.limit(query, 120), exception.getMessage());
            return Optional.empty();
        }
        return Optional.empty();
    }

    private List<SearchCandidate> parseCandidates(String html) {
        List<String> snippets = new ArrayList<>();
        Matcher snippetMatcher = RESULT_SNIPPET_PATTERN.matcher(html);
        while (snippetMatcher.find()) {
            String raw = snippetMatcher.group(1) != null ? snippetMatcher.group(1) : snippetMatcher.group(2);
            snippets.add(cleanHtml(raw));
        }

        List<SearchCandidate> candidates = new ArrayList<>();
        Matcher linkMatcher = RESULT_LINK_PATTERN.matcher(html);
        int index = 0;
        while (linkMatcher.find()) {
            String rawUrl = cleanHtml(linkMatcher.group(1));
            String title = cleanHtml(linkMatcher.group(2));
            String url = normalizeDuckDuckGoUrl(rawUrl);
            String snippet = index < snippets.size() ? snippets.get(index) : "";
            candidates.add(new SearchCandidate(title, url, snippet));
            index++;
        }

        return candidates.stream()
                .filter(candidate -> !candidate.title().isBlank() && !candidate.url().isBlank())
                .limit(10)
                .toList();
    }

    private Optional<String> fetchReadableText(String url) {
        try {
            String html = get(url);
            String text = htmlDecode(html)
                    .replaceAll("(?is)<script.*?</script>", " ")
                    .replaceAll("(?is)<style.*?</style>", " ")
                    .replaceAll("(?is)<noscript.*?</noscript>", " ")
                    .replaceAll("(?is)<nav.*?</nav>", " ")
                    .replaceAll("(?is)<header.*?</header>", " ")
                    .replaceAll("(?is)<footer.*?</footer>", " ")
                    .replaceAll("(?is)<svg.*?</svg>", " ")
                    .replaceAll("(?is)<aside.*?</aside>", " ")
                    .replaceAll("(?is)<form.*?</form>", " ")
                    .replaceAll("(?is)<button.*?</button>", " ")
                    .replaceAll("(?is)<li[^>]*class=\"[^\"]*(nav|menu|breadcrumb|toc)[^\"]*\".*?</li>", " ")
                    .replaceAll("(?is)<[^>]+>", " ");
            text = cleanWebText(text);
            if (text.length() > MAX_CONTENT_LENGTH) {
                text = text.substring(0, MAX_CONTENT_LENGTH);
            }
            return text.isBlank() ? Optional.empty() : Optional.of(text);
        } catch (Exception exception) {
            log.debug("Web content extraction failed for '{}': {}", TextUtil.limit(url, 160), exception.getMessage());
            return Optional.empty();
        }
    }

    private WebEvidence buildEvidence(String question,
                                      String query,
                                      String title,
                                      String url,
                                      String snippet,
                                      String extractedContent,
                                      Long userId) {
        String content = cleanWebText(extractedContent);
        String summary = summarize(content, question);
        WebEvidence evidence = new WebEvidence();
        evidence.setQuestion(question);
        evidence.setQuery(query);
        evidence.setTitle(TextUtil.limit(blankToDefault(title, shortTitle(question)), 300));
        evidence.setUrl(url);
        evidence.setDomain(domain(url));
        evidence.setSnippet(TextUtil.limit(clean(snippet), 1200));
        evidence.setExtractedContent(TextUtil.limit(summary.isBlank() ? content : summary, MAX_CONTENT_LENGTH));
        evidence.setSuggestedArticleTitle(TextUtil.limit(suggestTitle(question), 150));
        evidence.setSuggestedArticleContent(buildSuggestedArticle(question, summary, url, evidence.getDomain()));
        evidence.setCategory(KnowledgeCategoryResolver.infer(question + " " + title + " " + snippet));
        evidence.setCredibilityScore(scoreDomain(url));
        evidence.setEvidenceOrigin("WEB_SEARCH");
        evidence.setStatus("NEEDS_REVIEW");
        evidence.setCreatedBy(userId);
        return evidence;
    }

    private List<String> buildQueries(String question) {
        String trimmed = clean(question);
        LinkedHashSet<String> queries = new LinkedHashSet<>();
        for (String task : queryUnderstandingService.analyze(trimmed).tasks()) {
            String technicalTerms = extractTechnicalQuery(task);
            if (!technicalTerms.isBlank()) {
                queries.add(technicalTerms + " official documentation");
                queries.add(technicalTerms + " specification");
                for (String officialDomain : officialDomainsFor(task + " " + technicalTerms)) {
                    queries.add(technicalTerms + " site:" + officialDomain);
                }
                if (isTroubleshootingQuery(task)) {
                    queries.add(technicalTerms + " troubleshooting");
                }
            }
            queries.add(task + " official documentation");
        }
        queries.add(trimmed);
        queries.add(trimmed + " official documentation");
        if (isTroubleshootingQuery(trimmed)) {
            queries.add(trimmed + " CloudOps troubleshooting");
        }
        return queries.stream()
                .filter(query -> query.length() >= 8)
                .limit(8)
                .toList();
    }

    private List<String> officialDomainsFor(String text) {
        String category = KnowledgeCategoryResolver.infer(text);
        String normalized = TextUtil.safeLower(text + " " + category);
        LinkedHashSet<String> domains = new LinkedHashSet<>();
        if (TextUtil.containsAny(normalized, "docker", "dockerfile", "compose", "buildkit", "container")) {
            domains.add("docs.docker.com");
        }
        if (TextUtil.containsAny(normalized, "kubernetes", "pod", "ingress", "gateway", "hpa", "probe")) {
            domains.add("kubernetes.io/docs");
        }
        if (TextUtil.containsAny(normalized, "postgres", "postgresql", "database", "sql", "wal", "replication")) {
            domains.add("postgresql.org/docs");
        }
        if (TextUtil.containsAny(normalized, "redis", "cache", "eviction", "slowlog")) {
            domains.add("redis.io/docs");
        }
        if (TextUtil.containsAny(normalized, "jwt", "spring security", "oauth", "oidc", "bearer")) {
            domains.add("docs.spring.io");
            domains.add("datatracker.ietf.org");
        }
        if (TextUtil.containsAny(normalized, "incident", "severity", "priority", "sla", "ticket")) {
            domains.add("sre.google");
        }
        return domains.stream().limit(3).toList();
    }

    private boolean isTroubleshootingQuery(String question) {
        String normalized = TextUtil.safeLower(question);
        return TextUtil.containsAny(normalized,
                "fail", "fails", "failed", "failure", "error", "cannot", "can't", "unable",
                "timeout", "rejected", "down", "not working", "connection refused", "stuck");
    }

    private String extractTechnicalQuery(String question) {
        List<String> terms = queryUnderstandingService.extractSearchTerms(question).stream()
                .filter(term -> !TextUtil.containsAny(term, "why", "what", "how", "because", "even", "though", "not", "has", "have", "does", "with", "without", "first"))
                .distinct()
                .limit(8)
                .toList();
        return String.join(" ", terms);
    }

    private String get(String url) throws Exception {
        URI safeUri = externalUrlPolicy.requirePublicHttpUrl(url);
        HttpRequest request = HttpRequest.newBuilder(safeUri)
                .timeout(Duration.ofSeconds(12))
                .header("User-Agent", "AegisCloud-KnowledgeSupport/1.0")
                .header("Accept", "text/html,application/json;q=0.9,*/*;q=0.8")
                .GET()
                .build();
        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            response.body().close();
            throw new IllegalStateException("External search request failed");
        }
        try (InputStream body = response.body()) {
            return new String(readLimited(body, MAX_PAGE_RESPONSE_BYTES), StandardCharsets.UTF_8);
        }
    }

    private byte[] readLimited(InputStream input, int maxBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(maxBytes, 8192));
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(buffer, 0, Math.min(buffer.length, maxBytes - total + 1))) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new IOException("External response exceeds the configured size limit");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private String normalizeDuckDuckGoUrl(String rawUrl) {
        String decoded = htmlDecode(rawUrl);
        try {
            URI uri = URI.create(decoded);
            String query = uri.getRawQuery();
            if (query != null) {
                for (String part : query.split("&")) {
                    if (part.startsWith("uddg=")) {
                        return URLDecoder.decode(part.substring(5), StandardCharsets.UTF_8);
                    }
                }
            }
        } catch (Exception ignored) {
            return decoded;
        }
        return decoded;
    }

    private String summarize(String text, String question) {
        String normalizedQuestion = question.toLowerCase(Locale.ROOT);
        List<String> terms = Arrays.stream(normalizedQuestion.split("\\W+"))
                .filter(term -> term.length() >= 4)
                .distinct()
                .toList();

        List<String> sentences = Arrays.stream(text.split("(?<=[.!?])\\s+|\\R+"))
                .map(String::trim)
                .filter(sentence -> sentence.length() >= 24)
                .filter(sentence -> !isWebNoise(sentence))
                .sorted(Comparator.comparingInt(sentence -> -matchScore(sentence, terms)))
                .limit(8)
                .toList();

        String summary = String.join("\n", sentences);
        if (summary.isBlank()) {
            summary = TextUtil.limit(text, 1200);
        }
        return TextUtil.limit(summary, 1800);
    }

    private int matchScore(String sentence, List<String> terms) {
        String normalized = sentence.toLowerCase(Locale.ROOT);
        int score = 0;
        for (String term : terms) {
            if (normalized.contains(term)) {
                score++;
            }
        }
        if (TextUtil.containsAny(normalized,
                "means", "defines", "sets", "available", "persist", "build-time", "runtime",
                "difference", "different", "compared", "because", "improves", "used to",
                "purpose", "instruction", "command", "copies", "runs", "exposes", "executes", "default")) {
            score += 2;
        }
        if (TextUtil.containsAny(normalized,
                "we will", "let's", "in this guide", "in this tutorial", "getting started",
                "previous dockerfile", "copy page", "edit this page")) {
            score -= 4;
        }
        return score;
    }

    private String buildSuggestedArticle(String question, String summary, String url, String domain) {
        String category = KnowledgeCategoryResolver.infer(question + " " + summary);
        String title = suggestTitle(question);
        String answerDraft = buildUserAnswerDraft(question, summary);
        return """
                # %s

                Category: %s

                ## Overview
                %s

                ## Core Concepts
                %s

                ## Operational Notes
                %s

                ## Troubleshooting
                %s

                ## Best Practices
                %s
                """.formatted(
                title,
                category,
                buildSuggestedOverview(question, category),
                answerDraft,
                normalizeEvidenceForArticle(summary),
                buildTroubleshootingNotes(question, summary),
                buildBestPracticeNotes(question, summary)
        );
    }

    private String buildSuggestedOverview(String question, String category) {
        String technicalTopic = suggestTitle(question);
        return "This section explains " + technicalTopic + " for the " + category
                + " domain, focusing on the durable behavior, configuration impact, and operational checks that support teams can reuse.";
    }

    private String buildTroubleshootingNotes(String question, String summary) {
        String normalized = TextUtil.safeLower(question + " " + summary);
        LinkedHashSet<String> notes = new LinkedHashSet<>();
        if (TextUtil.containsAny(normalized, "error", "fail", "failure", "timeout", "latency", "rejected", "cannot", "connection")) {
            notes.add("Confirm the affected component, version, exact error, and first failing timestamp.");
            notes.add("Compare the runtime configuration with the expected behavior described in this knowledge.");
        }
        if (TextUtil.containsAny(normalized, "docker", "compose", "container")) {
            notes.add("Check container logs, service names, ports, environment variables, health checks, and build context.");
        }
        if (TextUtil.containsAny(normalized, "kubernetes", "pod", "gateway", "ingress", "httproute")) {
            notes.add("Check resource events, route attachment, namespace rules, backend service references, and controller status.");
        }
        if (TextUtil.containsAny(normalized, "postgres", "postgresql")) {
            notes.add("Check connection settings, server health, wait events, active sessions, and storage or I/O pressure.");
        }
        if (TextUtil.containsAny(normalized, "redis")) {
            notes.add("Check latency, slow logs, memory policy, key TTLs, eviction, persistence, and client connection behavior.");
        }
        if (notes.isEmpty()) {
            notes.add("Verify the production component, configuration, logs, metrics, and version before applying the guidance.");
        }
        return notes.stream().map(note -> "- " + note).reduce((left, right) -> left + "\n" + right).orElse("");
    }

    private String buildBestPracticeNotes(String question, String summary) {
        String normalized = TextUtil.safeLower(question + " " + summary);
        LinkedHashSet<String> notes = new LinkedHashSet<>();
        if (TextUtil.containsAny(normalized, "docker", "build", "image", "dockerfile")) {
            notes.add("Keep build-time concerns separate from runtime configuration and avoid adding unnecessary files or secrets to images.");
        }
        if (TextUtil.containsAny(normalized, "jwt", "oauth", "token")) {
            notes.add("Separate authentication failures from authorization failures and keep token validation rules explicit.");
        }
        if (TextUtil.containsAny(normalized, "gateway", "httproute", "ingress")) {
            notes.add("Use Gateway API when ownership boundaries and advanced routing need to be explicit.");
        }
        if (TextUtil.containsAny(normalized, "cache", "redis")) {
            notes.add("Use caching features only when invalidation behavior and data freshness requirements are understood.");
        }
        notes.add("Prefer guidance that is reproducible, version-aware, and safe to apply in production.");
        return notes.stream().map(note -> "- " + note).reduce((left, right) -> left + "\n" + right).orElse("");
    }

    private String buildUserAnswerDraft(String question, String summary) {
        List<String> points = Arrays.stream(summary.split("\\R+|(?<=[.!?])\\s+"))
                .map(this::clean)
                .filter(point -> point.length() >= 24)
                .filter(point -> !isWebNoise(point))
                .limit(5)
                .map(point -> "- " + TextUtil.limit(point, 220))
                .toList();
        if (points.isEmpty()) {
            return "- Review the source evidence and write a concise operational answer before publishing.";
        }
        return String.join("\n", points);
    }

    private String normalizeEvidenceForArticle(String summary) {
        List<String> points = Arrays.stream(blankToDefault(summary, "").split("\\R+|(?<=[.!?])\\s+"))
                .map(this::clean)
                .filter(point -> point.length() >= 24)
                .filter(point -> !isWebNoise(point))
                .limit(6)
                .map(point -> "- " + TextUtil.limit(point, 260))
                .toList();
        return points.isEmpty() ? "- No clean evidence summary was extracted. Re-open the source before approval." : String.join("\n", points);
    }

    private BigDecimal scoreDomain(String url) {
        int quality = sourceQualityScore(url, "", "");
        if (quality >= 90) {
            return new BigDecimal("0.96");
        }
        if (quality >= 75) {
            return new BigDecimal("0.92");
        }
        String domain = domain(url);
        if (domain.endsWith(".io") || domain.endsWith(".org") || domain.endsWith(".dev")) {
            return new BigDecimal("0.88");
        }
        if (domain.contains("docs.") || domain.contains("developer.") || domain.contains("learn.")) {
            return new BigDecimal("0.94");
        }
        if (domain.endsWith(".com")) {
            return new BigDecimal("0.82");
        }
        return new BigDecimal("0.75");
    }

    private boolean hasStrongOfficialCoverage(String question, List<WebEvidence> evidenceCandidates) {
        long requiredSources = requiresMultipleSources(question) ? 2 : 1;
        return evidenceCandidates.stream()
                .filter(evidence -> sourceQualityScore(evidence.getUrl(), evidence.getTitle(), evidence.getExtractedContent()) >= 90)
                .map(WebEvidence::getUrl)
                .distinct()
                .count() >= requiredSources;
    }

    private boolean requiresMultipleSources(String question) {
        String normalized = TextUtil.safeLower(question);
        return TextUtil.containsAny(normalized,
                "compare", "comparison", "differences between", "trade-offs", "tradeoffs",
                "multiple sources", "across", "from kubernetes, redis", "work together");
    }

    private WebEvidence mergeEvidence(String question, List<WebEvidence> ranked, Long userId) {
        WebEvidence primary = ranked.get(0);
        if (ranked.size() == 1) {
            return primary;
        }

        StringBuilder combined = new StringBuilder();
        for (WebEvidence evidence : ranked) {
            combined.append("Source: ")
                    .append(blankToDefault(evidence.getDomain(), "web evidence"))
                    .append("\n")
                    .append(evidence.getExtractedContent())
                    .append("\n\n");
        }

        String mergedContent = TextUtil.limit(combined.toString().trim(), MAX_CONTENT_LENGTH);
        primary.setQuestion(question);
        primary.setQuery(ranked.stream().map(WebEvidence::getQuery).distinct().reduce((left, right) -> left + " | " + right).orElse(primary.getQuery()));
        primary.setSnippet(TextUtil.limit(ranked.stream().map(WebEvidence::getSnippet).distinct().reduce((left, right) -> left + "\n" + right).orElse(primary.getSnippet()), 1200));
        primary.setExtractedContent(mergedContent);
        primary.setSuggestedArticleTitle(TextUtil.limit(suggestTitle(question), 150));
        primary.setSuggestedArticleContent(buildSuggestedArticle(question, mergedContent, primary.getUrl(), primary.getDomain()));
        primary.setCredibilityScore(scoreDomain(primary.getUrl()));
        primary.setCreatedBy(userId);
        return primary;
    }

    private int sourceQualityScore(String url, String title, String text) {
        String normalizedUrl = TextUtil.safeLower(url);
        String normalizedTitle = TextUtil.safeLower(title);
        String normalizedText = TextUtil.safeLower(text);
        if (isRejectedEvidencePage(normalizedUrl, normalizedTitle)) {
            return 0;
        }
        if (isOfficialDocumentation(normalizedUrl)) {
            return 100;
        }
        if (isRfcOrStandard(normalizedUrl, normalizedTitle)) {
            return 96;
        }
        if (TextUtil.containsAny(normalizedUrl, "github.com/", "github.io", "developer.", "api.")) {
            return 90;
        }
        if (isHighQualityEngineeringBlog(normalizedUrl, normalizedText)) {
            return 78;
        }
        if (isTechnicalArticle(normalizedUrl, normalizedTitle, normalizedText)) {
            return 68;
        }
        return hasConcreteTechnicalContent(normalizedText) ? 55 : 20;
    }

    private String domain(String url) {
        try {
            String host = URI.create(url).getHost();
            if (host == null) {
                return "";
            }
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (Exception ignored) {
            return "";
        }
    }

    private String suggestTitle(String question) {
        String title = extractTechnicalQuery(question);
        if (title.isBlank()) {
            title = shortTitle(question);
        }
        String normalized = Arrays.stream(title.split("\\s+"))
                .filter(term -> term.length() >= 2)
                .limit(9)
                .reduce((left, right) -> left + " " + right)
                .orElse(shortTitle(question));
        return toTitleCase(normalized);
    }

    private String toTitleCase(String value) {
        StringBuilder builder = new StringBuilder();
        for (String part : clean(value).split("\\s+")) {
            if (part.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            if (part.length() <= 4 && part.equals(part.toLowerCase(Locale.ROOT))) {
                builder.append(part.toUpperCase(Locale.ROOT));
            } else {
                builder.append(part.substring(0, 1).toUpperCase(Locale.ROOT))
                        .append(part.substring(1));
            }
        }
        return builder.toString();
    }

    private String shortTitle(String text) {
        String cleaned = clean(text);
        if (cleaned.length() <= 70) {
            return cleaned;
        }
        return cleaned.substring(0, 67).trim() + "...";
    }

    private String cleanHtml(String value) {
        return clean(htmlDecode(value).replaceAll("(?is)<[^>]+>", " "));
    }

    private String cleanWebText(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value
                .replace('\u00a0', ' ')
                .replaceAll("(?m)^\\s*(Skip to content|Search|Sign in|Sign up|Edit this page|Copy page|Table of contents)\\s*$", " ")
                .replaceAll("(?m)^\\s*(Previous|Next|Feedback|Was this page helpful\\??|On this page)\\s*$", " ")
                .replaceAll("(?im)^.*(advertisement|sponsored|enroll now|start free trial|pricing|course overview|course curriculum|bootcamp|certification).*$", " ")
                .replaceAll("\\{[^\\n]{80,}\\}", " ")
                .replaceAll("\\s+", " ")
                .trim();

        StringBuilder builder = new StringBuilder();
        for (String sentence : normalized.split("(?<=[.!?])\\s+|\\s{2,}")) {
            String cleaned = clean(sentence);
            if (!cleaned.isBlank() && !isWebNoise(cleaned)) {
                builder.append(cleaned).append("\n");
            }
        }
        return builder.toString().trim();
    }

    private boolean isWebNoise(String line) {
        String normalized = line == null ? "" : line.toLowerCase(Locale.ROOT);
        return normalized.length() < 16
                || normalized.contains("advertisement")
                || normalized.contains("sponsored")
                || normalized.contains("course curriculum")
                || normalized.contains("course overview")
                || normalized.contains("bootcamp")
                || normalized.contains("certification")
                || normalized.contains("enroll now")
                || normalized.contains("start free trial")
                || normalized.contains("pricing")
                || normalized.contains("window.")
                || normalized.contains("document.")
                || normalized.contains("function(")
                || normalized.contains("function ")
                || normalized.contains("webpack")
                || normalized.contains("__next")
                || normalized.contains("localstorage")
                || normalized.contains("cookie")
                || normalized.contains("google analytics")
                || normalized.contains("var ")
                || normalized.contains("const ")
                || normalized.contains("return false")
                || normalized.matches(".*[{};]{4,}.*");
    }

    private boolean isAcceptableEvidenceSource(String url, String title, String snippet, String content) {
        String normalizedUrl = TextUtil.safeLower(url);
        String normalizedTitle = TextUtil.safeLower(title);
        String normalizedText = TextUtil.safeLower(title + " " + snippet + " " + content);

        if (isRejectedEvidencePage(normalizedUrl, normalizedTitle)) {
            return false;
        }
        if (isOfficialDocumentation(normalizedUrl)) {
            return hasConcreteTechnicalContent(normalizedText);
        }
        if (isPromotionalContent(normalizedText) && !hasConcreteTechnicalContent(normalizedText)) {
            return false;
        }
        return isRfcOrStandard(normalizedUrl, normalizedTitle)
                || isTechnicalArticle(normalizedUrl, normalizedTitle, normalizedText)
                || isHighQualityEngineeringBlog(normalizedUrl, normalizedText);
    }

    private boolean isRejectedEvidencePage(String url, String title) {
        return TextUtil.containsAny(url, "/courses", "/course", "/training", "/bootcamp", "/certification", "/pricing", "/tag/", "/tags/", "/category/", "/categories/", "/search", "/archive")
                || TextUtil.containsAny(title, "course", "training", "bootcamp", "certification", "pricing", "tag:", "category:", "search results", "index");
    }

    private boolean isPromotionalContent(String text) {
        return TextUtil.containsAny(text, "enroll now", "course curriculum", "course overview", "start free trial",
                "buy now", "advertisement", "sponsored", "certificate of completion");
    }

    private boolean isOfficialDocumentation(String url) {
        return TextUtil.containsAny(url, "docs.", "/docs/", "documentation", "developer.", "learn.microsoft.com", "kubernetes.io/docs", "docs.docker.com", "postgresql.org/docs", "redis.io/docs", "spring.io/projects", "docs.spring.io");
    }

    private boolean isRfcOrStandard(String url, String title) {
        return TextUtil.containsAny(url, "rfc-editor.org", "ietf.org", "w3.org", "iso.org")
                || TextUtil.containsAny(title, "rfc ", "standard", "specification");
    }

    private boolean isTechnicalArticle(String url, String title, String text) {
        return TextUtil.containsAny(url, "/blog/", "/articles/", "/article/", ".dev/", "medium.com", "engineering")
                && hasConcreteTechnicalContent(title + " " + text);
    }

    private boolean isHighQualityEngineeringBlog(String url, String text) {
        return TextUtil.containsAny(url, "engineering.", "netflixtechblog.com", "cloudflare.com", "stripe.com/blog", "github.blog", "martinfowler.com", "morling.dev")
                && hasConcreteTechnicalContent(text);
    }

    private boolean hasConcreteTechnicalContent(String text) {
        return TextUtil.containsAny(text, "configure", "configuration", "command", "error", "failure", "diagnose", "troubleshoot", "protocol", "api", "postgres", "redis", "docker", "kubernetes", "jwt", "token", "sql", "yaml", "http", "tls");
    }

    private boolean hasQuestionCoverage(String question, String text) {
        String normalizedText = TextUtil.safeLower(text);
        List<String> terms = Arrays.stream(TextUtil.safeLower(question).split("[^a-z0-9+#./_-]+"))
                .map(String::trim)
                .filter(term -> term.length() >= 3)
                .filter(term -> !TextUtil.containsAny(term,
                        "why", "what", "how", "when", "should", "does", "with", "without", "because",
                        "even", "though", "first", "the", "and", "for", "from", "this", "that",
                        "use", "using", "used", "issue", "problem", "help", "check"))
                .distinct()
                .limit(10)
                .toList();
        if (terms.isEmpty()) {
            return true;
        }
        List<String> specificTerms = terms.stream()
                .filter(term -> term.matches(".*[0-9+#./_-].*") || term.length() >= 6)
                .toList();
        if (!specificTerms.isEmpty() && specificTerms.stream().noneMatch(normalizedText::contains)) {
            return false;
        }
        long matched = terms.stream()
                .filter(normalizedText::contains)
                .count();
        double coverage = (double) matched / (double) terms.size();
        return matched >= Math.min(3, terms.size()) || coverage >= 0.45;
    }

    private String clean(String value) {
        return value == null ? "" : value.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }

    private String htmlDecode(String value) {
        return value == null ? "" : value
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">");
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private record SearchCandidate(String title, String url, String snippet) {
    }
}
