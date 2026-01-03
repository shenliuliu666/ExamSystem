package com.examsystem.controller;

import com.examsystem.attempt.AnswerRecord;
import com.examsystem.attempt.ExamAttempt;
import com.examsystem.attempt.ExamAttemptService;
import com.examsystem.attempt.QuestionSnapshot;
import com.examsystem.course.ClassRepository;
import com.examsystem.course.Classroom;
import com.examsystem.exam.ExamArrangement;
import com.examsystem.exam.ExamService;
import com.examsystem.exam.ExamSettings;
import com.examsystem.proctor.ExamProctorService;
import com.examsystem.proctor.ProctorEvent;
import com.examsystem.result.ExamResult;
import com.examsystem.result.ExamResultItem;
import com.examsystem.result.ExamResultService;
import com.examsystem.user.UserProfile;
import com.examsystem.user.UserProfileRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/student/exams")
public class StudentExamController {
    private final ExamService examService;
    private final ExamAttemptService attemptService;
    private final ExamResultService resultService;
    private final ClassRepository classRepository;
    private final ExamProctorService proctorService;
    private final UserProfileRepository userProfileRepository;

    public StudentExamController(
            ExamService examService,
            ExamAttemptService attemptService,
            ExamResultService resultService,
            ClassRepository classRepository,
            ExamProctorService proctorService,
            UserProfileRepository userProfileRepository
    ) {
        this.examService = examService;
        this.attemptService = attemptService;
        this.resultService = resultService;
        this.classRepository = classRepository;
        this.proctorService = proctorService;
        this.userProfileRepository = userProfileRepository;
    }

    @GetMapping
    public List<ExamResponse> list() {
        String username = currentUsername();
        List<Long> classIds = classRepository.listJoinedClasses(username).stream()
                .map(c -> c.getId())
                .collect(Collectors.toList());
        List<ExamArrangement> exams = examService.listByClassIds(classIds);
        List<ExamAttempt> myAttempts = attemptService.listByStudent(username);
        List<ExamResult> myResults = resultService.listByStudent(username);

        Map<Long, ExamAttempt> attemptMap = myAttempts.stream()
                .collect(Collectors.toMap(
                        ExamAttempt::getExamId,
                        Function.identity(),
                        (a, b) -> a.getId() > b.getId() ? a : b
                ));

        Map<Long, ExamResult> resultMap = myResults.stream()
                .collect(Collectors.toMap(
                        ExamResult::getExamId,
                        Function.identity(),
                        (a, b) -> a
                ));

        return exams.stream()
                .map(e -> ExamResponse.from(e, examService, classRepository, attemptMap.get(e.getId()), resultMap.get(e.getId())))
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable("id") long id) {
        String username = currentUsername();
        return examService.findById(id)
                .filter(e -> canAccessExam(username, e))
                .<ResponseEntity<?>>map(e -> ResponseEntity.ok(ExamResponse.from(e, examService, classRepository)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "not_found")));
    }

    @GetMapping("/{id}/result")
    public ResponseEntity<?> myResult(@PathVariable("id") long examId) {
        String username = currentUsername();
        ExamArrangement exam = examService.findById(examId).orElse(null);
        if (exam == null || !canAccessExam(username, exam)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "not_found"));
        }
        ExamResult result = resultService.listByExamId(examId).stream()
                .filter(r -> r.getStudentUsername().equals(username))
                .findFirst()
                .orElse(null);

        if (result == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "not_found"));
        }

        ExamAttempt attempt = attemptService.findById(result.getAttemptId()).orElse(null);
        if (attempt == null) {
            // Should not happen if data integrity is maintained
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "attempt_not_found"));
        }

        ExamSettings settings = examService.getSettingsOrDefault(examId);
        
        // Check if student can view result
        if (!settings.isAllowReviewPaper()) {
             // If paper review is not allowed, maybe we only show basic info or block entirely?
             // User requirement: "Allow students to view paper after exam"
             // If false, they shouldn't see questions/answers.
             // But maybe they can see score if showScore is true?
             // Let's assume if !allowReviewPaper, we return limited info (no items).
             if (settings.isShowScore()) {
                 return ResponseEntity.ok(StudentResultResponse.fromScoreOnly(result));
             } else {
                 return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "review_not_allowed"));
             }
        }

        StudentResultResponse response = StudentResultResponse.from(result, attempt);
        
        // Filter based on settings
        if (!settings.isShowScore()) {
            response.hideScore();
        }

        String showAnswersStrategy = settings.getShowAnswersStrategy();
        boolean showAnswers = false;
        if ("AFTER_SUBMISSION".equals(showAnswersStrategy)) {
            showAnswers = true;
        } else if ("AFTER_DEADLINE".equals(showAnswersStrategy)) {
            if (Instant.now().isAfter(exam.getEndAt())) {
                showAnswers = true;
            }
        }
        
        if (!showAnswers) {
            response.hideAnswers();
        }

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/result/ai")
    public ResponseEntity<?> aiExplain(@PathVariable("id") long examId, @RequestBody StudentAiExplainRequest request) {
        String username = currentUsername();
        ExamArrangement exam = examService.findById(examId).orElse(null);
        if (exam == null || !canAccessExam(username, exam)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "not_found"));
        }

        ExamSettings settings = examService.getSettingsOrDefault(examId);
        if (!settings.isAllowReviewPaper()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "review_not_allowed"));
        }

        String showAnswersStrategy = settings.getShowAnswersStrategy();
        boolean showAnswers = false;
        if ("AFTER_SUBMISSION".equals(showAnswersStrategy)) {
            showAnswers = true;
        } else if ("AFTER_DEADLINE".equals(showAnswersStrategy)) {
            if (Instant.now().isAfter(exam.getEndAt())) {
                showAnswers = true;
            }
        }
        if (!showAnswers) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "answers_not_available"));
        }

        if (request == null || request.getQuestionId() <= 0) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "questionId_required"));
        }

        ExamResult result = resultService.listByExamId(examId).stream()
                .filter(r -> r.getStudentUsername().equals(username))
                .findFirst()
                .orElse(null);
        if (result == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "not_found"));
        }

        ExamAttempt attempt = attemptService.findById(result.getAttemptId()).orElse(null);
        if (attempt == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "attempt_not_found"));
        }

        ExamResultItem resultItem = result.getItems().stream()
                .filter(i -> i.getQuestionId() == request.getQuestionId())
                .findFirst()
                .orElse(null);
        if (resultItem == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "question_not_found"));
        }

        QuestionSnapshot snapshot = attempt.getQuestions().stream()
                .filter(q -> q.getId() == request.getQuestionId())
                .findFirst()
                .orElse(null);

        String prompt = buildAiExplainPrompt(exam, resultItem, snapshot, request.getMessages());
        String content = callOllamaOnce(prompt);
        return ResponseEntity.ok(Map.of("content", content));
    }

    @PostMapping(path = "/{id}/result/ai/stream", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<StreamingResponseBody> aiExplainStream(
            @PathVariable("id") long examId,
            @RequestBody StudentAiExplainRequest request
    ) {
        String username = currentUsername();
        ExamArrangement exam = examService.findById(examId).orElse(null);
        if (exam == null || !canAccessExam(username, exam)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(MediaType.TEXT_PLAIN).body(outputStream -> {
                outputStream.write("not_found".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            });
        }

        ExamSettings settings = examService.getSettingsOrDefault(examId);
        if (!settings.isAllowReviewPaper()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).contentType(MediaType.TEXT_PLAIN).body(outputStream -> {
                outputStream.write("review_not_allowed".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            });
        }

        String showAnswersStrategy = settings.getShowAnswersStrategy();
        boolean showAnswers = false;
        if ("AFTER_SUBMISSION".equals(showAnswersStrategy)) {
            showAnswers = true;
        } else if ("AFTER_DEADLINE".equals(showAnswersStrategy)) {
            if (Instant.now().isAfter(exam.getEndAt())) {
                showAnswers = true;
            }
        }
        if (!showAnswers) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).contentType(MediaType.TEXT_PLAIN).body(outputStream -> {
                outputStream.write("answers_not_available".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            });
        }

        if (request == null || request.getQuestionId() <= 0) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(MediaType.TEXT_PLAIN).body(outputStream -> {
                outputStream.write("questionId_required".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            });
        }

        ExamResult result = resultService.listByExamId(examId).stream()
                .filter(r -> r.getStudentUsername().equals(username))
                .findFirst()
                .orElse(null);
        if (result == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(MediaType.TEXT_PLAIN).body(outputStream -> {
                outputStream.write("not_found".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            });
        }

        ExamAttempt attempt = attemptService.findById(result.getAttemptId()).orElse(null);
        if (attempt == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(MediaType.TEXT_PLAIN).body(outputStream -> {
                outputStream.write("attempt_not_found".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            });
        }

        ExamResultItem resultItem = result.getItems().stream()
                .filter(i -> i.getQuestionId() == request.getQuestionId())
                .findFirst()
                .orElse(null);
        if (resultItem == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(MediaType.TEXT_PLAIN).body(outputStream -> {
                outputStream.write("question_not_found".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            });
        }

        QuestionSnapshot snapshot = attempt.getQuestions().stream()
                .filter(q -> q.getId() == request.getQuestionId())
                .findFirst()
                .orElse(null);

        String prompt = buildAiExplainPrompt(exam, resultItem, snapshot, request.getMessages());
        StreamingResponseBody body = outputStream -> {
            java.nio.charset.Charset utf8 = java.nio.charset.StandardCharsets.UTF_8;
            try {
                callOllamaStream(prompt, chunk -> {
                    try {
                        outputStream.write(chunk.getBytes(utf8));
                        outputStream.flush();
                    } catch (java.io.IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            } catch (RuntimeException e) {
                Throwable cause = e.getCause();
                if (cause instanceof java.io.IOException) {
                    return;
                }
                throw e;
            }
        };
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .header("X-Accel-Buffering", "no")
                .body(body);
    }

    @PostMapping("/{id}/start")
    public AttemptStartResponse start(@PathVariable("id") long examId) {
        String username = currentUsername();
        ExamArrangement exam = examService.findById(examId)
                .filter(e -> canAccessExam(username, e))
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND, "not_found"));
        ExamAttempt attempt = attemptService.start(examId, username);
        return buildAttemptStartResponse(attempt, exam);
    }

    @PostMapping("/{id}/submit")
    public SubmitResponse submit(@PathVariable("id") long examId, @RequestBody SubmitExamRequest request) {
        ExamArrangement exam = examService.findById(examId).orElse(null);
        if (exam == null || !canAccessExam(currentUsername(), exam)) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND, "not_found");
        }
        if (request == null) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "request body is required");
        }
        if (request.getAttemptId() <= 0) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "attemptId is required");
        }
        List<AnswerRecord> answers = request.getAnswers() == null ? List.of() : request.getAnswers().stream()
                .map(a -> new AnswerRecord(a.getQuestionId(), a.getAnswer()))
                .collect(Collectors.toList());
        ExamAttempt submitted = attemptService.submit(examId, request.getAttemptId(), currentUsername(), answers);
        return SubmitResponse.from(submitted);
    }

    @PostMapping("/{id}/heartbeat")
    public ResponseEntity<?> heartbeat(@PathVariable("id") long examId, @RequestBody HeartbeatRequest request) {
        String username = currentUsername();
        ExamAttempt attempt = attemptService.findById(request.getAttemptId())
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND, "attempt_not_found"));
        if (attempt.getExamId() != examId || !attempt.getStudentUsername().equals(username)) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN, "attempt_mismatch");
        }
        Instant ts = request.getTs() != null ? request.getTs() : Instant.now();
        proctorService.recordHeartbeat(attempt.getId(), username, ts, Instant.now());
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    @PostMapping("/{id}/events")
    public ResponseEntity<?> events(@PathVariable("id") long examId, @RequestBody ProctorEventRequest request) {
        String username = currentUsername();
        ExamAttempt attempt = attemptService.findById(request.getAttemptId())
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND, "attempt_not_found"));
        if (attempt.getExamId() != examId || !attempt.getStudentUsername().equals(username)) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN, "attempt_mismatch");
        }
        proctorService.recordEvent(
                examId,
                attempt.getId(),
                username,
                request.getType(),
                request.getPayloadJson(),
                Instant.now()
        );
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    @GetMapping("/{id}/proctor/messages")
    public List<ProctorMessageResponse> pollMessages(
            @PathVariable("id") long examId,
            @RequestParam("attemptId") long attemptId,
            @RequestParam(value = "afterEventId", required = false) Long afterEventId
    ) {
        String username = currentUsername();
        ExamAttempt attempt = attemptService.findById(attemptId)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND, "attempt_not_found"));
        if (attempt.getExamId() != examId || !attempt.getStudentUsername().equals(username)) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN, "attempt_mismatch");
        }
        List<ProctorEvent> events = proctorService.listRecentEvents(examId, 100);
        return events.stream()
                .filter(e -> e.getAttemptId() == attemptId)
                .filter(e -> "TEACHER_REMIND".equals(e.getType()) || "TEACHER_FORCE_SUBMIT".equals(e.getType()))
                .filter(e -> afterEventId == null || e.getId() > afterEventId)
                .sorted(java.util.Comparator.comparingLong(ProctorEvent::getId))
                .map(e -> new ProctorMessageResponse(e.getId(), e.getType(), e.getPayloadJson(), e.getCreatedAt().toString()))
                .collect(Collectors.toList());
    }

    private String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return String.valueOf(authentication.getPrincipal());
    }

    private boolean canAccessExam(String username, ExamArrangement exam) {
        if (exam.getClassId() == null || exam.getClassId() <= 0) {
            return false;
        }
        return classRepository.isMember(exam.getClassId(), username);
    }

    private String buildAiExplainPrompt(
            ExamArrangement exam,
            ExamResultItem resultItem,
            QuestionSnapshot snapshot,
            List<AiChatMessage> messages
    ) {
        String stem = snapshot == null ? "" : String.valueOf(snapshot.getStem());
        List<String> options = snapshot == null || snapshot.getOptions() == null ? List.of() : snapshot.getOptions();

        String myAnswer = resultItem.getAnswer();
        if (myAnswer == null || myAnswer.isBlank()) {
            myAnswer = "(未作答)";
        }

        String correctAnswer = resultItem.getCorrectAnswer();
        if (correctAnswer == null || correctAnswer.isBlank()) {
            correctAnswer = "(空)";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("你是一名严谨且耐心的考试讲解老师，用中文回答。");
        sb.append("\n请对学生给出清晰、分点的讲解，包含：");
        sb.append("\n- 正确答案与结论");
        sb.append("\n- 关键考点");
        sb.append("\n- 推理/计算过程（简洁但完整）");
        sb.append("\n- 为什么我的答案对/错，并指出易错点");
        if (options != null && !options.isEmpty()) {
            sb.append("\n- 选项对比（为什么其他选项不对/不完整）");
        }
        sb.append("\n\n【试题信息】");
        sb.append("\n考试：").append(exam.getName());
        sb.append("\n题型：").append(typeLabel(resultItem.getQuestionType()));
        sb.append("\n题干：").append(stem);
        if (options != null && !options.isEmpty()) {
            sb.append("\n选项：");
            for (int i = 0; i < options.size(); i++) {
                char label = (char) ('A' + i);
                sb.append("\n").append(label).append(". ").append(options.get(i));
            }
        }
        sb.append("\n我的答案：").append(myAnswer);
        sb.append("\n正确答案：").append(correctAnswer);
        sb.append("\n是否正确：").append(resultItem.isCorrect() ? "正确" : "错误");
        sb.append("\n得分：").append(resultItem.getEarnedScore()).append("/").append(resultItem.getMaxScore());

        List<AiChatMessage> ms = messages == null ? List.of() : messages;
        if (!ms.isEmpty()) {
            int start = Math.max(0, ms.size() - 20);
            sb.append("\n\n【对话历史】");
            for (int i = start; i < ms.size(); i++) {
                AiChatMessage m = ms.get(i);
                if (m == null) {
                    continue;
                }
                String role = m.getRole();
                String content = m.getContent();
                if (content == null || content.isBlank()) {
                    continue;
                }
                String prefix = "assistant".equalsIgnoreCase(role) ? "助手" : "用户";
                sb.append("\n").append(prefix).append("：").append(content);
            }
        }
        sb.append("\n\n助手：");
        return sb.toString();
    }

    private String typeLabel(String type) {
        if ("SINGLE_CHOICE".equals(type)) return "单选题";
        if ("MULTIPLE_CHOICE".equals(type)) return "多选题";
        if ("TRUE_FALSE".equals(type)) return "判断题";
        return type == null ? "" : type;
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
                throw new java.io.IOException("empty_response_from_ollama");
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
                    throw new java.io.IOException("empty_response_field_from_ollama");
                }
                return content;
            }
        } catch (java.io.IOException e) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_GATEWAY, "ai_service_unavailable");
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void callOllamaStream(String prompt, java.util.function.Consumer<String> onChunk) throws java.io.IOException {
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
                    "stream", Boolean.TRUE
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
                throw new java.io.IOException("empty_response_from_ollama");
            }

            try (is; java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) {
                        continue;
                    }
                    com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(line);
                    String chunk = node.path("response").asText("");
                    if (chunk != null && !chunk.isEmpty()) {
                        onChunk.accept(chunk);
                    }
                    boolean done = node.path("done").asBoolean(false);
                    if (done) {
                        break;
                    }
                }
            }
        } catch (java.io.IOException e) {
            throw e;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    public static class StudentAiExplainRequest {
        private long questionId;
        private List<AiChatMessage> messages;

        public long getQuestionId() {
            return questionId;
        }

        public void setQuestionId(long questionId) {
            this.questionId = questionId;
        }

        public List<AiChatMessage> getMessages() {
            return messages;
        }

        public void setMessages(List<AiChatMessage> messages) {
            this.messages = messages;
        }
    }

    public static class AiChatMessage {
        private String role;
        private String content;

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }

    private AttemptStartResponse buildAttemptStartResponse(ExamAttempt attempt, ExamArrangement exam) {
        Instant effectiveEndAt = exam.getEndAt();
        try {
            ExamSettings settings = examService.getSettingsOrDefault(exam.getId());
            if (settings != null && settings.getDurationMinutes() != null && settings.getDurationMinutes() > 0) {
                Instant byDuration = attempt.getStartedAt().plusSeconds(settings.getDurationMinutes() * 60L);
                if (byDuration.isBefore(effectiveEndAt)) {
                    effectiveEndAt = byDuration;
                }
            }
        } catch (Exception ignored) {
        }

        String className = "";
        if (exam.getClassId() != null) {
            className = classRepository.findById(exam.getClassId()).map(Classroom::getName).orElse("");
        }

        String studentName = "";
        String studentNo = "";
        try {
            UserProfile profile = userProfileRepository.findByUsername(attempt.getStudentUsername()).orElse(null);
            if (profile != null) {
                studentName = profile.getFullName();
                studentNo = profile.getStudentNo();
            }
        } catch (Exception ignored) {}
        
        if (studentName == null) studentName = "";
        if (studentNo == null) studentNo = "";
        if (studentNo.isBlank()) studentNo = attempt.getStudentUsername();

        return new AttemptStartResponse(
                attempt.getId(),
                attempt.getExamId(),
                attempt.getPaperId(),
                attempt.getStatus().name(),
                attempt.getStartedAt().toString(),
                effectiveEndAt.toString(),
                attempt.getQuestions(),
                studentName,
                studentNo,
                className
        );
    }

    public static class ProctorMessageResponse {
        private final long id;
        private final String type;
        private final String message;
        private final String createdAt;

        public ProctorMessageResponse(long id, String type, String message, String createdAt) {
            this.id = id;
            this.type = type;
            this.message = message;
            this.createdAt = createdAt;
        }

        public long getId() {
            return id;
        }

        public String getType() {
            return type;
        }

        public String getMessage() {
            return message;
        }

        public String getCreatedAt() {
            return createdAt;
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
        // Student specific fields
        private final String myStatus; // NOT_STARTED, IN_PROGRESS, SUBMITTED, GRADED
        private final Long attemptId;
        private final boolean hasResult;

        public ExamResponse(
                long id,
                String name,
                long paperId,
                Long classId,
                String className,
                String startAt,
                String endAt,
                String status,
                String myStatus,
                Long attemptId,
                boolean hasResult
        ) {
            this.id = id;
            this.name = name;
            this.paperId = paperId;
            this.classId = classId;
            this.className = className;
            this.startAt = startAt;
            this.endAt = endAt;
            this.status = status;
            this.myStatus = myStatus;
            this.attemptId = attemptId;
            this.hasResult = hasResult;
        }

        public static ExamResponse from(ExamArrangement exam, ExamService examService) {
            return from(exam, examService, null, null, null);
        }

        public static ExamResponse from(ExamArrangement exam, ExamService examService, ClassRepository classRepository) {
            return from(exam, examService, classRepository, null, null);
        }

        public static ExamResponse from(
                ExamArrangement exam,
                ExamService examService,
                ExamAttempt attempt,
                ExamResult result
        ) {
            return from(exam, examService, null, attempt, result);
        }

        public static ExamResponse from(
                ExamArrangement exam,
                ExamService examService,
                ClassRepository classRepository,
                ExamAttempt attempt,
                ExamResult result
        ) {
            String status = examService.statusOf(exam, Instant.now()).name();
            String myStatus = "NOT_STARTED";
            Long attemptId = null;
            boolean hasResult = result != null;
            String className = "";
            try {
                if (classRepository != null && exam.getClassId() != null) {
                    className = classRepository.findById(exam.getClassId()).map(Classroom::getName).orElse("");
                }
            } catch (Exception ignored) {
            }

            if (attempt != null && attempt.getStatus() == com.examsystem.attempt.AttemptStatus.IN_PROGRESS) {
                myStatus = "IN_PROGRESS";
                attemptId = attempt.getId();
            } else if (result != null) {
                myStatus = "GRADED";
                attemptId = result.getAttemptId();
            } else if (attempt != null) {
                attemptId = attempt.getId();
                if (attempt.getStatus() == com.examsystem.attempt.AttemptStatus.SUBMITTED) {
                    myStatus = "SUBMITTED";
                } else {
                    myStatus = "IN_PROGRESS";
                }
            }

            return new ExamResponse(
                    exam.getId(),
                    exam.getName(),
                    exam.getPaperId(),
                    exam.getClassId(),
                    className,
                    exam.getStartAt().toString(),
                    exam.getEndAt().toString(),
                    status,
                    myStatus,
                    attemptId,
                    hasResult
            );
        }

        public long getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public long getPaperId() {
            return paperId;
        }

        public Long getClassId() {
            return classId;
        }

        public String getClassName() {
            return className;
        }

        public String getStartAt() {
            return startAt;
        }

        public String getEndAt() {
            return endAt;
        }

        public String getStatus() {
            return status;
        }

        public String getMyStatus() {
            return myStatus;
        }

        public Long getAttemptId() {
            return attemptId;
        }

        public boolean isHasResult() {
            return hasResult;
        }
    }

    public static class SubmitExamRequest {
        private long attemptId;
        private List<AnswerRequest> answers;

        public long getAttemptId() {
            return attemptId;
        }

        public void setAttemptId(long attemptId) {
            this.attemptId = attemptId;
        }

        public List<AnswerRequest> getAnswers() {
            return answers;
        }

        public void setAnswers(List<AnswerRequest> answers) {
            this.answers = answers;
        }
    }

    public static class HeartbeatRequest {
        private long attemptId;
        private Instant ts;

        public long getAttemptId() {
            return attemptId;
        }

        public void setAttemptId(long attemptId) {
            this.attemptId = attemptId;
        }

        public Instant getTs() {
            return ts;
        }

        public void setTs(Instant ts) {
            this.ts = ts;
        }
    }

    public static class ProctorEventRequest {
        private long attemptId;
        private String type;
        private String payloadJson;

        public long getAttemptId() {
            return attemptId;
        }

        public void setAttemptId(long attemptId) {
            this.attemptId = attemptId;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getPayloadJson() {
            return payloadJson;
        }

        public void setPayloadJson(String payloadJson) {
            this.payloadJson = payloadJson;
        }
    }

    public static class AnswerRequest {
        private long questionId;
        private String answer;

        public long getQuestionId() {
            return questionId;
        }

        public void setQuestionId(long questionId) {
            this.questionId = questionId;
        }

        public String getAnswer() {
            return answer;
        }

        public void setAnswer(String answer) {
            this.answer = answer;
        }
    }

    public static class AttemptStartResponse {
        private final long attemptId;
        private final long examId;
        private final long paperId;
        private final String status;
        private final String startedAt;
        private final String endAt;
        private final List<QuestionSnapshot> questions;
        private final String studentName;
        private final String studentNo;
        private final String className;

        public AttemptStartResponse(
                long attemptId,
                long examId,
                long paperId,
                String status,
                String startedAt,
                String endAt,
                List<QuestionSnapshot> questions,
                String studentName,
                String studentNo,
                String className
        ) {
            this.attemptId = attemptId;
            this.examId = examId;
            this.paperId = paperId;
            this.status = status;
            this.startedAt = startedAt;
            this.endAt = endAt;
            this.questions = questions;
            this.studentName = studentName;
            this.studentNo = studentNo;
            this.className = className;
        }

        public long getAttemptId() {
            return attemptId;
        }

        public long getExamId() {
            return examId;
        }

        public long getPaperId() {
            return paperId;
        }

        public String getStatus() {
            return status;
        }

        public String getStartedAt() {
            return startedAt;
        }

        public String getEndAt() {
            return endAt;
        }

        public List<QuestionSnapshot> getQuestions() {
            return questions;
        }

        public String getStudentName() {
            return studentName;
        }

        public String getStudentNo() {
            return studentNo;
        }

        public String getClassName() {
            return className;
        }
    }

    public static class SubmitResponse {
        private final long attemptId;
        private final String status;
        private final String submittedAt;

        public SubmitResponse(long attemptId, String status, String submittedAt) {
            this.attemptId = attemptId;
            this.status = status;
            this.submittedAt = submittedAt;
        }

        public static SubmitResponse from(ExamAttempt attempt) {
            return new SubmitResponse(
                    attempt.getId(),
                    attempt.getStatus().name(),
                    attempt.getSubmittedAt() == null ? null : attempt.getSubmittedAt().toString()
            );
        }

        public long getAttemptId() {
            return attemptId;
        }

        public String getStatus() {
            return status;
        }

        public String getSubmittedAt() {
            return submittedAt;
        }
    }

    public static class StudentResultResponse {
        private final long resultId;
        private final long examId;
        private final long attemptId;
        private int totalScore;
        private int maxScore;
        private List<StudentResultItem> items;
        private final String createdAt;

        public StudentResultResponse(
                long resultId,
                long examId,
                long attemptId,
                int totalScore,
                int maxScore,
                List<StudentResultItem> items,
                String createdAt
        ) {
            this.resultId = resultId;
            this.examId = examId;
            this.attemptId = attemptId;
            this.totalScore = totalScore;
            this.maxScore = maxScore;
            this.items = items;
            this.createdAt = createdAt;
        }

        public static StudentResultResponse from(ExamResult result, ExamAttempt attempt) {
            Map<Long, QuestionSnapshot> snapshotMap = attempt.getQuestions().stream()
                    .collect(Collectors.toMap(QuestionSnapshot::getId, Function.identity()));

            List<StudentResultItem> items = result.getItems().stream()
                    .map(item -> StudentResultItem.from(item, snapshotMap.get(item.getQuestionId())))
                    .collect(Collectors.toList());
            return new StudentResultResponse(
                    result.getId(),
                    result.getExamId(),
                    result.getAttemptId(),
                    result.getTotalScore(),
                    result.getMaxScore(),
                    items,
                    result.getCreatedAt().toString()
            );
        }

        public static StudentResultResponse fromScoreOnly(ExamResult result) {
            return new StudentResultResponse(
                    result.getId(),
                    result.getExamId(),
                    result.getAttemptId(),
                    result.getTotalScore(),
                    result.getMaxScore(),
                    List.of(),
                    result.getCreatedAt().toString()
            );
        }

        public void hideScore() {
            this.totalScore = -1;
            this.maxScore = -1;
        }

        public void hideAnswers() {
            if (this.items == null || this.items.isEmpty()) {
                return;
            }
            this.items = this.items.stream()
                    .map(i -> new StudentResultItem(
                            i.getQuestionId(),
                            i.getQuestionType(),
                            i.getAnswer(),
                            "",
                            i.getMaxScore(),
                            i.getEarnedScore(),
                            i.isCorrect(),
                            i.getStem(),
                            i.getOptions()
                    ))
                    .collect(Collectors.toList());
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

        public int getTotalScore() {
            return totalScore;
        }

        public int getMaxScore() {
            return maxScore;
        }

        public List<StudentResultItem> getItems() {
            return items;
        }

        public String getCreatedAt() {
            return createdAt;
        }
    }

    public static class StudentResultItem {
        private final long questionId;
        private final String questionType;
        private final String answer;
        private final String correctAnswer;
        private final int maxScore;
        private final int earnedScore;
        private final boolean correct;
        private final String stem;
        private final List<String> options;

        public StudentResultItem(
                long questionId,
                String questionType,
                String answer,
                String correctAnswer,
                int maxScore,
                int earnedScore,
                boolean correct,
                String stem,
                List<String> options
        ) {
            this.questionId = questionId;
            this.questionType = questionType;
            this.answer = answer;
            this.correctAnswer = correctAnswer;
            this.maxScore = maxScore;
            this.earnedScore = earnedScore;
            this.correct = correct;
            this.stem = stem;
            this.options = options;
        }

        public static StudentResultItem from(ExamResultItem item, QuestionSnapshot snapshot) {
            return new StudentResultItem(
                    item.getQuestionId(),
                    item.getQuestionType(),
                    item.getAnswer(),
                    item.getCorrectAnswer(),
                    item.getMaxScore(),
                    item.getEarnedScore(),
                    item.isCorrect(),
                    snapshot != null ? snapshot.getStem() : "",
                    snapshot != null ? snapshot.getOptions() : List.of()
            );
        }

        public long getQuestionId() {
            return questionId;
        }

        public String getQuestionType() {
            return questionType;
        }

        public String getAnswer() {
            return answer;
        }

        public String getCorrectAnswer() {
            return correctAnswer;
        }

        public int getMaxScore() {
            return maxScore;
        }

        public int getEarnedScore() {
            return earnedScore;
        }

        public boolean isCorrect() {
            return correct;
        }

        public String getStem() {
            return stem;
        }

        public List<String> getOptions() {
            return options;
        }
    }
}
