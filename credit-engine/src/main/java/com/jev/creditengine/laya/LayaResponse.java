package com.jev.creditengine.laya;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LayaResponse(
    String complexity,
    Double confidence,
    Map<String, Map<String, Double>> probabilities,
    Map<String, Answer> answers,
    @JsonProperty("routing") String routing,
    @JsonProperty("routing_reason") String routingReason,
    String model,
    Double latencyMs
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Answer(
        String type,
        Double confidence,
        Map<String, Double> probabilities,
        String choice,
        Double score,
        Double noul
    ) {
    }
}
