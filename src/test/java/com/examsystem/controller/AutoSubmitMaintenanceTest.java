package com.examsystem.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.examsystem.exam.ExamMaintenanceService;
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
class AutoSubmitMaintenanceTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ExamMaintenanceService maintenanceService;

    @Test
    void endedExamAutoSubmitsInProgressAttempt_andCreatesResult() throws Exception {
        String teacherToken = loginAndExtractToken("teacher", "teacher123");
        String studentToken = loginAndExtractToken("student", "student123");

        long classId = createClassAndJoin(teacherToken, studentToken);
        long questionId = createQuestion(teacherToken);
        long paperId = createPaper(teacherToken, questionId);

        Instant now = Instant.now();
        Instant startAt = now.minusSeconds(60);
        Instant endAt = now.plusSeconds(60);
        long examId = createExam(teacherToken, paperId, classId, startAt, endAt);

        mockMvc.perform(post("/api/student/exams/{id}/start", examId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attemptId").isNumber());

        maintenanceService.runOnce(endAt.plusSeconds(1));

        mockMvc.perform(get("/api/student/exams/{id}/result", examId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.examId").value((int) examId))
                .andExpect(jsonPath("$.totalScore").value(0))
                .andExpect(jsonPath("$.maxScore").value(5));
    }

    private long createQuestion(String teacherToken) throws Exception {
        TeacherQuestionController.CreateOrUpdateQuestionRequest create = new TeacherQuestionController.CreateOrUpdateQuestionRequest();
        create.setType(QuestionType.SINGLE_CHOICE);
        create.setStem("1+2 等于几？");
        create.setOptions(List.of("1", "2", "3", "4"));
        create.setCorrectAnswer("C");
        create.setAnalysis("1+2=3");
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
        createPaper.setName("自动交卷试卷");
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
        createExam.setName("自动交卷考试");
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
        createClass.setName("自动交卷班级");

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
