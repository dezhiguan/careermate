package com.careermate.agent.memory.ltm;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * A4：chat 前召回长期记忆并格式化为 system prompt 注入块。仅 embedding + DB 检索，不做 LLM 调用；
 * 无命中返回空串。facts 作为"已知用户事实"提示，不覆盖用户当前输入。
 */
@Component
public class LongTermMemoryAdvisor {

    private final LongTermMemoryService longTermMemoryService;

    public LongTermMemoryAdvisor(LongTermMemoryService longTermMemoryService) {
        this.longTermMemoryService = longTermMemoryService;
    }

    public String buildMemoryBlock(Long userId, String userMessage) {
        if (userId == null || !StringUtils.hasText(userMessage)) {
            return "";
        }
        List<LtmMatch> facts = longTermMemoryService.recall(userId, userMessage);
        if (facts == null || facts.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("\n\n【已知用户事实（长期记忆，供参考，勿与当前输入冲突时以当前为准）】\n");
        for (LtmMatch f : facts) {
            sb.append("- [").append(f.getFactType()).append("] ").append(f.getFactText()).append('\n');
        }
        return sb.toString();
    }
}
