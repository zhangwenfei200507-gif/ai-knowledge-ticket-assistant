package com.zwf.aisupport.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.zwf.aisupport.vo.AiAnswerSourceVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiResponsesLlmGatewayServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;
    private String baseUrl;
    private final AtomicReference<JsonNode> requestPayload = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicInteger requestCount = new AtomicInteger();
    private int responseStatus;
    private String responseBody;

    @BeforeEach
    void startServer() throws IOException {
        responseStatus = 200;
        responseBody = "{\"output_text\":\"## Direct Answer\\nUse the verified evidence.\"}";
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/responses", exchange -> {
            requestCount.incrementAndGet();
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestPayload.set(objectMapper.readTree(exchange.getRequestBody()));
            byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(responseStatus, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void sendsBoundedGroundedRequestWithoutPersistingProviderState() {
        OpenAiResponsesLlmGatewayService gateway = gateway();
        AiAnswerSourceVO source = new AiAnswerSourceVO();
        source.setSourceType("ARTICLE");
        source.setSourceTitle("Internal article");
        source.setPreviewContent("Verified operational evidence.");

        var answer = gateway.generateGroundedAnswer("What should I check?", "ARTICLE", List.of(source));

        assertThat(answer).hasValueSatisfying(text -> assertThat(text).contains("Use the verified evidence."));
        assertThat(requestCount).hasValue(1);
        assertThat(authorization).hasValue("Bearer test-only-key");
        assertThat(requestPayload.get().path("model").asText()).isEqualTo("test-model");
        assertThat(requestPayload.get().path("store").asBoolean()).isFalse();
        assertThat(requestPayload.get().path("max_output_tokens").asInt()).isEqualTo(500);
        assertThat(gateway.getStatus().getSuccessfulCalls()).isEqualTo(1);
        assertThat(gateway.getStatus().toString()).doesNotContain("test-only-key");
    }

    @Test
    void classifiesRateLimitWithOneBoundedRetry() {
        responseStatus = 429;
        responseBody = "{\"error\":{\"message\":\"rate limited\"}}";

        var answer = gateway().generateDirectAnswer("Why is Redis unavailable?");

        assertThat(answer).isEmpty();
        assertThat(requestCount).hasValue(2);
    }

    private OpenAiResponsesLlmGatewayService gateway() {
        return new OpenAiResponsesLlmGatewayService(
                objectMapper, "test-only-key", baseUrl, "test-model", 4800, 1200, 500, 1, 0, "low", "", "");
    }
}
