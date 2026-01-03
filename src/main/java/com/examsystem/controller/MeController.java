package com.examsystem.controller;

import com.examsystem.user.UserProfileRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class MeController {
    private final UserProfileRepository userProfileRepository;

    public MeController(UserProfileRepository userProfileRepository) {
        this.userProfileRepository = userProfileRepository;
    }

    @GetMapping("/me")
    public Map<String, Object> me() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = String.valueOf(authentication.getPrincipal());
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", username);
        body.put("roles", roles);
        userProfileRepository.findByUsername(username).ifPresent(p -> {
            if (p.getFullName() != null) {
                body.put("fullName", p.getFullName());
            }
            if (p.getStudentNo() != null) {
                body.put("studentNo", p.getStudentNo());
            }
        });
        return body;
    }
}
