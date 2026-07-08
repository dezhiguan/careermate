package com.careermate.agent.memory.ltm;

import com.careermate.common.api.ApiResponse;
import com.careermate.model.entity.UserLongTermMemoryEntity;
import com.careermate.security.CurrentUserContext;
import lombok.Data;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * A4：/mine "小职认识你这些"——查看/忘掉长期记忆 fact。
 */
@RestController
@RequestMapping("/api/profile/long-term-memory")
public class LongTermMemoryController {

    private final LongTermMemoryService longTermMemoryService;

    public LongTermMemoryController(LongTermMemoryService longTermMemoryService) {
        this.longTermMemoryService = longTermMemoryService;
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
