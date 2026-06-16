package com.careermate.agent.memory.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.careermate.agent.memory.AgentMemoryContext;
import com.careermate.agent.memory.AgentMemoryService;
import com.careermate.jobmatch.JobMatchJsonSupport;
import com.careermate.mapper.AgentMessageMapper;
import com.careermate.mapper.AgentSessionMapper;
import com.careermate.mapper.CareerProfileMapper;
import com.careermate.model.entity.AgentMessageEntity;
import com.careermate.model.entity.AgentSessionEntity;
import com.careermate.model.entity.CareerProfileEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
public class AgentMemoryServiceImpl implements AgentMemoryService {

    private static final String PROFILE_HEADER = "【用户求职画像】";
    private static final int SUMMARY_RECENT_MESSAGES = 10;
    private static final int MESSAGE_SNIPPET_MAX = 120;
    private static final int CONVERSATION_SUMMARY_MAX = 800;
    private static final int WEAKNESS_SUMMARY_MAX = 1000;
    private static final int MEMORY_SUMMARY_MAX = 1000;
    private static final int KEYWORD_MAX = 80;
    private static final int QUESTION_TOPIC_MAX = 60;

    private final CareerProfileMapper careerProfileMapper;
    private final AgentSessionMapper agentSessionMapper;
    private final AgentMessageMapper agentMessageMapper;
    private final JobMatchJsonSupport jobMatchJsonSupport;

    public AgentMemoryServiceImpl(
            CareerProfileMapper careerProfileMapper,
            AgentSessionMapper agentSessionMapper,
            AgentMessageMapper agentMessageMapper,
            JobMatchJsonSupport jobMatchJsonSupport
    ) {
        this.careerProfileMapper = careerProfileMapper;
        this.agentSessionMapper = agentSessionMapper;
        this.agentMessageMapper = agentMessageMapper;
        this.jobMatchJsonSupport = jobMatchJsonSupport;
    }

    @Override
    public AgentMemoryContext loadMemoryContext(Long userId, String sessionId) {
        if (userId == null) {
            return AgentMemoryContext.empty();
        }

        CareerProfileEntity profile = findProfile(userId);
        String conversationSummary = loadSessionSummary(userId, sessionId);

        if (profile == null && (conversationSummary == null || conversationSummary.isBlank())) {
            return AgentMemoryContext.empty();
        }

        List<String> skills = profile == null
                ? List.of()
                : jobMatchJsonSupport.readStringList(profile.getSkillKeywords());
        List<String> weaknesses = profile == null
                ? List.of()
                : jobMatchJsonSupport.readStringList(profile.getWeaknessKeywords());

        String contextText = buildContextText(profile, skills, weaknesses, conversationSummary);
        if (contextText.isBlank()) {
            return AgentMemoryContext.empty();
        }

        return AgentMemoryContext.builder()
                .available(true)
                .contextText(contextText)
                .targetRole(profile == null ? null : profile.getTargetRole())
                .targetCity(profile == null ? null : profile.getTargetCity())
                .targetSalaryRange(profile == null ? null : profile.getTargetSalaryRange())
                .skillKeywords(skills)
                .weaknessKeywords(weaknesses)
                .interviewWeaknessSummary(profile == null ? null : profile.getInterviewWeaknessSummary())
                .memorySummary(profile == null ? null : profile.getMemorySummary())
                .conversationSummary(conversationSummary)
                .skillCount(skills.size())
                .weaknessCount(weaknesses.size())
                .hasSessionSummary(isNotBlank(conversationSummary))
                .build();
    }

    @Override
    @Transactional
    public void refreshConversationSummary(Long userId, String sessionId) {
        if (userId == null || sessionId == null || sessionId.isBlank()) {
            return;
        }
        AgentSessionEntity session = findAgentSession(userId, sessionId);
        if (session == null) {
            return;
        }

        List<AgentMessageEntity> recent = agentMessageMapper.selectList(
                new LambdaQueryWrapper<AgentMessageEntity>()
                        .eq(AgentMessageEntity::getSessionId, session.getId())
                        .eq(AgentMessageEntity::getUserId, userId)
                        .orderByDesc(AgentMessageEntity::getSequenceNo)
                        .last("LIMIT " + SUMMARY_RECENT_MESSAGES)
        );
        if (recent == null || recent.isEmpty()) {
            return;
        }

        List<String> parts = new ArrayList<>();
        for (int i = recent.size() - 1; i >= 0; i--) {
            AgentMessageEntity message = recent.get(i);
            String snippet = toSummarySnippet(message);
            if (snippet == null) {
                continue;
            }
            String roleLabel = isUserRole(message.getRole()) ? "用户" : "小职";
            parts.add(roleLabel + "：" + snippet);
        }
        if (parts.isEmpty()) {
            return;
        }

        String summary = truncate(String.join("；", parts), CONVERSATION_SUMMARY_MAX);
        OffsetDateTime now = OffsetDateTime.now();
        agentSessionMapper.update(null, new LambdaUpdateWrapper<AgentSessionEntity>()
                .eq(AgentSessionEntity::getId, session.getId())
                .eq(AgentSessionEntity::getUserId, userId)
                .set(AgentSessionEntity::getConversationSummary, summary)
                .set(AgentSessionEntity::getConversationSummaryUpdatedAt, now)
                .set(AgentSessionEntity::getUpdatedAt, now));
    }

    @Override
    @Transactional
    public void rememberInterviewWeakness(
            Long userId,
            int score,
            String questionType,
            String questionText,
            List<String> referencePoints,
            List<String> improvements
    ) {
        if (userId == null) {
            return;
        }
        boolean lowScore = score <= 70;
        boolean hasImprovements = improvements != null && improvements.stream().anyMatch(this::isNotBlank);
        if (!lowScore && !hasImprovements) {
            return;
        }

        List<String> keywords = extractWeaknessKeywords(questionType, questionText, referencePoints, improvements);
        String summarySnippet = buildWeaknessSummarySnippet(questionType, questionText, improvements);
        mergeCareerSignals(userId, List.of(), keywords, summarySnippet);
    }

    @Override
    @Transactional
    public void mergeCareerSignals(
            Long userId,
            List<String> skillKeywords,
            List<String> weaknessKeywords,
            String weaknessSummarySnippet
    ) {
        if (userId == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        CareerProfileEntity entity = findProfile(userId);
        if (entity == null) {
            entity = new CareerProfileEntity();
            entity.setUserId(userId);
            entity.setSkillKeywords(jobMatchJsonSupport.writeStringList(List.of()));
            entity.setWeaknessKeywords(jobMatchJsonSupport.writeStringList(List.of()));
            entity.setSource("agent");
            entity.setCreatedAt(now);
        }

        List<String> mergedSkills = mergeKeywords(
                jobMatchJsonSupport.readStringList(entity.getSkillKeywords()),
                skillKeywords
        );
        List<String> mergedWeaknesses = mergeKeywords(
                jobMatchJsonSupport.readStringList(entity.getWeaknessKeywords()),
                weaknessKeywords
        );

        if (!mergedSkills.isEmpty()) {
            entity.setSkillKeywords(jobMatchJsonSupport.writeStringList(mergedSkills));
        }
        if (!mergedWeaknesses.isEmpty()) {
            entity.setWeaknessKeywords(jobMatchJsonSupport.writeStringList(mergedWeaknesses));
        }

        if (isNotBlank(weaknessSummarySnippet)) {
            entity.setInterviewWeaknessSummary(mergeSummaryText(
                    entity.getInterviewWeaknessSummary(),
                    weaknessSummarySnippet,
                    WEAKNESS_SUMMARY_MAX
            ));
        }

        entity.setMemoryUpdatedAt(now);
        entity.setUpdatedAt(now);

        if (entity.getId() == null) {
            careerProfileMapper.insert(entity);
        } else {
            careerProfileMapper.updateById(entity);
        }
    }

    private CareerProfileEntity findProfile(Long userId) {
        return careerProfileMapper.selectOne(
                new LambdaQueryWrapper<CareerProfileEntity>()
                        .eq(CareerProfileEntity::getUserId, userId)
                        .last("LIMIT 1")
        );
    }

    private AgentSessionEntity findAgentSession(Long userId, String sessionId) {
        return agentSessionMapper.selectOne(
                new LambdaQueryWrapper<AgentSessionEntity>()
                        .eq(AgentSessionEntity::getSessionId, sessionId)
                        .eq(AgentSessionEntity::getUserId, userId)
                        .last("LIMIT 1")
        );
    }

    private String loadSessionSummary(Long userId, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return null;
        }
        AgentSessionEntity session = findAgentSession(userId, sessionId);
        if (session == null) {
            return null;
        }
        return session.getConversationSummary();
    }

    private String buildContextText(
            CareerProfileEntity profile,
            List<String> skills,
            List<String> weaknesses,
            String conversationSummary
    ) {
        StringBuilder sb = new StringBuilder(PROFILE_HEADER);
        if (profile != null) {
            appendLine(sb, "目标岗位", profile.getTargetRole());
            appendLine(sb, "目标城市", profile.getTargetCity());
            appendLine(sb, "期望薪资", profile.getTargetSalaryRange());
            appendLine(sb, "经验阶段", profile.getSeniority());
            appendLine(sb, "工作偏好", profile.getWorkMode());
            appendLine(sb, "技能关键词", joinList(skills));
            appendLine(sb, "近期弱项", resolveWeaknessLine(profile, weaknesses));
            appendLine(sb, "偏好说明", profile.getPreferenceSummary());
            appendLine(sb, "记忆摘要", profile.getMemorySummary());
        }
        appendLine(sb, "当前会话摘要", conversationSummary);
        if (sb.length() == PROFILE_HEADER.length()) {
            return "";
        }
        return sb.toString().trim();
    }

    private String resolveWeaknessLine(CareerProfileEntity profile, List<String> weaknesses) {
        if (isNotBlank(profile.getInterviewWeaknessSummary())) {
            return profile.getInterviewWeaknessSummary();
        }
        return joinList(weaknesses);
    }

    private void appendLine(StringBuilder sb, String label, String value) {
        if (!isNotBlank(value)) {
            return;
        }
        sb.append('\n').append(label).append('：').append(value.trim());
    }

    private String joinList(List<String> items) {
        if (items == null || items.isEmpty()) {
            return null;
        }
        return String.join(", ", items);
    }

    private String toSummarySnippet(AgentMessageEntity message) {
        if (message == null || !isTextLikeMessage(message)) {
            return null;
        }
        String content = message.getContent().trim();
        if (content.isEmpty()) {
            return null;
        }
        if (looksLikeStructuredPayload(content)) {
            return null;
        }
        if (content.length() > 2000) {
            return null;
        }
        return truncate(content.replace('\n', ' '), MESSAGE_SNIPPET_MAX);
    }

    private boolean isTextLikeMessage(AgentMessageEntity message) {
        String type = message.getMessageType();
        if (type == null || type.isBlank() || "text".equalsIgnoreCase(type)) {
            return true;
        }
        return "user".equalsIgnoreCase(message.getRole()) || "agent".equalsIgnoreCase(message.getRole());
    }

    private boolean looksLikeStructuredPayload(String content) {
        String trimmed = content.trim();
        return trimmed.startsWith("{") || trimmed.startsWith("[");
    }

    private List<String> extractWeaknessKeywords(
            String questionType,
            String questionText,
            List<String> referencePoints,
            List<String> improvements
    ) {
        Set<String> keywords = new LinkedHashSet<>();
        if (isNotBlank(questionType)) {
            keywords.add(truncate(normalizeKeyword(questionType), KEYWORD_MAX));
        }
        if (isNotBlank(questionText)) {
            keywords.add(truncate(extractQuestionTopic(questionText), KEYWORD_MAX));
        }
        if (referencePoints != null) {
            for (String point : referencePoints) {
                if (isNotBlank(point)) {
                    keywords.add(truncate(normalizeKeyword(point), KEYWORD_MAX));
                }
            }
        }
        if (improvements != null) {
            for (String improvement : improvements) {
                if (isNotBlank(improvement)) {
                    keywords.add(truncate(normalizeKeyword(improvement), KEYWORD_MAX));
                }
            }
        }
        keywords.removeIf(k -> k == null || k.isBlank());
        return new ArrayList<>(keywords);
    }

    private String buildWeaknessSummarySnippet(
            String questionType,
            String questionText,
            List<String> improvements
    ) {
        List<String> parts = new ArrayList<>();
        if (isNotBlank(questionType)) {
            parts.add(normalizeKeyword(questionType));
        }
        if (isNotBlank(questionText)) {
            parts.add(extractQuestionTopic(questionText));
        }
        if (improvements != null) {
            for (String improvement : improvements) {
                if (isNotBlank(improvement)) {
                    parts.add(normalizeKeyword(improvement));
                }
            }
        }
        if (parts.isEmpty()) {
            return null;
        }
        return truncate(String.join("、", parts), 240);
    }

    private String extractQuestionTopic(String questionText) {
        String normalized = questionText.trim().replace('\n', ' ');
        if (normalized.length() <= QUESTION_TOPIC_MAX) {
            return normalized;
        }
        return normalized.substring(0, QUESTION_TOPIC_MAX);
    }

    private String normalizeKeyword(String value) {
        return value.trim().replaceAll("\\s+", " ");
    }

    private List<String> mergeKeywords(List<String> existing, List<String> incoming) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        if (existing != null) {
            for (String item : existing) {
                if (isNotBlank(item)) {
                    merged.add(normalizeKeyword(item));
                }
            }
        }
        if (incoming != null) {
            for (String item : incoming) {
                if (isNotBlank(item)) {
                    merged.add(normalizeKeyword(item));
                }
            }
        }
        return new ArrayList<>(merged);
    }

    private String mergeSummaryText(String existing, String snippet, int maxLen) {
        if (!isNotBlank(snippet)) {
            return existing;
        }
        if (!isNotBlank(existing)) {
            return truncate(snippet, maxLen);
        }
        if (existing.contains(snippet)) {
            return truncate(existing, maxLen);
        }
        String merged = existing + "；" + snippet;
        return truncate(merged, maxLen);
    }

    private String truncate(String value, int maxLen) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= maxLen) {
            return trimmed;
        }
        return trimmed.substring(0, maxLen);
    }

    private boolean isUserRole(String role) {
        return "user".equalsIgnoreCase(role);
    }

    private boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }
}
