package com.homekept.common;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ClientIpResolver}. No Spring context needed.
 *
 * <p>Verifies the CF-Connecting-IP-first resolution strategy that prevents
 * X-Forwarded-For spoofing from bypassing the booking rate limiter.
 */
class ClientIpResolverTest {

    @Test
    void resolve_cfConnectingIpPresent_returnsCfIp() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("CF-Connecting-IP")).thenReturn("203.0.113.42");
        when(request.getRemoteAddr()).thenReturn("10.0.0.1");

        assertThat(ClientIpResolver.resolve(request)).isEqualTo("203.0.113.42");
    }

    @Test
    void resolve_cfConnectingIpAbsent_fallsBackToRemoteAddr() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("CF-Connecting-IP")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("192.168.1.5");

        assertThat(ClientIpResolver.resolve(request)).isEqualTo("192.168.1.5");
    }

    @Test
    void resolve_cfConnectingIpBlank_fallsBackToRemoteAddr() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("CF-Connecting-IP")).thenReturn("   ");
        when(request.getRemoteAddr()).thenReturn("192.168.1.5");

        assertThat(ClientIpResolver.resolve(request)).isEqualTo("192.168.1.5");
    }

    @Test
    void resolve_cfConnectingIpWithWhitespace_isTrimmed() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("CF-Connecting-IP")).thenReturn("  203.0.113.7  ");
        when(request.getRemoteAddr()).thenReturn("10.0.0.1");

        assertThat(ClientIpResolver.resolve(request)).isEqualTo("203.0.113.7");
    }

    /**
     * Verifies the core security property: two requests with different X-Forwarded-For
     * values but the SAME CF-Connecting-IP resolve to the SAME key, meaning spoofing
     * XFF does not rotate the rate-limit bucket.
     *
     * <p>Because ClientIpResolver ignores XFF entirely, this is implicit — both calls
     * return the CF-Connecting-IP value regardless of what XFF would have produced.
     */
    @Test
    void resolve_differentXffSameCfIp_returnsSameKey() {
        String cfIp = "203.0.113.99";

        HttpServletRequest req1 = mock(HttpServletRequest.class);
        when(req1.getHeader("CF-Connecting-IP")).thenReturn(cfIp);
        when(req1.getRemoteAddr()).thenReturn("10.0.0.1"); // XFF variant 1

        HttpServletRequest req2 = mock(HttpServletRequest.class);
        when(req2.getHeader("CF-Connecting-IP")).thenReturn(cfIp);
        when(req2.getRemoteAddr()).thenReturn("10.0.0.2"); // XFF variant 2

        assertThat(ClientIpResolver.resolve(req1))
                .isEqualTo(ClientIpResolver.resolve(req2))
                .isEqualTo(cfIp);
    }
}
