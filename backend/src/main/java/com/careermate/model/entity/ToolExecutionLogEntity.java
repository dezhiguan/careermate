package com.careermate.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.careermate.mybatis.JsonbStringTypeHandler;
import lombok.Data;

import java.time.OffsetDateTime;

/** B2：工具执行日志。 */
@Data
@TableName(value = "tool_execution_log", autoResultMap = true)
public class ToolExecutionLogEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String batchId;
    private String callId;
    private String toolName;
    private Integer attemptNo;

    @TableField(value = "depends_on", typeHandler = JsonbStringTypeHandler.class)
    private String dependsOn;

    private String status;
    private Integer durationMs;
    private String resultSummary;
    private String errorCode;
    private String errorMessage;
    private String traceId;
    private OffsetDateTime createdAt;
}
