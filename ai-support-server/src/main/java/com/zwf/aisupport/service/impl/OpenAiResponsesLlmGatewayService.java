package com.zwf.aisupport.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zwf.aisupport.common.util.TextUtil;
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
    public Optional<String> generateDirectAnswer(String question) {
        if (!isConfigured() || question == null || question.isBlank()) {
            return Optional.empty();
        }

        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", model);
            payload.put("instructions", """
                    You are AegisCloud, a CloudOps AI knowledge assistant.
                    Internal knowledge and external web search did not find a reliable answer.
                    Use general CloudOps, Linux, Kubernetes, Docker, Redis, PostgreSQL, Spring Security, and authentication knowledge to provide a careful fallback answer.
                    Do not invent AegisCloud-specific hostnames, dashboards, owners, secrets, service names, or production facts.
                    Always answer the user's question first.
                    Explain the likely cause before listing troubleshooting steps.
                    Synthesize the answer in your own words. Do not copy or quote retrieved text.
                    Never expose internal article titles, chunk names, runbook names, SOP names, retrieval metadata, or approval workflow details.
                    Keep the user-facing answer short and decisive: 3 to 5 bullets maximum.
                    If uncertain, explicitly say what must be verified.
                    """);
            payload.put("input", "User question:\n" + question);

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
        if ("WEB".equals(sourceType)) {
            return """
                    You are AegisCloud, a CloudOps AI knowledge assistant.
                    The user's internal knowledge base did not contain a reliable answer, so external web evidence was collected.
                    Use the supplied web evidence as the primary grounding, and you may use general CloudOps, Linux, Kubernetes, Docker, Redis, PostgreSQL, Spring Security, and authentication knowledge to make the answer complete.
                    Do not invent AegisCloud-specific hostnames, dashboards, owners, secrets, service names, or production facts.
                    Always answer the user's question first.
                    Explain the likely cause before listing troubleshooting steps.
                    Synthesize the answer in your own words. Do not copy or quote retrieved pages, articles, chunks, or raw evidence.
                    Never expose internal article titles, chunk names, runbook names, SOP names, retrieval metadata, candidate labels, or approval workflow details.
                    Prefer practical, operational answers: exact checks, commands, configuration areas, likely causes, safe next steps, and escalation signals.
                    If the evidence is weak or only partly related, say what is uncertain and separate verified guidance from assumptions.
                    Answer in the same language as the user's question when possible.
                    Keep the user-facing answer short and decisive: 3 to 5 bullets maximum.
                    Do not paste long evidence, raw article text, SQL tables, or review notes into the answer.
                    Use a table only when it makes the answer clearer; keep it to 2 to 4 columns with short cell text.
                    Source names are optional and must stay on one short final line when useful.
                    The current evidence type is WEB.
                    """;
        }

        return """
                You are AegisCloud, a CloudOps AI knowledge assistant.
                Answer the user's question using only the supplied evidence.
                If the evidence is not enough to answer precisely, say what cannot be confirmed and what should be checked next.
                Do not invent product-specific platform details.
                Always answer the user's question first.
                Explain the likely cause before listing troubleshooting steps.
                Synthesize the answer in your own words. Do not copy or quote retrieved articles, uploaded documents, chunks, or raw evidence.
                Never expose internal article titles, chunk names, runbook names, SOP names, retrieval metadata, candidate labels, or approval workflow details.
                Prefer practical, operational answers: first checks, commands or configuration areas, likely cause, and escalation signals.
                Answer in the same language as the user's question when possible.
                Keep the user-facing answer short and decisive: 3 to 5 bullets maximum.
                Do not paste long evidence, raw article text, SQL tables, or review notes into the answer.
                Use a table only when it makes the answer clearer; keep it to 2 to 4 columns with short cell text.
                Source names are optional and must stay on one short final line when useful.
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
                    .append(TextUtil.limit(source.getPreviewContent(), 2200))
                    .append("\n\n");
        }

        return """
                User question:
                %s

                Evidence source type:
                %s

                Evidence:
                %s
                """.formatted(question, sourceType, TextUtil.limit(evidence.toString(), MAX_EVIDENCE_CHARS));
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

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
