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
import java.util.UUID;

public class TracingMdcFilter extends OncePerRequestFilter {

    private final TraceIdResolver traceIdResolver;
    private final TraceHeaderPropagator traceHeaderPropagator;

    public TracingMdcFilter(TraceIdResolver traceIdResolver, TraceHeaderPropagator traceHeaderPropagator) {
        this.traceIdResolver = traceIdResolver;
        this.traceHeaderPropagator = traceHeaderPropagator;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
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
                response.setHeader(MdcKeys.HEADER_REQUEST_ID, requestId);
                String traceId = traceHeaderPropagator.currentTraceId();
                if (StringUtils.hasText(traceId)) {
                    response.setHeader(MdcKeys.HEADER_TRACE_ID, traceId);
                }
                filterChain.doFilter(request, response);
            } finally {
                clearMdc();
            }
            return;
        }

        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);
        try {
            filterChain.doFilter(request, responseWrapper);
        } finally {
            syncTraceMdc();
            responseWrapper.setHeader(MdcKeys.HEADER_REQUEST_ID, requestId);
            String traceId = traceHeaderPropagator.currentTraceId();
            if (StringUtils.hasText(traceId)) {
                responseWrapper.setHeader(MdcKeys.HEADER_TRACE_ID, traceId);
            }
            responseWrapper.copyBodyToResponse();
            clearMdc();
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

    private void clearMdc() {
        MDC.remove(MdcKeys.REQUEST_ID);
        MDC.remove(MdcKeys.USER_ID);
        MDC.remove(MdcKeys.SESSION_ID);
        MDC.remove(MdcKeys.TRACE_ID);
        MDC.remove(MdcKeys.SPAN_ID);
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
        if (StringUtils.hasText(traceId)) {
            MDC.put(MdcKeys.TRACE_ID, traceId);
        }
        String spanId = traceIdResolver.resolveSpanId();
        if (StringUtils.hasText(spanId)) {
            MDC.put(MdcKeys.SPAN_ID, spanId);
        }
    }
}
