package com.careermate.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("career_tasks")
public class CareerTaskEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String title;
    private String description;
    private String category;
    private String priority;
    private String status;
    private LocalDate dueDate;
    private String source;
    private String relatedType;
    private Long relatedId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;
    private LocalDateTime deletedAt;
}
