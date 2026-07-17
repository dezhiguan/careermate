package com.careermate.agent.memory.ltm;

import com.careermate.common.api.ApiResponse;
import com.careermate.mapper.AgentMessageMapper;
import com.careermate.model.entity.UserLongTermMemoryEntity;
import com.careermate.security.CurrentUserContext;
import lombok.Data;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A4：/mine "小职认识你这些"——查看/忘掉长期记忆 fact；以及自助触发蒸馏。
 */
@RestController
@RequestMapping("/api/profile/long-term-memory")
public class LongTermMemoryController {

    private final LongTermMemoryService longTermMemoryService;
    private final ConsolidationService consolidationService;
    private final AgentMessageMapper agentMessageMapper;
    private final LtmProperties properties;

    public LongTermMemoryController(LongTermMemoryService longTermMemoryService,
                                    ConsolidationService consolidationService,
                                    AgentMessageMapper agentMessageMapper,
                                    LtmProperties properties) {
        this.longTermMemoryService = longTermMemoryService;
        this.consolidationService = consolidationService;
        this.agentMessageMapper = agentMessageMapper;
        this.properties = properties;
    }

    /**
     * 自助触发蒸馏：把当前用户近 days 天的对话立刻蒸馏为长期记忆（不必等每日 3 点定时任务）。
     * 仅作用于当前登录用户自身；返回蒸馏统计（含 ltmEnabled/storage，便于确认激活状态）。
     */
    @PostMapping("/consolidate")
    public ApiResponse<Map<String, Object>> consolidate(
            @RequestParam(required = false, defaultValue = "7") int days) {
        Long userId = CurrentUserContext.getUserId();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ltmEnabled", properties.isEnabled());
        result.put("storage", properties.getStorage());
        result.put("userId", userId);
        if (userId == null) {
            result.put("factsStored", 0);
            result.put("messageCount", 0);
            return ApiResponse.success(result);
        }
        int safeDays = Math.max(1, Math.min(days, 90));
        ZoneId zone = ZoneId.systemDefault();
        OffsetDateTime until = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toOffsetDateTime();
        OffsetDateTime since = until.minusDays(safeDays);
        List<String> convo = agentMessageMapper.findConversationTexts(userId, since, until);
        int stored = consolidationService.consolidate(userId, convo);
        result.put("days", safeDays);
        result.put("messageCount", convo == null ? 0 : convo.size());
        result.put("factsStored", stored);
        return ApiResponse.success(result);
    }

    @GetMapping
    public ApiResponse<List<LtmFactVO>> list() {
        Long userId = CurrentUserContext.getUserId();
        if (userId == null) {
            return ApiResponse.success(List.of());
        }
        List<LtmFactVO> facts = longTermMemoryService.listActive(userId).stream()
                .map(LongTermMemoryController::toVO)
                .toList();
        return ApiResponse.success(facts);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> forget(@PathVariable Long id) {
        Long userId = CurrentUserContext.getUserId();
        if (userId != null) {
            longTermMemoryService.forget(userId, id);
        }
        return ApiResponse.success(null);
    }

    private static LtmFactVO toVO(UserLongTermMemoryEntity e) {
        LtmFactVO vo = new LtmFactVO();
        vo.setId(e.getId());
        vo.setFactType(e.getFactType());
        vo.setFactText(e.getFactText());
        vo.setConfidence(e.getConfidence());
        return vo;
    }

    @Data
    public static class LtmFactVO {
        private Long id;
        private String factType;
        private String factText;
        private Double confidence;
    }
}
