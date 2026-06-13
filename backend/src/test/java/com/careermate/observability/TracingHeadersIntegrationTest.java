package com.careermate.observability;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "management.tracing.enabled=true")
class TracingHeadersIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthReturnsRequestAndTraceHeaders() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(header().exists(MdcKeys.HEADER_REQUEST_ID))
                .andExpect(header().exists(MdcKeys.HEADER_TRACE_ID));
    }

    @Test
    void echoesIncomingRequestId() throws Exception {
        mockMvc.perform(get("/api/health").header(MdcKeys.HEADER_REQUEST_ID, "fixed-req-id"))
                .andExpect(status().isOk())
                .andExpect(header().string(MdcKeys.HEADER_REQUEST_ID, "fixed-req-id"));
    }

    @Test
    void mdcDoesNotLeakAfterRequest() throws Exception {
        MDC.put("preExisting", "value");
        try {
            mockMvc.perform(get("/api/health")).andExpect(status().isOk());
            assertThat(MDC.get("preExisting")).isEqualTo("value");
            assertThat(MDC.get(MdcKeys.REQUEST_ID)).isNull();
            assertThat(MDC.get(MdcKeys.TRACE_ID)).isNull();
        } finally {
            MDC.clear();
        }
    }
}
