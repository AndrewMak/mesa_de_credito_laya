package com.jev.creditengine.web;

import com.jev.creditengine.dto.ClientProfile;
import com.jev.creditengine.dto.CreditDecisionResponse;
import com.jev.creditengine.orchestration.CreditOrchestratorService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/credit")
public class CreditDecisionController {

    private final CreditOrchestratorService orchestrator;

    public CreditDecisionController(CreditOrchestratorService orchestrator) {
        this.orchestrator = orchestrator;
    }

    @PostMapping("/decide")
    public CreditDecisionResponse decide(@RequestBody ClientProfile profile) {
        return orchestrator.decide(profile);
    }
}
