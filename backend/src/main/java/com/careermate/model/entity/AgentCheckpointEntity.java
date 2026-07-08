package com.careermate.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/** B3：state 快照。 */
@Data
@TableName("agent_checkpoint")
public class AgentCheckpointEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String runId;
    private Integer stepIndex;
    private String stepName;
    private byte[] stateBlob;
    private Integer stateSize;
    private String stateHash;
    private Boolean isPaused;
    private String pauseReason;
    private OffsetDateTime createdAt;
}
