package com.careermate.pipeline;

import com.careermate.common.api.ApiResponse;
import com.careermate.pipeline.controller.PipelineController;
import com.careermate.pipeline.dto.ApplicationVO;
import com.careermate.pipeline.dto.CreateApplicationRequest;
import com.careermate.pipeline.dto.PipelineBoardVO;
import com.careermate.pipeline.dto.UpdateStageRequest;
import com.careermate.pipeline.service.PipelineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PipelineControllerTest {

    @Mock
    private PipelineService service;

    private PipelineController controller;

    @BeforeEach
    void setUp() {
        controller = new PipelineController(service);
    }

    @Test
    void boardDelegates() {
        PipelineBoardVO board = new PipelineBoardVO();
        board.setTotal(3);
        when(service.getBoard(any())).thenReturn(board);

        ApiResponse<PipelineBoardVO> resp = controller.board();
        assertNotNull(resp.getData());
        assertEquals(3, resp.getData().getTotal());
    }

    @Test
    void createDelegates() {
        ApplicationVO vo = new ApplicationVO();
        vo.setId(7L);
        when(service.createApplication(any(), any())).thenReturn(vo);

        ApiResponse<ApplicationVO> resp = controller.create(new CreateApplicationRequest());
        assertEquals(7L, resp.getData().getId());
    }

    @Test
    void updateStageDelegates() {
        ApplicationVO vo = new ApplicationVO();
        vo.setStage("OFFER");
        when(service.updateStage(any(), eq(10L), eq("OFFER"))).thenReturn(vo);

        UpdateStageRequest req = new UpdateStageRequest();
        req.setStage("OFFER");
        ApiResponse<ApplicationVO> resp = controller.updateStage(10L, req);
        assertEquals("OFFER", resp.getData().getStage());
    }

    @Test
    void archiveDelegates() {
        ApiResponse<Void> resp = controller.archive(10L);
        verify(service, times(1)).archiveApplication(any(), eq(10L));
        assertNotNull(resp);
    }
}
