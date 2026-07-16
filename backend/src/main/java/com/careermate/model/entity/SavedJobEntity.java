package com.careermate.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 暂存区：收藏但未动的 JD（不占投递看板，一键转为机会）。
 */
@Data
@TableName("saved_jobs")
public class SavedJobEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long jdDocId;
    private String company;
    private String roleTitle;
    private LocalDateTime savedAt;
    private LocalDateTime deletedAt;
}
