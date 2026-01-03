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
class PaperExamFlowTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void teacherCanCreatePaperAndExam_studentCanListExams() throws Exception {
        mockMvc.perform(get("/api/teacher/papers"))
                .andExpect(status().isUnauthorized());

        String studentToken = loginAndExtractToken("student", "student123");
        mockMvc.perform(get("/api/teacher/papers").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());

        String teacherToken = loginAndExtractToken("teacher", "teacher123");

        long classId = createClassAndJoin(teacherToken, studentToken);

        long questionId = createQuestion(teacherToken);

        TeacherPaperController.CreateOrUpdatePaperRequest createPaper = new TeacherPaperController.CreateOrUpdatePaperRequest();
        createPaper.setName("第一套试卷");
        createPaper.setQuestionIds(List.of(questionId));

        MvcResult createdPaperResult = mockMvc.perform(post("/api/teacher/papers")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(createPaper)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("第一套试卷"))
                .andExpect(jsonPath("$.questionIds[0]").value((int) questionId))
                .andReturn();

        long paperId = objectMapper.readTree(createdPaperResult.getResponse().getContentAsByteArray()).get("id").asLong();

        TeacherExamController.CreateExamRequest createExam = new TeacherExamController.CreateExamRequest();
        createExam.setName("第一次考试");
        createExam.setPaperId(paperId);
        createExam.setClassId(classId);
        createExam.setStartAt(Instant.now().plusSeconds(3600));
        createExam.setEndAt(Instant.now().plusSeconds(7200));

        MvcResult createdExamResult = mockMvc.perform(post("/api/teacher/exams")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(createExam)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.paperId").value((int) paperId))
                .andExpect(jsonPath("$.status").value("NOT_STARTED"))
                .andReturn();

        long examId = objectMapper.readTree(createdExamResult.getResponse().getContentAsByteArray()).get("id").asLong();

        mockMvc.perform(get("/api/student/exams").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value((int) examId))
                .andExpect(jsonPath("$[0].paperId").value((int) paperId))
                .andExpect(jsonPath("$[0].status").value("NOT_STARTED"));

        mockMvc.perform(get("/api/student/exams/{id}", examId).header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((int) examId))
                .andExpect(jsonPath("$.paperId").value((int) paperId));
    }

    private long createQuestion(String teacherToken) throws Exception {
        TeacherQuestionController.CreateOrUpdateQuestionRequest create = new TeacherQuestionController.CreateOrUpdateQuestionRequest();
        create.setType(QuestionType.SINGLE_CHOICE);
        create.setStem("2+2 等于几？");
        create.setOptions(List.of("1", "2", "3", "4"));
        create.setCorrectAnswer("D");
        create.setAnalysis("2+2=4");
        create.setScore(5);
        create.setDifficulty("EASY");
        create.setKnowledgePoint("基础加法");
        create.setEnabled(true);

        MvcResult createdResult = mockMvc.perform(post("/api/teacher/questions")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(create)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andReturn();

        return objectMapper.readTree(createdResult.getResponse().getContentAsByteArray()).get("id").asLong();
    }

    private long createClassAndJoin(String teacherToken, String studentToken) throws Exception {
        TeacherClassController.CreateClassRequest createClass = new TeacherClassController.CreateClassRequest();
        createClass.setName("纸质考试班级");

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
