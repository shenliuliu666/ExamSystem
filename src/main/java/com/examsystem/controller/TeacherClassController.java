package com.examsystem.controller;

import com.examsystem.course.ClassMember;
import com.examsystem.course.ClassService;
import com.examsystem.course.Classroom;
import com.examsystem.security.AuthTokenService;
import com.examsystem.security.Role;
import com.examsystem.user.UserProfile;
import com.examsystem.user.UserProfileRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/teacher/classes")
public class TeacherClassController {
    private final ClassService classService;
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final UserProfileRepository userProfileRepository;
    private final AuthTokenService authTokenService;

    public TeacherClassController(
            ClassService classService,
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder,
            UserProfileRepository userProfileRepository,
            AuthTokenService authTokenService
    ) {
        this.classService = classService;
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.userProfileRepository = userProfileRepository;
        this.authTokenService = authTokenService;
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody CreateClassRequest request) {
        String username = currentUsername();
        Classroom created = classService.create(request.getName(), username);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public List<Classroom> list() {
        String username = currentUsername();
        return classService.listByOwner(username);
    }

    @GetMapping("/{id}/members")
    public List<ClassMember> listMembers(@PathVariable("id") long classId) {
        String username = currentUsername();
        classService.findById(classId)
                .filter(c -> c.getOwnerUsername().equals(username))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "not_class_owner"));
        return classService.listMembers(classId);
    }

    @PostMapping("/{id}/members")
    @Transactional
    public ResponseEntity<?> addMember(@PathVariable("id") long classId, @RequestBody Map<String, String> body) {
        String teacherUsername = currentUsername();
        classService.findById(classId)
                .filter(c -> c.getOwnerUsername().equals(teacherUsername))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "not_class_owner"));
        String account = body.get("username");
        String studentNo = body.get("studentNo");
        String fullName = body.get("fullName");

        String u = account == null ? "" : account.trim();
        if (u.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "username required");
        }

        if (!existsUser(u)) {
            String sn = studentNo == null ? "" : studentNo.trim();
            String fn = fullName == null ? "" : fullName.trim();
            if (sn.isEmpty() || fn.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "studentNo/fullName required");
            }
            createStudentUser(u, sn, fn, teacherUsername);
        } else if (studentNo != null || fullName != null) {
            classService.upsertMemberProfile(u, studentNo, fullName, teacherUsername);
        }

        classService.addMember(classId, u);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/members/{username}")
    @Transactional
    public ResponseEntity<?> updateMember(@PathVariable("id") long classId, @PathVariable("username") String memberUsername, @RequestBody Map<String, String> body) {
        String teacherUsername = currentUsername();
        classService.findById(classId)
                .filter(c -> c.getOwnerUsername().equals(teacherUsername))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "not_class_owner"));

        if (!classService.isMember(classId, memberUsername)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "member_not_found_in_class");
        }

        String studentNo = body.get("studentNo");
        String fullName = body.get("fullName");
        String newUsername = body.get("username");

        String newU = newUsername == null ? "" : newUsername.trim();
        if (!newU.isEmpty() && !newU.equals(memberUsername)) {
            if (existsUser(newU)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "username_exists");
            }
            renameUser(memberUsername, newU);
            classService.upsertMemberProfile(newU, studentNo, fullName, teacherUsername);
        } else {
            classService.upsertMemberProfile(memberUsername, studentNo, fullName, teacherUsername);
        }
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/members/{username}/reset-password")
    @Transactional
    public ResponseEntity<?> resetMemberPassword(@PathVariable("id") long classId, @PathVariable("username") String memberUsername) {
        String teacherUsername = currentUsername();
        classService.findById(classId)
                .filter(c -> c.getOwnerUsername().equals(teacherUsername))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "not_class_owner"));
        if (!classService.isMember(classId, memberUsername)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "member_not_found_in_class");
        }
        Optional<UserProfile> p = userProfileRepository.findByUsername(memberUsername);
        String studentNo = p.map(UserProfile::getStudentNo).orElse(null);
        String base = studentNo == null || studentNo.isBlank() ? memberUsername : studentNo.trim();
        String rawPassword = base + "123456";

        jdbcTemplate.update(
                "UPDATE users SET password_hash = ?, updated_at = ? WHERE username = ?",
                passwordEncoder.encode(rawPassword),
                java.sql.Timestamp.from(Instant.now()),
                memberUsername
        );
        authTokenService.revokeByUsername(memberUsername);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}/members/{username}")
    public ResponseEntity<?> removeMember(@PathVariable("id") long classId, @PathVariable("username") String memberUsername) {
        String username = currentUsername();
        classService.findById(classId)
                .filter(c -> c.getOwnerUsername().equals(username))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "not_class_owner"));
        classService.removeMember(classId, memberUsername);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteClass(@PathVariable("id") long classId, @RequestParam(value = "deleteMembers", required = false, defaultValue = "false") boolean deleteMembers) {
        String username = currentUsername();
        classService.findById(classId)
                .filter(c -> c.getOwnerUsername().equals(username))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "not_class_owner"));
        
        // Check if members only belong to this class
        List<String> exclusiveMembers = classService.getMembersOnlyInClass(classId);
        classService.deleteClass(classId, deleteMembers);
        return ResponseEntity.ok().body(Map.of("exclusiveMembers", exclusiveMembers));
    }

    @GetMapping("/{id}/exclusive-members")
    public ResponseEntity<List<String>> getExclusiveMembers(@PathVariable("id") long classId) {
        String username = currentUsername();
        classService.findById(classId)
                .filter(c -> c.getOwnerUsername().equals(username))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "not_class_owner"));
        return ResponseEntity.ok(classService.getMembersOnlyInClass(classId));
    }

    private String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return String.valueOf(authentication.getPrincipal());
    }

    private boolean existsUser(String username) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM users WHERE username = ?",
                Integer.class,
                username
        );
        return count != null && count > 0;
    }

    private void createStudentUser(String username, String studentNo, String fullName, String createdBy) {
        Instant now = Instant.now();
        jdbcTemplate.update(
                "INSERT INTO users(username, password_hash, enabled, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
                username,
                passwordEncoder.encode(studentNo + "123456"),
                true,
                java.sql.Timestamp.from(now),
                java.sql.Timestamp.from(now)
        );
        jdbcTemplate.update(
                "INSERT INTO user_roles(username, role) VALUES (?, ?)",
                username,
                Role.STUDENT.name()
        );
        userProfileRepository.insert(username, studentNo, fullName, createdBy, now);
    }

    private void renameUser(String oldUsername, String newUsername) {
        String passwordHash;
        boolean enabled;
        try {
            passwordHash = jdbcTemplate.queryForObject(
                    "SELECT password_hash FROM users WHERE username = ?",
                    String.class,
                    oldUsername
            );
            Boolean enabledBoxed = jdbcTemplate.queryForObject(
                    "SELECT enabled FROM users WHERE username = ?",
                    Boolean.class,
                    oldUsername
            );
            enabled = enabledBoxed != null && enabledBoxed;
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "user_not_found");
        }

        Instant now = Instant.now();
        jdbcTemplate.update(
                "INSERT INTO users(username, password_hash, enabled, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
                newUsername,
                passwordHash,
                enabled,
                java.sql.Timestamp.from(now),
                java.sql.Timestamp.from(now)
        );

        Set<Role> roles = jdbcTemplate.query(
                "SELECT role FROM user_roles WHERE username = ?",
                (rs, rowNum) -> Role.valueOf(rs.getString("role")),
                oldUsername
        ).stream().collect(Collectors.toSet());
        if (roles.isEmpty()) {
            roles = Set.of(Role.STUDENT);
        }
        jdbcTemplate.batchUpdate(
                "INSERT INTO user_roles(username, role) VALUES (?, ?)",
                roles.stream().map(r -> new Object[] { newUsername, r.name() }).collect(Collectors.toList())
        );

        Optional<UserProfile> profile = userProfileRepository.findByUsername(oldUsername);
        if (profile.isPresent()) {
            UserProfile p = profile.get();
            userProfileRepository.insert(newUsername, p.getStudentNo(), p.getFullName(), p.getCreatedBy(), now);
        }

        jdbcTemplate.update("UPDATE class_members SET username = ? WHERE username = ?", newUsername, oldUsername);
        jdbcTemplate.update("UPDATE question_bank_members SET username = ? WHERE username = ?", newUsername, oldUsername);
        jdbcTemplate.update("UPDATE question_banks SET owner_username = ? WHERE owner_username = ?", newUsername, oldUsername);
        jdbcTemplate.update("UPDATE classes SET owner_username = ? WHERE owner_username = ?", newUsername, oldUsername);
        jdbcTemplate.update("UPDATE proctor_events SET username = ? WHERE username = ?", newUsername, oldUsername);
        jdbcTemplate.update("UPDATE attempt_heartbeats SET username = ? WHERE username = ?", newUsername, oldUsername);
        jdbcTemplate.update("UPDATE exam_attempts SET student_username = ? WHERE student_username = ?", newUsername, oldUsername);
        jdbcTemplate.update("UPDATE exam_results SET student_username = ? WHERE student_username = ?", newUsername, oldUsername);

        jdbcTemplate.update("DELETE FROM users WHERE username = ?", oldUsername);
        authTokenService.revokeByUsername(oldUsername);
    }

    public static class CreateClassRequest {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
