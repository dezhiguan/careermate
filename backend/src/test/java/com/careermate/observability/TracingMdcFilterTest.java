package com.careermate.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.util.ContentCachingResponseWrapper;

import com.careermate.common.api.ApiResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TracingMdcFilterTest {

    @Mock
    private TraceIdResolver traceIdResolver;

    @Mock
    private FilterChain filterChain;

    private TracingMdcFilter filter;

    @BeforeEach
    void setUp() {
        filter = new TracingMdcFilter(traceIdResolver, "careermate-backend");
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void fallsBackToRequestIdWhenTraceResolverReturnsNull() throws ServletException, IOException {
        when(traceIdResolver.resolveTraceId()).thenReturn(null);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/health");
        request.addHeader(MdcKeys.HEADER_REQUEST_ID, "fallback-req-id");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getHeader(MdcKeys.HEADER_TRACE_ID)).isEqualTo("fallback-req-id");
        assertThat(response.getHeader(MdcKeys.HEADER_REQUEST_ID)).isEqualTo("fallback-req-id");
    }

    @Test
    void restoresPreviousMdcAfterRequest() throws ServletException, IOException {
        MDC.put("upstreamKey", "upstreamValue");
        when(traceIdResolver.resolveTraceId()).thenReturn("trace-123");

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(MDC.get("upstreamKey")).isEqualTo("upstreamValue");
        assertThat(MDC.get(MdcKeys.REQUEST_ID)).isNull();
        assertThat(MDC.get(MdcKeys.TRACE_ID)).isNull();
    }

    @Test
    void sseRequestDoesNotWrapResponse() throws ServletException, IOException {
        when(traceIdResolver.resolveTraceId()).thenReturn("trace-sse");

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/agent/sessions/s1/messages/stream");
        request.addHeader("Accept", "text/event-stream");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, (req, res) -> {
            assertThat(res).isNotInstanceOf(ContentCachingResponseWrapper.class);
        });

        assertThat(response.getHeader(MdcKeys.HEADER_REQUEST_ID)).isNotBlank();
        assertThat(response.getHeader(MdcKeys.HEADER_TRACE_ID)).isEqualTo("trace-sse");
    }

    @Test
    void nonStreamingRequestUsesCachingWrapper() throws ServletException, IOException {
        when(traceIdResolver.resolveTraceId()).thenReturn("trace-rest");

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, (req, res) -> {
            assertThat(res).isInstanceOf(ContentCachingResponseWrapper.class);
            res.getWriter().write("ok");
        });

        assertThat(response.getHeader(MdcKeys.HEADER_REQUEST_ID)).isNotBlank();
        assertThat(response.getHeader(MdcKeys.HEADER_TRACE_ID)).isEqualTo("trace-rest");
        assertThat(response.getContentAsString()).isEqualTo("ok");
    }

    @Test
    void echoesIncomingRequestId() throws ServletException, IOException {
        when(traceIdResolver.resolveTraceId()).thenReturn("trace-echo");

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/health");
        request.addHeader(MdcKeys.HEADER_REQUEST_ID, "client-req-42");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getHeader(MdcKeys.HEADER_REQUEST_ID)).isEqualTo("client-req-42");
        verify(filterChain).doFilter(any(HttpServletRequest.class), any(HttpServletResponse.class));
    }

    @Test
    void mdcTraceIdMatchesResponseHeaderAndSkyWalkingTrace() throws ServletException, IOException {
        when(traceIdResolver.resolveTraceId()).thenReturn("sw-trace-align-42");

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, (req, res) -> {
            assertThat(MDC.get(MdcKeys.TRACE_ID)).isEqualTo("sw-trace-align-42");
            assertThat(MDC.get(MdcKeys.SERVICE)).isEqualTo("careermate-backend");
            assertThat(ApiResponse.success().getTraceId()).isEqualTo("sw-trace-align-42");
        });

        assertThat(response.getHeader(MdcKeys.HEADER_TRACE_ID)).isEqualTo("sw-trace-align-42");
    }
}
