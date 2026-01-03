package com.examsystem.controller;

import com.examsystem.exam.ExamArrangement;
import com.examsystem.exam.ExamDraft;
import com.examsystem.exam.ExamService;
import com.examsystem.exam.ExamSettings;
import com.examsystem.exam.ExamStatus;
import com.examsystem.course.ClassMember;
import com.examsystem.course.ClassRepository;
import com.examsystem.course.ClassService;
import com.examsystem.course.Classroom;
import com.examsystem.attempt.AttemptStatus;
import com.examsystem.attempt.ExamAttempt;
import com.examsystem.attempt.InMemoryExamAttemptRepository;
import com.examsystem.proctor.ExamProctorService;
import com.examsystem.proctor.HeartbeatRecord;
import com.examsystem.proctor.ProctorEvent;
import com.examsystem.result.ExamResult;
import com.examsystem.result.ExamResultItem;
import com.examsystem.result.ExamResultService;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/teacher/exams")
public class TeacherExamController {
    private final ExamService examService;
    private final ExamResultService resultService;
    private final InMemoryExamAttemptRepository attemptRepository;
    private final ClassRepository classRepository;
    private final ExamProctorService proctorService;
    private final ClassService classService;

    public TeacherExamController(
            ExamService examService,
            ExamResultService resultService,
            InMemoryExamAttemptRepository attemptRepository,
            ClassRepository classRepository,
            ExamProctorService proctorService,
            ClassService classService
    ) {
        this.examService = examService;
        this.resultService = resultService;
        this.attemptRepository = attemptRepository;
        this.classRepository = classRepository;
        this.proctorService = proctorService;
        this.classService = classService;
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody CreateExamRequest request) {
        String username = currentUsername();
        if (request == null || request.getClassId() == null || request.getClassId() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "classId is required");
        }
        Classroom classroom = classRepository.findById(request.getClassId())
                .filter(c -> c.getOwnerUsername().equals(username))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "not_class_owner"));
        ExamArrangement created = examService.create(request.toDraft());
        int totalMembers = classRepository.listMembers(classroom.getId()).size();
        return ResponseEntity.status(HttpStatus.CREATED).body(ExamResponse.from(created, examService, classroom.getName(), 0, totalMembers, request.getSettings()));
    }

    @org.springframework.web.bind.annotation.PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable("id") long id, @RequestBody CreateExamRequest request) {
        requireOwnedExam(id);
        String username = currentUsername();
        if (request == null || request.getClassId() == null || request.getClassId() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "classId is required");
        }
        Classroom classroom = classRepository.findById(request.getClassId())
                .filter(c -> c.getOwnerUsername().equals(username))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "not_class_owner"));
        
        examService.update(id, request.toDraft());
        ExamArrangement updated = examService.findById(id).orElseThrow();
        int totalMembers = classRepository.listMembers(classroom.getId()).size();
        int submitted = resultService.listByExamId(id).size();
        int unsubmitted = totalMembers - submitted;
        if (unsubmitted < 0) unsubmitted = 0;

        return ResponseEntity.ok(ExamResponse.from(updated, examService, classroom.getName(), submitted, unsubmitted, request.getSettings()));
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable("id") long id) {
        requireOwnedExamOrOrphanDeletable(id);
        examService.delete(id);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable("id") long id) {
        String username = currentUsername();
        return examService.findById(id)
                .filter(e -> isOwnerOfExam(username, e))
                .<ResponseEntity<?>>map(e -> {
                    String className = "";
                    int submitted = 0;
                    int unsubmitted = 0;
                    if (e.getClassId() != null) {
                        Classroom c = classRepository.findById(e.getClassId()).orElse(null);
                        if (c != null) {
                            className = c.getName();
                            int totalMembers = classRepository.listMembers(c.getId()).size();
                            submitted = resultService.listByExamId(e.getId()).size();
                            unsubmitted = totalMembers - submitted;
                            if (unsubmitted < 0) unsubmitted = 0;
                        }
                    }
                    return ResponseEntity.ok(ExamResponse.from(e, examService, className, submitted, unsubmitted, examService.getSettingsOrDefault(e.getId())));
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "not_found")));
    }

    @GetMapping
    public List<ExamResponse> list() {
        String username = currentUsername();
        List<Classroom> classes = classRepository.listByOwner(username);
        Map<Long, Classroom> classMap = classes.stream().collect(Collectors.toMap(Classroom::getId, c -> c));
        List<Long> classIds = classes.stream()
                .map(Classroom::getId)
                .collect(Collectors.toList());

        return examService.listByClassIds(classIds).stream()
                .map(e -> {
                    Classroom c = classMap.get(e.getClassId());
                    String className = c != null ? c.getName() : "";
                    int totalMembers = c != null ? classRepository.listMembers(c.getId()).size() : 0;
                    int submitted = resultService.listByExamId(e.getId()).size();
                    int unsubmitted = totalMembers - submitted;
                    if (unsubmitted < 0) unsubmitted = 0;
                    return ExamResponse.from(e, examService, className, submitted, unsubmitted, examService.getSettingsOrDefault(e.getId()));
                })
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}/results")
    public List<TeacherResultResponse> results(@PathVariable("id") long examId) {
        String username = currentUsername();
        ExamArrangement exam = examService.findById(examId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "not_found"));
        if (!isOwnerOfExam(username, exam)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "not_found");
        }

        Map<String, ClassMember> memberMap = classService.listMembers(exam.getClassId()).stream()
                .collect(Collectors.toMap(ClassMember::getUsername, m -> m, (a, b) -> a));

        return resultService.listByExamId(examId).stream()
                .map(r -> TeacherResultResponse.from(r, memberMap.get(r.getStudentUsername())))
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}/analytics")
    public ExamAnalyticsResponse analytics(@PathVariable("id") long examId) {
        requireOwnedExam(examId);
        List<ExamResult> results = resultService.listByExamId(examId);
        double passLineRatio = 0.6d;

        if (results.isEmpty()) {
            return new ExamAnalyticsResponse(
                    0,
                    0,
                    0,
                    0,
                    0.0d,
                    0.0d,
                    passLineRatio,
                    List.of()
            );
        }

        int participants = results.size();
        int maxTotal = results.stream().mapToInt(ExamResult::getTotalScore).max().orElse(0);
        int minTotal = results.stream().mapToInt(ExamResult::getTotalScore).min().orElse(0);
        int sumTotal = results.stream().mapToInt(ExamResult::getTotalScore).sum();
        double avgTotal = ((double) sumTotal) / participants;
        int maxScore = results.stream().mapToInt(ExamResult::getMaxScore).max().orElse(0);
        long passed = results.stream()
                .filter(r -> r.getMaxScore() > 0)
                .filter(r -> ((double) r.getTotalScore()) / r.getMaxScore() >= passLineRatio)
                .count();
        double passRate = ((double) passed) / participants;

        ConcurrentHashMap<Long, QuestionStatAgg> agg = new ConcurrentHashMap<>();
        for (ExamResult result : results) {
            for (ExamResultItem item : result.getItems()) {
                agg.compute(item.getQuestionId(), (k, existing) -> {
                    QuestionStatAgg next = existing == null ? new QuestionStatAgg(item.getQuestionId(), item.getQuestionType(), item.getMaxScore()) : existing;
                    next.totalCount++;
                    if (item.isCorrect()) {
                        next.correctCount++;
                    }
                    return next;
                });
            }
        }

        List<QuestionStat> questionStats = agg.values().stream()
                .sorted(Comparator.comparingLong(a -> a.questionId))
                .map(a -> new QuestionStat(
                        a.questionId,
                        a.questionType,
                        a.maxScore,
                        a.correctCount,
                        a.totalCount,
                        a.totalCount == 0 ? 0.0d : ((double) a.correctCount) / a.totalCount
                ))
                .collect(Collectors.toList());

        return new ExamAnalyticsResponse(
                participants,
                maxScore,
                maxTotal,
                minTotal,
                avgTotal,
                passRate,
                passLineRatio,
                questionStats
        );
    }

    @GetMapping(path = "/{id}/analytics/ai", produces = MediaType.APPLICATION_JSON_VALUE)
    public AiAnalysisResponse aiAnalytics(@PathVariable("id") long examId) {
        requireOwnedExam(examId);
        ExamArrangement exam = examService.findById(examId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "exam_not_found"));
        List<ExamResult> results = resultService.listByExamId(examId);
        if (results.isEmpty()) {
            return new AiAnalysisResponse("当前暂无成绩数据，无法进行智能分析。");
        }
        ExamAnalyticsResponse analytics = analytics(examId);
        String prompt = buildAiPrompt(exam, analytics, results);
        String content = callOllamaOnce(prompt);
        return new AiAnalysisResponse(content);
    }

    @GetMapping(value = "/{id}/export.csv", produces = "text/csv")
    public ResponseEntity<String> exportCsv(@PathVariable("id") long examId) {
        requireOwnedExam(examId);
        List<ExamResult> results = resultService.listByExamId(examId);
        StringBuilder sb = new StringBuilder();
        sb.append("studentUsername,totalScore,maxScore,createdAt\n");
        for (ExamResult r : results) {
            sb.append(r.getStudentUsername()).append(",")
                    .append(r.getTotalScore()).append(",")
                    .append(r.getMaxScore()).append(",")
                    .append(r.getCreatedAt().toString())
                    .append("\n");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv"));
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"exam-" + examId + "-results.csv\"");
        return new ResponseEntity<>(sb.toString(), headers, HttpStatus.OK);
    }

    @GetMapping(value = "/{id}/export.xlsx", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<byte[]> exportExcel(@PathVariable("id") long examId) {
        requireOwnedExam(examId);
        ExamArrangement exam = examService.findById(examId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "not_found"));

        Map<String, ClassMember> memberMap = classService.listMembers(exam.getClassId()).stream()
                .collect(Collectors.toMap(ClassMember::getUsername, m -> m, (a, b) -> a));

        List<ExamResult> results = resultService.listByExamId(examId);
        List<ExamResult> sortedResults = results.stream()
                .sorted((a, b) -> {
                    ClassMember ma = memberMap.get(a.getStudentUsername());
                    ClassMember mb = memberMap.get(b.getStudentUsername());
                    String sa = ma == null || ma.getStudentNo() == null ? "" : ma.getStudentNo();
                    String sb = mb == null || mb.getStudentNo() == null ? "" : mb.getStudentNo();
                    int cmp = sa.compareTo(sb);
                    if (cmp != 0) {
                        if (sa.isBlank()) {
                            return 1;
                        }
                        if (sb.isBlank()) {
                            return -1;
                        }
                        return cmp;
                    }
                    return String.valueOf(a.getStudentUsername()).compareTo(String.valueOf(b.getStudentUsername()));
                })
                .collect(Collectors.toList());

        List<Long> questionIds = results.stream()
                .flatMap(r -> (r.getItems() == null ? List.<ExamResultItem>of() : r.getItems()).stream())
                .map(ExamResultItem::getQuestionId)
                .distinct()
                .sorted()
                .collect(Collectors.toList());

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet summarySheet = workbook.createSheet("成绩表");

            Row header = summarySheet.createRow(0);
            String[] headers = {"序号", "姓名", "学号", "用户名", "总分", "满分", "得分率", "交卷时间"};
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }

            int rowIdx = 1;
            for (int i = 0; i < sortedResults.size(); i++) {
                ExamResult r = sortedResults.get(i);
                ClassMember m = memberMap.get(r.getStudentUsername());
                Row row = summarySheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(i + 1);
                row.createCell(1).setCellValue(m == null || m.getFullName() == null ? "" : m.getFullName());
                row.createCell(2).setCellValue(m == null || m.getStudentNo() == null ? "" : m.getStudentNo());
                row.createCell(3).setCellValue(r.getStudentUsername() == null ? "" : r.getStudentUsername());
                row.createCell(4).setCellValue(r.getTotalScore());
                row.createCell(5).setCellValue(r.getMaxScore());
                String ratio = "";
                if (r.getMaxScore() > 0) {
                    ratio = String.format(java.util.Locale.ROOT, "%.2f%%", ((double) r.getTotalScore()) * 100d / ((double) r.getMaxScore()));
                }
                row.createCell(6).setCellValue(ratio);
                row.createCell(7).setCellValue(r.getCreatedAt() == null ? "" : r.getCreatedAt().toString());
            }

            Sheet detailSheet = workbook.createSheet("小分表");
            Row header2 = detailSheet.createRow(0);
            int c = 0;
            header2.createCell(c++).setCellValue("序号");
            header2.createCell(c++).setCellValue("姓名");
            header2.createCell(c++).setCellValue("学号");
            header2.createCell(c++).setCellValue("用户名");
            header2.createCell(c++).setCellValue("总分");
            header2.createCell(c++).setCellValue("满分");
            header2.createCell(c++).setCellValue("交卷时间");
            for (Long qid : questionIds) {
                header2.createCell(c++).setCellValue("Q" + qid + "选");
                header2.createCell(c++).setCellValue("Q" + qid + "分");
            }

            int row2Idx = 1;
            for (int i = 0; i < sortedResults.size(); i++) {
                ExamResult r = sortedResults.get(i);
                ClassMember m = memberMap.get(r.getStudentUsername());
                Row row = detailSheet.createRow(row2Idx++);
                int cc = 0;
                row.createCell(cc++).setCellValue(i + 1);
                row.createCell(cc++).setCellValue(m == null || m.getFullName() == null ? "" : m.getFullName());
                row.createCell(cc++).setCellValue(m == null || m.getStudentNo() == null ? "" : m.getStudentNo());
                row.createCell(cc++).setCellValue(r.getStudentUsername() == null ? "" : r.getStudentUsername());
                row.createCell(cc++).setCellValue(r.getTotalScore());
                row.createCell(cc++).setCellValue(r.getMaxScore());
                row.createCell(cc++).setCellValue(r.getCreatedAt() == null ? "" : r.getCreatedAt().toString());

                Map<Long, ExamResultItem> itemByQid = (r.getItems() == null ? List.<ExamResultItem>of() : r.getItems()).stream()
                        .collect(Collectors.toMap(ExamResultItem::getQuestionId, x -> x, (a, b) -> b));

                for (Long qid : questionIds) {
                    ExamResultItem item = itemByQid.get(qid);
                    row.createCell(cc++).setCellValue(item == null || item.getAnswer() == null ? "" : item.getAnswer());
                    if (item == null) {
                        row.createCell(cc++).setCellValue("");
                    } else {
                        row.createCell(cc++).setCellValue(item.getEarnedScore());
                    }
                }
            }

            workbook.write(out);

            HttpHeaders headersHttp = new HttpHeaders();
            headersHttp.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            headersHttp.setContentDispositionFormData("attachment", "exam-" + examId + "-results.xlsx");

            return new ResponseEntity<>(out.toByteArray(), headersHttp, HttpStatus.OK);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "export_failed");
        }
    }

    @GetMapping(value = "/{id}/monitor/export")
    public ResponseEntity<byte[]> exportMonitorData(@PathVariable("id") long examId) {
        return exportMonitorData(examId, new MonitorExportRequest());
    }

    @PostMapping(value = "/{id}/monitor/export")
    public ResponseEntity<byte[]> exportMonitorData(@PathVariable("id") long examId, @RequestBody MonitorExportRequest request) {
        requireOwnedExam(examId);

        ExamMonitorResponse m = monitor(examId);
        Map<String, ExamMonitorResponse.StudentMonitorInfo> studentInfos = m.getStudentInfos() == null ? Map.of() : m.getStudentInfos();

        String statusFilter = request == null || request.getStatusFilter() == null ? "ALL" : request.getStatusFilter();
        String keyword = request == null ? null : request.getKeyword();
        List<String> marked = request == null || request.getMarkedUsernames() == null ? List.of() : request.getMarkedUsernames();
        Set<String> markedSet = marked.stream()
                .filter(x -> x != null && !x.isBlank())
                .collect(Collectors.toSet());

        Set<String> usernames = new java.util.HashSet<>();
        studentInfos.keySet().forEach(u -> {
            if (u != null && !u.isBlank()) {
                usernames.add(u);
            }
        });
        m.getInProgressUsers().forEach(u -> {
            if (u != null && !u.isBlank()) {
                usernames.add(u);
            }
        });
        m.getSubmittedUsers().forEach(u -> {
            if (u != null && !u.isBlank()) {
                usernames.add(u);
            }
        });
        m.getLatestHeartbeats().forEach(h -> {
            String u = h == null ? null : h.getUsername();
            if (u != null && !u.isBlank()) {
                usernames.add(u);
            }
        });
        m.getEvents().forEach(e -> {
            String u = e == null ? null : e.getUsername();
            if (u != null && !u.isBlank()) {
                usernames.add(u);
            }
        });

        List<String> sortedUsernames = usernames.stream()
                .sorted((a, b) -> {
                    String sa = studentInfos.get(a) == null ? "" : String.valueOf(studentInfos.get(a).getStudentNo() == null ? "" : studentInfos.get(a).getStudentNo());
                    String sb = studentInfos.get(b) == null ? "" : String.valueOf(studentInfos.get(b).getStudentNo() == null ? "" : studentInfos.get(b).getStudentNo());
                    int cmp = sa.compareTo(sb);
                    if (cmp != 0) {
                        if (sa.isBlank()) {
                            return 1;
                        }
                        if (sb.isBlank()) {
                            return -1;
                        }
                        return cmp;
                    }
                    return a.compareTo(b);
                })
                .collect(Collectors.toList());

        List<ExportRow> rows = sortedUsernames.stream()
                .map(u -> {
                    ExamMonitorResponse.StudentMonitorInfo info = studentInfos.get(u);
                    String fullName = info == null || info.getFullName() == null ? "" : info.getFullName();
                    String studentNo = info == null || info.getStudentNo() == null ? "" : info.getStudentNo();

                    String status = "未开始";
                    if (m.getSubmittedUsers().contains(u)) {
                        status = "已交卷";
                    } else if (m.getInProgressUsers().contains(u)) {
                        status = "作答中";
                    }

                    TabStats tabStats = computeTabStats(m.getEvents(), u);
                    boolean isMarked = markedSet.contains(u);

                    return new ExportRow(u, fullName, studentNo, isMarked, status, tabStats.tabSwitchCount, tabStats.tabAwaySeconds);
                })
                .filter(r -> {
                    if ("IN_PROGRESS".equals(statusFilter) && !"作答中".equals(r.status)) {
                        return false;
                    }
                    if ("SUBMITTED".equals(statusFilter) && !"已交卷".equals(r.status)) {
                        return false;
                    }
                    if (keyword != null && !keyword.isBlank()) {
                        String k = keyword.trim();
                        return r.username.contains(k) || r.fullName.contains(k) || r.studentNo.contains(k);
                    }
                    return true;
                })
                .collect(Collectors.toList());

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("监控数据");

            Row header = sheet.createRow(0);
            String[] headers = {"序号", "姓名", "学号", "用户名", "是否标记", "状态", "切屏次数", "切屏时长"};
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }

            int rowIdx = 1;
            for (int i = 0; i < rows.size(); i++) {
                ExportRow r = rows.get(i);
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(i + 1);
                row.createCell(1).setCellValue(r.fullName);
                row.createCell(2).setCellValue(r.studentNo);
                row.createCell(3).setCellValue(r.username);
                row.createCell(4).setCellValue(r.isMarked ? "是" : "-");
                row.createCell(5).setCellValue(r.status);
                row.createCell(6).setCellValue(r.tabSwitchCount);
                row.createCell(7).setCellValue(formatDurationCn(r.tabAwaySeconds));
            }

            workbook.write(out);

            HttpHeaders headersHttp = new HttpHeaders();
            headersHttp.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            headersHttp.setContentDispositionFormData("attachment", "exam-" + examId + "-monitor.xlsx");

            return new ResponseEntity<>(out.toByteArray(), headersHttp, HttpStatus.OK);

        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "export_failed");
        }
    }

    private static TabStats computeTabStats(List<ExamMonitorResponse.ProctorEventBrief> events, String username) {
        if (username == null || username.isBlank()) {
            return new TabStats(0, 0);
        }
        List<ExamMonitorResponse.ProctorEventBrief> userEvents = events.stream()
                .filter(e -> username.equals(e.getUsername()))
                .sorted(Comparator.comparing(ExamMonitorResponse.ProctorEventBrief::getCreatedAt))
                .collect(Collectors.toList());

        int tabSwitchCount = 0;
        Long awayStart = null;
        long awayMs = 0;

        for (ExamMonitorResponse.ProctorEventBrief e : userEvents) {
            long ts;
            try {
                ts = Instant.parse(e.getCreatedAt()).toEpochMilli();
            } catch (Exception ex) {
                continue;
            }
            if ("WINDOW_BLUR".equals(e.getType()) || "VISIBILITY_HIDDEN".equals(e.getType())) {
                if (awayStart == null) {
                    awayStart = ts;
                }
            } else if ("WINDOW_FOCUS".equals(e.getType()) || "VISIBILITY_VISIBLE".equals(e.getType())) {
                if (awayStart != null) {
                    if (ts > awayStart) {
                        awayMs += ts - awayStart;
                    }
                    tabSwitchCount += 1;
                    awayStart = null;
                }
            }
        }

        return new TabStats(tabSwitchCount, Math.round(awayMs / 1000.0d));
    }

    private static String formatDurationCn(long seconds) {
        if (seconds <= 0) {
            return "0 秒";
        }
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        if (hours > 0) {
            if (minutes > 0) {
                return hours + " 时 " + minutes + " 分";
            }
            return hours + " 时";
        }
        if (minutes > 0) {
            if (secs > 0) {
                return minutes + " 分 " + secs + " 秒";
            }
            return minutes + " 分";
        }
        return secs + " 秒";
    }

    public static class MonitorExportRequest {
        private List<String> markedUsernames;
        private String statusFilter;
        private String keyword;

        public List<String> getMarkedUsernames() {
            return markedUsernames;
        }

        public void setMarkedUsernames(List<String> markedUsernames) {
            this.markedUsernames = markedUsernames;
        }

        public String getStatusFilter() {
            return statusFilter;
        }

        public void setStatusFilter(String statusFilter) {
            this.statusFilter = statusFilter;
        }

        public String getKeyword() {
            return keyword;
        }

        public void setKeyword(String keyword) {
            this.keyword = keyword;
        }
    }

    private static class TabStats {
        private final int tabSwitchCount;
        private final long tabAwaySeconds;

        private TabStats(int tabSwitchCount, long tabAwaySeconds) {
            this.tabSwitchCount = tabSwitchCount;
            this.tabAwaySeconds = tabAwaySeconds;
        }
    }

    private static class ExportRow {
        private final String username;
        private final String fullName;
        private final String studentNo;
        private final boolean isMarked;
        private final String status;
        private final int tabSwitchCount;
        private final long tabAwaySeconds;

        private ExportRow(
                String username,
                String fullName,
                String studentNo,
                boolean isMarked,
                String status,
                int tabSwitchCount,
                long tabAwaySeconds
        ) {
            this.username = username;
            this.fullName = fullName;
            this.studentNo = studentNo;
            this.isMarked = isMarked;
            this.status = status;
            this.tabSwitchCount = tabSwitchCount;
            this.tabAwaySeconds = tabAwaySeconds;
        }
    }

    @GetMapping("/{id}/monitor")
    public ExamMonitorResponse monitor(@PathVariable("id") long examId) {
        requireOwnedExam(examId);
        List<ExamAttempt> attempts = attemptRepository.listByExamId(examId);
        int startedCount = attempts.size();
        int inProgressCount = (int) attempts.stream().filter(a -> a.getStatus() == AttemptStatus.IN_PROGRESS).count();
        int submittedCount = (int) attempts.stream()
                .filter(a -> a.getStatus() == AttemptStatus.SUBMITTED || a.getStatus() == AttemptStatus.AUTO_SUBMITTED)
                .count();
        Set<String> inProgressUsers = attempts.stream()
                .filter(a -> a.getStatus() == AttemptStatus.IN_PROGRESS)
                .map(ExamAttempt::getStudentUsername)
                .collect(Collectors.toSet());
        Set<String> submittedUsers = attempts.stream()
                .filter(a -> a.getStatus() == AttemptStatus.SUBMITTED || a.getStatus() == AttemptStatus.AUTO_SUBMITTED)
                .map(ExamAttempt::getStudentUsername)
                .collect(Collectors.toSet());
        int resultsCount = resultService.listByExamId(examId).size();

        ExamArrangement exam = examService.findById(examId).orElseThrow();
        List<ClassMember> classMembers = classService.listMembers(exam.getClassId());
        Map<String, ExamMonitorResponse.StudentMonitorInfo> studentInfos = classMembers.stream()
                .collect(Collectors.toMap(
                        ClassMember::getUsername,
                        m -> new ExamMonitorResponse.StudentMonitorInfo(m.getFullName(), m.getStudentNo()),
                        (a, b) -> a
                ));

        List<ProctorEvent> events = proctorService.listRecentEvents(examId, 50);
        List<HeartbeatRecord> heartbeats = proctorService.listLatestHeartbeats(examId);

        List<ExamMonitorResponse.ProctorEventBrief> eventDtos = events.stream()
                .map(e -> new ExamMonitorResponse.ProctorEventBrief(
                        e.getId(),
                        e.getAttemptId(),
                        e.getUsername(),
                        e.getType(),
                        e.getCreatedAt().toString()
                ))
                .collect(Collectors.toList());

        List<ExamMonitorResponse.HeartbeatBrief> heartbeatDtos = heartbeats.stream()
                .map(h -> new ExamMonitorResponse.HeartbeatBrief(
                        h.getAttemptId(),
                        h.getUsername(),
                        h.getTs().toString()
                ))
                .collect(Collectors.toList());

        return new ExamMonitorResponse(
                startedCount,
                inProgressCount,
                submittedCount,
                resultsCount,
                inProgressUsers.stream().sorted().collect(Collectors.toList()),
                submittedUsers.stream().sorted().collect(Collectors.toList()),
                eventDtos,
                heartbeatDtos,
                studentInfos
        );
    }

    @PostMapping("/{id}/proctor/remind")
    public ResponseEntity<?> remindStudent(@PathVariable("id") long examId, @RequestBody ProctorCommandRequest request) {
        requireOwnedExam(examId);
        String targetUsername = request == null ? null : request.getUsername();
        if (targetUsername == null || targetUsername.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "username_required");
        }
        String message = request.getMessage();
        if (message == null) {
            message = "";
        }
        List<ExamAttempt> attempts = attemptRepository.listByExamId(examId);
        ExamAttempt targetAttempt = attempts.stream()
                .filter(a -> a.getStudentUsername().equals(targetUsername))
                .filter(a -> a.getStatus() == AttemptStatus.IN_PROGRESS)
                .max(Comparator.comparingLong(ExamAttempt::getId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "no_in_progress_attempt"));
        proctorService.recordEvent(
                examId,
                targetAttempt.getId(),
                targetUsername,
                "TEACHER_REMIND",
                message,
                Instant.now()
        );
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    @PostMapping("/{id}/proctor/force-submit")
    public ResponseEntity<?> forceSubmit(@PathVariable("id") long examId, @RequestBody ProctorCommandRequest request) {
        requireOwnedExam(examId);
        String targetUsername = request == null ? null : request.getUsername();
        if (targetUsername == null || targetUsername.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "username_required");
        }
        List<ExamAttempt> attempts = attemptRepository.listByExamId(examId);
        ExamAttempt targetAttempt = attempts.stream()
                .filter(a -> a.getStudentUsername().equals(targetUsername))
                .filter(a -> a.getStatus() == AttemptStatus.IN_PROGRESS)
                .max(Comparator.comparingLong(ExamAttempt::getId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "no_in_progress_attempt"));
        return attemptRepository.autoSubmitIfInProgress(targetAttempt.getId())
                .map(submitted -> {
                    resultService.ensureResultCreated(examId, submitted);
                    proctorService.recordEvent(
                            examId,
                            submitted.getId(),
                            targetUsername,
                            "TEACHER_FORCE_SUBMIT",
                            "",
                            Instant.now()
                    );
                    return ResponseEntity.ok(Map.of(
                            "status", "ok",
                            "attemptId", submitted.getId(),
                            "submittedAt", submitted.getSubmittedAt() == null ? null : submitted.getSubmittedAt().toString()
                    ));
                })
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "cannot_force_submit"));
    }

    @PostMapping("/{id}/proctor/reopen")
    public ResponseEntity<?> reopenAttempt(@PathVariable("id") long examId, @RequestBody ProctorCommandRequest request) {
        requireOwnedExam(examId);
        String targetUsername = request == null ? null : request.getUsername();
        if (targetUsername == null || targetUsername.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "username_required");
        }

        ExamArrangement exam = examService.findById(examId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "not_found"));
        ExamStatus status = examService.statusOf(exam, Instant.now());
        if (status != ExamStatus.IN_PROGRESS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "exam_not_in_progress");
        }

        List<ExamAttempt> attempts = attemptRepository.listByExamId(examId);
        ExamAttempt targetAttempt = attempts.stream()
                .filter(a -> a.getStudentUsername().equals(targetUsername))
                .filter(a -> a.getStatus() == AttemptStatus.SUBMITTED || a.getStatus() == AttemptStatus.AUTO_SUBMITTED)
                .max(Comparator.comparingLong(ExamAttempt::getId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "no_submitted_attempt"));

        return attemptRepository.reopenIfSubmitted(targetAttempt.getId())
                .map(reopened -> ResponseEntity.ok(Map.of(
                        "status", "ok",
                        "attemptId", reopened.getId(),
                        "attemptStatus", reopened.getStatus().name()
                )))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "cannot_reopen_attempt"));
    }

    private String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return String.valueOf(authentication.getPrincipal());
    }

    private void requireOwnedExam(long examId) {
        String username = currentUsername();
        ExamArrangement exam = examService.findById(examId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "not_found"));
        if (!isOwnerOfExam(username, exam)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "not_found");
        }
    }

    private void requireOwnedExamOrOrphanDeletable(long examId) {
        String username = currentUsername();
        ExamArrangement exam = examService.findById(examId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "not_found"));
        Long classId = exam.getClassId();
        if (classId == null || classId <= 0) {
            return;
        }
        if (classRepository.findById(classId).isEmpty()) {
            return;
        }
        if (!isOwnerOfExam(username, exam)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "not_found");
        }
    }

    private boolean isOwnerOfExam(String username, ExamArrangement exam) {
        if (exam.getClassId() == null || exam.getClassId() <= 0) {
            return false;
        }
        return classRepository.findById(exam.getClassId())
                .map(c -> c.getOwnerUsername().equals(username))
                .orElse(false);
    }

    public static class ProctorCommandRequest {
        private String username;
        private String message;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }

    public static class CreateExamRequest {
        private String name;
        private long paperId;
        private Long classId;
        private Instant startAt;
        private Instant endAt;
        private ExamSettings settings;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public long getPaperId() {
            return paperId;
        }

        public void setPaperId(long paperId) {
            this.paperId = paperId;
        }

        public Long getClassId() {
            return classId;
        }

        public void setClassId(Long classId) {
            this.classId = classId;
        }

        public Instant getStartAt() {
            return startAt;
        }

        public void setStartAt(Instant startAt) {
            this.startAt = startAt;
        }

        public Instant getEndAt() {
            return endAt;
        }

        public void setEndAt(Instant endAt) {
            this.endAt = endAt;
        }

        public ExamSettings getSettings() {
            return settings;
        }

        public void setSettings(ExamSettings settings) {
            this.settings = settings;
        }

        public ExamDraft toDraft() {
            return new ExamDraft(name, paperId, classId, startAt, endAt, settings);
        }
    }

    public static class ExamResponse {
        private final long id;
        private final String name;
        private final long paperId;
        private final Long classId;
        private final String className;
        private final String startAt;
        private final String endAt;
        private final String status;
        private final String createdAt;
        private final String updatedAt;
        private final int submittedCount;
        private final int unsubmittedCount;
        private final ExamSettings settings;

        public ExamResponse(
                long id,
                String name,
                long paperId,
                Long classId,
                String className,
                String startAt,
                String endAt,
                String status,
                String createdAt,
                String updatedAt,
                int submittedCount,
                int unsubmittedCount,
                ExamSettings settings
        ) {
            this.id = id;
            this.name = name;
            this.paperId = paperId;
            this.classId = classId;
            this.className = className;
            this.startAt = startAt;
            this.endAt = endAt;
            this.status = status;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
            this.submittedCount = submittedCount;
            this.unsubmittedCount = unsubmittedCount;
            this.settings = settings;
        }

        public static ExamResponse from(ExamArrangement exam, ExamService examService) {
             return from(exam, examService, "", 0, 0, null);
        }

        public static ExamResponse from(ExamArrangement exam, ExamService examService, String className, int submittedCount, int unsubmittedCount, ExamSettings settings) {
            String status = examService.statusOf(exam, Instant.now()).name();
            return new ExamResponse(
                    exam.getId(),
                    exam.getName(),
                    exam.getPaperId(),
                    exam.getClassId(),
                    className,
                    exam.getStartAt().toString(),
                    exam.getEndAt().toString(),
                    status,
                    exam.getCreatedAt().toString(),
                    exam.getUpdatedAt().toString(),
                    submittedCount,
                    unsubmittedCount,
                    settings
            );
        }

        public long getId() { return id; }
        public String getName() { return name; }
        public long getPaperId() { return paperId; }
        public Long getClassId() { return classId; }
        public String getClassName() { return className; }
        public String getStartAt() { return startAt; }
        public String getEndAt() { return endAt; }
        public String getStatus() { return status; }
        public String getCreatedAt() { return createdAt; }
        public String getUpdatedAt() { return updatedAt; }
        public int getSubmittedCount() { return submittedCount; }
        public int getUnsubmittedCount() { return unsubmittedCount; }
        public ExamSettings getSettings() { return settings; }
    }

    public static class TeacherResultResponse {
        private final long resultId;
        private final long examId;
        private final long attemptId;
        private final String studentUsername;
        private final String fullName;
        private final String studentNo;
        private final int totalScore;
        private final int maxScore;
        private final String createdAt;

        public TeacherResultResponse(
                long resultId,
                long examId,
                long attemptId,
                String studentUsername,
                String fullName,
                String studentNo,
                int totalScore,
                int maxScore,
                String createdAt
        ) {
            this.resultId = resultId;
            this.examId = examId;
            this.attemptId = attemptId;
            this.studentUsername = studentUsername;
            this.fullName = fullName;
            this.studentNo = studentNo;
            this.totalScore = totalScore;
            this.maxScore = maxScore;
            this.createdAt = createdAt;
        }

        public static TeacherResultResponse from(ExamResult result, ClassMember member) {
            return new TeacherResultResponse(
                    result.getId(),
                    result.getExamId(),
                    result.getAttemptId(),
                    result.getStudentUsername(),
                    member == null ? null : member.getFullName(),
                    member == null ? null : member.getStudentNo(),
                    result.getTotalScore(),
                    result.getMaxScore(),
                    result.getCreatedAt().toString()
            );
        }

        public long getResultId() {
            return resultId;
        }

        public long getExamId() {
            return examId;
        }

        public long getAttemptId() {
            return attemptId;
        }

        public String getStudentUsername() {
            return studentUsername;
        }

        public String getFullName() {
            return fullName;
        }

        public String getStudentNo() {
            return studentNo;
        }

        public int getTotalScore() {
            return totalScore;
        }

        public int getMaxScore() {
            return maxScore;
        }

        public String getCreatedAt() {
            return createdAt;
        }
    }

    public static class ExamAnalyticsResponse {
        private final int participants;
        private final int maxScore;
        private final int maxTotalScore;
        private final int minTotalScore;
        private final double avgTotalScore;
        private final double passRate;
        private final double passLineRatio;
        private final List<QuestionStat> questionStats;

        public ExamAnalyticsResponse(
                int participants,
                int maxScore,
                int maxTotalScore,
                int minTotalScore,
                double avgTotalScore,
                double passRate,
                double passLineRatio,
                List<QuestionStat> questionStats
        ) {
            this.participants = participants;
            this.maxScore = maxScore;
            this.maxTotalScore = maxTotalScore;
            this.minTotalScore = minTotalScore;
            this.avgTotalScore = avgTotalScore;
            this.passRate = passRate;
            this.passLineRatio = passLineRatio;
            this.questionStats = questionStats;
        }

        public int getParticipants() {
            return participants;
        }

        public int getMaxScore() {
            return maxScore;
        }

        public int getMaxTotalScore() {
            return maxTotalScore;
        }

        public int getMinTotalScore() {
            return minTotalScore;
        }

        public double getAvgTotalScore() {
            return avgTotalScore;
        }

        public double getPassRate() {
            return passRate;
        }

        public double getPassLineRatio() {
            return passLineRatio;
        }

        public List<QuestionStat> getQuestionStats() {
            return questionStats;
        }
    }

    public static class AiAnalysisResponse {
        private final String content;

        public AiAnalysisResponse(String content) {
            this.content = content;
        }

        public String getContent() {
            return content;
        }
    }

    private String buildAiPrompt(ExamArrangement exam, ExamAnalyticsResponse analytics, List<ExamResult> results) {
        List<Integer> scores = results.stream()
                .map(ExamResult::getTotalScore)
                .sorted()
                .collect(Collectors.toList());
        int n = scores.size();
        double avg = analytics.getAvgTotalScore();
        double variance = scores.stream()
                .mapToDouble(s -> {
                    double d = s - avg;
                    return d * d;
                })
                .sum() / (n == 0 ? 1 : n);
        double stddev = Math.sqrt(variance);

        double median;
        if (n == 0) {
            median = 0;
        } else if (n % 2 == 1) {
            median = scores.get(n / 2);
        } else {
            median = (scores.get(n / 2 - 1) + scores.get(n / 2)) / 2.0;
        }

        Map<Long, String> questionStemById = attemptRepository.listByExamId(exam.getId()).stream()
                .flatMap(a -> a.getQuestions().stream())
                .collect(Collectors.toMap(
                        q -> q.getId(),
                        q -> q.getStem(),
                        (a, b) -> a
                ));

        List<QuestionStat> questionStats = analytics.getQuestionStats() == null ? List.of() : analytics.getQuestionStats();
        List<QuestionStat> sortedByCorrectRateDesc = questionStats.stream()
                .sorted(Comparator.comparingDouble(QuestionStat::getCorrectRate).reversed())
                .collect(Collectors.toList());
        List<QuestionStat> sortedByCorrectRateAsc = questionStats.stream()
                .sorted(Comparator.comparingDouble(QuestionStat::getCorrectRate))
                .collect(Collectors.toList());

        List<QuestionStat> highQuestions = sortedByCorrectRateDesc.subList(0, Math.min(3, sortedByCorrectRateDesc.size()));
        List<QuestionStat> lowQuestions = sortedByCorrectRateAsc.subList(0, Math.min(3, sortedByCorrectRateAsc.size()));

        Map<String, Integer> scoreByUsername = results.stream()
                .collect(Collectors.toMap(
                        ExamResult::getStudentUsername,
                        ExamResult::getTotalScore,
                        Integer::max
                ));

        List<Map.Entry<String, Integer>> scoreEntries = scoreByUsername.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .collect(Collectors.toList());
        List<Map.Entry<String, Integer>> topStudents = scoreEntries.stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(3)
                .collect(Collectors.toList());
        List<Map.Entry<String, Integer>> bottomStudents = scoreEntries.stream()
                .limit(3)
                .collect(Collectors.toList());

        Map<String, String> nameByUsername = classService.listMembers(exam.getClassId()).stream()
                .collect(Collectors.toMap(
                        ClassMember::getUsername,
                        ClassMember::getFullName,
                        (a, b) -> a
                ));

        StringBuilder sb = new StringBuilder();
        sb.append("你是一名经验丰富的班主任，需要根据一次班级考试的统计数据，给出面向教师的中文学情分析和教学建议。");
        sb.append("\n\n");
        sb.append("【考试基本情况】").append("\n");
        sb.append("考试名称：").append(exam.getName()).append("\n");
        sb.append("参与人数：").append(analytics.getParticipants()).append(" 人\n");
        sb.append("试卷满分：").append(analytics.getMaxScore()).append(" 分\n");
        sb.append("平均分：").append(String.format(java.util.Locale.ROOT, "%.2f", analytics.getAvgTotalScore())).append(" 分\n");
        sb.append("中位数：").append(String.format(java.util.Locale.ROOT, "%.2f", median)).append(" 分\n");
        sb.append("标准差：").append(String.format(java.util.Locale.ROOT, "%.2f", stddev)).append(" 分\n");
        sb.append("及格率：").append(String.format(java.util.Locale.ROOT, "%.2f", analytics.getPassRate() * 100)).append("%\n");

        sb.append("\n");
        sb.append("【题目表现（用于推断知识点掌握情况）】").append("\n");
        sb.append("得分较高的前三题：").append("\n");
        if (highQuestions.isEmpty()) {
            sb.append("暂无题目统计数据。").append("\n");
        } else {
            int idx = 1;
            for (QuestionStat q : highQuestions) {
                String stem = questionStemById.getOrDefault(q.getQuestionId(), "");
                sb.append(idx).append("）题号 ").append(q.getQuestionId())
                        .append("，分值 ").append(q.getMaxScore()).append(" 分，正确率 ")
                        .append(String.format(java.util.Locale.ROOT, "%.2f", q.getCorrectRate() * 100)).append("%。");
                if (!stem.isBlank()) {
                    sb.append("题干：").append(stem);
                }
                sb.append("\n");
                idx++;
            }
        }
        sb.append("得分较低的前三题：").append("\n");
        if (lowQuestions.isEmpty()) {
            sb.append("暂无题目统计数据。").append("\n");
        } else {
            int idx = 1;
            for (QuestionStat q : lowQuestions) {
                String stem = questionStemById.getOrDefault(q.getQuestionId(), "");
                sb.append(idx).append("）题号 ").append(q.getQuestionId())
                        .append("，分值 ").append(q.getMaxScore()).append(" 分，正确率 ")
                        .append(String.format(java.util.Locale.ROOT, "%.2f", q.getCorrectRate() * 100)).append("%。");
                if (!stem.isBlank()) {
                    sb.append("题干：").append(stem);
                }
                sb.append("\n");
                idx++;
            }
        }

        sb.append("\n");
        sb.append("【学生表现】").append("\n");
        sb.append("得分较高的学生（重点表扬）：").append("\n");
        if (topStudents.isEmpty()) {
            sb.append("暂无学生成绩。").append("\n");
        } else {
            int idx = 1;
            for (Map.Entry<String, Integer> e : topStudents) {
                String username = e.getKey();
                Integer score = e.getValue();
                String name = nameByUsername.getOrDefault(username, username);
                sb.append(idx).append("）").append(name).append("（用户名：").append(username).append("），总分 ")
                        .append(score).append(" 分。\n");
                idx++;
            }
        }
        sb.append("得分较低的学生（需要重点关注）：").append("\n");
        if (bottomStudents.isEmpty()) {
            sb.append("暂无学生成绩。").append("\n");
        } else {
            int idx = 1;
            for (Map.Entry<String, Integer> e : bottomStudents) {
                String username = e.getKey();
                Integer score = e.getValue();
                String name = nameByUsername.getOrDefault(username, username);
                sb.append(idx).append("）").append(name).append("（用户名：").append(username).append("），总分 ")
                        .append(score).append(" 分。\n");
                idx++;
            }
        }

        sb.append("\n");
        sb.append("请你结合以上信息，用简明的中文给出：").append("\n");
        sb.append("1. 全班整体情况概述；").append("\n");
        sb.append("2. 知识点掌握情况分析（可结合高分题和低分题推断）；").append("\n");
        sb.append("3. 对班级教学的改进建议（至少 3 条，尽量具体可执行）；").append("\n");
        sb.append("4. 对得分较低学生的后续跟进与辅导建议。").append("\n");
        sb.append("输出要求：直接以教师视角输出一段连续的自然语言分析，不要重复列出上述原始统计数据，不要使用列表符号或 JSON，只输出中文文字分析内容。");

        return sb.toString();
    }

    private String callOllamaOnce(String prompt) {
        java.net.HttpURLConnection connection = null;
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        try {
            java.net.URL url = new java.net.URL("http://localhost:11434/api/generate");
            connection = (java.net.HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(600000);

            Map<String, Object> payload = Map.of(
                    "model", "qwen2",
                    "prompt", prompt,
                    "stream", Boolean.FALSE
            );

            try (java.io.OutputStream os = connection.getOutputStream()) {
                mapper.writeValue(os, payload);
                os.flush();
            }

            int status = connection.getResponseCode();
            java.io.InputStream is = status == java.net.HttpURLConnection.HTTP_OK
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            if (is == null) {
                throw new IOException("empty_response_from_ollama");
            }
            try (is; java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                String json = sb.toString();
                com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(json);
                String content = node.path("response").asText("");
                if (content == null || content.isBlank()) {
                    throw new IOException("empty_response_field_from_ollama");
                }
                return content;
            }
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "ai_service_unavailable");
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static class QuestionStatAgg {
        private final long questionId;
        private final String questionType;
        private final int maxScore;
        private int correctCount;
        private int totalCount;

        private QuestionStatAgg(long questionId, String questionType, int maxScore) {
            this.questionId = questionId;
            this.questionType = questionType;
            this.maxScore = maxScore;
        }
    }

    public static class QuestionStat {
        private final long questionId;
        private final String questionType;
        private final int maxScore;
        private final int correctCount;
        private final int totalCount;
        private final double correctRate;

        public QuestionStat(
                long questionId,
                String questionType,
                int maxScore,
                int correctCount,
                int totalCount,
                double correctRate
        ) {
            this.questionId = questionId;
            this.questionType = questionType;
            this.maxScore = maxScore;
            this.correctCount = correctCount;
            this.totalCount = totalCount;
            this.correctRate = correctRate;
        }

        public long getQuestionId() {
            return questionId;
        }

        public String getQuestionType() {
            return questionType;
        }

        public int getMaxScore() {
            return maxScore;
        }

        public int getCorrectCount() {
            return correctCount;
        }

        public int getTotalCount() {
            return totalCount;
        }

        public double getCorrectRate() {
            return correctRate;
        }
    }

    public static class ExamMonitorResponse {
        private final int startedCount;
        private final int inProgressCount;
        private final int submittedCount;
        private final int resultsCount;
        private final List<String> inProgressUsers;
        private final List<String> submittedUsers;
        private final List<ProctorEventBrief> events;
        private final List<HeartbeatBrief> latestHeartbeats;
        private final Map<String, StudentMonitorInfo> studentInfos;

        public ExamMonitorResponse(
                int startedCount,
                int inProgressCount,
                int submittedCount,
                int resultsCount,
                List<String> inProgressUsers,
                List<String> submittedUsers,
                List<ProctorEventBrief> events,
                List<HeartbeatBrief> latestHeartbeats,
                Map<String, StudentMonitorInfo> studentInfos
        ) {
            this.startedCount = startedCount;
            this.inProgressCount = inProgressCount;
            this.submittedCount = submittedCount;
            this.resultsCount = resultsCount;
            this.inProgressUsers = inProgressUsers;
            this.submittedUsers = submittedUsers;
            this.events = events;
            this.latestHeartbeats = latestHeartbeats;
            this.studentInfos = studentInfos;
        }

        public int getStartedCount() {
            return startedCount;
        }

        public int getInProgressCount() {
            return inProgressCount;
        }

        public int getSubmittedCount() {
            return submittedCount;
        }

        public int getResultsCount() {
            return resultsCount;
        }

        public List<String> getInProgressUsers() {
            return inProgressUsers;
        }

        public List<String> getSubmittedUsers() {
            return submittedUsers;
        }

        public List<ProctorEventBrief> getEvents() {
            return events;
        }

        public List<HeartbeatBrief> getLatestHeartbeats() {
            return latestHeartbeats;
        }

        public Map<String, StudentMonitorInfo> getStudentInfos() {
            return studentInfos;
        }

        public static class StudentMonitorInfo {
            private final String fullName;
            private final String studentNo;

            public StudentMonitorInfo(String fullName, String studentNo) {
                this.fullName = fullName;
                this.studentNo = studentNo;
            }

            public String getFullName() {
                return fullName;
            }

            public String getStudentNo() {
                return studentNo;
            }
        }

        public static class ProctorEventBrief {
            private final long id;
            private final long attemptId;
            private final String username;
            private final String type;
            private final String createdAt;

            public ProctorEventBrief(long id, long attemptId, String username, String type, String createdAt) {
                this.id = id;
                this.attemptId = attemptId;
                this.username = username;
                this.type = type;
                this.createdAt = createdAt;
            }

            public long getId() {
                return id;
            }

            public long getAttemptId() {
                return attemptId;
            }

            public String getUsername() {
                return username;
            }

            public String getType() {
                return type;
            }

            public String getCreatedAt() {
                return createdAt;
            }
        }

        public static class HeartbeatBrief {
            private final long attemptId;
            private final String username;
            private final String ts;

            public HeartbeatBrief(long attemptId, String username, String ts) {
                this.attemptId = attemptId;
                this.username = username;
                this.ts = ts;
            }

            public long getAttemptId() {
                return attemptId;
            }

            public String getUsername() {
                return username;
            }

            public String getTs() {
                return ts;
            }
        }
    }
}
