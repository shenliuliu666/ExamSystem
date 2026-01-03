package com.examsystem.controller;

import com.examsystem.question.Question;
import com.examsystem.question.QuestionBankService;
import com.examsystem.question.QuestionDraft;
import com.examsystem.question.QuestionQuery;
import com.examsystem.question.QuestionService;
import com.examsystem.question.QuestionType;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/teacher/questions")
public class TeacherQuestionController {
    private final QuestionService questionService;
    private final QuestionBankService questionBankService;

    public TeacherQuestionController(QuestionService questionService, QuestionBankService questionBankService) {
        this.questionService = questionService;
        this.questionBankService = questionBankService;
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody CreateOrUpdateQuestionRequest request) {
        if (request.getBankId() != null && !questionBankService.canAccessBank(request.getBankId(), currentUsername())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "forbidden"));
        }
        Question created = questionService.create(request.toDraft());
        return ResponseEntity.status(HttpStatus.CREATED).body(QuestionResponse.from(created));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable("id") long id) {
        return questionService.findById(id)
                .<ResponseEntity<?>>map(q -> ResponseEntity.ok(QuestionResponse.from(q)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "not_found")));
    }

    @GetMapping
    public QuestionService.PagedResult<QuestionResponse> list(
            @RequestParam(value = "type", required = false) QuestionType type,
            @RequestParam(value = "enabled", required = false) Boolean enabled,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "bankId", required = false) Long bankId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        if (bankId != null && !questionBankService.canAccessBank(bankId, currentUsername())) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN, "forbidden");
        }
        QuestionQuery query = new QuestionQuery(type, enabled, keyword, bankId);
        QuestionService.PagedResult<Question> result = questionService.list(query, page, size);
        List<QuestionResponse> items = result.getItems().stream().map(QuestionResponse::from).collect(Collectors.toList());
        return new QuestionService.PagedResult<>(items, result.getTotal(), result.getPage(), result.getSize());
    }

    @GetMapping(value = "/export.xlsx", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<byte[]> exportExcel(
            @RequestParam(value = "type", required = false) QuestionType type,
            @RequestParam(value = "enabled", required = false) Boolean enabled,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "bankId", required = false) Long bankId
    ) {
        if (bankId != null && !questionBankService.canAccessBank(bankId, currentUsername())) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN, "forbidden");
        }

        QuestionQuery query = new QuestionQuery(type, enabled, keyword, bankId);
        List<Question> questions = questionService.listAll(query);

        try (org.apache.poi.ss.usermodel.Workbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
            org.apache.poi.ss.usermodel.Sheet sheet = wb.createSheet("题库");
            int r = 0;

            org.apache.poi.ss.usermodel.Row header = sheet.createRow(r++);
            int c = 0;
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
            header.createCell(c++).setCellValue("标签");

            for (Question q : questions) {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(r++);
                int cc = 0;
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
                List<String> tags = q.getTags() == null ? List.of() : q.getTags();
                row.createCell(cc++).setCellValue(String.join(",", tags));
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);

            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String filename = "questions_" + ts + ".xlsx";
            String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(out.toByteArray());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "export_failed", e);
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(
            @PathVariable("id") long id,
            @RequestBody CreateOrUpdateQuestionRequest request
    ) {
        if (request.getBankId() != null && !questionBankService.canAccessBank(request.getBankId(), currentUsername())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "forbidden"));
        }
        Optional<Question> updated = questionService.update(id, request.toDraft());
        return updated.<ResponseEntity<?>>map(q -> ResponseEntity.ok(QuestionResponse.from(q)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "not_found")));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable("id") long id) {
        boolean deleted = questionService.delete(id);
        if (!deleted) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "not_found"));
        }
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportResult importExcel(
            @RequestParam(value = "bankId", required = false) Long bankId,
            @RequestParam(value = "tags", required = false) String tags,
            @RequestParam("file") MultipartFile file
    ) {
        if (bankId != null && !questionBankService.canAccessBank(bankId, currentUsername())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "forbidden");
        }
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file is required");
        }

        List<String> extraTags = splitTags(tags);

        try (InputStream in = file.getInputStream(); org.apache.poi.ss.usermodel.Workbook workbook = org.apache.poi.ss.usermodel.WorkbookFactory.create(in)) {
            org.apache.poi.ss.usermodel.Sheet sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
            if (sheet == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "empty_excel");
            }
            int firstRow = sheet.getFirstRowNum();
            int lastRow = sheet.getLastRowNum();
            if (lastRow < firstRow) {
                return new ImportResult(0, 0, List.of(), List.of());
            }

            org.apache.poi.ss.usermodel.Row header = sheet.getRow(firstRow);
            if (header == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "missing_header");
            }

            HeaderIndex hi = parseHeader(header);
            if (hi.typeCol < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "type header required");
            }
            if (hi.stemCol < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "stem header required");
            }
            if (hi.answerCol < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "correctAnswer header required");
            }

            int successCount = 0;
            List<ImportFailure> failures = new java.util.ArrayList<>();
            List<QuestionResponse> questions = new java.util.ArrayList<>();
            Set<String> seenRowKeys = new java.util.HashSet<>();

            for (int r = firstRow + 1; r <= lastRow; r++) {
                org.apache.poi.ss.usermodel.Row row = sheet.getRow(r);
                int rowNumber = r + 1;
                if (row == null) {
                    continue;
                }
                String rawType = getCellString(row, hi.typeCol).trim();
                String stem = getCellString(row, hi.stemCol).trim();
                String rawAnswer = getCellString(row, hi.answerCol).trim();

                String blankProbe = (rawType + stem + rawAnswer).trim();
                if (blankProbe.isEmpty() && allOptionCellsBlank(row, hi.optionCols)) {
                    continue;
                }

                try {
                    QuestionType type = parseType(rawType);
                    if (stem.isEmpty()) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "stem is required");
                    }

                    List<String> options = readOptions(row, hi.optionCols, type);
                    String analysis = hi.analysisCol >= 0 ? getCellString(row, hi.analysisCol).trim() : "";
                    int score = parseScore(hi.scoreCol >= 0 ? getCellString(row, hi.scoreCol).trim() : "");
                    String difficulty = normalizeDifficulty(hi.difficultyCol >= 0 ? getCellString(row, hi.difficultyCol).trim() : "");
                    String knowledgePoint = hi.knowledgePointCol >= 0 ? getCellString(row, hi.knowledgePointCol).trim() : "";
                    List<String> rowTags = hi.tagsCol >= 0 ? splitTags(getCellString(row, hi.tagsCol)) : List.of();
                    List<String> mergedTags = mergeTags(rowTags, extraTags);

                    String normalizedAnswer = normalizeAnswer(type, rawAnswer);

                    String rowKey = type.name() + "|" + stem;
                    if (!seenRowKeys.add(rowKey)) {
                        failures.add(new ImportFailure(rowNumber, "题干在导入文件中重复"));
                        continue;
                    }

                    Question created = questionService.create(new QuestionDraft(
                            bankId,
                            type,
                            stem,
                            options,
                            normalizedAnswer,
                            analysis,
                            score,
                            difficulty,
                            knowledgePoint,
                            mergedTags,
                            true
                    ));
                    questions.add(QuestionResponse.from(created));
                    successCount++;
                } catch (ResponseStatusException e) {
                    String reason = e.getReason() == null ? "invalid_row" : e.getReason();
                    failures.add(new ImportFailure(rowNumber, reason));
                } catch (Exception e) {
                    failures.add(new ImportFailure(rowNumber, "invalid_row"));
                }
            }

            return new ImportResult(successCount, failures.size(), failures, questions);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid_excel", e);
        }
    }

    private String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return String.valueOf(authentication.getPrincipal());
    }

    private HeaderIndex parseHeader(org.apache.poi.ss.usermodel.Row header) {
        int typeCol = -1;
        int stemCol = -1;
        int answerCol = -1;
        int analysisCol = -1;
        int scoreCol = -1;
        int difficultyCol = -1;
        int knowledgePointCol = -1;
        int tagsCol = -1;
        java.util.Map<String, Integer> optionCols = new java.util.HashMap<>();

        int lastCell = header.getLastCellNum();
        for (int c = header.getFirstCellNum(); c < lastCell; c++) {
            String raw = getCellString(header, c);
            if (raw == null) {
                continue;
            }
            String v = raw.trim();
            if (v.isEmpty()) {
                continue;
            }
            String label = v.trim().toLowerCase(Locale.ROOT);

            if (typeCol < 0 && (label.equals("题型") || label.equals("type"))) {
                typeCol = c;
                continue;
            }
            if (stemCol < 0 && (label.equals("题干") || label.equals("stem"))) {
                stemCol = c;
                continue;
            }
            if (answerCol < 0 && (label.equals("正确答案") || label.equals("答案") || label.equals("correctanswer") || label.equals("correct_answer"))) {
                answerCol = c;
                continue;
            }
            if (analysisCol < 0 && (label.equals("解析") || label.equals("analysis"))) {
                analysisCol = c;
                continue;
            }
            if (scoreCol < 0 && (label.equals("分值") || label.equals("score"))) {
                scoreCol = c;
                continue;
            }
            if (difficultyCol < 0 && (label.equals("难度") || label.equals("difficulty"))) {
                difficultyCol = c;
                continue;
            }
            if (knowledgePointCol < 0 && (label.equals("知识点") || label.equals("knowledgepoint") || label.equals("knowledge_point"))) {
                knowledgePointCol = c;
                continue;
            }
            if (tagsCol < 0 && (label.equals("标签") || label.equals("tags"))) {
                tagsCol = c;
                continue;
            }

            String optionLetter = parseOptionHeader(label);
            if (optionLetter != null && !optionCols.containsKey(optionLetter)) {
                optionCols.put(optionLetter, c);
            }
        }

        java.util.List<java.util.Map.Entry<String, Integer>> entries = new java.util.ArrayList<>(optionCols.entrySet());
        entries.sort(java.util.Comparator.comparing(java.util.Map.Entry::getKey));
        java.util.List<HeaderIndex.OptionCol> optionColsSorted = entries.stream()
                .map(e -> new HeaderIndex.OptionCol(e.getValue()))
                .collect(java.util.stream.Collectors.toList());

        return new HeaderIndex(
                typeCol,
                stemCol,
                answerCol,
                analysisCol,
                scoreCol,
                difficultyCol,
                knowledgePointCol,
                tagsCol,
                optionColsSorted
        );
    }

    private String parseOptionHeader(String lowerLabel) {
        if (lowerLabel == null) {
            return null;
        }
        String s = lowerLabel.trim();
        if (s.matches("^[a-h]$")) {
            return s.toUpperCase(Locale.ROOT);
        }
        if (s.matches("^选项[a-h]$")) {
            return s.substring(s.length() - 1).toUpperCase(Locale.ROOT);
        }
        if (s.matches("^option[a-h]$")) {
            return s.substring(s.length() - 1).toUpperCase(Locale.ROOT);
        }
        return null;
    }

    private boolean allOptionCellsBlank(org.apache.poi.ss.usermodel.Row row, List<HeaderIndex.OptionCol> optionCols) {
        if (optionCols == null || optionCols.isEmpty()) {
            return true;
        }
        for (HeaderIndex.OptionCol oc : optionCols) {
            String v = getCellString(row, oc.colIndex);
            if (v != null && !v.trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private List<String> readOptions(org.apache.poi.ss.usermodel.Row row, List<HeaderIndex.OptionCol> optionCols, QuestionType type) {
        if (type == QuestionType.TRUE_FALSE) {
            return List.of();
        }
        if (optionCols == null || optionCols.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "options header required");
        }

        List<String> options = new java.util.ArrayList<>();
        boolean ended = false;
        for (HeaderIndex.OptionCol oc : optionCols) {
            String v = getCellString(row, oc.colIndex).trim();
            if (v.isEmpty()) {
                ended = true;
                continue;
            }
            if (ended) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "options must be continuous");
            }
            options.add(v);
        }
        while (!options.isEmpty() && options.get(options.size() - 1).trim().isEmpty()) {
            options.remove(options.size() - 1);
        }
        if (options.size() < 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "options must have at least 2 items");
        }
        return options;
    }

    private QuestionType parseType(String raw) {
        String v = raw == null ? "" : raw.trim();
        if (v.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "type is required");
        }
        String upper = v.toUpperCase(Locale.ROOT);
        if (upper.equals("SINGLE_CHOICE") || v.equals("单选题") || v.equals("单选")) {
            return QuestionType.SINGLE_CHOICE;
        }
        if (upper.equals("MULTIPLE_CHOICE") || v.equals("多选题") || v.equals("多选")) {
            return QuestionType.MULTIPLE_CHOICE;
        }
        if (upper.equals("TRUE_FALSE") || upper.equals("TRUE/FALSE") || v.equals("判断题") || v.equals("判断")) {
            return QuestionType.TRUE_FALSE;
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unknown type");
    }

    private String normalizeAnswer(QuestionType type, String rawAnswer) {
        if (rawAnswer == null || rawAnswer.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "correctAnswer is required");
        }
        if (type == QuestionType.SINGLE_CHOICE) {
            String upper = rawAnswer.trim().toUpperCase(Locale.ROOT);
            if (upper.length() != 1 || upper.charAt(0) < 'A' || upper.charAt(0) > 'Z') {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "correctAnswer must be A-Z for SINGLE_CHOICE");
            }
            return upper;
        }
        if (type == QuestionType.MULTIPLE_CHOICE) {
            String s = rawAnswer.trim().toUpperCase(Locale.ROOT);
            String normalized = s.replace('，', ',')
                    .replace('；', ',')
                    .replace(';', ',')
                    .replace('|', ',')
                    .replace('、', ',');
            List<String> parts;
            if (normalized.contains(",")) {
                parts = java.util.Arrays.stream(normalized.split(","))
                        .map(String::trim)
                        .filter(x -> !x.isEmpty())
                        .collect(java.util.stream.Collectors.toList());
            } else if (normalized.matches("^[A-Z]+$")) {
                parts = new java.util.ArrayList<>();
                for (int i = 0; i < normalized.length(); i++) {
                    parts.add(String.valueOf(normalized.charAt(i)));
                }
            } else {
                parts = java.util.Arrays.stream(normalized.split("\\s+"))
                        .map(String::trim)
                        .filter(x -> !x.isEmpty())
                        .collect(java.util.stream.Collectors.toList());
            }
            java.util.LinkedHashSet<String> set = new java.util.LinkedHashSet<>();
            for (String p : parts) {
                if (p.length() == 1 && p.charAt(0) >= 'A' && p.charAt(0) <= 'Z') {
                    set.add(p);
                } else {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "correctAnswer must be A-Z for MULTIPLE_CHOICE");
                }
            }
            List<String> list = new java.util.ArrayList<>(set);
            list.sort(String::compareTo);
            return String.join(",", list);
        }
        if (type == QuestionType.TRUE_FALSE) {
            String v = rawAnswer.trim().toLowerCase(Locale.ROOT);
            if (v.equals("true") || v.equals("t") || v.equals("1") || v.equals("是") || v.equals("对") || v.equals("正确") || v.equals("√")) {
                return "true";
            }
            if (v.equals("false") || v.equals("f") || v.equals("0") || v.equals("否") || v.equals("错") || v.equals("错误") || v.equals("×") || v.equals("x")) {
                return "false";
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "correctAnswer must be true/false for TRUE_FALSE");
        }
        return rawAnswer.trim();
    }

    private int parseScore(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return 5;
        }
        try {
            int v = Integer.parseInt(raw.trim());
            return v <= 0 ? 5 : v;
        } catch (NumberFormatException e) {
            return 5;
        }
    }

    private String normalizeDifficulty(String raw) {
        String v = raw == null ? "" : raw.trim();
        return v.isEmpty() ? "0.5" : v;
    }

    private List<String> splitTags(String raw) {
        if (raw == null) {
            return List.of();
        }
        String normalized = raw.replace('，', ',')
                .replace('；', ',')
                .replace(';', ',')
                .replace('|', ',')
                .replace('、', ',');
        java.util.LinkedHashSet<String> set = new java.util.LinkedHashSet<>();
        for (String p : normalized.split(",")) {
            String v = p == null ? "" : p.trim();
            if (!v.isEmpty()) {
                set.add(v);
            }
        }
        return new java.util.ArrayList<>(set);
    }

    private List<String> mergeTags(List<String> a, List<String> b) {
        java.util.LinkedHashSet<String> set = new java.util.LinkedHashSet<>();
        if (a != null) {
            for (String x : a) {
                if (x != null && !x.trim().isEmpty()) {
                    set.add(x.trim());
                }
            }
        }
        if (b != null) {
            for (String x : b) {
                if (x != null && !x.trim().isEmpty()) {
                    set.add(x.trim());
                }
            }
        }
        return new java.util.ArrayList<>(set);
    }

    private String getCellString(org.apache.poi.ss.usermodel.Row row, int index) {
        if (row == null || index < 0) {
            return "";
        }
        org.apache.poi.ss.usermodel.Cell cell = row.getCell(index);
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

    public static class ImportResult {
        private final int successCount;
        private final int failedCount;
        private final List<ImportFailure> failures;
        private final List<QuestionResponse> questions;

        public ImportResult(int successCount, int failedCount, List<ImportFailure> failures, List<QuestionResponse> questions) {
            this.successCount = successCount;
            this.failedCount = failedCount;
            this.failures = failures;
            this.questions = questions;
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

        public List<QuestionResponse> getQuestions() {
            return questions;
        }
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

    private static class HeaderIndex {
        private final int typeCol;
        private final int stemCol;
        private final int answerCol;
        private final int analysisCol;
        private final int scoreCol;
        private final int difficultyCol;
        private final int knowledgePointCol;
        private final int tagsCol;
        private final List<OptionCol> optionCols;

        private HeaderIndex(
                int typeCol,
                int stemCol,
                int answerCol,
                int analysisCol,
                int scoreCol,
                int difficultyCol,
                int knowledgePointCol,
                int tagsCol,
                List<OptionCol> optionCols
        ) {
            this.typeCol = typeCol;
            this.stemCol = stemCol;
            this.answerCol = answerCol;
            this.analysisCol = analysisCol;
            this.scoreCol = scoreCol;
            this.difficultyCol = difficultyCol;
            this.knowledgePointCol = knowledgePointCol;
            this.tagsCol = tagsCol;
            this.optionCols = optionCols == null ? List.of() : optionCols;
        }

        private static class OptionCol {
            private final int colIndex;

            private OptionCol(int colIndex) {
                this.colIndex = colIndex;
            }
        }
    }

    public static class CreateOrUpdateQuestionRequest {
        private Long bankId;
        private QuestionType type;
        private String stem;
        private List<String> options;
        private List<String> tags;
        private String correctAnswer;
        private String analysis;
        private Integer score;
        private String difficulty;
        private String knowledgePoint;
        private Boolean enabled;

        public Long getBankId() {
            return bankId;
        }

        public void setBankId(Long bankId) {
            this.bankId = bankId;
        }

        public QuestionType getType() {
            return type;
        }

        public void setType(QuestionType type) {
            this.type = type;
        }

        public String getStem() {
            return stem;
        }

        public void setStem(String stem) {
            this.stem = stem;
        }

        public List<String> getOptions() {
            return options;
        }

        public void setOptions(List<String> options) {
            this.options = options;
        }

        public List<String> getTags() {
            return tags;
        }

        public void setTags(List<String> tags) {
            this.tags = tags;
        }

        public String getCorrectAnswer() {
            return correctAnswer;
        }

        public void setCorrectAnswer(String correctAnswer) {
            this.correctAnswer = correctAnswer;
        }

        public String getAnalysis() {
            return analysis;
        }

        public void setAnalysis(String analysis) {
            this.analysis = analysis;
        }

        public Integer getScore() {
            return score;
        }

        public void setScore(Integer score) {
            this.score = score;
        }

        public String getDifficulty() {
            return difficulty;
        }

        public void setDifficulty(String difficulty) {
            this.difficulty = difficulty;
        }

        public String getKnowledgePoint() {
            return knowledgePoint;
        }

        public void setKnowledgePoint(String knowledgePoint) {
            this.knowledgePoint = knowledgePoint;
        }

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }

        public QuestionDraft toDraft() {
            return new QuestionDraft(
                    bankId,
                    type,
                    stem,
                    options,
                    correctAnswer,
                    analysis,
                    score == null ? 0 : score,
                    difficulty,
                    knowledgePoint,
                    tags,
                    enabled == null || enabled
            );
        }
    }

    public static class QuestionResponse {
        private final long id;
        private final Long bankId;
        private final String type;
        private final String stem;
        private final List<String> options;
        private final List<String> tags;
        private final String correctAnswer;
        private final String analysis;
        private final int score;
        private final String difficulty;
        private final String knowledgePoint;
        private final boolean enabled;
        private final String createdAt;
        private final String updatedAt;

        public QuestionResponse(
                long id,
                Long bankId,
                String type,
                String stem,
                List<String> options,
                List<String> tags,
                String correctAnswer,
                String analysis,
                int score,
                String difficulty,
                String knowledgePoint,
                boolean enabled,
                String createdAt,
                String updatedAt
        ) {
            this.id = id;
            this.bankId = bankId;
            this.type = type;
            this.stem = stem;
            this.options = options;
            this.tags = tags;
            this.correctAnswer = correctAnswer;
            this.analysis = analysis;
            this.score = score;
            this.difficulty = difficulty;
            this.knowledgePoint = knowledgePoint;
            this.enabled = enabled;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
        }

        public static QuestionResponse from(Question question) {
            return new QuestionResponse(
                    question.getId(),
                    question.getBankId(),
                    question.getType().name(),
                    question.getStem(),
                    question.getOptions(),
                    question.getTags(),
                    question.getCorrectAnswer(),
                    question.getAnalysis(),
                    question.getScore(),
                    question.getDifficulty(),
                    question.getKnowledgePoint(),
                    question.isEnabled(),
                    question.getCreatedAt().toString(),
                    question.getUpdatedAt().toString()
            );
        }

        public long getId() {
            return id;
        }

        public Long getBankId() {
            return bankId;
        }

        public String getType() {
            return type;
        }

        public String getStem() {
            return stem;
        }

        public List<String> getOptions() {
            return options;
        }

        public List<String> getTags() {
            return tags;
        }

        public String getCorrectAnswer() {
            return correctAnswer;
        }

        public String getAnalysis() {
            return analysis;
        }

        public int getScore() {
            return score;
        }

        public String getDifficulty() {
            return difficulty;
        }

        public String getKnowledgePoint() {
            return knowledgePoint;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public String getCreatedAt() {
            return createdAt;
        }

        public String getUpdatedAt() {
            return updatedAt;
        }
    }
}
