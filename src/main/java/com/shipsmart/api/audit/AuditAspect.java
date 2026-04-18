package com.shipsmart.api.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shipsmart.api.auth.AuthHelper;
import com.shipsmart.api.domain.AuditLog;
import com.shipsmart.api.dto.ShipmentSummaryDto;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ExecutorService;

@Aspect
@Component
public class AuditAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);

    private final AuditLogRepository repo;
    private final ExecutorService auditExecutor;
    private final ObjectMapper mapper;

    public AuditAspect(AuditLogRepository repo,
                       ExecutorService auditExecutor,
                       ObjectMapper mapper) {
        this.repo = repo;
        this.auditExecutor = auditExecutor;
        this.mapper = mapper;
    }

    @Around("@annotation(audited)")
    public Object audit(ProceedingJoinPoint pjp, Audited audited) throws Throwable {
        String requestId = MDC.get("requestId");
        String userIdStr = AuthHelper.getUserId().orElse(null);
        Object result = pjp.proceed();
        try {
            AuditLog row = new AuditLog();
            row.setAction(audited.action());
            row.setEntity(audited.entity());
            row.setRequestId(requestId);
            if (userIdStr != null) {
                try { row.setUserId(UUID.fromString(userIdStr)); } catch (IllegalArgumentException ignored) {}
            }
            if (result instanceof ShipmentSummaryDto dto) {
                row.setEntityId(dto.id());
                row.setDiff(mapper.writeValueAsString(dto));
            } else if (result != null) {
                row.setDiff(mapper.writeValueAsString(result));
            }
            auditExecutor.submit(() -> {
                try {
                    repo.save(row);
                } catch (Exception e) {
                    log.warn("Audit write failed (non-fatal) for {} {}: {}",
                            audited.action(), audited.entity(), e.getMessage());
                }
            });
        } catch (Exception e) {
            log.warn("Audit capture failed for {}#{}: {}",
                    ((MethodSignature) pjp.getSignature()).getMethod().getName(),
                    audited.action(), e.getMessage());
        }
        return result;
    }
}
