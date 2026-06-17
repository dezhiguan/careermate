package com.careermate.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.careermate.mybatis.JsonbStringTypeHandler;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName(value = "agent_pending_actions", autoResultMap = true)
public class AgentPendingActionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String actionId;
    private Long userId;
    private String sessionId;
    private String actionType;
    private String status;
    @TableField(value = "payload", typeHandler = JsonbStringTypeHandler.class)
    private String payload;
    private OffsetDateTime expiresAt;
    private OffsetDateTime consumedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
