package com.careermate.agent.memory.ltm;

import com.careermate.common.api.ApiResponse;
import com.careermate.model.entity.UserLongTermMemoryEntity;
import com.careermate.security.CurrentUser;
import com.careermate.security.CurrentUserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LongTermMemoryControllerTest {

    private final LongTermMemoryService service = mock(LongTermMemoryService.class);
    private final ConsolidationService consolidationService = mock(ConsolidationService.class);
    private final com.careermate.mapper.AgentMessageMapper agentMessageMapper =
            mock(com.careermate.mapper.AgentMessageMapper.class);
    private final LtmProperties properties = new LtmProperties();
    private final LongTermMemoryController controller =
            new LongTermMemoryController(service, consolidationService, agentMessageMapper, properties);

    @AfterEach
    void clear() {
        CurrentUserContext.clear();
    }

    private void login(Long userId) {
        CurrentUserContext.set(CurrentUser.builder().userId(userId).authenticated(true).build());
    }

    private UserLongTermMemoryEntity fact(long id, String type, String text) {
        UserLongTermMemoryEntity e = new UserLongTermMemoryEntity();
        e.setId(id);
        e.setFactType(type);
        e.setFactText(text);
        e.setConfidence(0.8);
        return e;
    }

    @Test
    void list_noUser_returnsEmpty() {
        ApiResponse<List<LongTermMemoryController.LtmFactVO>> resp = controller.list();
        assertThat(resp.getData()).isEmpty();
        verify(service, never()).listActive(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void list_mapsFactsToVo() {
        login(7L);
        when(service.listActive(7L)).thenReturn(List.of(fact(1, "PREFERENCE", "只想远程")));
        ApiResponse<List<LongTermMemoryController.LtmFactVO>> resp = controller.list();
        assertThat(resp.getData()).hasSize(1);
        assertThat(resp.getData().get(0).getFactText()).isEqualTo("只想远程");
        assertThat(resp.getData().get(0).getFactType()).isEqualTo("PREFERENCE");
    }

    @Test
    void forget_delegatesToService() {
        login(7L);
        controller.forget(9L);
        verify(service).forget(7L, 9L);
    }

    @Test
    void forget_noUser_noop() {
        controller.forget(9L);
        verify(service, never()).forget(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void consolidate_perSession_gathersConvoAndReturnsStats() {
        login(7L);
        properties.setEnabled(true);
        when(agentMessageMapper.findActiveSessionIds(org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(11L, 22L));
        when(agentMessageMapper.findSessionConversationTexts(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of("m1", "m2", "m3", "m4"));
        when(consolidationService.consolidate(org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.anyLong())).thenReturn(2);

        ApiResponse<java.util.Map<String, Object>> resp = controller.consolidate(7);

        assertThat(resp.getData().get("ltmEnabled")).isEqualTo(true);
        assertThat(resp.getData().get("sessionCount")).isEqualTo(2);
        assertThat(resp.getData().get("factsStored")).isEqualTo(4);   // 2 会话 × 2
        assertThat(resp.getData().get("messageCount")).isEqualTo(8);  // 2 会话 × 4
        // 每条会话都带上其 sessionId 蒸馏
        verify(consolidationService).consolidate(org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.eq(11L));
        verify(consolidationService).consolidate(org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.eq(22L));
    }

    @Test
    void consolidate_noUser_noop() {
        ApiResponse<java.util.Map<String, Object>> resp = controller.consolidate(7);
        assertThat(resp.getData().get("factsStored")).isEqualTo(0);
        verify(consolidationService, never()).consolidate(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.anyLong());
    }
}
