package com.careermate.common.api;

import com.careermate.observability.MdcKeys;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.MDC;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {

    private Integer code;
    private String message;
    private T data;
    private String traceId;
    private Long timestamp;

    public static <T> ApiResponse<T> success() {
        return success(null);
    }

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .code(ErrorCode.SUCCESS.getCode())
                .message(ErrorCode.SUCCESS.getMessage())
                .data(data)
                .traceId(resolveCurrentTraceId())
                .timestamp(System.currentTimeMillis())
                .build();
    }

    public static <T> ApiResponse<T> fail(Integer code, String message) {
        return fail(code, message, null);
    }

    public static <T> ApiResponse<T> fail(Integer code, String message, String traceId) {
        return ApiResponse.<T>builder()
                .code(code)
                .message(message)
                .data(null)
                .traceId(StringUtils.hasText(traceId) ? traceId : resolveCurrentTraceId())
                .timestamp(System.currentTimeMillis())
                .build();
    }

    static String resolveCurrentTraceId() {
        String traceId = MDC.get(MdcKeys.TRACE_ID);
        if (StringUtils.hasText(traceId)) {
            return traceId;
        }
        String requestId = MDC.get(MdcKeys.REQUEST_ID);
        if (StringUtils.hasText(requestId)) {
            return requestId;
        }
        return UUID.randomUUID().toString();
    }
}
