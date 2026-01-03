package com.examsystem.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.examsystem.question.QuestionType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class AutoGradingFlowTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void submitGeneratesResult_studentAndTeacherCanQuery() throws Exception {
        String teacherToken = loginAndExtractToken("teacher", "teacher123");
        String studentToken = loginAndExtractToken("student", "student123");

        long classId = createClassAndJoin(teacherToken, studentToken);
        long questionId = createQuestion(teacherToken);
        long paperId = createPaper(teacherToken, questionId);

        long examId = createExam(
                teacherToken,
                paperId,
                classId,
                Instant.now().minusSeconds(10),
                Instant.now().plusSeconds(600)
        );

        MvcResult started = mockMvc.perform(post("/api/student/exams/{id}/start", examId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attemptId").isNumber())
                .andReturn();

        long attemptId = objectMapper.readTree(started.getResponse().getContentAsByteArray()).get("attemptId").asLong();

        StudentExamController.SubmitExamRequest submit = new StudentExamController.SubmitExamRequest();
        submit.setAttemptId(attemptId);
        StudentExamController.AnswerRequest answer = new StudentExamController.AnswerRequest();
        answer.setQuestionId(questionId);
        answer.setAnswer("B");
        submit.setAnswers(List.of(answer));

        mockMvc.perform(post("/api/student/exams/{id}/submit", examId)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(submit)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));

        mockMvc.perform(get("/api/student/exams/{id}/result", examId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.examId").value((int) examId))
                .andExpect(jsonPath("$.attemptId").value((int) attemptId))
                .andExpect(jsonPath("$.totalScore").value(5))
                .andExpect(jsonPath("$.maxScore").value(5))
                .andExpect(jsonPath("$.items[0].questionId").value((int) questionId))
                .andExpect(jsonPath("$.items[0].correct").value(true))
                .andExpect(jsonPath("$.items[0].earnedScore").value(5));

        mockMvc.perform(get("/api/teacher/exams/{id}/results", examId)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].examId").value((int) examId))
                .andExpect(jsonPath("$[0].attemptId").value((int) attemptId))
                .andExpect(jsonPath("$[0].studentUsername").value("student"))
                .andExpect(jsonPath("$[0].totalScore").value(5));
    }

    private long createQuestion(String teacherToken) throws Exception {
        TeacherQuestionController.CreateOrUpdateQuestionRequest create = new TeacherQuestionController.CreateOrUpdateQuestionRequest();
        create.setType(QuestionType.SINGLE_CHOICE);
        create.setStem("1+1 等于几？");
        create.setOptions(List.of("1", "2", "3", "4"));
        create.setCorrectAnswer("B");
        create.setAnalysis("1+1=2");
        create.setScore(5);
        create.setDifficulty("EASY");
        create.setKnowledgePoint("基础加法");
        create.setEnabled(true);

        MvcResult createdResult = mockMvc.perform(post("/api/teacher/questions")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(create)))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(createdResult.getResponse().getContentAsByteArray()).get("id").asLong();
    }

    private long createPaper(String teacherToken, long questionId) throws Exception {
        TeacherPaperController.CreateOrUpdatePaperRequest createPaper = new TeacherPaperController.CreateOrUpdatePaperRequest();
        createPaper.setName("自动阅卷试卷");
        createPaper.setQuestionIds(List.of(questionId));

        MvcResult createdPaperResult = mockMvc.perform(post("/api/teacher/papers")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(createPaper)))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(createdPaperResult.getResponse().getContentAsByteArray()).get("id").asLong();
    }

    private long createExam(String teacherToken, long paperId, long classId, Instant startAt, Instant endAt) throws Exception {
        TeacherExamController.CreateExamRequest createExam = new TeacherExamController.CreateExamRequest();
        createExam.setName("自动阅卷考试");
        createExam.setPaperId(paperId);
        createExam.setClassId(classId);
        createExam.setStartAt(startAt);
        createExam.setEndAt(endAt);

        MvcResult createdExamResult = mockMvc.perform(post("/api/teacher/exams")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(createExam)))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(createdExamResult.getResponse().getContentAsByteArray()).get("id").asLong();
    }

    private long createClassAndJoin(String teacherToken, String studentToken) throws Exception {
        TeacherClassController.CreateClassRequest createClass = new TeacherClassController.CreateClassRequest();
        createClass.setName("自动阅卷班级");

        MvcResult createdClassResult = mockMvc.perform(post("/api/teacher/classes")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(createClass)))
                .andExpect(status().isCreated())
                .andReturn();

        long classId = objectMapper.readTree(createdClassResult.getResponse().getContentAsByteArray()).get("id").asLong();
        String inviteCode = objectMapper.readTree(createdClassResult.getResponse().getContentAsByteArray()).get("inviteCode").asText();

        StudentClassController.JoinClassRequest joinRequest = new StudentClassController.JoinClassRequest();
        joinRequest.setInviteCode(inviteCode);
        joinRequest.setStudentNo("20230001");
        joinRequest.setFullName("测试学生");

        mockMvc.perform(post("/api/student/classes/join")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(joinRequest)))
                .andExpect(status().isOk());

        return classId;
    }

    private String loginAndExtractToken(String username, String password) throws Exception {
        AuthController.LoginRequest request = new AuthController.LoginRequest();
        request.setUsername(username);
        request.setPassword(password);

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsByteArray()).get("token").asText();
    }
}
