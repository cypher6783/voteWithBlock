package com.votewithblock.gateway.config;

import com.votewithblock.gateway.repository.AdminSessionRepository;
import com.votewithblock.gateway.service.AdminTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AdminAuthFilter extends OncePerRequestFilter {

    private final AdminTokenService adminTokenService;
    private final AdminSessionRepository adminSessionRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            Map<String, Object> claims = adminTokenService.validateAndExtractClaims(authHeader);
            String username = (String) claims.get("sub");
            String role = (String) claims.get("role");
            String jti = (String) claims.get("jti");

            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                // Check if session is revoked
                if (adminSessionRepository.existsByJtiAndRevokedFalse(jti)) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            username, null, Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role))
                    );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                } else {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.getWriter().write("Session revoked or invalid");
                    return;
                }
            }
        } catch (Exception e) {
            // Token validation failed, let Spring Security handle unauthorized access
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
    
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return !path.startsWith("/api/admin/") || path.equals("/api/admin/auth/login");
    }
}
