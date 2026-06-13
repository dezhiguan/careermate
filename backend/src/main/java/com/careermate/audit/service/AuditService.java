package com.careermate.audit.service;

import com.careermate.audit.AuditActionType;

public interface AuditService {

    void recordSuccess(Long userId, AuditActionType actionType, String resourceType, String resourceId, String actionDetail);

    void recordFailure(Long userId, AuditActionType actionType, String resourceType, String resourceId, String failureReason);
}
