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
            MDC.remove(MdcKeys.REQUEST_ID);
            MDC.remove(MdcKeys.USER_ID);
            MDC.remove(MdcKeys.SESSION_ID);
            MDC.remove(MdcKeys.TRACE_ID);
            MDC.remove(MdcKeys.SPAN_ID);
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
        if (StringUtils.hasText(traceId)) {
            MDC.put(MdcKeys.TRACE_ID, traceId);
        }
        String spanId = traceIdResolver.resolveSpanId();
        if (StringUtils.hasText(spanId)) {
            MDC.put(MdcKeys.SPAN_ID, spanId);
        }
    }
}
