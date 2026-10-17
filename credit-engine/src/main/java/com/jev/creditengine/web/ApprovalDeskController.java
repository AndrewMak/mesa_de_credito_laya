package com.jev.creditengine.web;

import com.jev.creditengine.approval.ApprovalDeskService;
import com.jev.creditengine.dto.HumanDecisionRequest;
import com.jev.creditengine.persistence.CreditProposal;
import com.jev.creditengine.persistence.FinalDecisionStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/credit")
public class ApprovalDeskController {

    private final ApprovalDeskService service;

    public ApprovalDeskController(ApprovalDeskService service) {
        this.service = service;
    }

    @GetMapping("/proposals")
    public List<CreditProposal> list(@RequestParam(required = false) FinalDecisionStatus status) {
        return service.list(status == null ? FinalDecisionStatus.HUMAN_REVIEW_PENDING : status);
    }

    @PatchMapping("/proposals/{id}")
    public CreditProposal decide(@PathVariable Long id, @RequestBody HumanDecisionRequest req) {
        return service.decide(id, req.decision(), req.note());
    }

    @GetMapping("/proposals/{id}/trace")
    public Map<String, Object> trace(@PathVariable Long id) {
        CreditProposal p = service.getProposal(id);
        List<Map<String, Object>> steps = new java.util.ArrayList<>();

        // Layer 1
        Map<String, Object> l1Data = new java.util.LinkedHashMap<>();
        l1Data.put("status", p.getLayer1Status());
        l1Data.put("ruleId", p.getLayer1RuleId());
        steps.add(Map.of("step", "LAYER1", "detail", "Regras DynamoDB (SpEL)", "data", l1Data));

        // Layer 2
        Map<String, Object> l2Data = new java.util.LinkedHashMap<>();
        l2Data.put("complexity", p.getLayer2Complexity());
        l2Data.put("confidence", p.getLayer2Confidence());
        if (p.getLayer2Details() != null) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> details = new com.fasterxml.jackson.databind.ObjectMapper()
                        .readValue(p.getLayer2Details(), Map.class);
                l2Data.putAll(details);
            } catch (Exception ignored) {
            }
        }
        steps.add(Map.of("step", "LAYER2", "detail", "Laya (GPU local)", "data", l2Data));

        // Layer 3
        Map<String, Object> l3Data = new java.util.LinkedHashMap<>();
        l3Data.put("decision", p.getLayer3Decision());
        l3Data.put("confidence", p.getLayer3Confidence());
        l3Data.put("fallback", p.getLayer3Fallback());
        l3Data.put("reason", p.getLayer3Reason());
        l3Data.put("opinion", p.getLayer3Opinion());
        steps.add(Map.of("step", "LAYER3", "detail", "LLM (OpenRouter)", "data", l3Data));

        // Final
        Map<String, Object> finalData = new java.util.LinkedHashMap<>();
        finalData.put("status", p.getStatus() != null ? p.getStatus().name() : "PENDING");
        finalData.put("message", p.getFinalMessage());
        steps.add(Map.of("step", "FINAL", "detail", "Decisão final", "data", finalData));

        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("proposalId", p.getId());
        result.put("clientId", p.getClientId());
        result.put("status", p.getStatus() != null ? p.getStatus().name() : "PENDING");
        result.put("message", p.getFinalMessage());
        result.put("steps", steps);
        return result;
    }
}
