package com.careermate.pipeline;

import com.careermate.agent.tool.AgentToolContext;
import com.careermate.agent.tool.AgentToolDomain;
import com.careermate.agent.tool.AgentToolResult;
import com.careermate.agent.tool.GetPipelineBoardTool;
import com.careermate.pipeline.dto.PipelineBoardVO;
import com.careermate.pipeline.service.PipelineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetPipelineBoardToolTest {

    @Mock
    private PipelineService pipelineService;

    private GetPipelineBoardTool tool;

    @BeforeEach
    void setUp() {
        tool = new GetPipelineBoardTool(pipelineService);
    }

    @Test
    void metadataIsWellFormed() {
        assertEquals("get_pipeline_board", tool.name());
        assertEquals(AgentToolDomain.DASHBOARD, tool.definition().getDomain());
        assertTrue(tool.supports(AgentToolContext.builder().build()));
    }

    @Test
    void executeUnauthenticatedFails() {
        AgentToolResult r = tool.execute(AgentToolContext.builder().build());
        assertFalse(r.isSuccess());
    }

    @Test
    void executeSummarizesBoard() {
        when(pipelineService.getBoard(eq(1L))).thenReturn(board());

        AgentToolResult r = tool.execute(AgentToolContext.builder().userId(1L).build());

        assertTrue(r.isSuccess());
        assertEquals(3, r.getData().get("total"));
        assertTrue(r.getSummary().contains("3 个在办"));
        assertTrue(r.getSummary().contains("面试中"));
    }

    @Test
    void executeEmptyBoard() {
        PipelineBoardVO empty = new PipelineBoardVO();
        empty.setTotal(0);
        empty.setColumns(List.of());
        when(pipelineService.getBoard(eq(1L))).thenReturn(empty);

        AgentToolResult r = tool.execute(AgentToolContext.builder().userId(1L).build());
        assertTrue(r.isSuccess());
        assertTrue(r.getSummary().contains("还没有"));
    }

    private static PipelineBoardVO board() {
        PipelineBoardVO b = new PipelineBoardVO();
        b.setTotal(3);
        b.setColumns(List.of(
                column("PREPARING", "准备/投递", 2),
                column("INTERVIEWING", "面试中", 1),
                column("OFFER", "Offer/谈薪", 0)
        ));
        return b;
    }

    private static PipelineBoardVO.Column column(String stage, String label, int count) {
        PipelineBoardVO.Column c = new PipelineBoardVO.Column();
        c.setStage(stage);
        c.setLabel(label);
        c.setCount(count);
        return c;
    }
}
