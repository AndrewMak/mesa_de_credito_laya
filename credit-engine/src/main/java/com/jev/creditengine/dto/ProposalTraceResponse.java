package com.jev.creditengine.dto;

import java.util.List;
import java.util.Map;

public record ProposalTraceResponse(
    Long proposalId,
    String clientId,
    FinalTraceStatus status,
    String message,
    List<TraceStep> steps
) {
    public record FinalTraceStatus(String name) {}

    public record TraceStep(
        String step,
        String detail,
        Map<String, Object> data
    ) {
    }
}
