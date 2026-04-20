package com.shipsmart.api.web;

import com.shipsmart.api.exception.RateLimitExceededException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimitFilterTest {

    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter();
        ReflectionTestUtils.setField(filter, "enabled", true);
        ReflectionTestUtils.setField(filter, "shipmentsPerMinute", 3);
        ReflectionTestUtils.setField(filter, "quotesPerMinute", 10);
        ReflectionTestUtils.setField(filter, "bookingsPerMinute", 10);
    }

    @Test
    void allowsUpToLimitThenRejects() throws Exception {
        FilterChain chain = (r, s) -> {};
        for (int i = 0; i < 3; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/shipments");
            req.setRemoteAddr("10.0.0.1");
            filter.doFilter(req, new MockHttpServletResponse(), chain);
        }

        MockHttpServletRequest fourth = new MockHttpServletRequest("POST", "/api/v1/shipments");
        fourth.setRemoteAddr("10.0.0.1");
        assertThatThrownBy(() -> filter.doFilter(fourth, new MockHttpServletResponse(), chain))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void differentIpsGetSeparateBuckets() throws Exception {
        FilterChain chain = (r, s) -> {};
        for (int i = 0; i < 3; i++) {
            MockHttpServletRequest a = new MockHttpServletRequest("POST", "/api/v1/shipments");
            a.setRemoteAddr("10.0.0.1");
            filter.doFilter(a, new MockHttpServletResponse(), chain);
        }
        MockHttpServletRequest b = new MockHttpServletRequest("POST", "/api/v1/shipments");
        b.setRemoteAddr("10.0.0.2");
        MockHttpServletResponse resB = new MockHttpServletResponse();
        filter.doFilter(b, resB, chain);
        assertThat(resB.getHeader("X-RateLimit-Remaining")).isEqualTo("2");
    }
}
