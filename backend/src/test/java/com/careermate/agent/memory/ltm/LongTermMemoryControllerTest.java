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
    private final LongTermMemoryController controller = new LongTermMemoryController(service);

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
}
