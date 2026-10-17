package com.jev.creditengine.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "credit_proposals")
public class CreditProposal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id", nullable = false, length = 64)
    private String clientId;

    @Column(name = "score", nullable = false)
    private int score;

    @Column(name = "income", nullable = false, precision = 19, scale = 2)
    private BigDecimal income;

    @Column(name = "has_active_restrictive", nullable = false)
    private boolean hasActiveRestrictive;

    @Column(name = "has_recent_bankruptcy", nullable = false)
    private boolean hasRecentBankruptcy;

    @Column(name = "requested_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal requestedAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private FinalDecisionStatus status = FinalDecisionStatus.PENDING;

    @Column(name = "layer1_status", length = 20)
    private String layer1Status;

    @Column(name = "layer1_rule_id", length = 64)
    private String layer1RuleId;

    @Column(name = "layer2_complexity", length = 8)
    private String layer2Complexity;

    @Column(name = "layer2_confidence")
    private Double layer2Confidence;

    @Column(name = "layer3_opinion", columnDefinition = "TEXT")
    private String layer3Opinion;

    @Column(name = "layer3_decision", length = 24)
    private String layer3Decision;

    @Column(name = "layer3_confidence")
    private Double layer3Confidence;

    @Column(name = "layer3_fallback")
    private Boolean layer3Fallback;

    @Column(name = "layer3_reason", length = 255)
    private String layer3Reason;

    @Column(name = "layer2_details", columnDefinition = "TEXT")
    private String layer2Details;

    @Column(name = "final_message", columnDefinition = "TEXT")
    private String finalMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    protected void preUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public BigDecimal getIncome() {
        return income;
    }

    public void setIncome(BigDecimal income) {
        this.income = income;
    }

    public boolean isHasActiveRestrictive() {
        return hasActiveRestrictive;
    }

    public void setHasActiveRestrictive(boolean hasActiveRestrictive) {
        this.hasActiveRestrictive = hasActiveRestrictive;
    }

    public boolean isHasRecentBankruptcy() {
        return hasRecentBankruptcy;
    }

    public void setHasRecentBankruptcy(boolean hasRecentBankruptcy) {
        this.hasRecentBankruptcy = hasRecentBankruptcy;
    }

    public BigDecimal getRequestedAmount() {
        return requestedAmount;
    }

    public void setRequestedAmount(BigDecimal requestedAmount) {
        this.requestedAmount = requestedAmount;
    }

    public FinalDecisionStatus getStatus() {
        return status;
    }

    public void setStatus(FinalDecisionStatus status) {
        this.status = status;
    }

    public String getLayer1Status() {
        return layer1Status;
    }

    public void setLayer1Status(String layer1Status) {
        this.layer1Status = layer1Status;
    }

    public String getLayer1RuleId() {
        return layer1RuleId;
    }

    public void setLayer1RuleId(String layer1RuleId) {
        this.layer1RuleId = layer1RuleId;
    }

    public String getLayer2Complexity() {
        return layer2Complexity;
    }

    public void setLayer2Complexity(String layer2Complexity) {
        this.layer2Complexity = layer2Complexity;
    }

    public Double getLayer2Confidence() {
        return layer2Confidence;
    }

    public void setLayer2Confidence(Double layer2Confidence) {
        this.layer2Confidence = layer2Confidence;
    }

    public String getLayer3Opinion() {
        return layer3Opinion;
    }

    public void setLayer3Opinion(String layer3Opinion) {
        this.layer3Opinion = layer3Opinion;
    }

    public String getLayer3Decision() {
        return layer3Decision;
    }

    public void setLayer3Decision(String layer3Decision) {
        this.layer3Decision = layer3Decision;
    }

    public Double getLayer3Confidence() {
        return layer3Confidence;
    }

    public void setLayer3Confidence(Double layer3Confidence) {
        this.layer3Confidence = layer3Confidence;
    }

    public Boolean getLayer3Fallback() {
        return layer3Fallback;
    }

    public void setLayer3Fallback(Boolean layer3Fallback) {
        this.layer3Fallback = layer3Fallback;
    }

    public String getLayer3Reason() {
        return layer3Reason;
    }

    public void setLayer3Reason(String layer3Reason) {
        this.layer3Reason = layer3Reason;
    }

    public String getLayer2Details() {
        return layer2Details;
    }

    public void setLayer2Details(String layer2Details) {
        this.layer2Details = layer2Details;
    }

    public String getFinalMessage() {
        return finalMessage;
    }

    public void setFinalMessage(String finalMessage) {
        this.finalMessage = finalMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
