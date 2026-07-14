package com.careermate.resume.coldstart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.careermate.agent.context.CareerProfileContextProvider;
import com.careermate.agent.context.CareerProfileContextResult;
import com.careermate.resume.service.ResumeService;

@ExtendWith(MockitoExtension.class)
class ColdStartResumeServiceTest {

    @Mock
    private ResumeService resumeService;
    @Mock
    private CareerProfileContextProvider profileProvider;

    private ColdStartResumeService service;

    void init() {
        service = new ColdStartResumeService(resumeService, new DefaultResumeSkeletonBuilder(), profileProvider);
    }

    @Test
    void buildUsesL2WhenProfileHasTargetRole() {
        init();
        when(profileProvider.load(7L)).thenReturn(CareerProfileContextResult.builder()
                .available(true).targetRole("高级 Java 工程师").skillCount(3).build());

        ColdStartResume cold = service.build(7L);

        assertThat(cold.origin()).isEqualTo(ResumeOrigin.COLD_START);
        assertThat(cold.readiness()).isEqualTo(ResumeReadiness.DRAFT_SKELETON);
        assertThat(cold.title()).contains("高级 Java 工程师");
        assertThat(cold.sourceSignals()).contains("career_profile");
    }

    @Test
    void buildFallsBackToL3WhenNoProfile() {
        init();
        when(profileProvider.load(7L)).thenReturn(CareerProfileContextResult.empty());

        ColdStartResume cold = service.build(7L);

        assertThat(cold.sourceSignals()).contains("default_skeleton");
        assertThat(cold.title()).isEqualTo("我的简历（待完善）");
    }

    @Test
    void buildFallsBackToL3WhenProfileLoadThrows() {
        init();
        when(profileProvider.load(7L)).thenThrow(new RuntimeException("memory down"));

        ColdStartResume cold = service.build(7L);

        assertThat(cold.readiness()).isEqualTo(ResumeReadiness.DRAFT_SKELETON);
        assertThat(cold.sourceSignals()).contains("default_skeleton");
    }

    @Test
    void createForUserPersistsBuiltSkeleton() {
        init();
        when(profileProvider.load(7L)).thenReturn(CareerProfileContextResult.empty());

        service.createForUser(7L);

        ArgumentCaptor<ColdStartResume> captor = ArgumentCaptor.forClass(ColdStartResume.class);
        verify(resumeService).createColdStartResume(eq(7L), captor.capture());
        assertThat(captor.getValue().isDraftSkeleton()).isTrue();
    }
}
