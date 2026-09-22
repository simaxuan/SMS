package com.exam;

import com.exam.dto.LoginRequest;
import com.exam.dto.RegisterRequest;
import com.exam.entity.Account;
import com.exam.repository.StudentRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 优化落地新增能力的集成测试：
 * 系统设置(排名开关)、排名、班级对比、家长自定义小测、家长绑定关系。
 */
@SpringBootTest
@AutoConfigureMockMvc
class SettingsAndAnalysisTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper om;
    @Autowired private StudentRepository studentRepository;

    private String json(Object o) throws Exception {
        return om.writeValueAsString(o);
    }

    /** 以 UTF-8 解码响应体，避免 getContentAsString() 默认 ISO-8859-1 导致中文乱码。 */
    private String body(MvcResult r) throws Exception {
        return new String(r.getResponse().getContentAsByteArray(), java.nio.charset.StandardCharsets.UTF_8);
    }

    private String login(String user, String pass, String role) throws Exception {
        LoginRequest req = new LoginRequest();
        req.setUsername(user);
        req.setPassword(pass);
        req.setRole(role);
        MvcResult r = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(json(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        return om.readTree(body(r)).at("/data/token").asText();
    }

    private long firstExamId(String token) throws Exception {
        return om.readTree(body(mockMvc.perform(get("/api/exams").header("X-Token", token))
                .andExpect(status().isOk()).andReturn())).path("data").get(0).path("id").asLong();
    }

    private long firstCourseId(String token) throws Exception {
        return om.readTree(body(mockMvc.perform(get("/api/courses").header("X-Token", token))
                .andExpect(status().isOk()).andReturn())).path("data").get(0).path("id").asLong();
    }

    @Test
    void 老师可更新排名开关并读取() throws Exception {
        String t = login("admin", "admin123", Account.ROLE_TEACHER);
        // 先置 false
        mockMvc.perform(put("/api/settings/parent_rank_visible")
                        .header("X-Token", t).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("value", false))))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/settings").header("X-Token", t))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parent_rank_visible").value(false));
        // 恢复 true，避免影响其他用例
        mockMvc.perform(put("/api/settings/parent_rank_visible")
                        .header("X-Token", t).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("value", true))))
                .andExpect(status().isOk());
    }

    @Test
    void 家长不可修改设置返回403() throws Exception {
        String p = login("parent1", "parent123", Account.ROLE_PARENT);
        mockMvc.perform(put("/api/settings/parent_rank_visible")
                        .header("X-Token", p).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("value", false))))
                .andExpect(status().isForbidden());
        // 但可读
        mockMvc.perform(get("/api/settings").header("X-Token", p))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parent_rank_visible").isNotEmpty());
    }

    @Test
    void 老师排名接口返回名次与总量() throws Exception {
        String t = login("admin", "admin123", Account.ROLE_TEACHER);
        long eid = firstExamId(t);
        long cid = firstCourseId(t);
        mockMvc.perform(get("/api/statistics/rank").header("X-Token", t)
                        .param("examId", String.valueOf(eid))
                        .param("courseId", String.valueOf(cid)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rows").isArray())
                .andExpect(jsonPath("$.data.total").isNumber());
    }

    @Test
    void 班级对比接口返回各班级聚合() throws Exception {
        String t = login("admin", "admin123", Account.ROLE_TEACHER);
        long eid = firstExamId(t);
        long cid = firstCourseId(t);
        mockMvc.perform(get("/api/statistics/comparison").header("X-Token", t)
                        .param("examId", String.valueOf(eid))
                        .param("courseId", String.valueOf(cid)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.classes").isArray())
                .andExpect(jsonPath("$.data.overall.total").isNumber());
    }

    @Test
    void 家长可创建并列出自定义小测() throws Exception {
        String p = login("parent1", "parent123", Account.ROLE_PARENT);
        String name = "数学小测_" + System.nanoTime();
        mockMvc.perform(post("/api/exams/custom").header("X-Token", p)
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("name", name))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.source").value("custom"));
        mockMvc.perform(get("/api/exams/custom").header("X-Token", p))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.name=='" + name + "')]").exists());
    }

    @Test
    void 家长绑定带关系落库并回读() throws Exception {
        // 老师取一个学生（按 id 倒序，首页第一条为种子 demo 学生，父手机号 13800000001）
        String t = login("admin", "admin123", Account.ROLE_TEACHER);
        String stuBody = body(mockMvc.perform(get("/api/students").header("X-Token", t)
                        .param("page", "0").param("size", "1"))
                .andExpect(status().isOk()).andReturn());
        String studentNo = om.readTree(stuBody).path("data").path("content").get(0).path("studentNo").asText();
        String name = om.readTree(stuBody).path("data").path("content").get(0).path("name").asText();
        String fatherPhone = om.readTree(stuBody).path("data").path("content").get(0).path("fatherPhone").asText();

        // 新家长（手机号须与种子学生父亲手机号一致，满足四要素契约）
        String uname = "p_bind_" + System.nanoTime();
        RegisterRequest rr = new RegisterRequest();
        rr.setUsername(uname);
        rr.setPassword("pass123");
        rr.setRole(Account.ROLE_PARENT);
        rr.setNickname("绑定测试");
        rr.setPhone(fatherPhone);
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(json(rr)))
                .andExpect(status().isOk());
        String p = login(uname, "pass123", Account.ROLE_PARENT);

        // 四要素绑定：学号+姓名+手机号+身份证后8位(+密码)；身份证末8位为 03071234（种子固定）
        mockMvc.perform(post("/api/parent/bind").header("X-Token", p)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "studentNo", studentNo,
                                "name", name,
                                "phone", fatherPhone,
                                "idCardLast8", "03071234",
                                "relation", "father",
                                "password", "03071234"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.relation").value("father"));

        mockMvc.perform(get("/api/parent/binds").header("X-Token", p))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].relation").value("father"));
    }

    @Test
    void 家长偏好读写与默认值() throws Exception {
        String p = login("parent1", "parent123", Account.ROLE_PARENT);
        mockMvc.perform(get("/api/parent/preference").header("X-Token", p))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.classScope").value(true))
                .andExpect(jsonPath("$.data.showTies").value(true));
        // 改为全校 + 唯一顺延
        mockMvc.perform(put("/api/parent/preference").header("X-Token", p)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("classScope", false, "showTies", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.classScope").value(false))
                .andExpect(jsonPath("$.data.showTies").value(false));
        // 恢复默认，避免影响其他用例
        mockMvc.perform(put("/api/parent/preference").header("X-Token", p)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("classScope", true, "showTies", true))))
                .andExpect(status().isOk());
    }

    @Test
    void 排名接口支持班内范围与并列开关() throws Exception {
        String t = login("admin", "admin123", Account.ROLE_TEACHER);
        long eid = firstExamId(t);
        long cid = firstCourseId(t);
        mockMvc.perform(get("/api/statistics/rank").header("X-Token", t)
                        .param("examId", String.valueOf(eid))
                        .param("courseId", String.valueOf(cid))
                        .param("classScope", "true")
                        .param("showTies", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rows").isArray())
                .andExpect(jsonPath("$.data.showTies").value(false))
                .andExpect(jsonPath("$.data.classScope").value(false));
    }

    @Test
    void 学期对比接口返回班级聚合() throws Exception {
        String t = login("admin", "admin123", Account.ROLE_TEACHER);
        long cid = firstCourseId(t);
        String sem = "2026春";
        mockMvc.perform(get("/api/statistics/comparison").header("X-Token", t)
                        .param("semester", sem)
                        .param("courseId", String.valueOf(cid)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.examId").isEmpty())
                .andExpect(jsonPath("$.data.semester").value(sem))
                .andExpect(jsonPath("$.data.classes").isArray());
    }

    @Test
    void 学期列表接口返回全部学期() throws Exception {
        String t = login("admin", "admin123", Account.ROLE_TEACHER);
        mockMvc.perform(get("/api/statistics/semesters").header("X-Token", t))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void 家长全校排名名次与教师端一致_P1回归() throws Exception {
        String t = login("admin", "admin123", Account.ROLE_TEACHER);
        long eid = firstExamId(t);
        long cid = firstCourseId(t);
        // 教师端全校排名（含全部学生名次），取第一名某学生做绑定目标
        JsonNode teacherRows = om.readTree(body(mockMvc.perform(get("/api/statistics/rank")
                .header("X-Token", t)
                .param("examId", String.valueOf(eid))
                .param("courseId", String.valueOf(cid)))
                .andExpect(status().isOk()).andReturn())).path("data").path("rows");
        JsonNode target = teacherRows.get(0);
        String tsn = target.path("studentNo").asText();
        String tname = target.path("name").asText();
        int teacherRankNo = target.path("rank").asInt();

        // 取该目标学生的父亲手机号（满足四要素契约：账号手机=学生父/母手机=绑定手机）
        String tStuBody = body(mockMvc.perform(get("/api/students").header("X-Token", t)
                        .param("keyword", tsn).param("page", "0").param("size", "1"))
                .andExpect(status().isOk()).andReturn());
        String fatherPhone = om.readTree(tStuBody).path("data").path("content").get(0).path("fatherPhone").asText();

        // 新家长绑定该有成绩的学生（手机号与种子学生父手机号一致）
        String uname = "p_rank_" + System.nanoTime();
        RegisterRequest rr = new RegisterRequest();
        rr.setUsername(uname);
        rr.setPassword("pass123");
        rr.setRole(Account.ROLE_PARENT);
        rr.setNickname("排名回归测试");
        rr.setPhone(fatherPhone);
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(json(rr)))
                .andExpect(status().isOk());
        String p = login(uname, "pass123", Account.ROLE_PARENT);
        mockMvc.perform(post("/api/parent/bind").header("X-Token", p)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "studentNo", tsn,
                                "name", tname,
                                "phone", fatherPhone,
                                "idCardLast8", "03071234",
                                "relation", "father",
                                "password", "03071234"))))
                .andExpect(status().isOk());

        // 家长「全校范围」排名
        JsonNode parentRows = om.readTree(body(mockMvc.perform(get("/api/statistics/rank")
                .header("X-Token", p)
                .param("examId", String.valueOf(eid))
                .param("courseId", String.valueOf(cid))
                .param("classScope", "false")
                .param("showTies", "true"))
                .andExpect(status().isOk()).andReturn())).path("data").path("rows");
        int got = -1;
        for (JsonNode r : parentRows) {
            if (r.path("studentNo").asText().equals(tsn)) {
                got = r.path("rank").asInt();
            }
        }
        assertEquals(teacherRankNo, got, "家长全校排名应等于教师端全校名次（而非绑定子集内内部名次）");
    }

    @Test
    void 老师可查看学生个人趋势() throws Exception {
        String t = login("admin", "admin123", Account.ROLE_TEACHER);
        long eid = firstExamId(t);
        long cid = firstCourseId(t);
        // 取教师排名中「必有成绩」的学生，保证趋势非空
        JsonNode rows = om.readTree(body(mockMvc.perform(get("/api/statistics/rank")
                .header("X-Token", t)
                .param("examId", String.valueOf(eid))
                .param("courseId", String.valueOf(cid)))
                .andExpect(status().isOk()).andReturn())).path("data").path("rows");
        long sid = rows.get(0).path("studentId").asLong();
        mockMvc.perform(get("/api/statistics/student-trend").header("X-Token", t)
                .param("studentId", String.valueOf(sid)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.trend").isArray())
                .andExpect(jsonPath("$.data.trend[0].examName").isNotEmpty())
                .andExpect(jsonPath("$.data.trend[0].courses").isArray());
    }
}
