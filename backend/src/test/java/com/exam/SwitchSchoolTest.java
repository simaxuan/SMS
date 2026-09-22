package com.exam;

import com.exam.dto.LoginRequest;
import com.exam.entity.Account;
import com.exam.entity.School;
import com.exam.repository.SchoolRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 三遗留处理（项 1）：全局超管登录校切换端点集成测试。
 * 覆盖：ALL 超管可切到具体校、可切回总览（NULL）；非 ALL 账号切换被 403 拒绝。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SwitchSchoolTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper om;
    @Autowired private SchoolRepository schoolRepository;

    private String body(MvcResult r) throws Exception {
        return new String(r.getResponse().getContentAsByteArray(), java.nio.charset.StandardCharsets.UTF_8);
    }

    private String login(String user, String pass, String role) throws Exception {
        LoginRequest req = new LoginRequest();
        req.setUsername(user);
        req.setPassword(pass);
        req.setRole(role);
        MvcResult r = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();
        return om.readTree(body(r)).at("/data/token").asText();
    }

    @Test
    void 全局超管可切换登录校并刷新会话() throws Exception {
        String t = login("admin", "admin123", Account.ROLE_TEACHER);
        School school = schoolRepository.findAll().get(0);
        Long sid = school.getId();

        // 切到具体校：返回 user.schoolId == 该校
        MvcResult r1 = mockMvc.perform(post("/api/auth/switch-school")
                        .header("X-Token", t)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"schoolId\":" + sid + "}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode n1 = om.readTree(body(r1));
        assertEquals(sid, n1.at("/data/user/schoolId").asLong());
        assertNotNull(n1.at("/data/token").asText());

        // 切回总览（NULL）：返回 user.schoolId 为 null
        MvcResult r2 = mockMvc.perform(post("/api/auth/switch-school")
                        .header("X-Token", t)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"schoolId\":null}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode n2 = om.readTree(body(r2));
        assertTrue(n2.at("/data/user/schoolId").isNull(), "切回总览后 schoolId 应为 null");
    }

    @Test
    void 非全局超管切换被拒() throws Exception {
        // parent1 非 SCOPE_ALL，调用切换端点应 403
        String p = login("parent1", "parent123", Account.ROLE_PARENT);
        mockMvc.perform(post("/api/auth/switch-school")
                        .header("X-Token", p)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"schoolId\":1}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 切换到不存在的学校被拒() throws Exception {
        String t = login("admin", "admin123", Account.ROLE_TEACHER);
        mockMvc.perform(post("/api/auth/switch-school")
                        .header("X-Token", t)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"schoolId\":999999}"))
                .andExpect(status().isNotFound());
    }
}
