package com.examsystem.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class TeacherStudentImportTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void teacherCanImportStudentsFromExcelIntoClass() throws Exception {
        String teacherToken = loginAndExtractToken("teacher", "teacher123");

        long classId = createClass(teacherToken);

        byte[] excel = buildSampleExcel();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "students.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                excel
        );

        mockMvc.perform(multipart("/api/teacher/students/import")
                        .file(file)
                        .param("classId", Long.toString(classId))
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.successCount").value(2))
                .andExpect(jsonPath("$.failedCount").value(0));

        mockMvc.perform(get("/api/teacher/classes/{id}/members", classId)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("20230101"))
                .andExpect(jsonPath("$[0].studentNo").value("20230101"))
                .andExpect(jsonPath("$[0].fullName").value("张三"))
                .andExpect(jsonPath("$[1].username").value("20230102"))
                .andExpect(jsonPath("$[1].studentNo").value("20230102"))
                .andExpect(jsonPath("$[1].fullName").value("李四"));
    }

    private byte[] buildSampleExcel() throws Exception {
        Workbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("students");
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("学号");
        header.createCell(1).setCellValue("姓名");
        header.createCell(2).setCellValue("密码");

        Row r1 = sheet.createRow(1);
        r1.createCell(0).setCellValue("20230101");
        r1.createCell(1).setCellValue("张三");
        r1.createCell(2).setCellValue("");

        Row r2 = sheet.createRow(2);
        r2.createCell(0).setCellValue("20230102");
        r2.createCell(1).setCellValue("李四");
        r2.createCell(2).setCellValue("abc123456");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wb.write(out);
        wb.close();
        return out.toByteArray();
    }

    private long createClass(String teacherToken) throws Exception {
        TeacherClassController.CreateClassRequest createClass = new TeacherClassController.CreateClassRequest();
        createClass.setName("导入测试班级");

        MvcResult createdClassResult = mockMvc.perform(post("/api/teacher/classes")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(createClass)))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(createdClassResult.getResponse().getContentAsByteArray()).get("id").asLong();
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

