CREATE UNIQUE INDEX IF NOT EXISTS uk_resume_versions_user_target_seq
  ON resume_versions(user_id, COALESCE(target_jd_id, -1), version_seq);
