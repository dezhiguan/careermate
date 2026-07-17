package com.careermate.agent.memory.ltm;

import com.careermate.mapper.AgentMessageMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * A4：每日凌晨 3 点把昨日对话蒸馏为长期记忆。ltm 未启用时 no-op。
 */
@Slf4j
@Component
public class ConsolidationScheduler {

    private final AgentMessageMapper agentMessageMapper;
    private final ConsolidationService consolidationService;
    private final LtmProperties properties;

    public ConsolidationScheduler(AgentMessageMapper agentMessageMapper,
                                  ConsolidationService consolidationService, LtmProperties properties) {
        this.agentMessageMapper = agentMessageMapper;
        this.consolidationService = consolidationService;
        this.properties = properties;
    }

    @Scheduled(cron = "${careermate.agent.ltm.consolidation-cron:0 0 3 * * *}")
    public void consolidateYesterday() {
        if (!properties.isEnabled()) {
            return;
        }
        ZoneId zone = ZoneId.systemDefault();
        OffsetDateTime until = LocalDate.now(zone).atStartOfDay(zone).toOffsetDateTime();
        OffsetDateTime since = until.minusDays(1);
        try {
            List<Long> users = agentMessageMapper.findActiveUserIds(since, until);
            int totalFacts = 0;
            for (Long userId : users) {
                // 按会话粒度蒸馏：尊重"隔离对话"红线，每条 JD 线单独蒸并记来源会话。
                List<Long> sessionIds = agentMessageMapper.findActiveSessionIds(userId, since, until);
                for (Long sessionId : sessionIds) {
                    List<String> convo = agentMessageMapper.findSessionConversationTexts(sessionId, since, until);
                    totalFacts += consolidationService.consolidate(userId, convo, sessionId);
                }
            }
            log.info("长期记忆蒸馏完成：用户 {}，新增 fact {}", users.size(), totalFacts);
        } catch (Exception e) {
            log.warn("长期记忆蒸馏调度异常（跳过本次）: {}", e.getMessage());
        }
    }
}
