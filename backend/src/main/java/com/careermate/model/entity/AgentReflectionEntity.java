package com.careermate.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.careermate.mybatis.JsonbStringTypeHandler;
import lombok.Data;

import java.time.OffsetDateTime;

/** A3 反思闭环：每轮 reflector 判定。 */
@Data
@TableName(value = "agent_reflection", autoResultMap = true)
public class AgentReflectionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String runId;
    private Integer roundNo;
    private Long planId;
    private Boolean satisfied;
    private Double confidence;

    @TableField(value = "gaps", typeHandler = JsonbStringTypeHandler.class)
    private String gaps;

    @TableField(value = "suggestions", typeHandler = JsonbStringTypeHandler.class)
    private String suggestions;

    private String verdict;
    private String reflectorModel;
    private OffsetDateTime createdAt;
}
