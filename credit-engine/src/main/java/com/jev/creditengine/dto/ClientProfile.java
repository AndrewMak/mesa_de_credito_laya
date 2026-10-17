package com.jev.creditengine.dto;

public record ClientProfile(String clientId, int score, long income, boolean hasActiveRestrictive,
                           long requestedAmount, boolean hasRecentBankruptcy) {
}
