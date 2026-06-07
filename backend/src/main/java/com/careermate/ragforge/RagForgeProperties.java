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
    /** 单次请求超时（毫秒） */
    private int timeoutMs = 5000;
    /** RAGForge 中 JD Pattern KB 的 id；空则禁用 JD 搜索能力 */
    private String jdKbId = "";
}
