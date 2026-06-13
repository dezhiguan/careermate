package com.careermate.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

public class TracingMdcFilter extends OncePerRequestFilter {

    private final TraceIdResolver traceIdResolver;

    public TracingMdcFilter(TraceIdResolver traceIdResolver) {
        this.traceIdResolver = traceIdResolver;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Map<String, String> previousMdc = MDC.getCopyOfContextMap();
        String requestId = resolveRequestId(request);
        String sessionId = request.getHeader(MdcKeys.HEADER_SESSION_ID);

        MDC.put(MdcKeys.REQUEST_ID, requestId);
        if (StringUtils.hasText(sessionId)) {
            MDC.put(MdcKeys.SESSION_ID, sessionId.trim());
        }
        syncTraceMdc();

        // SSE / 流式（异步）响应不能用 ContentCachingResponseWrapper 缓冲，
        // 否则事件会被缓存在内存直到 copyBodyToResponse() 才输出，导致前端实时收不到
        // token/message/done，只能刷新后从落库数据里读取。这类请求直接透传原始响应实时 flush。
        if (isStreamingRequest(request)) {
            try {
                // 响应头必须在 body 写出前设置，fetch 才能在读流前拿到 X-Trace-Id
                writeTraceHeaders(response, requestId);
                filterChain.doFilter(request, response);
            } finally {
                syncTraceMdc();
                restoreMdc(previousMdc);
            }
            return;
        }

        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);
        try {
            filterChain.doFilter(request, responseWrapper);
        } finally {
            syncTraceMdc();
            writeTraceHeaders(responseWrapper, requestId);
            responseWrapper.copyBodyToResponse();
            restoreMdc(previousMdc);
        }
    }

    private boolean isStreamingRequest(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains("text/event-stream")) {
            return true;
        }
        String uri = request.getRequestURI();
        return uri != null && uri.endsWith("/messages/stream");
    }

    private void writeTraceHeaders(HttpServletResponse response, String requestId) {
        response.setHeader(MdcKeys.HEADER_REQUEST_ID, requestId);
        String traceId = MDC.get(MdcKeys.TRACE_ID);
        if (!StringUtils.hasText(traceId)) {
            traceId = requestId;
        }
        response.setHeader(MdcKeys.HEADER_TRACE_ID, traceId);
    }

    private static void restoreMdc(Map<String, String> previousMdc) {
        if (previousMdc == null) {
            MDC.clear();
        } else {
            MDC.setContextMap(previousMdc);
        }
    }

    private String resolveRequestId(HttpServletRequest request) {
        String incoming = request.getHeader(MdcKeys.HEADER_REQUEST_ID);
        if (StringUtils.hasText(incoming)) {
            return incoming.trim();
        }
        return UUID.randomUUID().toString();
    }

    private void syncTraceMdc() {
        String traceId = traceIdResolver.resolveTraceId();
        if (!StringUtils.hasText(traceId)) {
            traceId = MDC.get(MdcKeys.TRACE_ID);
        }
        if (!StringUtils.hasText(traceId)) {
            String requestId = MDC.get(MdcKeys.REQUEST_ID);
            traceId = StringUtils.hasText(requestId) ? requestId : UUID.randomUUID().toString();
        }
        MDC.put(MdcKeys.TRACE_ID, traceId);
        String spanId = traceIdResolver.resolveSpanId();
        if (StringUtils.hasText(spanId)) {
            MDC.put(MdcKeys.SPAN_ID, spanId);
        }
    }
}
