package com.jev.creditengine.llm;

public record Layer3Result(String technicalOpinion, String suggestedDecision,
                          double confidence, boolean fallback, String reason) {
}
