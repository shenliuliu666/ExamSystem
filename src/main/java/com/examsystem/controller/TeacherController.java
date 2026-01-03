package com.examsystem.controller;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/teacher")
public class TeacherController {
    @GetMapping("/ping")
    public Map<String, Object> ping() {
        return Map.of("status", "ok");
    }
}

