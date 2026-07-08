package com.careermate.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.careermate.mybatis.JsonbStringTypeHandler;
import lombok.Data;

import java.time.OffsetDateTime;

/** A3 反思闭环：每轮 plan。 */
@Data
@TableName(value = "agent_plan", autoResultMap = true)
public class AgentPlanEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String runId;
    private Integer roundNo;

    @TableField(value = "goals", typeHandler = JsonbStringTypeHandler.class)
    private String goals;

    @TableField(value = "subgoals", typeHandler = JsonbStringTypeHandler.class)
    private String subgoals;

    @TableField(value = "success_criteria", typeHandler = JsonbStringTypeHandler.class)
    private String successCriteria;

    private Long revisedFrom;
    private String sanityStatus;
    private String sanityReason;
    private OffsetDateTime createdAt;
}
