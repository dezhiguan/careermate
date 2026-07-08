package com.careermate.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.careermate.mybatis.JsonbStringTypeHandler;
import lombok.Data;

import java.time.OffsetDateTime;

/** B1：debate 每轮消息。 */
@Data
@TableName(value = "agent_collab_message", autoResultMap = true)
public class AgentCollabMessageEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String sessionId;
    private String fromAgent;
    private String toAgent;
    private String msgType;
    private Integer roundNo;

    @TableField(value = "payload", typeHandler = JsonbStringTypeHandler.class)
    private String payload;

    private OffsetDateTime createdAt;
}
