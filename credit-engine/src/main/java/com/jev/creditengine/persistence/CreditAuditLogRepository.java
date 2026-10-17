package com.jev.creditengine.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CreditAuditLogRepository extends JpaRepository<CreditAuditLog, Long> {
}
