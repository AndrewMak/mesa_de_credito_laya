package com.jev.creditengine.approval;

import com.jev.creditengine.persistence.CreditProposal;
import com.jev.creditengine.persistence.CreditProposalRepository;
import com.jev.creditengine.persistence.FinalDecisionStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ApprovalDeskService {

    private final CreditProposalRepository repository;

    public ApprovalDeskService(CreditProposalRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<CreditProposal> list(FinalDecisionStatus status) {
        if (status == null) {
            return repository.findAll();
        }
        return repository.findAll().stream()
                .filter(p -> p.getStatus() == status)
                .toList();
    }

    @Transactional(readOnly = true)
    public CreditProposal getProposal(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Proposal not found: " + id));
    }

    @Transactional
    public CreditProposal decide(Long id, String decision, String note) {
        CreditProposal p = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Proposal not found: " + id));
        if ("APPROVED".equalsIgnoreCase(decision)) {
            p.setStatus(FinalDecisionStatus.HUMAN_APPROVED);
        } else if ("REJECTED".equalsIgnoreCase(decision)) {
            p.setStatus(FinalDecisionStatus.HUMAN_REJECTED);
        } else {
            throw new IllegalArgumentException("Invalid decision: " + decision);
        }
        p.setFinalMessage(note == null ? "Decisao humana: " + decision : note);
        return repository.save(p);
    }
}
