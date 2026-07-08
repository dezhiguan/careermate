package com.careermate.model.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/** B3：Agent 运行。 */
@Data
@TableName("agent_run")
public class AgentRunEntity {

    @TableId
    private String runId;

    private Long userId;
    private String sessionId;
    private String workflowName;
    private String status;
    private String parentRunId;
    private OffsetDateTime startedAt;
    private OffsetDateTime finishedAt;
}
