package com.examsystem.controller;

import com.examsystem.course.ClassRepository;
import com.examsystem.course.Classroom;
import com.examsystem.security.Role;
import com.examsystem.user.UserProfile;
import com.examsystem.user.UserProfileRepository;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/teacher/students")
public class TeacherStudentController {
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final UserProfileRepository userProfileRepository;
    private final ClassRepository classRepository;

    public TeacherStudentController(
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder,
            UserProfileRepository userProfileRepository,
            ClassRepository classRepository
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.userProfileRepository = userProfileRepository;
        this.classRepository = classRepository;
    }

    @PostMapping("/import")
    public ImportResult importStudents(
            @RequestParam("classId") long classId,
            @RequestParam("file") MultipartFile file
    ) {
        String teacherUsername = currentUsername();
        Classroom classroom = classRepository.findById(classId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "class_not_found"));
        if (!classroom.getOwnerUsername().equals(teacherUsername)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "not_class_owner");
        }
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file is required");
        }
        try (InputStream in = file.getInputStream(); Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
            if (sheet == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "empty_excel");
            }
            int firstRow = sheet.getFirstRowNum();
            int lastRow = sheet.getLastRowNum();
            if (lastRow <= firstRow) {
                return new ImportResult(0, 0, List.of());
            }
            Row header = sheet.getRow(firstRow);
            if (header == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "missing_header");
            }
            int studentNoCol = -1;
            int nameCol = -1;
            int passwordCol = -1;
            int lastCell = header.getLastCellNum();
            for (int c = header.getFirstCellNum(); c < lastCell; c++) {
                Cell cell = header.getCell(c);
                String v = cell == null ? null : cell.getStringCellValue();
                if (v == null) {
                    continue;
                }
                String label = v.trim().toLowerCase(Locale.ROOT);
                if (label.isEmpty()) {
                    continue;
                }
                if (studentNoCol < 0 && (label.equals("学号") || label.equals("studentno"))) {
                    studentNoCol = c;
                } else if (nameCol < 0 && (label.equals("姓名") || label.equals("name"))) {
                    nameCol = c;
                } else if (passwordCol < 0 && (label.equals("密码") || label.equals("password"))) {
                    passwordCol = c;
                }
            }
            if (studentNoCol < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "student_no_header_required");
            }

            int successCount = 0;
            List<ImportFailure> failures = new ArrayList<>();
            Set<String> seenStudentNos = new HashSet<>();

            for (int r = firstRow + 1; r <= lastRow; r++) {
                Row row = sheet.getRow(r);
                int rowNumber = r + 1;
                if (row == null) {
                    continue;
                }
                String studentNo = getCellString(row, studentNoCol).trim();
                String fullName = nameCol >= 0 ? getCellString(row, nameCol).trim() : "";
                String password = passwordCol >= 0 ? getCellString(row, passwordCol) : "";
                if (studentNo.isEmpty() && fullName.isEmpty() && password.isEmpty()) {
                    continue;
                }
                if (studentNo.isEmpty()) {
                    failures.add(new ImportFailure(rowNumber, "学号为空"));
                    continue;
                }
                if (containsWhitespace(studentNo)) {
                    failures.add(new ImportFailure(rowNumber, "学号包含空格"));
                    continue;
                }
                if (!seenStudentNos.add(studentNo)) {
                    failures.add(new ImportFailure(rowNumber, "学号在导入文件中重复"));
                    continue;
                }

                String username = studentNo;
                boolean userExists = existsUser(username);
                if (!userExists) {
                    String rawPassword = password == null || password.trim().isEmpty()
                            ? generateDefaultPassword(studentNo)
                            : password;
                    createStudentUser(username, rawPassword, studentNo, fullName, teacherUsername);
                } else {
                    Optional<UserProfile> profile = userProfileRepository.findByUsername(username);
                    if (profile.isEmpty()) {
                        Instant now = Instant.now();
                        userProfileRepository.insert(username, studentNo, fullName, teacherUsername, now);
                    }
                }

                if (classRepository.isMember(classId, username)) {
                    failures.add(new ImportFailure(rowNumber, "已在班级中"));
                    continue;
                }
                classRepository.addMember(classId, username);
                successCount++;
            }

            int failedCount = failures.size();
            return new ImportResult(successCount, failedCount, failures);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid_excel", e);
        }
    }

    private boolean existsUser(String username) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM users WHERE username = ?",
                    Integer.class,
                    username
            );
            return count != null && count > 0;
        } catch (DataAccessException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sql_exists_user:" + e.getClass().getSimpleName(), e);
        }
    }

    private void createStudentUser(String username, String rawPassword, String studentNo, String fullName, String createdBy) {
        try {
            Instant now = Instant.now();
            jdbcTemplate.update(
                    "INSERT INTO users(username, password_hash, enabled, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
                    username,
                    passwordEncoder.encode(rawPassword),
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
        } catch (DataAccessException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sql_create_student:" + e.getClass().getSimpleName(), e);
        }
    }

    private boolean containsWhitespace(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.isWhitespace(s.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private String generateDefaultPassword(String studentNo) {
        String s = studentNo == null ? "" : studentNo;
        return s + "123456";
    }

    private String getCellString(Row row, int index) {
        if (index < 0) {
            return "";
        }
        Cell cell = row.getCell(index);
        if (cell == null) {
            return "";
        }
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                double v = cell.getNumericCellValue();
                long lv = (long) v;
                if (Math.abs(v - lv) < 0.0000001) {
                    return Long.toString(lv);
                }
                return Double.toString(v);
            case BOOLEAN:
                return Boolean.toString(cell.getBooleanCellValue());
            default:
                return "";
        }
    }

    private String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return String.valueOf(authentication.getPrincipal());
    }

    public static class ImportFailure {
        private final int row;
        private final String reason;

        public ImportFailure(int row, String reason) {
            this.row = row;
            this.reason = reason;
        }

        public int getRow() {
            return row;
        }

        public String getReason() {
            return reason;
        }
    }

    public static class ImportResult {
        private final int successCount;
        private final int failedCount;
        private final List<ImportFailure> failures;

        public ImportResult(int successCount, int failedCount, List<ImportFailure> failures) {
            this.successCount = successCount;
            this.failedCount = failedCount;
            this.failures = failures;
        }

        public int getSuccessCount() {
            return successCount;
        }

        public int getFailedCount() {
            return failedCount;
        }

        public List<ImportFailure> getFailures() {
            return failures;
        }
    }
}
