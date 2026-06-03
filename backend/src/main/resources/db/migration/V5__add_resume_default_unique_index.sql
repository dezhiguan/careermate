-- 每个用户仅允许一条 ACTIVE 默认简历；迁移前先收敛历史重复默认。
WITH ranked AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY user_id
               ORDER BY updated_at DESC NULLS LAST, id DESC
           ) AS rn
    FROM resumes
    WHERE status = 'ACTIVE'
      AND is_default IS TRUE
)
UPDATE resumes r
SET is_default = FALSE,
    updated_at = NOW()
FROM ranked
WHERE r.id = ranked.id
  AND ranked.rn > 1;

CREATE UNIQUE INDEX uk_resumes_user_active_default
    ON resumes (user_id)
    WHERE status = 'ACTIVE' AND is_default IS TRUE;
