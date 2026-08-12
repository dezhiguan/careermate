package com.careermate.pipeline;

import com.careermate.common.exception.BizException;
import com.careermate.mapper.SavedJobMapper;
import com.careermate.model.entity.SavedJobEntity;
import com.careermate.pipeline.dto.ApplicationVO;
import com.careermate.pipeline.dto.SaveJobRequest;
import com.careermate.pipeline.dto.SavedJobVO;
import com.careermate.pipeline.service.PipelineService;
import com.careermate.pipeline.service.SavedJobService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SavedJobServiceTest {

    @Mock
    private SavedJobMapper mapper;
    @Mock
    private PipelineService pipelineService;
    @Mock
    private com.careermate.opportunity.service.OpportunityService opportunityService;

    private SavedJobService service;

    @BeforeEach
    void setUp() {
        service = new SavedJobService(mapper, pipelineService, opportunityService);
    }

    @Test
    void saveNewInserts() {
        when(mapper.selectOne(any())).thenReturn(null);
        SaveJobRequest req = new SaveJobRequest();
        req.setJdDocId(89840L);
        req.setCompany("字节跳动");
        req.setRoleTitle("Java");

        SavedJobVO vo = service.save(1L, req);

        verify(mapper, times(1)).insert(any(SavedJobEntity.class));
        assertEquals(89840L, vo.jdDocId());
        assertEquals("字节跳动", vo.company());
    }

    @Test
    void saveIdempotentReturnsExisting() {
        when(mapper.selectOne(any())).thenReturn(entity(5L, 1L, 89840L));
        SaveJobRequest req = new SaveJobRequest();
        req.setJdDocId(89840L);

        SavedJobVO vo = service.save(1L, req);

        verify(mapper, never()).insert(any(SavedJobEntity.class));
        assertEquals(5L, vo.id());
    }

    @Test
    void saveMissingJdRejected() {
        assertThrows(BizException.class, () -> service.save(1L, new SaveJobRequest()));
    }

    @Test
    void removeByJdSoftDeletes() {
        when(mapper.selectOne(any())).thenReturn(entity(5L, 1L, 89840L));
        service.removeByJd(1L, 89840L);
        ArgumentCaptor<SavedJobEntity> cap = ArgumentCaptor.forClass(SavedJobEntity.class);
        verify(mapper).updateById((SavedJobEntity) cap.capture());
        assertNotNull(cap.getValue().getDeletedAt());
    }

    @Test
    void removeByJdMissingIsNoop() {
        when(mapper.selectOne(any())).thenReturn(null);
        service.removeByJd(1L, 89840L);
        verify(mapper, never()).updateById(any(SavedJobEntity.class));
    }

    @Test
    void promoteCreatesApplicationAndRemovesSaved() {
        when(mapper.selectOne(any())).thenReturn(entity(5L, 1L, 89840L));
        ApplicationVO app = new ApplicationVO();
        app.setId(10L);
        app.setStage("PREPARING");
        when(pipelineService.createApplication(any(), any())).thenReturn(app);

        ApplicationVO vo = service.promote(1L, 89840L);

        verify(pipelineService, times(1)).createApplication(any(), any());
        ArgumentCaptor<SavedJobEntity> cap = ArgumentCaptor.forClass(SavedJobEntity.class);
        verify(mapper).updateById((SavedJobEntity) cap.capture());
        assertNotNull(cap.getValue().getDeletedAt());
        assertEquals(10L, vo.getId());
    }

    @Test
    void promoteMissingRejected() {
        when(mapper.selectOne(any())).thenReturn(null);
        assertThrows(BizException.class, () -> service.promote(1L, 89840L));
        verify(pipelineService, never()).createApplication(any(), any());
    }

    @Test
    void listMapsRows() {
        when(mapper.selectList(any())).thenReturn(List.of(entity(1L, 1L, 1L), entity(2L, 1L, 2L)));
        List<SavedJobVO> list = service.list(1L);
        assertEquals(2, list.size());
    }

    @Test
    void listEmptyForNullUser() {
        assertEquals(0, service.list(null).size());
    }

    private static SavedJobEntity entity(Long id, Long userId, Long jdDocId) {
        SavedJobEntity e = new SavedJobEntity();
        e.setId(id);
        e.setUserId(userId);
        e.setJdDocId(jdDocId);
        e.setCompany("公司" + id);
        e.setSavedAt(LocalDateTime.now());
        return e;
    }
}
