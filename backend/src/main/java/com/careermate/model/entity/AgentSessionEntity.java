package com.careermate.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.careermate.mybatis.JsonbStringTypeHandler;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@TableName(value = "agent_sessions", autoResultMap = true)
public class AgentSessionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String sessionId;
    private Long userId;
    private String status;
    private String intent;
    private String taskType;
    private String title;
    private Long totalLatencyMs;
    private Long llmLatencyMs;
    private Integer inputTokens;
    private Integer outputTokens;
    private BigDecimal estimatedCost;
    private String modelProvider;
    private String modelName;
    private Integer toolCallCount;
    private String errorCode;
    private String workspaceType;
    private String jdId;
    @TableField(value = "jd_snapshot", typeHandler = JsonbStringTypeHandler.class)
    private String jdSnapshot;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
