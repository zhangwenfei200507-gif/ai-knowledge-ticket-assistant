package com.zwf.aisupport.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zwf.aisupport.service.LlmGatewayService;
import com.zwf.aisupport.vo.AiAnswerSourceVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class OpenAiResponsesLlmGatewayService implements LlmGatewayService {

    private static final int MAX_EVIDENCE_CHARS = 9000;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String apiKey;
    private final String baseUrl;
    private final String model;

    public OpenAiResponsesLlmGatewayService(ObjectMapper objectMapper,
                                            @Value("${app.llm.openai.api-key:}") String apiKey,
                                            @Value("${app.llm.openai.base-url:https://api.openai.com/v1}") String baseUrl,
                                            @Value("${app.llm.openai.model:gpt-4o-mini}") String model) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.model = model;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(12))
                .build();
    }

    @Override
    public Optional<String> generateGroundedAnswer(String question, String sourceType, List<AiAnswerSourceVO> sources) {
        if (!isConfigured() || question == null || question.isBlank() || sources == null || sources.isEmpty()) {
            return Optional.empty();
        }

        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", model);
            payload.put("instructions", buildInstructions(sourceType));
            payload.put("input", buildInput(question, sourceType, sources));

            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/responses"))
                    .timeout(Duration.ofSeconds(45))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400) {
                return Optional.empty();
            }

            JsonNode root = objectMapper.readTree(response.body());
            String outputText = root.path("output_text").asText("");
            if (!outputText.isBlank()) {
                return Optional.of(outputText.trim());
            }
            return extractOutputText(root).filter(text -> !text.isBlank()).map(String::trim);
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    @Override
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank() && baseUrl != null && !baseUrl.isBlank() && model != null && !model.isBlank();
    }

    private String buildInstructions(String sourceType) {
        return """
                You are AegisCloud, a CloudOps AI knowledge assistant.
                Answer the user's question using only the supplied evidence.
                If the evidence is not enough to answer precisely, say what cannot be confirmed and what should be checked next.
                Do not invent product-specific platform details.
                Prefer practical, operational answers: first checks, commands or configuration areas, likely cause, and escalation signals.
                Keep the answer concise but useful. Include a short "Sources" section with the source titles or domains.
                The current evidence type is %s.
                """.formatted(sourceType);
    }

    private String buildInput(String question, String sourceType, List<AiAnswerSourceVO> sources) {
        StringBuilder evidence = new StringBuilder();
        int index = 1;
        for (AiAnswerSourceVO source : sources) {
            if (evidence.length() >= MAX_EVIDENCE_CHARS) {
                break;
            }
            evidence.append("Source ").append(index++).append("\n")
                    .append("Type: ").append(nullToDash(source.getSourceType())).append("\n")
                    .append("Title: ").append(nullToDash(source.getSourceTitle())).append("\n")
                    .append("Document: ").append(nullToDash(source.getSourceDocumentName())).append("\n")
                    .append("Domain: ").append(nullToDash(source.getSourceDomain())).append("\n")
                    .append("URL: ").append(nullToDash(source.getSourceUrl())).append("\n")
                    .append("Content:\n")
                    .append(limit(source.getPreviewContent(), 2200))
                    .append("\n\n");
        }

        return """
                User question:
                %s

                Evidence source type:
                %s

                Evidence:
                %s
                """.formatted(question, sourceType, limit(evidence.toString(), MAX_EVIDENCE_CHARS));
    }

    private Optional<String> extractOutputText(JsonNode root) {
        StringBuilder text = new StringBuilder();
        root.path("output").forEach(item ->
                item.path("content").forEach(content -> {
                    String value = content.path("text").asText("");
                    if (!value.isBlank()) {
                        text.append(value).append("\n");
                    }
                })
        );
        return text.isEmpty() ? Optional.empty() : Optional.of(text.toString());
    }

    private String nullToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String limit(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength).trim();
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
