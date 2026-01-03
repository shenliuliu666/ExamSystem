package com.examsystem.security;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class BearerTokenAuthFilter extends OncePerRequestFilter {
    private final AuthTokenService tokenService;

    public BearerTokenAuthFilter(AuthTokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        String token = null;
        if (header != null && header.startsWith("Bearer ")) {
            token = header.substring("Bearer ".length()).trim();
        } else {
            String param = request.getParameter("access_token");
            if (param != null && !param.isBlank()) {
                token = param.trim();
            }
        }

        if (token != null && !token.isBlank()) {
            tokenService.findValidToken(token).ifPresent(info -> {
                List<SimpleGrantedAuthority> authorities = info.getRoles().stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                        .collect(Collectors.toList());
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        info.getUsername(),
                        null,
                        authorities
                );
                SecurityContextHolder.getContext().setAuthentication(authentication);
            });
        }

        filterChain.doFilter(request, response);
    }
}
