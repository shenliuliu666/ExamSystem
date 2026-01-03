package com.examsystem.controller;

import com.examsystem.paper.Paper;
import com.examsystem.paper.PaperDraft;
import com.examsystem.paper.PaperItem;
import com.examsystem.paper.PaperQuery;
import com.examsystem.paper.PaperService;
import com.examsystem.question.Question;
import com.examsystem.question.QuestionService;
import com.examsystem.question.QuestionType;
import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
@RequestMapping("/api/teacher/papers")
public class TeacherPaperController {
    private final PaperService paperService;
    private final QuestionService questionService;
    private final JdbcTemplate jdbcTemplate;

    public TeacherPaperController(PaperService paperService, QuestionService questionService, JdbcTemplate jdbcTemplate) {
        this.paperService = paperService;
        this.questionService = questionService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody CreateOrUpdatePaperRequest request) {
        Paper created = paperService.create(request.toDraft());
        return ResponseEntity.status(HttpStatus.CREATED).body(PaperResponse.from(created, computePaperDifficulty(created.getId())));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable("id") long id) {
        return paperService.findById(id)
                .<ResponseEntity<?>>map(p -> ResponseEntity.ok(PaperResponse.from(p, computePaperDifficulty(p.getId()))))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "not_found")));
    }

    @GetMapping
    public PaperService.PagedResult<PaperResponse> list(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        PaperQuery query = new PaperQuery(keyword);
        PaperService.PagedResult<Paper> result = paperService.list(query, page, size);
        List<PaperResponse> items = result.getItems().stream()
                .map(p -> PaperResponse.from(p, computePaperDifficulty(p.getId())))
                .collect(Collectors.toList());
        return new PaperService.PagedResult<>(items, result.getTotal(), result.getPage(), result.getSize());
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(
            @PathVariable("id") long id,
            @RequestBody CreateOrUpdatePaperRequest request
    ) {
        Optional<Paper> updated = paperService.update(id, request.toDraft());
        return updated.<ResponseEntity<?>>map(p -> ResponseEntity.ok(PaperResponse.from(p, computePaperDifficulty(p.getId()))))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "not_found")));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable("id") long id) {
        try {
            boolean deleted = paperService.delete(id);
            if (!deleted) {
                return ResponseEntity.ok(Map.of("status", "ok"));
            }
            return ResponseEntity.ok(Map.of("status", "ok"));
        } catch (ResponseStatusException e) {
            if (e.getStatus() == HttpStatus.CONFLICT) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                        "error", "paper_in_use",
                        "message", e.getReason() == null ? "paper is used by exam/attempt, cannot delete" : e.getReason(),
                        "usage", buildUsage(id)
                ));
            }
            throw e;
        }
    }

    @GetMapping("/{id}/usage")
    public ResponseEntity<?> usage(@PathVariable("id") long id) {
        if (paperService.findById(id).isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "not_found"));
        }
        return ResponseEntity.ok(buildUsage(id));
    }

    @GetMapping(value = "/{id}/export.xlsx", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<byte[]> exportExcel(@PathVariable("id") long id) {
        Paper paper = paperService.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "not_found"));

        List<Question> questions = loadPaperQuestions(paper);
        try (org.apache.poi.ss.usermodel.Workbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
            org.apache.poi.ss.usermodel.Sheet sheet = wb.createSheet("试卷");
            int r = 0;

            org.apache.poi.ss.usermodel.Row meta1 = sheet.createRow(r++);
            meta1.createCell(0).setCellValue("试卷名称");
            meta1.createCell(1).setCellValue(paper.getName() == null ? "" : paper.getName());
            org.apache.poi.ss.usermodel.Row meta2 = sheet.createRow(r++);
            meta2.createCell(0).setCellValue("导出时间");
            meta2.createCell(1).setCellValue(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            r++;

            org.apache.poi.ss.usermodel.Row header = sheet.createRow(r++);
            int c = 0;
            header.createCell(c++).setCellValue("序号");
            header.createCell(c++).setCellValue("题型");
            header.createCell(c++).setCellValue("题干");
            header.createCell(c++).setCellValue("A");
            header.createCell(c++).setCellValue("B");
            header.createCell(c++).setCellValue("C");
            header.createCell(c++).setCellValue("D");
            header.createCell(c++).setCellValue("E");
            header.createCell(c++).setCellValue("F");
            header.createCell(c++).setCellValue("G");
            header.createCell(c++).setCellValue("H");
            header.createCell(c++).setCellValue("正确答案");
            header.createCell(c++).setCellValue("解析");
            header.createCell(c++).setCellValue("分值");
            header.createCell(c++).setCellValue("难度");
            header.createCell(c++).setCellValue("知识点");

            int no = 1;
            for (Question q : questions) {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(r++);
                int cc = 0;
                row.createCell(cc++).setCellValue(no++);
                row.createCell(cc++).setCellValue(q.getType() == null ? "" : q.getType().name());
                row.createCell(cc++).setCellValue(q.getStem() == null ? "" : q.getStem());

                List<String> options = q.getOptions() == null ? List.of() : q.getOptions();
                for (int i = 0; i < 8; i++) {
                    String v = i < options.size() ? String.valueOf(options.get(i)) : "";
                    row.createCell(cc++).setCellValue(v);
                }
                row.createCell(cc++).setCellValue(q.getCorrectAnswer() == null ? "" : q.getCorrectAnswer());
                row.createCell(cc++).setCellValue(q.getAnalysis() == null ? "" : q.getAnalysis());
                row.createCell(cc++).setCellValue(q.getScore());
                row.createCell(cc++).setCellValue(q.getDifficulty() == null ? "" : q.getDifficulty());
                row.createCell(cc++).setCellValue(q.getKnowledgePoint() == null ? "" : q.getKnowledgePoint());
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);

            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String base = safeFilename(paper.getName());
            String filename = (base.isEmpty() ? ("paper_" + id) : base) + "_" + ts + ".xlsx";
            String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(out.toByteArray());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "export_failed", e);
        }
    }

    @GetMapping(value = "/{id}/export.docx", produces = "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    public ResponseEntity<byte[]> exportWord(@PathVariable("id") long id) {
        Paper paper = paperService.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "not_found"));

        List<Question> questions = loadPaperQuestions(paper);
        try (org.apache.poi.xwpf.usermodel.XWPFDocument doc = new org.apache.poi.xwpf.usermodel.XWPFDocument()) {
            org.apache.poi.xwpf.usermodel.XWPFParagraph title = doc.createParagraph();
            org.apache.poi.xwpf.usermodel.XWPFRun titleRun = title.createRun();
            titleRun.setBold(true);
            titleRun.setFontSize(16);
            titleRun.setText(paper.getName() == null ? "" : paper.getName());

            org.apache.poi.xwpf.usermodel.XWPFParagraph meta = doc.createParagraph();
            org.apache.poi.xwpf.usermodel.XWPFRun metaRun = meta.createRun();
            metaRun.setFontSize(10);
            metaRun.setText("导出时间：" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

            int no = 1;
            for (Question q : questions) {
                String typeLabel = typeLabel(q.getType());
                org.apache.poi.xwpf.usermodel.XWPFParagraph p = doc.createParagraph();
                org.apache.poi.xwpf.usermodel.XWPFRun run = p.createRun();
                run.setBold(true);
                run.setText(no + ".【" + typeLabel + "】(" + q.getScore() + "分) " + (q.getStem() == null ? "" : q.getStem()));

                if (q.getType() != null && q.getType() != QuestionType.TRUE_FALSE) {
                    List<String> options = q.getOptions() == null ? List.of() : q.getOptions();
                    for (int i = 0; i < options.size(); i++) {
                        char letter = (char) ('A' + i);
                        org.apache.poi.xwpf.usermodel.XWPFParagraph op = doc.createParagraph();
                        org.apache.poi.xwpf.usermodel.XWPFRun opRun = op.createRun();
                        opRun.setText(letter + ". " + String.valueOf(options.get(i)));
                    }
                }

                org.apache.poi.xwpf.usermodel.XWPFParagraph a = doc.createParagraph();
                org.apache.poi.xwpf.usermodel.XWPFRun aRun = a.createRun();
                aRun.setText("答案：" + (q.getCorrectAnswer() == null ? "" : q.getCorrectAnswer()));

                String analysis = q.getAnalysis() == null ? "" : q.getAnalysis().trim();
                if (!analysis.isEmpty()) {
                    org.apache.poi.xwpf.usermodel.XWPFParagraph an = doc.createParagraph();
                    org.apache.poi.xwpf.usermodel.XWPFRun anRun = an.createRun();
                    anRun.setText("解析：" + analysis);
                }

                String difficulty = q.getDifficulty() == null ? "" : q.getDifficulty().trim();
                String kp = q.getKnowledgePoint() == null ? "" : q.getKnowledgePoint().trim();
                if (!difficulty.isEmpty() || !kp.isEmpty()) {
                    org.apache.poi.xwpf.usermodel.XWPFParagraph extra = doc.createParagraph();
                    org.apache.poi.xwpf.usermodel.XWPFRun extraRun = extra.createRun();
                    StringBuilder sb = new StringBuilder();
                    if (!difficulty.isEmpty()) {
                        sb.append("难度：").append(difficulty);
                    }
                    if (!kp.isEmpty()) {
                        if (sb.length() > 0) sb.append("    ");
                        sb.append("知识点：").append(kp);
                    }
                    extraRun.setText(sb.toString());
                }

                no++;
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);

            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String base = safeFilename(paper.getName());
            String filename = (base.isEmpty() ? ("paper_" + id) : base) + "_" + ts + ".docx";
            String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                    .body(out.toByteArray());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "export_failed", e);
        }
    }

    private Map<String, Object> buildUsage(long id) {
        String username = currentUsername();

        Integer totalExamCount = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM exams WHERE paper_id = ?", Integer.class, id);
        Integer totalAttemptCount = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM exam_attempts WHERE paper_id = ?", Integer.class, id);

        Integer deletableExamCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM exams e LEFT JOIN classes c ON e.class_id = c.id WHERE e.paper_id = ? AND (c.owner_username = ? OR c.id IS NULL)",
                Integer.class,
                id,
                username
        );
        Integer deletableAttemptCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM exam_attempts a JOIN exams e ON a.exam_id = e.id LEFT JOIN classes c ON e.class_id = c.id WHERE a.paper_id = ? AND (c.owner_username = ? OR c.id IS NULL)",
                Integer.class,
                id,
                username
        );

        List<Long> examIds = jdbcTemplate.query(
                "SELECT e.id FROM exams e LEFT JOIN classes c ON e.class_id = c.id WHERE e.paper_id = ? AND (c.owner_username = ? OR c.id IS NULL) ORDER BY e.id DESC LIMIT 200",
                (rs, rowNum) -> rs.getLong("id"),
                id,
                username
        );
        List<Map<String, Object>> exams = jdbcTemplate.query(
                "SELECT e.id, e.name FROM exams e LEFT JOIN classes c ON e.class_id = c.id WHERE e.paper_id = ? AND (c.owner_username = ? OR c.id IS NULL) ORDER BY e.id DESC LIMIT 20",
                (rs, rowNum) -> Map.of(
                        "id", rs.getLong("id"),
                        "name", rs.getString("name")
                ),
                id,
                username
        );
        return Map.of(
                "totalExamCount", totalExamCount == null ? 0 : totalExamCount,
                "totalAttemptCount", totalAttemptCount == null ? 0 : totalAttemptCount,
                "blockedExamCount", Math.max(0, (totalExamCount == null ? 0 : totalExamCount) - (deletableExamCount == null ? 0 : deletableExamCount)),
                "blockedAttemptCount", Math.max(0, (totalAttemptCount == null ? 0 : totalAttemptCount) - (deletableAttemptCount == null ? 0 : deletableAttemptCount)),
                "examCount", deletableExamCount == null ? 0 : deletableExamCount,
                "attemptCount", deletableAttemptCount == null ? 0 : deletableAttemptCount,
                "examIds", examIds,
                "exams", exams
        );
    }

    private List<Question> loadPaperQuestions(Paper paper) {
        List<PaperItem> items = paper.getItems() == null ? List.of() : paper.getItems();
        List<PaperItem> sorted = items.stream()
                .sorted(Comparator.comparingInt(PaperItem::getOrderIndex))
                .collect(Collectors.toList());

        List<Question> questions = new java.util.ArrayList<>();
        for (PaperItem item : sorted) {
            if (item == null) {
                continue;
            }
            long qid = item.getQuestionId();
            questionService.findById(qid).ifPresent(questions::add);
        }
        return questions;
    }

    private String safeFilename(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return "";
        }
        return s.replaceAll("[\\\\/:*?\"<>|\\r\\n\\t]+", "_");
    }

    private String typeLabel(QuestionType type) {
        if (type == null) {
            return "";
        }
        if (type == QuestionType.SINGLE_CHOICE) return "单选";
        if (type == QuestionType.MULTIPLE_CHOICE) return "多选";
        if (type == QuestionType.TRUE_FALSE) return "判断";
        return type.name();
    }

    private double computePaperDifficulty(long paperId) {
        List<DifficultyRow> rows = jdbcTemplate.query(
                "SELECT q.score, q.difficulty FROM paper_items pi JOIN questions q ON pi.question_id = q.id WHERE pi.paper_id = ?",
                (rs, rowNum) -> new DifficultyRow(rs.getInt("score"), rs.getString("difficulty")),
                paperId
        );

        double totalScore = 0.0d;
        double weighted = 0.0d;
        for (DifficultyRow row : rows) {
            int score = row.score;
            if (score <= 0) {
                continue;
            }
            double difficulty = parseDifficulty(row.difficulty);
            totalScore += score;
            weighted += difficulty * score;
        }
        if (totalScore <= 0.0d) {
            return 0.0d;
        }
        return weighted / totalScore;
    }

    private double parseDifficulty(String raw) {
        String v = raw == null ? "" : raw.trim();
        if (v.isEmpty()) {
            return 0.5d;
        }
        try {
            double d = Double.parseDouble(v);
            if (!Double.isFinite(d)) {
                return 0.5d;
            }
            return d;
        } catch (NumberFormatException e) {
            return 0.5d;
        }
    }

    private static class DifficultyRow {
        private final int score;
        private final String difficulty;

        private DifficultyRow(int score, String difficulty) {
            this.score = score;
            this.difficulty = difficulty;
        }
    }

    private String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return String.valueOf(authentication.getPrincipal());
    }

    public static class CreateOrUpdatePaperRequest {
        private String name;
        private List<Long> questionIds;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public List<Long> getQuestionIds() {
            return questionIds;
        }

        public void setQuestionIds(List<Long> questionIds) {
            this.questionIds = questionIds;
        }

        public PaperDraft toDraft() {
            return new PaperDraft(name, questionIds);
        }
    }

    public static class PaperResponse {
        private final long id;
        private final String name;
        private final List<Long> questionIds;
        private final double difficulty;
        private final String createdAt;
        private final String updatedAt;

        public PaperResponse(long id, String name, List<Long> questionIds, double difficulty, String createdAt, String updatedAt) {
            this.id = id;
            this.name = name;
            this.questionIds = questionIds;
            this.difficulty = difficulty;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
        }

        public static PaperResponse from(Paper paper, double difficulty) {
            List<Long> questionIds = paper.getItems().stream()
                    .sorted(Comparator.comparingInt(PaperItem::getOrderIndex))
                    .map(PaperItem::getQuestionId)
                    .collect(Collectors.toList());
            return new PaperResponse(
                    paper.getId(),
                    paper.getName(),
                    questionIds,
                    difficulty,
                    paper.getCreatedAt().toString(),
                    paper.getUpdatedAt().toString()
            );
        }

        public long getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public List<Long> getQuestionIds() {
            return questionIds;
        }

        public double getDifficulty() {
            return difficulty;
        }

        public String getCreatedAt() {
            return createdAt;
        }

        public String getUpdatedAt() {
            return updatedAt;
        }
    }
}
