package com.careermate.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("security_audit_logs")
public class SecurityAuditLogEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String actionType;

    private String actionDetail;

    private String resourceType;

    private String resourceId;

    private Boolean success;

    private String failureReason;

    private String ipAddress;

    private String userAgent;

    private OffsetDateTime createdAt;
}
