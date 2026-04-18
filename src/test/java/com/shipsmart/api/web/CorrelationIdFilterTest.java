package com.shipsmart.api.web;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CorrelationIdFilterTest {

    @Test
    void mintsRequestIdAndTraceIdWhenMissing() throws Exception {
        CorrelationIdFilter filter = new CorrelationIdFilter();
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = (r, s) -> {
            assertThat(MDC.get("requestId")).isNotBlank();
            assertThat(MDC.get("traceId")).hasSize(32);
            assertThat(MDC.get("spanId")).hasSize(16);
        };

        filter.doFilter(req, res, chain);

        assertThat(res.getHeader("X-Request-Id")).isNotBlank();
        assertThat(res.getHeader("traceparent")).startsWith("00-");
    }

    @Test
    void honorsInboundTraceparent() throws Exception {
        CorrelationIdFilter filter = new CorrelationIdFilter();
        MockHttpServletRequest req = new MockHttpServletRequest();
        String inbound = "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01";
        req.addHeader("traceparent", inbound);
        req.addHeader("X-Request-Id", "abc-123");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, mock(FilterChain.class));

        assertThat(res.getHeader("X-Request-Id")).isEqualTo("abc-123");
        assertThat(res.getHeader("traceparent")).isEqualTo(inbound);
    }
}
