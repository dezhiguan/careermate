package com.careermate.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.careermate.mybatis.JsonbStringTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName(value = "agent_artifacts", autoResultMap = true)
public class AgentArtifactEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String artifactId;
    private Long userId;
    private String sessionId;
    private String artifactType;
    private String title;
    private String summary;
    private String refType;
    private String refId;
    @TableField(value = "metadata", typeHandler = JsonbStringTypeHandler.class)
    private String metadata;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
