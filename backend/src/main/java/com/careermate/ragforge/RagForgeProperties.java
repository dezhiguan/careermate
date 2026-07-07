package com.careermate.ragforge;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "careermate.ragforge")
public class RagForgeProperties {
    /** 总开关；dev 默认 false，true 时才真实调用 RAGForge */
    private boolean enabled = false;
    /** RAGForge HTTP 地址 */
    private String url = "http://localhost:8080";
    /** X-API-Key */
    private String apiKey = "";
    /** Auth Gateway token-exchange requested audience. */
    private String requestedAudience = "ragforge-api";
    /** Auth Gateway token-exchange requested scopes. */
    private String requestedScopes = "rag:search";
    /** Local exchanged-token cache TTL in milliseconds. */
    private long exchangedTokenCacheTtlMs = 300000L;
    /** 单次请求超时（毫秒） */
    private int timeoutMs = 1500;
    /** RAGForge 中 JD Pattern KB 的 id；空则禁用 JD 搜索能力 */
    private String jdKbId = "";
    /** RAGForge 中 Interview Q&A KB 的 id；空则禁用面试题 RAG 增强 */
    private String interviewKbId = "";
    /** RAGForge 中 Personal KB 的 id；空则禁用简历同步 */
    private String personalKbId = "";
    /** A1-4：薪资行情库 id（scene=MARKET）；空则回退 JD 库 */
    private String marketKbId = "";
    /** A1-4：目标公司库 id（scene=COMPANY）；空则回退 JD 库 */
    private String companyKbId = "";
    /** A1-4：岗位技能画像库 id（简历定制技能对齐用）；空则不做技能对齐检索 */
    private String skillKbId = "";
}
