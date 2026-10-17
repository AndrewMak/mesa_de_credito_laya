package com.jev.creditengine.orchestration;

import com.jev.creditengine.dto.ClientProfile;
import com.jev.creditengine.dto.CreditDecisionResponse;
import com.jev.creditengine.laya.LayaGateway;
import com.jev.creditengine.laya.LayaResponse;
import com.jev.creditengine.llm.Layer3Result;
import com.jev.creditengine.llm.OpenRouterGateway;
import com.jev.creditengine.persistence.CreditProposal;
import com.jev.creditengine.persistence.CreditProposalRepository;
import com.jev.creditengine.persistence.FinalDecisionStatus;
import com.jev.creditengine.rules.Layer1Evaluator;
import com.jev.creditengine.rules.Layer1Result;
import com.jev.creditengine.rules.Layer1Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreditOrchestratorServiceTest {

    @Mock
    private CreditProposalRepository proposalRepository;
    @Mock
    private com.jev.creditengine.persistence.CreditAuditLogRepository auditLogRepository;
    @Mock
    private Layer1Evaluator layer1Engine;
    @Mock
    private LayaGateway layaClient;
    @Mock
    private OpenRouterGateway openRouterAnalyzer;

    private CreditOrchestratorService service;

    @BeforeEach
    void setUp() {
        service = new CreditOrchestratorService(proposalRepository, auditLogRepository,
                layer1Engine, layaClient, openRouterAnalyzer);

        when(proposalRepository.save(any(CreditProposal.class))).thenAnswer(inv -> {
            CreditProposal p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId(1L);
            }
            return p;
        });

        when(auditLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private ClientProfile baseProfile(int score, boolean restrictive, long amount) {
        return new ClientProfile("C1", score, 5000, restrictive, amount, false);
    }

    private LayaResponse layaLowRisk() {
        return new LayaResponse(
                "LOW", 0.85,
                Map.of(),
                Map.of(
                        "risk_level", new LayaResponse.Answer("score", 0.9, Map.of(), null, 1.5, null),
                        "fraud_risk", new LayaResponse.Answer("noul", 0.95, Map.of(), null, null, 0.05),
                        "income_stability", new LayaResponse.Answer("choice", 0.9, Map.of(), "STABLE", null, null),
                        "documentation", new LayaResponse.Answer("noul", 0.9, Map.of(), null, null, 0.9),
                        "complexity", new LayaResponse.Answer("choice", 0.85, Map.of(), "LOW", null, null)
                ),
                "LLM", "Low risk, stable income, straightforward", "laya", 50.0);
    }

    private LayaResponse layaHighRisk() {
        return new LayaResponse(
                "HIGH", 0.75,
                Map.of(),
                Map.of(
                        "risk_level", new LayaResponse.Answer("score", 0.8, Map.of(), null, 4.5, null),
                        "fraud_risk", new LayaResponse.Answer("noul", 0.7, Map.of(), null, null, 0.7),
                        "income_stability", new LayaResponse.Answer("choice", 0.7, Map.of(), "UNVERIFIABLE", null, null),
                        "documentation", new LayaResponse.Answer("noul", 0.6, Map.of(), null, null, 0.4),
                        "complexity", new LayaResponse.Answer("choice", 0.75, Map.of(), "HIGH", null, null)
                ),
                "HUMAN", "High fraud risk or very high risk score", "laya", 50.0);
    }

    private LayaResponse layaLowConfidence() {
        return new LayaResponse(
                "LOW", 0.45,
                Map.of(),
                Map.of(
                        "risk_level", new LayaResponse.Answer("score", 0.5, Map.of(), null, 2.0, null),
                        "fraud_risk", new LayaResponse.Answer("noul", 0.5, Map.of(), null, null, 0.3),
                        "income_stability", new LayaResponse.Answer("choice", 0.5, Map.of(), "STABLE", null, null),
                        "documentation", new LayaResponse.Answer("noul", 0.5, Map.of(), null, null, 0.6),
                        "complexity", new LayaResponse.Answer("choice", 0.45, Map.of(), "LOW", null, null)
                ),
                "HUMAN", "Low model confidence", "laya", 50.0);
    }

    @Test
    void layer1ApprovedReturnsAutoApprovedAndSkipsOtherLayers() {
        when(layer1Engine.evaluate(any())).thenReturn(
                new Layer1Result(Layer1Status.APPROVED, "HIGH_SCORE", "Score excelente", "ok"));

        CreditDecisionResponse r = service.decide(baseProfile(900, false, 5000));

        assertEquals(FinalDecisionStatus.AUTO_APPROVED, r.finalStatus());
        verify(layaClient, never()).classify(any(), any());
        verify(openRouterAnalyzer, never()).analyze(any());
    }

    @Test
    void layer1RejectedReturnsRejected() {
        when(layer1Engine.evaluate(any())).thenReturn(
                new Layer1Result(Layer1Status.REJECTED, "LOW_SCORE", "Score muito baixo", "ok"));

        CreditDecisionResponse r = service.decide(baseProfile(250, false, 5000));

        assertEquals(FinalDecisionStatus.REJECTED, r.finalStatus());
        verify(layaClient, never()).classify(any(), any());
    }

    @Test
    void grayZoneHighAmountGoesToHumanBeforeLlm() {
        when(layer1Engine.evaluate(any())).thenReturn(
                new Layer1Result(Layer1Status.GRAY_ZONE, null, null, "gray"));
        when(layaClient.classify(any(), any())).thenReturn(layaLowRisk());

        CreditDecisionResponse r = service.decide(baseProfile(600, false, 150000));

        assertEquals(FinalDecisionStatus.HUMAN_REVIEW_PENDING, r.finalStatus());
        verify(openRouterAnalyzer, never()).analyze(any());
    }

    @Test
    void grayZoneLowRiskPassesToLlmAndReturnsLlmApproved() {
        when(layer1Engine.evaluate(any())).thenReturn(
                new Layer1Result(Layer1Status.GRAY_ZONE, null, null, "gray"));
        when(layaClient.classify(any(), any())).thenReturn(layaLowRisk());
        when(openRouterAnalyzer.analyze(any())).thenReturn(
                new Layer3Result("parecer", "APPROVED", 0.9, false, "OK"));

        CreditDecisionResponse r = service.decide(baseProfile(600, false, 5000));

        assertEquals(FinalDecisionStatus.LLM_APPROVED, r.finalStatus());
    }

    @Test
    void grayZoneLowRiskLlmFallbackGoesToHuman() {
        when(layer1Engine.evaluate(any())).thenReturn(
                new Layer1Result(Layer1Status.GRAY_ZONE, null, null, "gray"));
        when(layaClient.classify(any(), any())).thenReturn(layaLowRisk());
        when(openRouterAnalyzer.analyze(any())).thenReturn(
                new Layer3Result("", "HUMAN_REVIEW", 0.0, true, "RATE_LIMITED (HTTP 429)"));

        CreditDecisionResponse r = service.decide(baseProfile(600, false, 5000));

        assertEquals(FinalDecisionStatus.HUMAN_REVIEW_PENDING, r.finalStatus());
    }

    @Test
    void grayZoneLowRiskLowConfidenceGoesToHuman() {
        when(layer1Engine.evaluate(any())).thenReturn(
                new Layer1Result(Layer1Status.GRAY_ZONE, null, null, "gray"));
        when(layaClient.classify(any(), any())).thenReturn(layaLowRisk());
        when(openRouterAnalyzer.analyze(any())).thenReturn(
                new Layer3Result("parecer", "APPROVED", 0.4, false, "OK"));

        CreditDecisionResponse r = service.decide(baseProfile(600, false, 5000));

        assertEquals(FinalDecisionStatus.HUMAN_REVIEW_PENDING, r.finalStatus());
    }

    @Test
    void grayZoneLowRiskLlmRejectedReturnsRejected() {
        when(layer1Engine.evaluate(any())).thenReturn(
                new Layer1Result(Layer1Status.GRAY_ZONE, null, null, "gray"));
        when(layaClient.classify(any(), any())).thenReturn(layaLowRisk());
        when(openRouterAnalyzer.analyze(any())).thenReturn(
                new Layer3Result("parecer", "REJECTED", 0.9, false, "OK"));

        CreditDecisionResponse r = service.decide(baseProfile(600, false, 5000));

        assertEquals(FinalDecisionStatus.REJECTED, r.finalStatus());
    }

    @Test
    void grayZoneHighRiskGoesToHumanWithoutLlm() {
        when(layer1Engine.evaluate(any())).thenReturn(
                new Layer1Result(Layer1Status.GRAY_ZONE, null, null, "gray"));
        when(layaClient.classify(any(), any())).thenReturn(layaHighRisk());

        CreditDecisionResponse r = service.decide(baseProfile(600, false, 5000));

        assertEquals(FinalDecisionStatus.HUMAN_REVIEW_PENDING, r.finalStatus());
        verify(openRouterAnalyzer, never()).analyze(any());
    }

    @Test
    void grayZoneLowConfidenceGoesToHumanWithoutLlm() {
        when(layer1Engine.evaluate(any())).thenReturn(
                new Layer1Result(Layer1Status.GRAY_ZONE, null, null, "gray"));
        when(layaClient.classify(any(), any())).thenReturn(layaLowConfidence());

        CreditDecisionResponse r = service.decide(baseProfile(600, false, 5000));

        assertEquals(FinalDecisionStatus.HUMAN_REVIEW_PENDING, r.finalStatus());
        verify(openRouterAnalyzer, never()).analyze(any());
    }

    @Test
    void layaFailureGoesToHumanWithoutLlm() {
        when(layer1Engine.evaluate(any())).thenReturn(
                new Layer1Result(Layer1Status.GRAY_ZONE, null, null, "gray"));
        when(layaClient.classify(any(), any())).thenThrow(new RuntimeException("connection refused"));

        CreditDecisionResponse r = service.decide(baseProfile(600, false, 5000));

        assertEquals(FinalDecisionStatus.HUMAN_REVIEW_PENDING, r.finalStatus());
        verify(openRouterAnalyzer, never()).analyze(any());
    }
}
