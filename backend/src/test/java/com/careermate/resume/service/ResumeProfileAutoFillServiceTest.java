package com.careermate.resume.service;

import com.careermate.profile.dto.CareerProfileResponse;
import com.careermate.profile.dto.CareerProfileUpsertRequest;
import com.careermate.profile.service.CareerProfileService;
import com.careermate.resume.service.ResumeProfileExtractor.ExtractedProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResumeProfileAutoFillServiceTest {

    @Mock
    private ResumeProfileExtractor extractor;
    @Mock
    private CareerProfileService careerProfileService;

    private ResumeProfileAutoFillService service;

    @BeforeEach
    void setUp() {
        service = new ResumeProfileAutoFillService(extractor, careerProfileService);
    }

    @Test
    void fillsOnlyEmptyFieldsAndNeverOverwritesUserValues() {
        // 用户已手填 targetRole=架构师；其它字段为空
        when(extractor.extract(any())).thenReturn(Optional.of(new ExtractedProfile(
                "Java后端工程师", "广州", "3-5年", "全职", List.of("Java", "Spring"))));
        when(careerProfileService.getProfile(1L)).thenReturn(CareerProfileResponse.builder()
                .targetRole("架构师")
                .targetCity("")
                .seniority(null)
                .workMode(null)
                .skillKeywords(List.of())
                .build());

        List<String> filled = service.autoFill(1L, "简历内容");

        // targetRole 已填，不应被覆盖
        assertFalse(filled.contains("targetRole"));
        assertTrue(filled.containsAll(List.of("targetCity", "seniority", "workMode", "skillKeywords")));

        ArgumentCaptor<CareerProfileUpsertRequest> captor = ArgumentCaptor.forClass(CareerProfileUpsertRequest.class);
        verify(careerProfileService).upsertProfile(eq(1L), captor.capture(), eq("resume_autofill"));
        CareerProfileUpsertRequest req = captor.getValue();
        assertNull(req.getTargetRole(), "已填字段不应出现在回填请求里");
        assertEquals("广州", req.getTargetCity());
        assertEquals("3-5年", req.getSeniority());
        assertEquals(List.of("Java", "Spring"), req.getSkillKeywords());
    }

    @Test
    void noUpsertWhenExtractionEmpty() {
        when(extractor.extract(any())).thenReturn(Optional.empty());

        List<String> filled = service.autoFill(1L, "简历内容");

        assertTrue(filled.isEmpty());
        verify(careerProfileService, never()).upsertProfile(any(), any(), any());
    }

    @Test
    void noUpsertWhenAllFieldsAlreadyFilled() {
        when(extractor.extract(any())).thenReturn(Optional.of(new ExtractedProfile(
                "Java后端工程师", "广州", "3-5年", "全职", List.of("Java"))));
        when(careerProfileService.getProfile(1L)).thenReturn(CareerProfileResponse.builder()
                .targetRole("后端")
                .targetCity("深圳")
                .seniority("5-10年")
                .workMode("全职")
                .skillKeywords(List.of("Go"))
                .build());

        List<String> filled = service.autoFill(1L, "简历内容");

        assertTrue(filled.isEmpty());
        verify(careerProfileService, never()).upsertProfile(any(), any(), any());
    }

    @Test
    void nullUserIdReturnsEmptyWithoutExtracting() {
        List<String> filled = service.autoFill(null, "简历内容");
        assertTrue(filled.isEmpty());
        verify(extractor, never()).extract(any());
    }
}
