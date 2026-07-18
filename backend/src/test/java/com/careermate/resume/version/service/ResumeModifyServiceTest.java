package com.careermate.resume.version.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.careermate.llm.LlmClient;
import com.careermate.llm.dto.ChatRequest;
import com.careermate.llm.dto.ChatResponse;
import com.careermate.mapper.ResumeVersionMapper;
import com.careermate.model.entity.ResumeVersionEntity;
import com.careermate.prompt.PromptRenderResult;
import com.careermate.prompt.PromptTemplateService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ResumeModifyServiceTest {

    private final LlmClient llmClient = mock(LlmClient.class);
    private final PromptTemplateService promptTemplateService = mock(PromptTemplateService.class);
    private final ResumeVersionService resumeVersionService = mock(ResumeVersionService.class);
    private final ResumeVersionMapper mapper = mock(ResumeVersionMapper.class);
    private final ResumeModifyService service =
            new ResumeModifyService(llmClient, promptTemplateService, resumeVersionService, mapper);

    @Test
    void modify_appliesMinimalEditAndCreatesVersion() {
        ResumeVersionEntity cur = new ResumeVersionEntity();
        cur.setVersionId("v-1");
        cur.setUserId(7L);
        cur.setSessionId("s-1");
        cur.setTargetJdId(88L);
        cur.setTargetJdLabel("字节 Java");
        cur.setContentMarkdown("# 张三\n## 专业技能\nJava");
        when(mapper.selectOne(any())).thenReturn(cur);
        when(promptTemplateService.render(anyString()))
                .thenReturn(new PromptRenderResult("resume-generate-from-jd", "v3", "SYSTEM"));
        when(llmClient.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder()
                .content("# 张三\n## 专业技能\nJava、Go\n\n```meta\n{\"change_summary\":\"补 Go\","
                        + "\"changes\":[{\"reason\":\"补技能\",\"anchor\":\"Java、Go\"}]}\n```")
                .build());

        service.modify(7L, "v-1", "技能里加上 Go");

        ArgumentCaptor<String> md = ArgumentCaptor.forClass(String.class);
        verify(resumeVersionService).createVersion(eq(7L), eq("s-1"), any(),
                eq("88"), eq("字节 Java"), any(), any(),
                md.capture(), anyString(), any());
        assertThat(md.getValue()).contains("Java、Go").doesNotContain("meta");
    }

    @Test
    void modify_blankInstruction_throws() {
        try {
            service.modify(7L, "v-1", "  ");
            assertThat(false).isTrue();
        } catch (Exception e) {
            assertThat(e).isNotNull();
        }
    }
}
