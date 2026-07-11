package com.careermate.common.exception;

import com.careermate.common.api.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.MissingServletRequestParameterException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * QA 发现修复：缺必填请求参数原先落到通用 500「系统异常」，现改为友好 400。
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void missingRequiredParamReturnsFriendly400() {
        MissingServletRequestParameterException ex =
                new MissingServletRequestParameterException("q", "String");

        ApiResponse<Void> resp = handler.handleMissingParam(ex);

        assertEquals(400, resp.getCode());
        assertTrue(resp.getMessage().contains("缺少必填参数"), "应为友好中文提示");
        assertTrue(resp.getMessage().contains("q"), "应含缺失的参数名");
    }
}
