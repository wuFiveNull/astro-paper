package com.astropaper.api.auth;

import com.astropaper.api.domain.entity.AuditLogEntity;
import com.astropaper.api.domain.entity.UserEntity;
import com.astropaper.api.domain.repository.AuditLogRepository;
import com.astropaper.api.domain.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class AuditLogWriter {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    public AuditLogWriter(AuditLogRepository auditLogRepository, UserRepository userRepository) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(
        Authentication authentication,
        String action,
        String targetType,
        Long targetId,
        Map<String, Object> details
    ) {
        if (authentication == null || !(authentication.getPrincipal() instanceof BlogUserPrincipal actor)) {
            throw new AccountNotActiveException();
        }
        UserEntity account = userRepository.findById(actor.getId())
            .orElseThrow(AccountNotActiveException::new);
        auditLogRepository.save(new AuditLogEntity(account, action, targetType, targetId, details));
    }
}
