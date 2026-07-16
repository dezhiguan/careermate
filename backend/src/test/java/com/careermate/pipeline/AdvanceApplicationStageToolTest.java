package com.careermate.pipeline;

import com.careermate.agent.tool.AdvanceApplicationStageTool;
import com.careermate.agent.tool.AgentToolContext;
import com.careermate.agent.tool.AgentToolDomain;
import com.careermate.agent.tool.AgentToolResult;
import com.careermate.common.exception.BizException;
import com.careermate.pipeline.dto.ApplicationVO;
import com.careermate.pipeline.service.PipelineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdvanceApplicationStageToolTest {

    @Mock
    private PipelineService pipelineService;

    private AdvanceApplicationStageTool tool;

    @BeforeEach
    void setUp() {
        tool = new AdvanceApplicationStageTool(pipelineService);
    }

    @Test
    void metadataIsWellFormed() {
        assertEquals("advance_application_stage", tool.name());
        assertEquals(AgentToolDomain.DASHBOARD, tool.definition().getDomain());
        assertEquals(4, tool.definition().getParameters().size());
        assertTrue(tool.supports(AgentToolContext.builder().build()));
    }

    @Test
    void executeMovesStage() {
        ApplicationVO vo = new ApplicationVO();
        vo.setId(10L);
        vo.setJdDocId(599L);
        vo.setCompany("字节跳动");
        vo.setStage("INTERVIEW_SCHEDULED");
        vo.setStageLabel("约面");
        when(pipelineService.updateStageByJd(eq(1L), eq(599L), eq("INTERVIEW_SCHEDULED"), any(), any()))
                .thenReturn(vo);

        AgentToolResult r = tool.execute(AgentToolContext.builder()
                .userId(1L)
                .args(Map.of("jdDocId", "599", "stage", "INTERVIEW_SCHEDULED"))
                .build());

        assertTrue(r.isSuccess());
        assertTrue(r.getSummary().contains("字节跳动"));
        assertTrue(r.getSummary().contains("约面"));
        assertEquals("INTERVIEW_SCHEDULED", r.getData().get("stage"));
    }

    @Test
    void executeAcceptsDocPrefixedJdId() {
        // 小职上下文里的 jdId 是「doc-89840」，工具需容错剥前缀取数字
        ApplicationVO vo = new ApplicationVO();
        vo.setId(10L);
        vo.setJdDocId(89840L);
        vo.setCompany("字节跳动");
        vo.setStage("OFFER");
        vo.setStageLabel("Offer/谈薪");
        when(pipelineService.updateStageByJd(eq(1L), eq(89840L), eq("OFFER"), any(), any()))
                .thenReturn(vo);

        AgentToolResult r = tool.execute(AgentToolContext.builder()
                .userId(1L)
                .args(Map.of("jdDocId", "doc-89840", "stage", "OFFER"))
                .build());

        assertTrue(r.isSuccess());
        assertEquals("OFFER", r.getData().get("stage"));
    }

    @Test
    void executeUnauthenticatedFails() {
        AgentToolResult r = tool.execute(AgentToolContext.builder()
                .args(Map.of("jdDocId", "1", "stage", "OFFER"))
                .build());
        assertFalse(r.isSuccess());
    }

    @Test
    void executeMissingJdDocIdFails() {
        AgentToolResult r = tool.execute(AgentToolContext.builder()
                .userId(1L)
                .args(Map.of("stage", "OFFER"))
                .build());
        assertFalse(r.isSuccess());
    }

    @Test
    void executeMissingStageFails() {
        AgentToolResult r = tool.execute(AgentToolContext.builder()
                .userId(1L)
                .args(Map.of("jdDocId", "599"))
                .build());
        assertFalse(r.isSuccess());
    }

    @Test
    void executeWrapsServiceError() {
        when(pipelineService.updateStageByJd(any(), any(), any(), any(), any()))
                .thenThrow(new BizException(400, "非法阶段：HIRED"));

        AgentToolResult r = tool.execute(AgentToolContext.builder()
                .userId(1L)
                .args(Map.of("jdDocId", "599", "stage", "HIRED"))
                .build());

        assertFalse(r.isSuccess());
    }
}
