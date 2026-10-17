package com.jev.creditengine.rules;

import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class CreditRuleEngine implements Layer1Evaluator {

    private final CreditRulesCache cache;
    private final ExpressionParser parser = new SpelExpressionParser();

    public CreditRuleEngine(CreditRulesCache cache) {
        this.cache = cache;
    }

    public Layer1Result evaluate(com.jev.creditengine.dto.ClientProfile client) {
        StandardEvaluationContext context = new StandardEvaluationContext();
        context.setVariable("client", client);

        List<CreditRule> ordered = cache.getRules().stream()
                .sorted(Comparator.comparingInt(CreditRule::getPriority).reversed())
                .toList();

        String triggeredId = null;
        String triggeredName = null;
        boolean rejected = false;
        boolean approved = false;

        for (CreditRule rule : ordered) {
            if (!rule.isActive()) {
                continue;
            }
            try {
                Expression expr = parser.parseExpression(rule.getExpression());
                Boolean matched = expr.getValue(context, Boolean.class);
                if (Boolean.TRUE.equals(matched)) {
                    if ("REJECTED".equalsIgnoreCase(rule.getResult())) {
                        triggeredId = rule.getRuleId();
                        triggeredName = rule.getRuleName();
                        rejected = true;
                        break;
                    }
                    if ("APPROVED".equalsIgnoreCase(rule.getResult())) {
                        if (triggeredId == null) {
                            triggeredId = rule.getRuleId();
                            triggeredName = rule.getRuleName();
                        }
                        approved = true;
                    }
                }
            } catch (Exception ex) {
                // skip malformed rule, log later
            }
        }

        if (rejected) {
            return new Layer1Result(Layer1Status.REJECTED, triggeredId, triggeredName,
                    "Hard cutoff rule triggered");
        }
        if (approved) {
            return new Layer1Result(Layer1Status.APPROVED, triggeredId, triggeredName,
                    "Auto approval rule triggered");
        }
        return new Layer1Result(Layer1Status.GRAY_ZONE, null, null,
                "No deterministic rule matched - gray zone");
    }
}
