package com.careermate.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.careermate.mybatis.JsonbStringTypeHandler;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName(value = "agent_task_states", autoResultMap = true)
public class AgentTaskStateEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;
    private Long userId;
    private String taskType;
    private Integer currentStep;
    private Integer totalSteps;
    @TableField(value = "state_data", typeHandler = JsonbStringTypeHandler.class)
    private String stateData;
    private String status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
