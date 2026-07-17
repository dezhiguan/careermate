-- A4 长期记忆改走知识库方案（RAGForge KB），不再依赖 pgvector。
-- 复用 user_long_term_memory 作为本地元数据映射：新增 rag_doc_id 关联 RAGForge 文档，
-- 用于按 docId 过滤实现用户级隔离检索、以及"忘掉"时删除对应 KB 文档。
-- KB 模式下 embedding 列不再写入（保留列以兼容旧数据与 PGVECTOR 回退模式）。
ALTER TABLE user_long_term_memory ADD COLUMN IF NOT EXISTS rag_doc_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_ltm_rag_doc ON user_long_term_memory(rag_doc_id);

COMMENT ON COLUMN user_long_term_memory.rag_doc_id IS 'KB 模式下该 fact 在 RAGForge 记忆库中的文档 id；PGVECTOR 模式为空';
