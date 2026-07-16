package com.careermate.agent.tool.springai;

import com.careermate.agent.tool.AgentToolResult;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 验证 {@link SpringAiToolCallbackFactory#buildRenderableCard} 只为六项能力工具、成功且有数据时产出卡片。
 */
class SpringAiToolCardTest {

    @Test
    void companyToolProducesCompanyCard() {
        AgentToolResult r = AgentToolResult.success("get_company_atmosphere", "s",
                Map.of("companyName", "字节跳动", "dataAvailable", true));
        Map<String, Object> card = SpringAiToolCallbackFactory.buildRenderableCard(r);
        assertEquals("COMPANY_ATMOSPHERE", card.get("type"));
        assertEquals("字节跳动", card.get("companyName"));
    }

    @Test
    void interviewToolProducesInterviewCard() {
        AgentToolResult r = AgentToolResult.success("generate_jd_aware_questions", "s",
                Map.of("jdTitle", "字节-后端", "questionCount", 3));
        Map<String, Object> card = SpringAiToolCallbackFactory.buildRenderableCard(r);
        assertEquals("INTERVIEW_QUESTIONS", card.get("type"));
        assertEquals(3, card.get("questionCount"));
    }

    @Test
    void salaryToolProducesSalaryCard() {
        AgentToolResult r = AgentToolResult.success("get_salary_guidance", "s",
                Map.of("p50", "30K", "quartile", "P50-P75"));
        Map<String, Object> card = SpringAiToolCallbackFactory.buildRenderableCard(r);
        assertEquals("SALARY_GUIDANCE", card.get("type"));
        assertEquals("P50-P75", card.get("quartile"));
    }

    @Test
    void nonCardToolReturnsNull() {
        AgentToolResult r = AgentToolResult.success("create_career_task", "s", Map.of("taskId", 1));
        assertNull(SpringAiToolCallbackFactory.buildRenderableCard(r));
    }

    @Test
    void failureOrEmptyOrNullReturnsNull() {
        assertNull(SpringAiToolCallbackFactory.buildRenderableCard(null));
        assertNull(SpringAiToolCallbackFactory.buildRenderableCard(
                AgentToolResult.failure("get_company_atmosphere", "fail", "err")));
        assertNull(SpringAiToolCallbackFactory.buildRenderableCard(
                AgentToolResult.success("get_company_atmosphere", "s", Map.of())));
    }
}
