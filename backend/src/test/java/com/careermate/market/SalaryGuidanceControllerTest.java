package com.careermate.market;

import com.careermate.common.api.ApiResponse;
import com.careermate.market.controller.SalaryGuidanceController;
import com.careermate.market.dto.SalaryGuidanceVO;
import com.careermate.market.service.SalaryGuidanceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SalaryGuidanceControllerTest {

    @Mock
    private SalaryGuidanceService service;

    @Test
    void salaryGuidanceDelegatesAndWraps() {
        SalaryGuidanceVO vo = new SalaryGuidanceVO();
        vo.setDataAvailable(true);
        when(service.getSalaryGuidance(any(), eq("Java后端"), eq("广州"), eq("3-5年"))).thenReturn(vo);

        SalaryGuidanceController controller = new SalaryGuidanceController(service);
        ApiResponse<SalaryGuidanceVO> response = controller.salaryGuidance("Java后端", "广州", "3-5年");

        assertNotNull(response.getData());
        assertTrue(response.getData().isDataAvailable());
    }
}
