package com.careermate.audit;

public interface AuditService {

    void recordSuccess(Long userId, AuditActionType actionType, String resourceType, String resourceId, String actionDetail);

    void recordFailure(Long userId, AuditActionType actionType, String resourceType, String resourceId, String failureReason);
}
