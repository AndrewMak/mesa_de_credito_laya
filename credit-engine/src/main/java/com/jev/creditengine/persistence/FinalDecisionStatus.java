package com.jev.creditengine.persistence;

public enum FinalDecisionStatus {
    PENDING,
    AUTO_APPROVED,
    LOCAL_APPROVED,
    LLM_APPROVED,
    REJECTED,
    HUMAN_REVIEW_PENDING,
    HUMAN_APPROVED,
    HUMAN_REJECTED
}
