package com.careermate.common.api;

import com.careermate.observability.MdcKeys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mockStatic;

class ApiResponseTest {

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void successWithoutDataUsesMdcTraceId() {
        MDC.put(MdcKeys.TRACE_ID, "trace-from-mdc");

        ApiResponse<Void> response = ApiResponse.success();

        assertEquals(ErrorCode.SUCCESS.getCode(), response.getCode());
        assertEquals(ErrorCode.SUCCESS.getMessage(), response.getMessage());
        assertNull(response.getData());
        assertEquals("trace-from-mdc", response.getTraceId());
        assertNotNull(response.getTimestamp());
    }

    @Test
    void successWithDataUsesMdcTraceId() {
        MDC.put(MdcKeys.TRACE_ID, "trace-abc");

        ApiResponse<String> response = ApiResponse.success("ok");

        assertEquals(ErrorCode.SUCCESS.getCode(), response.getCode());
        assertEquals("ok", response.getData());
        assertEquals("trace-abc", response.getTraceId());
    }

    @Test
    void successPrefersSkyWalkingTraceIdOverMdc() {
        MDC.put(MdcKeys.TRACE_ID, "trace-from-mdc");

        try (MockedStatic<org.apache.skywalking.apm.toolkit.trace.TraceContext> skyWalking =
                     mockStatic(org.apache.skywalking.apm.toolkit.trace.TraceContext.class)) {
            skyWalking.when(org.apache.skywalking.apm.toolkit.trace.TraceContext::traceId)
                    .thenReturn("sw-trace-001");

            ApiResponse<String> response = ApiResponse.success("ok");

            assertEquals("sw-trace-001", response.getTraceId());
        }
    }

    @Test
    void successFallsBackToRequestIdWhenTraceIdMissing() {
        MDC.put(MdcKeys.REQUEST_ID, "req-fallback-99");

        ApiResponse<Void> response = ApiResponse.success();

        assertEquals("req-fallback-99", response.getTraceId());
    }

    @Test
    void failWithoutExplicitTraceIdUsesMdcTraceId() {
        MDC.put(MdcKeys.TRACE_ID, "trace-fail");

        ApiResponse<Void> response = ApiResponse.fail(400, "bad request");

        assertEquals(400, response.getCode());
        assertEquals("bad request", response.getMessage());
        assertNull(response.getData());
        assertEquals("trace-fail", response.getTraceId());
    }

    @Test
    void failFallsBackToRequestIdWhenTraceIdMissing() {
        MDC.put(MdcKeys.REQUEST_ID, "req-fail-fallback");

        ApiResponse<Void> response = ApiResponse.fail(401, "unauthorized");

        assertEquals("req-fail-fallback", response.getTraceId());
    }

    @Test
    void failWithExplicitTraceIdOverridesMdc() {
        MDC.put(MdcKeys.TRACE_ID, "trace-mdc");

        ApiResponse<Void> response = ApiResponse.fail(500, "error", "trace-123");

        assertEquals(500, response.getCode());
        assertEquals("trace-123", response.getTraceId());
    }

    @Test
    void failGeneratesTraceIdWhenMdcEmpty() {
        ApiResponse<Void> response = ApiResponse.fail(500, "error");

        assertNotNull(response.getTraceId());
    }
}
