package com.careermate.agent.memory.ltm;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * A4 长期记忆配置。
 */
@Data
@ConfigurationProperties(prefix = "careermate.agent.ltm")
public class LtmProperties {

    /** 是否启用长期记忆（召回注入 + 蒸馏）。 */
    private boolean enabled = false;
    /** 存储后端：KB=RAGForge 知识库（默认）；PGVECTOR=本地 pgvector（保留可回退）。 */
    private String storage = "KB";
    /** DashScope 兼容 embedding endpoint。 */
    private String embeddingEndpoint = "https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings";
    /** embedding 模型。 */
    private String embeddingModel = "text-embedding-v3";
    /** 向量维度（须与表列一致）。 */
    private int dimensions = 1024;
    /** embedding API key；留空复用 LLM_API_KEY。 */
    private String apiKey = "";
    /** 召回 top-k。 */
    private int recallTopK = 5;
    /** 召回相似度阈值。 */
    private double recallThreshold = 0.7;
    /** embedding 缓存 TTL 秒。 */
    private long cacheTtlSeconds = 3600;
    /** 请求超时毫秒。 */
    private int timeoutMs = 3000;
    /** 重复判定阈值（同 type cosine ≥ 视为重复，+confidence）。 */
    private double duplicateThreshold = 0.85;

    /** 是否走知识库（RAGForge）存储后端。 */
    public boolean isKbMode() {
        return storage == null || !"PGVECTOR".equalsIgnoreCase(storage.trim());
    }
}
