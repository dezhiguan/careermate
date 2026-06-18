ALTER TABLE resume_versions
  ADD COLUMN IF NOT EXISTS target_company VARCHAR(120),
  ADD COLUMN IF NOT EXISTS target_jd_title VARCHAR(200),
  ADD COLUMN IF NOT EXISTS version_seq INT NOT NULL DEFAULT 1;

ALTER TABLE resume_versions
  ALTER COLUMN target_jd_id TYPE BIGINT
  USING CASE
    WHEN target_jd_id IS NULL OR btrim(target_jd_id) = '' THEN NULL
    WHEN btrim(target_jd_id) ~ '^[0-9]+$' THEN btrim(target_jd_id)::BIGINT
    WHEN btrim(target_jd_id) ~ '^doc-[0-9]+$' THEN substring(btrim(target_jd_id) from 5)::BIGINT
    ELSE NULL
  END;

UPDATE resume_versions
SET
  target_company = COALESCE(
    NULLIF(target_company, ''),
    NULLIF(split_part(target_jd_label, ' - ', 1), ''),
    '未知公司'
  ),
  target_jd_title = COALESCE(
    NULLIF(target_jd_title, ''),
    CASE
      WHEN target_jd_label LIKE '% - %' THEN NULLIF(substring(target_jd_label from position(' - ' in target_jd_label) + 3), '')
      ELSE NULLIF(target_jd_label, '')
    END,
    NULLIF(NULLIF(version_name, '定制简历版'), ''),
    '历史定制简历'
  )
WHERE target_company IS NULL
   OR target_company = ''
   OR target_jd_title IS NULL
   OR target_jd_title = '';

WITH ranked AS (
  SELECT
    id,
    row_number() OVER (
      PARTITION BY user_id, target_jd_id
      ORDER BY created_at ASC, id ASC
    ) AS seq
  FROM resume_versions
)
UPDATE resume_versions rv
SET version_seq = ranked.seq
FROM ranked
WHERE rv.id = ranked.id;

CREATE INDEX IF NOT EXISTS idx_resume_versions_user_target_seq
  ON resume_versions(user_id, target_jd_id, version_seq DESC);
