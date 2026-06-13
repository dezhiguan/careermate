package com.careermate.audit.service.impl;

import com.careermate.audit.AuditActionType;
import com.careermate.audit.service.AuditService;
import com.careermate.mapper.SecurityAuditLogMapper;
import com.careermate.model.entity.SecurityAuditLogEntity;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Slf4j
@Service
public class AuditServiceImpl implements AuditService {

    private final SecurityAuditLogMapper securityAuditLogMapper;

    public AuditServiceImpl(SecurityAuditLogMapper securityAuditLogMapper) {
        this.securityAuditLogMapper = securityAuditLogMapper;
    }

    @Override
    public void recordSuccess(Long userId, AuditActionType actionType, String resourceType, String resourceId, String actionDetail) {
        save(userId, actionType, resourceType, resourceId, actionDetail, true, null);
    }

    @Override
    public void recordFailure(Long userId, AuditActionType actionType, String resourceType, String resourceId, String failureReason) {
        save(userId, actionType, resourceType, resourceId, null, false, failureReason);
    }

    private void save(
            Long userId,
            AuditActionType actionType,
            String resourceType,
            String resourceId,
            String actionDetail,
            boolean success,
            String failureReason
    ) {
        try {
            SecurityAuditLogEntity entity = new SecurityAuditLogEntity();
            entity.setUserId(userId);
            entity.setActionType(actionType.name());
            entity.setActionDetail(actionDetail);
            entity.setResourceType(resourceType);
            entity.setResourceId(resourceId);
            entity.setSuccess(success);
            entity.setFailureReason(failureReason);

            HttpServletRequest request = currentRequest();
            if (request != null) {
                entity.setIpAddress(request.getRemoteAddr());
                entity.setUserAgent(request.getHeader("User-Agent"));
            }
            securityAuditLogMapper.insert(entity);
        } catch (Exception e) {
            log.warn("Failed to persist security audit log: actionType={}", actionType, e);
        }
    }

    private HttpServletRequest currentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletRequestAttributes) {
            return servletRequestAttributes.getRequest();
        }
        return null;
    }
}
