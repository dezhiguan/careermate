package com.careermate.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 投递看板记录：用户正在准备/推进的一个求职机会及其阶段。
 */
@Data
@TableName("job_applications")
public class JobApplicationEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long jdDocId;
    private String company;
    private String roleTitle;
    private String stage;
    private String resumeVersionId;
    private String notes;
    private String source;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastActiveAt;
    private LocalDateTime deletedAt;
}
