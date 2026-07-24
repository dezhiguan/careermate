package com.careermate.agent.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.careermate.common.api.PageResult;
import com.careermate.model.entity.AgentSessionEntity;
import com.careermate.opportunity.dto.OpportunityListItemVO;
import com.careermate.opportunity.service.OpportunityService;
import com.careermate.profile.dto.CareerProfileUpsertRequest;
import com.careermate.profile.service.CareerProfileService;
import com.careermate.resume.version.dto.ResumeVersionListItemVO;
import com.careermate.resume.version.service.ResumeVersionService;
import com.careermate.workspace.support.WorkspaceSessionRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DesignGapToolsTest {

    @Mock
    private CareerProfileService careerProfileService;
    @Mock
    private OpportunityService opportunityService;
    @Mock
    private ResumeVersionService resumeVersionService;
    @Mock
    private WorkspaceSessionRepository workspaceSessionRepository;

    // ---- #P1-2 update_career_profile ----

    @Test
    void updateProfileOnlyAppliesProvidedFields() {
        UpdateCareerProfileTool tool = new UpdateCareerProfileTool(careerProfileService);
        AgentToolContext ctx = AgentToolContext.builder()
                .userId(7L).sessionId("WS-a")
                .args(Map.of("targetSalaryRange", "30-45k"))
                .build();

        AgentToolResult r = tool.execute(ctx);

        assertTrue(r.isSuccess());
        ArgumentCaptor<CareerProfileUpsertRequest> cap = ArgumentCaptor.forClass(CareerProfileUpsertRequest.class);
        verify(careerProfileService).upsertProfile(eq(7L), cap.capture(), eq("agent"));
        assertEquals("30-45k", cap.getValue().getTargetSalaryRange());
        assertEquals(null, cap.getValue().getTargetCity());
    }

    @Test
    void updateProfileFailsWhenNoRecognizableField() {
        UpdateCareerProfileTool tool = new UpdateCareerProfileTool(careerProfileService);
        AgentToolContext ctx = AgentToolContext.builder()
                .userId(7L).args(Map.of("unknown", "x")).build();
        assertFalse(tool.execute(ctx).isSuccess());
    }

    // ---- #P1-3 filter_opportunities ----

    @Test
    void filterOpportunitiesPassesCityKeywordAndMapsItems() {
        FilterOpportunitiesTool tool = new FilterOpportunitiesTool(opportunityService);
        OpportunityListItemVO vo = new OpportunityListItemVO(
                "doc-9", 9L, "字节", "后端", "P6", "30-45k", "广州", "3-5年", 3, 5,
                "本科", "10000人", "2026-07-01", 88, "HIGH",
                List.of("Java"), List.of(), List.of("Java"), 0.9, "url", false);
        when(opportunityService.list(eq(11L), any())).thenReturn(
                new PageResult<>(1L, 1, 8, true, "match", List.of(vo)));

        AgentToolContext ctx = AgentToolContext.builder()
                .userId(11L).args(Map.of("city", "广州", "keyword", "后端")).build();
        AgentToolResult r = tool.execute(ctx);

        assertTrue(r.isSuccess());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) r.getData().get("items");
        assertEquals(1, items.size());
        assertEquals("doc-9", items.get(0).get("jdId"));
        assertEquals("广州", items.get(0).get("city"));
    }

    // ---- #P1-4 list_resume_versions ----

    @Test
    void listResumeVersionsRequiresJdPrepSession() {
        ListResumeVersionsTool tool = new ListResumeVersionsTool(workspaceSessionRepository, resumeVersionService);
        AgentSessionEntity session = new AgentSessionEntity();
        session.setWorkspaceType(WorkspaceSessionRepository.WORKSPACE_JD_PREP);
        when(workspaceSessionRepository.requireSession(5L, "WS-b")).thenReturn(session);

        AgentToolContext ctx = AgentToolContext.builder().userId(5L).sessionId("WS-b").build();
        assertTrue(tool.supports(ctx));
    }

    @Test
    void listResumeVersionsReturnsVersions() {
        ListResumeVersionsTool tool = new ListResumeVersionsTool(workspaceSessionRepository, resumeVersionService);
        ResumeVersionListItemVO v = new ResumeVersionListItemVO(
                "ver-1", "字节·后端 v1", "字节JD", "字节", 9L, "后端", 1,
                "初版", null, false, 0, null);
        when(resumeVersionService.listBySession(5L, "WS-b")).thenReturn(List.of(v));

        AgentToolContext ctx = AgentToolContext.builder().userId(5L).sessionId("WS-b").build();
        AgentToolResult r = tool.execute(ctx);

        assertTrue(r.isSuccess());
        assertEquals(1, r.getData().get("count"));
    }
}
