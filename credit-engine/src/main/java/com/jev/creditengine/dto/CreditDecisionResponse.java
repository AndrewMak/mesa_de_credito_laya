package com.jev.creditengine.dto;

import com.jev.creditengine.persistence.FinalDecisionStatus;

public record CreditDecisionResponse(Long proposalId, FinalDecisionStatus finalStatus, String message,
                                    String layer1Status, String layer1RuleId,
                                    String layer2Complexity, Double layer2Confidence,
                                    String layer3Decision, Double layer3Confidence) {
}
