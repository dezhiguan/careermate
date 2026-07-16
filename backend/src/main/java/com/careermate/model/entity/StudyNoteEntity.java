package com.careermate.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 八股题库（个人）：用户收录的题 + 手写答案，user 级、跨机会复用。
 */
@Data
@TableName("study_notes")
public class StudyNoteEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String question;
    private String questionHash;
    private String skillTag;
    private String answer;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
}
