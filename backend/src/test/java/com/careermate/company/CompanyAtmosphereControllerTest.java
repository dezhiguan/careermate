package com.careermate.company;

import com.careermate.common.api.ApiResponse;
import com.careermate.company.controller.CompanyAtmosphereController;
import com.careermate.company.dto.CompanyAtmosphereVO;
import com.careermate.company.service.CompanyAtmosphereService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyAtmosphereControllerTest {

    @Mock
    private CompanyAtmosphereService service;

    @Test
    void atmosphereDelegatesToServiceAndWraps() {
        CompanyAtmosphereVO vo = new CompanyAtmosphereVO();
        vo.setCompanyName("字节跳动");
        vo.setDataAvailable(true);
        when(service.getCompanyAtmosphere(eq("字节跳动"))).thenReturn(vo);

        CompanyAtmosphereController controller = new CompanyAtmosphereController(service);
        ApiResponse<CompanyAtmosphereVO> response = controller.atmosphere("字节跳动");

        assertNotNull(response.getData());
        assertEquals("字节跳动", response.getData().getCompanyName());
        assertTrue(response.getData().isDataAvailable());
    }
}
