package com.careermate.common.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
                .traceId(null)
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
                .traceId(traceId)
                .timestamp(System.currentTimeMillis())
                .build();
    }
}
