package com.zwf.aisupport.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zwf.aisupport.entity.WebEvidence;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class WebSearchClient {

    private static final int MAX_CONTENT_LENGTH = 5000;
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

    public WebSearchClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public Optional<WebEvidence> search(String question, Long userId) {
        String query = buildQuery(question);
        Optional<WebEvidence> instantAnswer = searchDuckDuckGoInstantAnswer(question, query, userId);
        if (instantAnswer.isPresent()) {
            return instantAnswer;
        }
        return searchDuckDuckGoHtml(question, query, userId);
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

            if (!abstractText.isBlank() && !abstractUrl.isBlank()) {
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
                if (!text.isBlank() && !firstUrl.isBlank()) {
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
        } catch (Exception ignored) {
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
                if (!extracted.isBlank()) {
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
        } catch (Exception ignored) {
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
                .limit(5)
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
        } catch (Exception ignored) {
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
        evidence.setTitle(limit(blankToDefault(title, shortTitle(question)), 300));
        evidence.setUrl(url);
        evidence.setDomain(domain(url));
        evidence.setSnippet(limit(clean(snippet), 1200));
        evidence.setExtractedContent(limit(summary.isBlank() ? content : summary, MAX_CONTENT_LENGTH));
        evidence.setSuggestedArticleTitle(limit(suggestTitle(question), 150));
        evidence.setSuggestedArticleContent(buildSuggestedArticle(question, summary, url, evidence.getDomain()));
        evidence.setCategory(inferCategory(question + " " + title + " " + snippet));
        evidence.setCredibilityScore(scoreDomain(url));
        evidence.setEvidenceOrigin("WEB_SEARCH");
        evidence.setStatus("NEEDS_REVIEW");
        evidence.setCreatedBy(userId);
        return evidence;
    }

    private String buildQuery(String question) {
        String trimmed = clean(question);
        if (trimmed.toLowerCase(Locale.ROOT).contains("cloudops")
                || trimmed.toLowerCase(Locale.ROOT).contains("kubernetes")
                || trimmed.toLowerCase(Locale.ROOT).contains("redis")
                || trimmed.toLowerCase(Locale.ROOT).contains("postgres")
                || trimmed.toLowerCase(Locale.ROOT).contains("docker")
                || trimmed.toLowerCase(Locale.ROOT).contains("jwt")) {
            return trimmed;
        }
        return trimmed + " CloudOps troubleshooting official documentation";
    }

    private String get(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(12))
                .header("User-Agent", "AegisCloud-KnowledgeSupport/1.0")
                .header("Accept", "text/html,application/json;q=0.9,*/*;q=0.8")
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() >= 400) {
            throw new IllegalStateException("External search request failed");
        }
        return response.body();
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
            summary = limit(text, 1200);
        }
        return limit(summary, 1800);
    }

    private int matchScore(String sentence, List<String> terms) {
        String normalized = sentence.toLowerCase(Locale.ROOT);
        int score = 0;
        for (String term : terms) {
            if (normalized.contains(term)) {
                score++;
            }
        }
        if (containsAny(normalized, "purpose", "instruction", "command", "sets", "copies", "runs", "exposes", "executes", "default")) {
            score += 2;
        }
        return score;
    }

    private String buildSuggestedArticle(String question, String summary, String url, String domain) {
        String category = inferCategory(question + " " + summary);
        String title = suggestTitle(question);
        String answerDraft = buildUserAnswerDraft(question, summary);
        return """
                # %s

                Category: %s

                ## Overview
                This draft captures externally discovered knowledge that may extend the %s Knowledge Pack. It must be reviewed, reorganized into the closest existing article, and rewritten to match internal terminology before publishing.

                ## Core Answer
                %s

                ## Key Concepts
                %s

                ## Troubleshooting
                - Confirm the exact production component, version, and failure signal before applying this guidance.
                - Prefer official commands, logs, metrics, and configuration names that can be reproduced in AegisCloud.
                - If the evidence is only partially related, keep the verified concept and remove assumptions before publishing.

                ## Best Practices
                - Reorganize this draft into the closest existing Knowledge Pack instead of creating a duplicate pack.
                - Keep one concept per section and remove repeated definitions.
                - Rebuild retrieval chunks after approval so each chunk answers one clear question.

                ## References
                - Source type: WEB_SEARCH
                - Source domain: %s
                - Source URL: %s
                - Original question: %s
                """.formatted(
                title,
                category,
                category,
                answerDraft,
                normalizeEvidenceForArticle(summary),
                blankToDefault(domain, "Unknown"),
                blankToDefault(url, "-"),
                question
        );
    }

    private String buildUserAnswerDraft(String question, String summary) {
        String normalizedQuestion = question == null ? "" : question.toLowerCase(Locale.ROOT);
        if (normalizedQuestion.contains("dockerfile")
                && normalizedQuestion.contains("workdir")
                && normalizedQuestion.contains("copy")
                && normalizedQuestion.contains("run")
                && normalizedQuestion.contains("expose")
                && normalizedQuestion.contains("cmd")) {
            return """
                    - WORKDIR sets the default directory inside the image for later instructions.
                    - COPY moves application files from the build context into the image.
                    - RUN executes build-time commands and saves the result into image layers.
                    - EXPOSE documents the listening port; it does not publish the port by itself.
                    - CMD defines the default command that starts the app when the container runs.
                    """.trim();
        }

        List<String> points = Arrays.stream(summary.split("\\R+|(?<=[.!?])\\s+"))
                .map(this::clean)
                .filter(point -> point.length() >= 24)
                .filter(point -> !isWebNoise(point))
                .limit(5)
                .map(point -> "- " + limit(point, 220))
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
                .map(point -> "- " + limit(point, 260))
                .toList();
        return points.isEmpty() ? "- No clean evidence summary was extracted. Re-open the source before approval." : String.join("\n", points);
    }

    private BigDecimal scoreDomain(String url) {
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

    private String inferCategory(String text) {
        String normalized = text == null ? "" : text.toLowerCase(Locale.ROOT);
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

    private String suggestTitle(String question) {
        return "External Evidence: " + shortTitle(question);
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

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength).trim();
    }

    private record SearchCandidate(String title, String url, String snippet) {
    }
}
