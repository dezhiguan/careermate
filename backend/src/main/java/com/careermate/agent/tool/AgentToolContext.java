package com.careermate.agent.tool;

import com.careermate.agent.path.AgentPathMode;
import lombok.Builder;
import lombok.Value;

import java.util.Collections;
import java.util.Map;

@Value
@Builder
public class AgentToolContext {

    Long userId;
    String sessionId;
    String userMessage;
    @Builder.Default
    Map<String, Object> args = Collections.emptyMap();
    /** A2：本轮执行路径分层，供 deep-only 能力（带引用检索、反思）gate。默认 FAST。 */
    @Builder.Default
    AgentPathMode pathMode = AgentPathMode.FAST;

    public boolean isDeep() {
        return pathMode == AgentPathMode.DEEP;
    }
}
