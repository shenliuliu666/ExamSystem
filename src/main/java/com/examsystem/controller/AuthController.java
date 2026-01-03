package com.examsystem.controller;

import com.examsystem.security.AuthService;
import com.examsystem.security.Role;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        return authService.login(request.getUsername(), request.getPassword())
                .<ResponseEntity<?>>map(result -> ResponseEntity.ok(new LoginResponse(
                        result.getToken(),
                        result.getUsername(),
                        result.getRoles().stream().map(Role::name).collect(Collectors.toList())
                )))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "invalid_credentials")));
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring("Bearer ".length()).trim();
            authService.logout(token);
        }
        return Map.of("status", "ok");
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        try {
            return authService.registerStudent(request.getUsername(), request.getPassword())
                    .<ResponseEntity<?>>map(result -> ResponseEntity.status(HttpStatus.CREATED).body(new LoginResponse(
                            result.getToken(),
                            result.getUsername(),
                            result.getRoles().stream().map(Role::name).collect(Collectors.toList())
                    )))
                    .orElseGet(() -> ResponseEntity.status(HttpStatus.CONFLICT)
                            .body(Map.of("error", "username_exists")));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "invalid_request"));
        }
    }

    public static class LoginRequest {
        private String username;
        private String password;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    public static class RegisterRequest {
        private String username;
        private String password;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    public static class LoginResponse {
        private final String token;
        private final String username;
        private final List<String> roles;

        public LoginResponse(String token, String username, List<String> roles) {
            this.token = token;
            this.username = username;
            this.roles = roles;
        }

        public String getToken() {
            return token;
        }

        public String getUsername() {
            return username;
        }

        public List<String> getRoles() {
            return roles;
        }
    }
}
