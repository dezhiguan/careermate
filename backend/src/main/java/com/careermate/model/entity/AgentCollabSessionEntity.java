package com.careermate.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.careermate.mybatis.JsonbStringTypeHandler;
import lombok.Data;

import java.time.OffsetDateTime;

/** B1：Writer-Critic 协作会话。 */
@Data
@TableName(value = "agent_collab_session", autoResultMap = true)
public class AgentCollabSessionEntity {

    @TableId
    private String id;

    private String type;

    @TableField(value = "participants", typeHandler = JsonbStringTypeHandler.class)
    private String participants;

    @TableField(value = "config", typeHandler = JsonbStringTypeHandler.class)
    private String config;

    private String status;
    private OffsetDateTime startedAt;
    private OffsetDateTime finishedAt;
}
