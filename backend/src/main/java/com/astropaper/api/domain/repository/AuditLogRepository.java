package com.astropaper.api.domain.repository;

import com.astropaper.api.domain.entity.AuditLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;

public interface AuditLogRepository extends JpaRepository<AuditLogEntity, Long> {

    long countByActionIn(Collection<String> actions);
}
