package com.careermate.mcp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class McpAuditService {

    public void recordToolWrite(Long userId, String toolName, String summary) {
        log.info("MCP audit: userId={} tool={} summary={}", userId, toolName, summary);
    }
}
