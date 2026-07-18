-- 通知偏好（设计 06 我的·设置卡「通知偏好」）。按用户存一份，prefs 为灵活 JSON。
CREATE TABLE IF NOT EXISTS user_notification_prefs (
    user_id     BIGINT PRIMARY KEY,
    prefs       JSONB NOT NULL DEFAULT '{}'::jsonb,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE user_notification_prefs IS '用户通知偏好：邮件/推送/摘要频率等，prefs 键由前端约定';
