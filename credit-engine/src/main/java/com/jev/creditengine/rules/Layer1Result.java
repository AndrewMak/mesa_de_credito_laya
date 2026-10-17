package com.jev.creditengine.rules;

public record Layer1Result(Layer1Status status, String triggeredRuleId, String triggeredRuleName, String message) {
}
