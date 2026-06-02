package com.careermate.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@TableName("agent_sessions")
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
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
