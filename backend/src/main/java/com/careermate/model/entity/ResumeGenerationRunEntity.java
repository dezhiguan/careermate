package com.careermate.model.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/** #5.10：简历生成运行态，用于崩溃/重启后的自愈。 */
@Data
@TableName("resume_generation_run")
public class ResumeGenerationRunEntity {

    @TableId
    private String runId;

    private Long userId;
    private String sessionId;
    private String jdId;
    private String status;
    private String error;
    private OffsetDateTime startedAt;
    private OffsetDateTime updatedAt;
}
