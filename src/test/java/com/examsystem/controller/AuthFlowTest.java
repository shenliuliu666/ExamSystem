package com.examsystem.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void protectsStudentAndTeacherEndpoints() throws Exception {
        mockMvc.perform(get("/api/student/ping"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/teacher/ping"))
                .andExpect(status().isUnauthorized());

        String studentToken = loginAndExtractToken("student", "student123");
        mockMvc.perform(get("/api/student/ping").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));

        mockMvc.perform(get("/api/teacher/ping").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());

        String teacherToken = loginAndExtractToken("teacher", "teacher123");
        mockMvc.perform(get("/api/teacher/ping").header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void exposesMeEndpointForAuthenticatedUser() throws Exception {
        String token = loginAndExtractToken("student", "student123");
        mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("student"))
                .andExpect(jsonPath("$.roles").isArray());
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

