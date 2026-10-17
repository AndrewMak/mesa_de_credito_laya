package com.jev.creditengine.llm;

import com.jev.creditengine.config.OpenRouterProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class OpenRouterClient {

    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";

    private final WebClient webClient;
    private final OpenRouterProperties properties;

    public OpenRouterClient(WebClient openRouterWebClient, OpenRouterProperties properties) {
        this.webClient = openRouterWebClient;
        this.properties = properties;
    }

    public String complete(String systemPrompt, String userPrompt) {
        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> system = new HashMap<>();
        system.put("role", "system");
        system.put("content", systemPrompt);
        messages.add(system);
        Map<String, String> user = new HashMap<>();
        user.put("role", "user");
        user.put("content", userPrompt);
        messages.add(user);

        Map<String, Object> body = new HashMap<>();
        body.put("model", properties.getModel());
        body.put("messages", messages);
        body.put("temperature", 0.1);
        body.put("response_format", Map.of("type", "json_object"));

        return webClient.post()
                .uri(CHAT_COMPLETIONS_PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                .header("HTTP-Referer", "http://localhost:8082")
                .header("X-Title", "Credit Decision Engine")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(byte[].class)
                .map(b -> new String(b, StandardCharsets.UTF_8))
                .block();
    }
}
