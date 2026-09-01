package com.kiki.video.api.observability;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void generatesAndPropagatesRequestId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> assertThat(MDC.get(RequestId.MDC_KEY)).isNotBlank();

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(RequestId.HEADER)).isNotBlank();
        assertThat(RequestId.isValid(response.getHeader(RequestId.HEADER))).isTrue();
        assertThat(MDC.get(RequestId.MDC_KEY)).isNull();
    }

    @Test
    void reusesValidIncomingRequestId() throws Exception {
        String incoming = "550e8400-e29b-41d4-a716-446655440000";
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/health");
        request.addHeader(RequestId.HEADER, incoming);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) ->
                assertThat(MDC.get(RequestId.MDC_KEY)).isEqualTo(incoming));

        assertThat(response.getHeader(RequestId.HEADER)).isEqualTo(incoming);
        assertThat(MDC.get(RequestId.MDC_KEY)).isNull();
    }

    @Test
    void replacesOversizedHeaderAndClearsMdcAfterFailure() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/health");
        request.addHeader(RequestId.HEADER, "x".repeat(400));
        MockHttpServletResponse response = new MockHttpServletResponse();

        try {
            filter.doFilter(request, response, (req, res) -> {
                assertThat(MDC.get(RequestId.MDC_KEY)).isNotEqualTo("x".repeat(400));
                throw new IllegalStateException("boom");
            });
        } catch (IllegalStateException ignored) {
            // expected
        }

        assertThat(response.getHeader(RequestId.HEADER)).isNotEqualTo("x".repeat(400));
        assertThat(MDC.get(RequestId.MDC_KEY)).isNull();
    }
}
