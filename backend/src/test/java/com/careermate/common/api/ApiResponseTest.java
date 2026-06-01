package com.careermate.common.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ApiResponseTest {

    @Test
    void successWithoutData() {
        ApiResponse<Void> response = ApiResponse.success();

        assertEquals(ErrorCode.SUCCESS.getCode(), response.getCode());
        assertEquals(ErrorCode.SUCCESS.getMessage(), response.getMessage());
        assertNull(response.getData());
        assertNull(response.getTraceId());
        assertNotNull(response.getTimestamp());
    }

    @Test
    void successWithData() {
        ApiResponse<String> response = ApiResponse.success("ok");

        assertEquals(ErrorCode.SUCCESS.getCode(), response.getCode());
        assertEquals("ok", response.getData());
    }

    @Test
    void failWithoutTraceId() {
        ApiResponse<Void> response = ApiResponse.fail(400, "bad request");

        assertEquals(400, response.getCode());
        assertEquals("bad request", response.getMessage());
        assertNull(response.getData());
        assertNull(response.getTraceId());
    }

    @Test
    void failWithTraceId() {
        ApiResponse<Void> response = ApiResponse.fail(500, "error", "trace-123");

        assertEquals(500, response.getCode());
        assertEquals("trace-123", response.getTraceId());
    }
}
