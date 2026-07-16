package com.careermate.pipeline;

import com.careermate.common.exception.BizException;
import com.careermate.mapper.JobApplicationMapper;
import com.careermate.model.entity.JobApplicationEntity;
import com.careermate.pipeline.dto.ApplicationVO;
import com.careermate.pipeline.dto.CreateApplicationRequest;
import com.careermate.pipeline.dto.PipelineBoardVO;
import com.careermate.pipeline.service.PipelineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PipelineServiceTest {

    @Mock
    private JobApplicationMapper applicationMapper;
    @Mock
    private com.careermate.mapper.ResumeVersionMapper resumeVersionMapper;

    private PipelineService service;

    @BeforeEach
    void setUp() {
        service = new PipelineService(applicationMapper, resumeVersionMapper);
    }

    @Test
    void createNewInsertsWithDefaultStage() {
        when(applicationMapper.selectOne(any())).thenReturn(null);
        CreateApplicationRequest req = new CreateApplicationRequest();
        req.setJdDocId(599L);
        req.setCompany("字节跳动");
        req.setRoleTitle("后端开发");

        ApplicationVO vo = service.createApplication(1L, req);

        verify(applicationMapper, times(1)).insert(any(JobApplicationEntity.class));
        assertEquals("PREPARING", vo.getStage());
        assertEquals("准备/投递", vo.getStageLabel());
        assertEquals("字节跳动", vo.getCompany());
    }

    @Test
    void createDedupReturnsExistingWithoutInsert() {
        JobApplicationEntity existing = entity(10L, 1L, 599L, "INTERVIEWING");
        when(applicationMapper.selectOne(any())).thenReturn(existing);
        CreateApplicationRequest req = new CreateApplicationRequest();
        req.setJdDocId(599L);

        ApplicationVO vo = service.createApplication(1L, req);

        verify(applicationMapper, never()).insert(any(JobApplicationEntity.class));
        verify(applicationMapper, times(1)).updateById(any(JobApplicationEntity.class));
        assertEquals(10L, vo.getId());
        assertEquals("INTERVIEWING", vo.getStage());
    }

    @Test
    void createWithoutJdDocIdSkipsDedupAndInserts() {
        CreateApplicationRequest req = new CreateApplicationRequest();
        req.setCompany("内推公司");
        req.setStage("interviewing");

        ApplicationVO vo = service.createApplication(1L, req);

        verify(applicationMapper, never()).selectOne(any());
        verify(applicationMapper, times(1)).insert(any(JobApplicationEntity.class));
        assertEquals("INTERVIEWING", vo.getStage());
    }

    @Test
    void createUnauthenticatedThrows() {
        assertThrows(BizException.class, () -> service.createApplication(null, new CreateApplicationRequest()));
    }

    @Test
    void updateStageMovesCard() {
        when(applicationMapper.selectById(10L)).thenReturn(entity(10L, 1L, 599L, "PREPARING"));

        ApplicationVO vo = service.updateStage(1L, 10L, "offer");

        ArgumentCaptor<JobApplicationEntity> cap = ArgumentCaptor.forClass(JobApplicationEntity.class);
        verify(applicationMapper).updateById(cap.capture());
        assertEquals("OFFER", cap.getValue().getStage());
        assertEquals("OFFER", vo.getStage());
        assertEquals("Offer/谈薪", vo.getStageLabel());
    }

    @Test
    void updateStageRejectsInvalidStage() {
        when(applicationMapper.selectById(10L)).thenReturn(entity(10L, 1L, 599L, "PREPARING"));
        assertThrows(BizException.class, () -> service.updateStage(1L, 10L, "HIRED"));
    }

    @Test
    void updateStageRejectsForeignOwner() {
        when(applicationMapper.selectById(10L)).thenReturn(entity(10L, 2L, 599L, "PREPARING"));
        assertThrows(BizException.class, () -> service.updateStage(1L, 10L, "OFFER"));
    }

    @Test
    void updateStageNotFoundThrows() {
        when(applicationMapper.selectById(99L)).thenReturn(null);
        assertThrows(BizException.class, () -> service.updateStage(1L, 99L, "OFFER"));
    }

    @Test
    void updateStageByJdUpdatesExisting() {
        when(applicationMapper.selectOne(any())).thenReturn(entity(10L, 1L, 599L, "PREPARING"));

        ApplicationVO vo = service.updateStageByJd(1L, 599L, "INTERVIEW_SCHEDULED", null, null);

        verify(applicationMapper, never()).insert(any(JobApplicationEntity.class));
        verify(applicationMapper, times(1)).updateById(any(JobApplicationEntity.class));
        assertEquals("INTERVIEW_SCHEDULED", vo.getStage());
        assertEquals("约面", vo.getStageLabel());
    }

    @Test
    void updateStageByJdAutoCreatesWhenMissing() {
        when(applicationMapper.selectOne(any())).thenReturn(null);

        ApplicationVO vo = service.updateStageByJd(1L, 700L, "INTERVIEWING", "小红书", "Java 工程师");

        verify(applicationMapper, times(1)).insert(any(JobApplicationEntity.class));
        assertEquals("INTERVIEWING", vo.getStage());
        assertEquals("小红书", vo.getCompany());
    }

    @Test
    void updateStageByJdRejectsInvalidStage() {
        assertThrows(BizException.class, () -> service.updateStageByJd(1L, 599L, "HIRED", null, null));
    }

    @Test
    void updateStageByJdRejectsNullJd() {
        assertThrows(BizException.class, () -> service.updateStageByJd(1L, null, "OFFER", null, null));
    }

    @Test
    void updateStageByJdUnauthenticatedThrows() {
        assertThrows(BizException.class, () -> service.updateStageByJd(null, 599L, "OFFER", null, null));
    }

    @Test
    void getBoardGroupsIntoFiveColumns() {
        when(applicationMapper.selectList(any())).thenReturn(List.of(
                entity(1L, 1L, 1L, "PREPARING"),
                entity(2L, 1L, 2L, "PREPARING"),
                entity(3L, 1L, 3L, "INTERVIEWING"),
                entity(4L, 1L, 4L, "OFFER")
        ));

        PipelineBoardVO board = service.getBoard(1L);

        assertEquals(4, board.getTotal());
        assertEquals(5, board.getColumns().size());
        assertEquals(2, board.getColumns().get(0).getCount()); // PREPARING
        assertEquals("准备/投递", board.getColumns().get(0).getLabel());
        assertEquals(0, board.getColumns().get(1).getCount()); // INTERVIEW_SCHEDULED empty
        assertEquals(1, board.getColumns().get(2).getCount()); // INTERVIEWING
    }

    @Test
    void getBoardEnrichesVersionCountAndNegotiationFlag() {
        when(applicationMapper.selectList(any())).thenReturn(List.of(
                entity(1L, 1L, 700L, "PREPARING"),
                entity(2L, 1L, 800L, "OFFER")
        ));
        com.careermate.model.entity.ResumeVersionEntity v1 = new com.careermate.model.entity.ResumeVersionEntity();
        v1.setUserId(1L);
        v1.setTargetJdId(700L);
        com.careermate.model.entity.ResumeVersionEntity v2 = new com.careermate.model.entity.ResumeVersionEntity();
        v2.setUserId(1L);
        v2.setTargetJdId(700L);
        when(resumeVersionMapper.selectList(any())).thenReturn(List.of(v1, v2));

        PipelineBoardVO board = service.getBoard(1L);
        ApplicationVO preparing = board.getColumns().get(0).getApplications().get(0); // jd 700
        ApplicationVO offer = board.getColumns().get(3).getApplications().get(0); // OFFER col, jd 800

        assertEquals(2, preparing.getResumeVersionCount());
        assertEquals(0, offer.getResumeVersionCount());
        org.junit.jupiter.api.Assertions.assertTrue(offer.isNeedsSalaryNegotiation());
        org.junit.jupiter.api.Assertions.assertFalse(preparing.isNeedsSalaryNegotiation());
    }

    @Test
    void getBoardEmptyForNullUser() {
        PipelineBoardVO board = service.getBoard(null);
        assertEquals(0, board.getTotal());
        assertEquals(5, board.getColumns().size());
    }

    @Test
    void archiveSoftDeletes() {
        when(applicationMapper.selectById(10L)).thenReturn(entity(10L, 1L, 599L, "CLOSED"));

        service.archiveApplication(1L, 10L);

        ArgumentCaptor<JobApplicationEntity> cap = ArgumentCaptor.forClass(JobApplicationEntity.class);
        verify(applicationMapper).updateById(cap.capture());
        org.junit.jupiter.api.Assertions.assertNotNull(cap.getValue().getDeletedAt());
    }

    private static JobApplicationEntity entity(Long id, Long userId, Long jdDocId, String stage) {
        JobApplicationEntity e = new JobApplicationEntity();
        e.setId(id);
        e.setUserId(userId);
        e.setJdDocId(jdDocId);
        e.setStage(stage);
        e.setCompany("公司" + id);
        e.setLastActiveAt(LocalDateTime.now());
        return e;
    }
}
