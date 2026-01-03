package com.examsystem.controller;

import com.examsystem.course.ClassService;
import com.examsystem.course.Classroom;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/student/classes")
public class StudentClassController {
    private final ClassService classService;

    public StudentClassController(ClassService classService) {
        this.classService = classService;
    }

    @PostMapping("/join")
    public ResponseEntity<?> join(@RequestBody JoinClassRequest request) {
        String username = currentUsername();
        if (request == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "invalid_request"));
        }

        String inviteCode = request.getInviteCode() == null ? "" : request.getInviteCode().trim();
        String studentNo = request.getStudentNo() == null ? "" : request.getStudentNo().trim();
        String fullName = request.getFullName() == null ? "" : request.getFullName().trim();

        if (inviteCode.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "inviteCode required"));
        }
        if (studentNo.isEmpty() || fullName.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "studentNo/fullName required"));
        }

        classService.upsertMemberProfile(username, studentNo, fullName, username);

        return classService.joinByInviteCode(inviteCode, username)
                .<ResponseEntity<?>>map(c -> ResponseEntity.ok(c))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "invalid_code")));
    }

    @GetMapping
    public List<Classroom> list() {
        String username = currentUsername();
        return classService.listJoinedClasses(username);
    }

    private String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return String.valueOf(authentication.getPrincipal());
    }

    public static class JoinClassRequest {
        private String inviteCode;
        private String studentNo;
        private String fullName;

        public String getInviteCode() {
            return inviteCode;
        }

        public void setInviteCode(String inviteCode) {
            this.inviteCode = inviteCode;
        }

        public String getStudentNo() {
            return studentNo;
        }

        public void setStudentNo(String studentNo) {
            this.studentNo = studentNo;
        }

        public String getFullName() {
            return fullName;
        }

        public void setFullName(String fullName) {
            this.fullName = fullName;
        }
    }
}
