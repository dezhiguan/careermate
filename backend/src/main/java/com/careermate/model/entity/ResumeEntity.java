package com.careermate.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.careermate.mybatis.JsonbStringTypeHandler;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName(value = "resumes", autoResultMap = true)
public class ResumeEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String title;

    private String content;

    private String sourceType;

    private Boolean isDefault;

    private String status;

    /** P1 简历来源：UPLOAD / MANUAL / COLD_START，默认 UPLOAD。 */
    private String origin;

    /** P1 填充就绪度：READY / DRAFT_SKELETON，默认 READY。 */
    private String readiness;

    /** P1 冷启动信号来源标记（JSON：{source:conversation/career_profile/default_skeleton}）。 */
    @TableField(value = "source_signals", typeHandler = JsonbStringTypeHandler.class)
    private String sourceSignals;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    /** RAGForge Personal KB 中对应的文档 ID，null 表示未同步 */
    private Long ragDocId;
}
