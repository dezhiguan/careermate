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

    private static final String SKYWALKING_IGNORED_TRACE = "Ignored_Trace";

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
        String skyWalkingTraceId = org.apache.skywalking.apm.toolkit.trace.TraceContext.traceId();
        if (isUsableTraceId(skyWalkingTraceId)) {
            return skyWalkingTraceId;
        }
        String traceId = MDC.get(MdcKeys.TRACE_ID);
        if (isUsableTraceId(traceId)) {
            return traceId;
        }
        String requestId = MDC.get(MdcKeys.REQUEST_ID);
        if (isUsableTraceId(requestId)) {
            return requestId;
        }
        return UUID.randomUUID().toString();
    }

    private static boolean isUsableTraceId(String traceId) {
        return StringUtils.hasText(traceId)
                && !SKYWALKING_IGNORED_TRACE.equals(traceId)
                && !"N/A".equalsIgnoreCase(traceId);
    }
}
