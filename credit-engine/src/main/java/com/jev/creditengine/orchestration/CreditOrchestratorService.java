package com.jev.creditengine.orchestration;

import com.jev.creditengine.dto.ClientProfile;
import com.jev.creditengine.dto.CreditDecisionResponse;
import com.jev.creditengine.laya.LayaGateway;
import com.jev.creditengine.laya.LayaResponse;
import com.jev.creditengine.llm.Layer3Result;
import com.jev.creditengine.llm.OpenRouterGateway;
import com.jev.creditengine.persistence.CreditAuditLog;
import com.jev.creditengine.persistence.CreditAuditLogRepository;
import com.jev.creditengine.persistence.CreditProposal;
import com.jev.creditengine.persistence.CreditProposalRepository;
import com.jev.creditengine.persistence.FinalDecisionStatus;
import com.jev.creditengine.rules.Layer1Evaluator;
import com.jev.creditengine.rules.Layer1Result;
import com.jev.creditengine.rules.Layer1Status;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;

@Service
public class CreditOrchestratorService {

    private static final BigDecimal HIGH_AMOUNT_THRESHOLD = new BigDecimal("100000");
    private static final double LOW_CONFIDENCE_THRESHOLD = 0.6;

    private final CreditProposalRepository proposalRepository;
    private final CreditAuditLogRepository auditLogRepository;
    private final Layer1Evaluator layer1Engine;
    private final LayaGateway layaClient;
    private final OpenRouterGateway openRouterAnalyzer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CreditOrchestratorService(CreditProposalRepository proposalRepository,
                                    CreditAuditLogRepository auditLogRepository,
                                    Layer1Evaluator layer1Engine,
                                    LayaGateway layaClient,
                                    OpenRouterGateway openRouterAnalyzer) {
        this.proposalRepository = proposalRepository;
        this.auditLogRepository = auditLogRepository;
        this.layer1Engine = layer1Engine;
        this.layaClient = layaClient;
        this.openRouterAnalyzer = openRouterAnalyzer;
    }

    @Transactional
    public CreditDecisionResponse decide(ClientProfile profile) {
        CreditProposal proposal = new CreditProposal();
        proposal.setClientId(profile.clientId());
        proposal.setScore(profile.score());
        proposal.setIncome(BigDecimal.valueOf(profile.income()));
        proposal.setHasActiveRestrictive(profile.hasActiveRestrictive());
        proposal.setHasRecentBankruptcy(profile.hasRecentBankruptcy());
        proposal.setRequestedAmount(BigDecimal.valueOf(profile.requestedAmount()));
        proposal.setStatus(FinalDecisionStatus.PENDING);
        proposal = proposalRepository.save(proposal);

        String description = describe(profile);
        Map<String, Object> structured = structuredData(profile);

        // ---- Camada 1: regras determinísticas (DynamoDB) ----
        Layer1Result layer1 = layer1Engine.evaluate(profile);
        proposal.setLayer1Status(layer1.status().name());
        proposal.setLayer1RuleId(layer1.triggeredRuleId());
        audit(proposal.getId(), "LAYER1", layer1.status() + " | rule=" + layer1.triggeredRuleId()
                + " | " + layer1.message());
        proposalRepository.save(proposal);

        if (layer1.status() == Layer1Status.APPROVED) {
            finish(proposal, FinalDecisionStatus.AUTO_APPROVED, layer1.message());
            return toResponse(proposal);
        }
        if (layer1.status() == Layer1Status.REJECTED) {
            finish(proposal, FinalDecisionStatus.REJECTED, layer1.message());
            return toResponse(proposal);
        }

        // ---- Camada 2: classificador local (Laya) - multi-signal typed decisions ----
        String complexity = "HIGH";
        Double layaConfidence = null;
        Double riskScore = 3.0;
        Double fraudProb = 0.0;
        String incomeStability = "IRREGULAR";
        String docsOk = "false";
        String layaRouting = "HUMAN";
        String layaRoutingReason = "error";
        Map<String, Object> layaAnswers = new java.util.HashMap<>();

        try {
            LayaResponse laya = layaClient.classify(description, structured);
            if (laya != null) {
                complexity = laya.complexity() == null ? "HIGH" : laya.complexity().toUpperCase();
                layaConfidence = laya.confidence();
                layaAnswers = new java.util.HashMap<>(laya.answers());

                LayaResponse.Answer riskAnswer = laya.answers().get("risk_level");
                if (riskAnswer != null && riskAnswer.score() != null) {
                    riskScore = riskAnswer.score();
                }
                LayaResponse.Answer fraudAnswer = laya.answers().get("fraud_risk");
                if (fraudAnswer != null && fraudAnswer.noul() != null) {
                    fraudProb = fraudAnswer.noul();
                }
                LayaResponse.Answer incomeAnswer = laya.answers().get("income_stability");
                if (incomeAnswer != null && incomeAnswer.choice() != null) {
                    incomeStability = incomeAnswer.choice();
                }
                LayaResponse.Answer docsAnswer = laya.answers().get("documentation");
                if (docsAnswer != null && docsAnswer.noul() != null) {
                    docsOk = docsAnswer.noul() > 0.5 ? "true" : "false";
                }
                layaRouting = laya.routing();
                layaRoutingReason = laya.routingReason();
            }
        } catch (Exception ex) {
            complexity = "HIGH";
            audit(proposal.getId(), "LAYER2_ERROR", ex.getMessage());
        }
        proposal.setLayer2Complexity(complexity);
        proposal.setLayer2Confidence(layaConfidence);
        Map<String, Object> l2Details = new java.util.LinkedHashMap<>();
        l2Details.put("answers", layaAnswers);
        l2Details.put("routing", layaRouting);
        l2Details.put("routingReason", layaRoutingReason);
        l2Details.put("riskScore", riskScore);
        l2Details.put("fraudProb", fraudProb);
        l2Details.put("incomeStability", incomeStability);
        l2Details.put("docsOk", docsOk);
        proposal.setLayer2Details(toJson(l2Details));
        audit(proposal.getId(), "LAYER2", "complexity=" + complexity
                + " confidence=" + layaConfidence
                + " riskScore=" + riskScore
                + " fraudProb=" + fraudProb
                + " income=" + incomeStability
                + " docs=" + docsOk
                + " routing=" + layaRouting
                + " reason=" + layaRoutingReason);
        proposalRepository.save(proposal);

        boolean highAmount = proposal.getRequestedAmount().compareTo(HIGH_AMOUNT_THRESHOLD) >= 0;

        if (highAmount) {
            finish(proposal, FinalDecisionStatus.HUMAN_REVIEW_PENDING,
                    "Valor solicitado alto - revisao humana");
            return toResponse(proposal);
        }

        // Multi-signal routing based on Laya typed decisions + confidence bands
        if ("HUMAN".equals(layaRouting) || fraudProb > 0.5 || riskScore >= 4.0
                || "UNVERIFIABLE".equals(incomeStability)
                || (layaConfidence != null && layaConfidence < 0.60)) {
            finish(proposal, FinalDecisionStatus.HUMAN_REVIEW_PENDING,
                    "Laya: " + layaRoutingReason);
            return toResponse(proposal);
        }

        // ---- Camada 3: LLM avançada (OpenRouter) ----
        Layer3Result layer3 = openRouterAnalyzer.analyze(description);
        proposal.setLayer3Opinion(layer3.technicalOpinion());
        proposal.setLayer3Decision(layer3.suggestedDecision());
        proposal.setLayer3Confidence(layer3.confidence());
        proposal.setLayer3Fallback(layer3.fallback());
        proposal.setLayer3Reason(layer3.reason());
        audit(proposal.getId(), "LAYER3",
                "decision=" + layer3.suggestedDecision() + " confidence=" + layer3.confidence()
                        + " fallback=" + layer3.fallback() + " reason=" + layer3.reason());
        proposalRepository.save(proposal);

        if (layer3.fallback() || layer3.confidence() < LOW_CONFIDENCE_THRESHOLD || highAmount) {
            finish(proposal, FinalDecisionStatus.HUMAN_REVIEW_PENDING,
                    "Trava de seguranca: fallback/confianca baixa/valor alto");
            return toResponse(proposal);
        }

        if ("APPROVED".equalsIgnoreCase(layer3.suggestedDecision())) {
            finish(proposal, FinalDecisionStatus.LLM_APPROVED, layer3.technicalOpinion());
        } else if ("REJECTED".equalsIgnoreCase(layer3.suggestedDecision())) {
            finish(proposal, FinalDecisionStatus.REJECTED, layer3.technicalOpinion());
        } else {
            finish(proposal, FinalDecisionStatus.HUMAN_REVIEW_PENDING, layer3.technicalOpinion());
        }
        return toResponse(proposal);
    }

    private void finish(CreditProposal proposal, FinalDecisionStatus status, String message) {
        proposal.setStatus(status);
        proposal.setFinalMessage(message);
        proposalRepository.save(proposal);
        audit(proposal.getId(), "FINAL", status + " | " + message);
    }

    private void audit(Long proposalId, String step, String detail) {
        CreditAuditLog log = new CreditAuditLog();
        log.setProposalId(proposalId);
        log.setStep(step);
        log.setDetail(detail);
        auditLogRepository.save(log);
    }

    private CreditDecisionResponse toResponse(CreditProposal proposal) {
        return new CreditDecisionResponse(
                proposal.getId(),
                proposal.getStatus(),
                proposal.getFinalMessage(),
                proposal.getLayer1Status(),
                proposal.getLayer1RuleId(),
                proposal.getLayer2Complexity(),
                proposal.getLayer2Confidence(),
                proposal.getLayer3Decision(),
                proposal.getLayer3Confidence()
        );
    }

    private String describe(ClientProfile profile) {
        StringBuilder narrative = new StringBuilder();
        boolean clean = !profile.hasActiveRestrictive() && !profile.hasRecentBankruptcy();
        if (clean && profile.income() >= 4000) {
            narrative.append("Perfil simples, limpo, sinais consistentes, baixo risco.");
        } else if (clean) {
            narrative.append("Perfil com sinais mistos, renda moderada, zona cinzenta, precisa revisao.");
        } else {
            narrative.append("Perfil complexo, sinais mistos, ambiguo, precisa revisao humana.");
        }

        return "Proposta de credito - cliente=" + profile.clientId()
                + ", score=" + profile.score()
                + ", renda=" + profile.income()
                + ", valor_solicitado=" + profile.requestedAmount()
                + ", restritivo_ativo=" + profile.hasActiveRestrictive()
                + ", falencia_recente=" + profile.hasRecentBankruptcy()
                + ". " + narrative
                + " Texto descritivo: " + profile.clientId() + " solicita "
                + profile.requestedAmount() + " com score " + profile.score() + ".";
    }

    private Map<String, Object> structuredData(ClientProfile profile) {
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("client_id", profile.clientId());
        data.put("score", profile.score());
        data.put("income", profile.income());
        data.put("requested_amount", profile.requestedAmount());
        data.put("has_active_restrictive", profile.hasActiveRestrictive());
        data.put("has_recent_bankruptcy", profile.hasRecentBankruptcy());
        return data;
    }

    private String toJson(Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            return "{}";
        }
    }
}
