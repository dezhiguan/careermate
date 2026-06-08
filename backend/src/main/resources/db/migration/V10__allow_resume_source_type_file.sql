ALTER TABLE resumes DROP CONSTRAINT chk_resumes_source_type;
ALTER TABLE resumes ADD CONSTRAINT chk_resumes_source_type CHECK (source_type IN ('TEXT', 'FILE'));
