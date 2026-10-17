package com.jev.creditengine.rules;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class CreditRulesSeeder implements ApplicationRunner {

    private final CreditRulesDynamoRepository repository;
    private final CreditRulesCache cache;

    public CreditRulesSeeder(CreditRulesDynamoRepository repository, CreditRulesCache cache) {
        this.repository = repository;
        this.cache = cache;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!repository.isEmpty()) {
            cache.reload();
            return;
        }

        seed("HARD_CUTOFF", "ACTIVE_RESTRICTIVE", "Restritivo ativo",
                "#client.hasActiveRestrictive() == true", "REJECTED", 100,
                "Cliente com restritivo cadastral ativo -> rejeicao automatica");
        seed("HARD_CUTOFF", "RECENT_BANKRUPTCY", "Falencia recente",
                "#client.hasRecentBankruptcy() == true", "REJECTED", 95,
                "Cliente com falencia recente -> rejeicao automatica");
        seed("HARD_CUTOFF", "LOW_SCORE", "Score muito baixo",
                "#client.score() < 300", "REJECTED", 90,
                "Score abaixo do piso minimo -> rejeicao automatica");

        seed("AUTO_APPROVAL", "HIGH_SCORE", "Score excelente",
                "#client.score() > 850", "APPROVED", 80,
                "Score excelente -> aprovacao automatica");
        seed("AUTO_APPROVAL", "LOW_RISK", "Baixo risco",
                "#client.score() >= 700 && #client.income() >= 5000", "APPROVED", 70,
                "Score e renda compatíveis -> aprovacao automatica");

        cache.reload();
    }

    private void seed(String category, String id, String name, String expression, String result,
                      int priority, String description) {
        CreditRule rule = new CreditRule();
        rule.setRuleCategory(category);
        rule.setRuleId(id);
        rule.setRuleName(name);
        rule.setExpression(expression);
        rule.setResult(result);
        rule.setPriority(priority);
        rule.setActive(true);
        rule.setDescription(description);
        repository.save(rule);
    }
}
