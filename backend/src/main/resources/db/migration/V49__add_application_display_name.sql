-- 准备看板卡片可改名（设计 v2.3：卡片名 公司·职位·区分项 可改名）。
-- display_name 为空时前端用「公司·职位·区分项」自动生成。
ALTER TABLE job_applications ADD COLUMN IF NOT EXISTS display_name VARCHAR(120);

COMMENT ON COLUMN job_applications.display_name IS '用户自定义卡片名；空则前端按 公司·职位·区分项 自动生成';
