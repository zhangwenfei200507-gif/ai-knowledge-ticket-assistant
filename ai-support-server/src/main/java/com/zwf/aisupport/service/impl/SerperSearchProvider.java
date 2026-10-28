package com.zwf.aisupport.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zwf.aisupport.common.util.TextUtil;
import com.zwf.aisupport.service.WebSearchProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class SerperSearchProvider implements WebSearchProvider {

    private static final Logger log = LoggerFactory.getLogger(SerperSearchProvider.class);
    private static final int MAX_RESPONSE_BYTES = 1_000_000;

    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String baseUrl;
    private final HttpClient httpClient;

    public SerperSearchProvider(ObjectMapper objectMapper,
                                @Value("${app.search.serper.api-key:}") String apiKey,
                                @Value("${app.search.serper.base-url:https://google.serper.dev}") String baseUrl) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl != null && baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    }

    @Override
    public String name() {
        return "SERPER";
    }

    @Override
    public ProviderResult search(String query, int limit) {
        if (apiKey == null || apiKey.isBlank()) {
            return new ProviderResult(name(), List.of(), "SKIPPED", "SEARCH_NOT_CONFIGURED", "Search provider is not configured");
        }
        try {
            Map<String, Object> payload = Map.of("q", query, "num", Math.max(1, Math.min(10, limit)));
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/search"))
                    .timeout(Duration.ofSeconds(12))
                    .header("X-API-KEY", apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return new ProviderResult(name(), List.of(), "FAILED", classify(response.statusCode()), "Search provider HTTP " + response.statusCode());
            }
            JsonNode root = objectMapper.readTree(TextUtil.limit(response.body(), MAX_RESPONSE_BYTES));
            List<SearchResult> results = new ArrayList<>();
            root.path("organic").forEach(item -> {
                if (results.size() >= Math.min(10, limit)) {
                    return;
                }
                String title = item.path("title").asText("").trim();
                String url = item.path("link").asText("").trim();
                String snippet = item.path("snippet").asText("").trim();
                if (!title.isBlank() && url.startsWith("http")) {
                    results.add(new SearchResult(title, url, snippet));
                }
            });
            return new ProviderResult(name(), results, results.isEmpty() ? "INSUFFICIENT" : "SUCCEEDED", null, null);
        } catch (java.net.http.HttpTimeoutException exception) {
            return new ProviderResult(name(), List.of(), "FAILED", "SEARCH_TIMEOUT", "Search provider timed out");
        } catch (java.net.ConnectException | java.net.UnknownHostException exception) {
            return new ProviderResult(name(), List.of(), "FAILED", "SEARCH_NETWORK_FAILURE", "Search provider network failure");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new ProviderResult(name(), List.of(), "FAILED", "SEARCH_INTERRUPTED", "Search provider request interrupted");
        } catch (Exception exception) {
            log.warn("Serper search provider failed: {}", TextUtil.limit(exception.getMessage(), 220));
            return new ProviderResult(name(), List.of(), "FAILED", "SEARCH_INVALID_RESPONSE", "Search provider returned an invalid response");
        }
    }

    private String classify(int statusCode) {
        if (statusCode == 403) return "SEARCH_FORBIDDEN";
        if (statusCode == 429) return "SEARCH_RATE_LIMITED";
        if (statusCode >= 500) return "SEARCH_PROVIDER_ERROR";
        return "SEARCH_HTTP_ERROR";
    }
}
