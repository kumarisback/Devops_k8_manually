package com.example.orderservice.web;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class FaultInjectionFilterTest {

    private static MockHttpServletResponse run(double rate, double draw, String uri, FilterChain chain) throws Exception {
        FaultInjectionFilter filter = new FaultInjectionFilter(new FaultInjectionProperties(rate), () -> draw);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    @Test
    void failsARequestThatDrawsBelowTheRate() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse response = run(0.3, 0.29, "/api/orders/status", chain);

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getContentAsString()).contains("injected fault");
        verify(chain, never()).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void passesARequestThatDrawsAtOrAboveTheRate() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse response = run(0.3, 0.3, "/api/orders/status", chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void neverFailsActuatorOrAnythingWhenOff() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        assertThat(run(1.0, 0.0, "/actuator/health/liveness", chain).getStatus()).isEqualTo(200);
        assertThat(run(0.0, 0.0, "/api/orders/status", chain).getStatus()).isEqualTo(200);
    }

    @Test
    void refusesARateOutsideZeroToOne() {
        assertThatThrownBy(() -> new FaultInjectionProperties(1.5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FaultInjectionProperties(-0.1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FaultInjectionProperties(Double.NaN)).isInstanceOf(IllegalArgumentException.class);
    }
}
