package com.careermate.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.careermate.mybatis.JsonbStringTypeHandler;
import java.time.OffsetDateTime;
import lombok.Data;

/** 用户通知偏好（prefs 为前端约定的 JSON）。 */
@Data
@TableName(value = "user_notification_prefs", autoResultMap = true)
public class NotificationPreferenceEntity {

    @TableId(type = IdType.INPUT)
    private Long userId;

    @TableField(value = "prefs", typeHandler = JsonbStringTypeHandler.class)
    private String prefs;

    private OffsetDateTime updatedAt;
}
