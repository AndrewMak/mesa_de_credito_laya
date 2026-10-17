package com.jev.creditengine.llm;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jev.creditengine.config.OpenRouterProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Map;

@Service
public class OpenRouterCreditAnalyzer implements OpenRouterGateway {

    private static final String SYSTEM_PROMPT = """
            You are a senior credit risk analyst. Analyze the credit proposal provided
            and return a strictly structured JSON object with the following fields:
            - parecer_tecnico: a concise technical opinion in Portuguese.
            - sugestao_decisao: one of APPROVED, REJECTED, GRAY_ZONE.
            - nivel_confianca: a number between 0 and 1 representing your confidence.
            Do not include any text outside the JSON object.
            """;

    private final OpenRouterClient client;
    private final OpenRouterProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OpenRouterCreditAnalyzer(OpenRouterClient client, OpenRouterProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    public Layer3Result analyze(String proposalText) {
        if (!properties.isConfigured()) {
            return new Layer3Result("", "HUMAN_REVIEW", 0.0, true,
                    "OpenRouter API key not configured");
        }
        try {
            String raw = client.complete(SYSTEM_PROMPT, proposalText);
            String content = extractContent(raw);
            Map<String, Object> parsed = extractJson(content);
            String opinion = stringValue(parsed.get("parecer_tecnico"));
            String suggestion = stringValue(parsed.get("sugestao_decisao"));
            double confidence = doubleValue(parsed.get("nivel_confianca"));
            if (suggestion.isBlank()) {
                suggestion = "GRAY_ZONE";
            }
            return new Layer3Result(opinion, suggestion, confidence, false, "OK");
        } catch (WebClientResponseException e) {
            if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                return new Layer3Result("", "HUMAN_REVIEW", 0.0, true, "RATE_LIMITED (HTTP 429)");
            }
            return new Layer3Result("", "HUMAN_REVIEW", 0.0, true,
                    "HTTP " + e.getStatusCode().value() + ": " + e.getMessage());
        } catch (Exception e) {
            return new Layer3Result("", "HUMAN_REVIEW", 0.0, true,
                    "ERROR: " + e.getMessage());
        }
    }

    private Map<String, Object> extractJson(String raw) throws Exception {
        String json = raw;
        int first = raw.indexOf('{');
        int last = raw.lastIndexOf('}');
        if (first >= 0 && last > first) {
            json = raw.substring(first, last + 1);
        }
        return objectMapper.readValue(json, new TypeReference<>() {
        });
    }

    private String extractContent(String raw) {
        try {
            JsonNode root = objectMapper.readTree(raw);
            JsonNode choices = root.path("choices");
            if (choices.isArray() && choices.size() > 0) {
                return choices.get(0).path("message").path("content").asText();
            }
        } catch (Exception ignored) {
        }
        return raw;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private double doubleValue(Object value) {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        if (value != null) {
            try {
                return Double.parseDouble(String.valueOf(value));
            } catch (NumberFormatException ignored) {
            }
        }
        return 0.0;
    }
}
