package com.careermate.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.careermate.mybatis.JsonbStringTypeHandler;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName(value = "agent_messages", autoResultMap = true)
public class AgentMessageEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;
    private Long userId;
    private String role;
    private String content;
    private String messageType;
    private Integer sequenceNo;
    @TableField(value = "metadata", typeHandler = JsonbStringTypeHandler.class)
    private String metadata;
    private OffsetDateTime createdAt;
}
