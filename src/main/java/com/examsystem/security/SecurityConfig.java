package com.examsystem.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import javax.servlet.http.HttpServletResponse;

@Configuration
public class SecurityConfig {
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, BearerTokenAuthFilter bearerTokenAuthFilter)
            throws Exception {
        http.csrf().disable();
        http.sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS);

        http.authorizeRequests()
                .antMatchers("/api/health", "/api/auth/login", "/api/auth/register").permitAll()
                .antMatchers("/api/student/**").hasRole(Role.STUDENT.name())
                .antMatchers("/api/teacher/**").hasRole(Role.TEACHER.name())
                .antMatchers("/api/admin/**").hasRole(Role.ADMIN.name())
                .anyRequest().authenticated();

        http.exceptionHandling()
                .authenticationEntryPoint((request, response, authException) ->
                        response.sendError(HttpServletResponse.SC_UNAUTHORIZED))
                .accessDeniedHandler((request, response, accessDeniedException) ->
                        response.sendError(HttpServletResponse.SC_FORBIDDEN));

        http.addFilterBefore(bearerTokenAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
