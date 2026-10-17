package com.jev.creditengine.rules;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CreditRulesCache {

    private final CreditRulesDynamoRepository repository;
    private volatile List<CreditRule> rules = List.of();

    public CreditRulesCache(CreditRulesDynamoRepository repository) {
        this.repository = repository;
    }

    public synchronized void reload() {
        this.rules = repository.findAll().stream()
                .filter(CreditRule::isActive)
                .toList();
    }

    public List<CreditRule> getRules() {
        if (rules.isEmpty()) {
            reload();
        }
        return rules;
    }

    @Scheduled(fixedDelay = 300_000)
    public void refresh() {
        reload();
    }
}
