package com.jev.creditengine.laya;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
public class LayaClient implements LayaGateway {

    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LayaClient(@Value("${app.laya.url}") String baseUrl) {
        this.webClient = WebClient.builder().baseUrl(baseUrl).build();
    }

    @Override
    public LayaResponse classify(String text) {
        return classify(text, null);
    }

    public LayaResponse classify(String text, Map<String, Object> structured) {
        LayaRequest request = new LayaRequest(text, structured);
        return webClient.post()
                .uri("/classify")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(byte[].class)
                .map(b -> new String(b, StandardCharsets.UTF_8))
                .map(s -> {
                    try {
                        return objectMapper.readValue(s, LayaResponse.class);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .block();
    }
}
