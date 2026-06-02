package com.careermate.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.careermate.mybatis.JsonbStringTypeHandler;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName(value = "agent_tool_calls", autoResultMap = true)
public class AgentToolCallEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;
    private Long userId;
    private Long messageId;
    private String toolName;
    private String toolLayer;
    @TableField(value = "request_params_summary", typeHandler = JsonbStringTypeHandler.class)
    private String requestParamsSummary;

    @TableField(value = "response_summary", typeHandler = JsonbStringTypeHandler.class)
    private String responseSummary;
    private String status;
    private Long latencyMs;
    private Long ragLatencyMs;
    private Boolean fallbackUsed;
    private String errorCode;
    private OffsetDateTime createdAt;
}
