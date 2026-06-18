UPDATE resume_versions
SET target_company = '未知公司'
WHERE target_company IN ('定制简历版', '定制简历')
   OR target_company IS NULL
   OR target_company = '';

UPDATE resume_versions
SET target_jd_title = '历史定制简历'
WHERE target_jd_title IN ('定制简历版', '定制简历')
   OR target_jd_title IS NULL
   OR target_jd_title = '';
