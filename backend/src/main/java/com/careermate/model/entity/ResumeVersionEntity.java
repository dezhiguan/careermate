package com.careermate.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.careermate.mybatis.JsonbStringTypeHandler;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName(value = "resume_versions", autoResultMap = true)
public class ResumeVersionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String versionId;
    private Long userId;
    private String sessionId;
    private Long sourceResumeId;
    private String parentVersionId;
    private Long targetJdId;
    @TableField("legacy_target_jd_id_raw")
    private String legacyTargetJdIdRaw;
    private String targetJdLabel;
    private String targetCompany;
    private String targetJdTitle;
    private Integer versionSeq;
    private String versionName;
    private String changeSummary;
    private String contentMarkdown;
    @TableField(value = "optimization_notes", typeHandler = JsonbStringTypeHandler.class)
    private String optimizationNotes;
    /** P2 确定性事实校验结果（JSON：{status, unsourcedFacts, checkedAt}），null 表示未校验。 */
    @TableField(value = "fact_check", typeHandler = JsonbStringTypeHandler.class)
    private String factCheck;
    /** P4 版本来源：GENERATED（AI 生成）/ MANUAL_EDIT（手工编辑派生），默认 GENERATED。 */
    private String origin;
    private BigDecimal aiScore;
    /** 是否已导出过 PDF/Word。 */
    private Boolean exported;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
