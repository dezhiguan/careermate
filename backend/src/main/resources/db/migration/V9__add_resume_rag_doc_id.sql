ALTER TABLE resumes ADD COLUMN IF NOT EXISTS rag_doc_id BIGINT;
COMMENT ON COLUMN resumes.rag_doc_id IS 'RAGForge Personal KB 中对应文档的 doc_id，null 表示未同步';
