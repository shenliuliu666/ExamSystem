package com.examsystem.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.examsystem.question.QuestionType;
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
class QuestionCrudTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void teacherCanCrudQuestions_studentCannot() throws Exception {
        mockMvc.perform(get("/api/teacher/questions"))
                .andExpect(status().isUnauthorized());

        String studentToken = loginAndExtractToken("student", "student123");
        mockMvc.perform(get("/api/teacher/questions").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());

        String teacherToken = loginAndExtractToken("teacher", "teacher123");

        TeacherQuestionController.CreateOrUpdateQuestionRequest create = new TeacherQuestionController.CreateOrUpdateQuestionRequest();
        create.setBankId(null);
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
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.type").value("SINGLE_CHOICE"))
                .andExpect(jsonPath("$.stem").value("1+1 等于几？"))
                .andReturn();

        long id = objectMapper.readTree(createdResult.getResponse().getContentAsByteArray()).get("id").asLong();

        mockMvc.perform(get("/api/teacher/questions/{id}", id).header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.correctAnswer").value("B"))
                .andExpect(jsonPath("$.options").isArray());

        mockMvc.perform(get("/api/teacher/questions?page=1&size=20").header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(id));

        TeacherQuestionController.CreateOrUpdateQuestionRequest update = new TeacherQuestionController.CreateOrUpdateQuestionRequest();
        update.setType(QuestionType.TRUE_FALSE);
        update.setStem("地球是圆的。");
        update.setCorrectAnswer("true");
        update.setAnalysis("常识题");
        update.setScore(2);
        update.setDifficulty("EASY");
        update.setKnowledgePoint("常识");
        update.setEnabled(true);

        mockMvc.perform(put("/api/teacher/questions/{id}", id)
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("TRUE_FALSE"))
                .andExpect(jsonPath("$.correctAnswer").value("true"));

        mockMvc.perform(delete("/api/teacher/questions/{id}", id).header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));

        mockMvc.perform(get("/api/teacher/questions/{id}", id).header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isNotFound());
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
