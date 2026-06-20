package com.homekept.identity;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Extracts the JWT access token from the {@code hk_access} httpOnly cookie,
 * validates it, and populates the {@link org.springframework.security.core.context.SecurityContext}.
 *
 * <p>On success, the authentication principal is the user ID (Long), the credentials
 * are null, and the authorities contain a single {@code ROLE_<ROLE>} authority
 * (e.g. {@code ROLE_ADMIN}) — compatible with Spring Security's {@code hasRole("ADMIN")}
 * and {@code @PreAuthorize("hasRole('ADMIN')")} expressions.
 *
 * <p>On failure (missing/invalid/expired token) the filter does nothing — the request
 * proceeds unauthenticated and Spring Security's authorization layer will reject it
 * with a 401 if the endpoint requires auth.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final CookieHelper cookieHelper;

    public JwtAuthFilter(JwtService jwtService, CookieHelper cookieHelper) {
        this.jwtService = jwtService;
        this.cookieHelper = cookieHelper;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        Optional<String> tokenOpt = cookieHelper.extractAccessToken(request);
        if (tokenOpt.isPresent()) {
            jwtService.validateAndParseClaims(tokenOpt.get()).ifPresent(claims -> {
                Long userId = Long.parseLong((String) claims.get("sub"));
                String role = (String) claims.get("role");
                var auth = new UsernamePasswordAuthenticationToken(
                        userId,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role))
                );
                SecurityContextHolder.getContext().setAuthentication(auth);
            });
        }

        filterChain.doFilter(request, response);
    }
}
