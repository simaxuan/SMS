package com.exam;

import com.exam.common.R;
import com.exam.dto.LoginRequest;
import com.exam.dto.RegisterRequest;
import com.exam.entity.Account;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 控制器/鉴权层 MockMvc 集成测试：登录注册、角色限制、
 * 未登录 401、HTTP 状态映射（400/401/403/404）。
 * 使用 H2 内存库 + DataSeeder 播种（含 admin 老师、parent1 家长账号）。
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper om;

    private String json(Object o) throws Exception {
        return om.writeValueAsString(o);
    }

    private LoginRequest loginReq(String u, String p, String role) {
        LoginRequest r = new LoginRequest();
        r.setUsername(u);
        r.setPassword(p);
        r.setRole(role);
        return r;
    }

    private RegisterRequest regReq(String u, String p, String role) {
        RegisterRequest r = new RegisterRequest();
        r.setUsername(u);
        r.setPassword(p);
        r.setRole(role);
        r.setNickname("测试");
        return r;
    }

    @Test
    void 未登录访问业务接口返回401() throws Exception {
        mockMvc.perform(get("/api/grades"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void admin老师可登录并获取me() throws Exception {
        String body = json(loginReq("admin", "admin123", Account.ROLE_TEACHER));
        MvcResult r = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andReturn();
        // 取 token
        String resp = r.getResponse().getContentAsString();
        String token = om.readTree(resp).at("/data/token").asText();
        // me
        mockMvc.perform(get("/api/auth/me").header("X-Token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("admin"));
    }

    @Test
    void 角色不匹配登录被拒() throws Exception {
        String body = json(loginReq("admin", "admin123", Account.ROLE_PARENT));
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 家长可自助注册() throws Exception {
        String uname = "p_reg_" + System.nanoTime();
        String body = json(regReq(uname, "pass123", Account.ROLE_PARENT));
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value(Account.ROLE_PARENT));
    }

    @Test
    void 不允许自助注册老师() throws Exception {
        String uname = "t_reg_" + System.nanoTime();
        String body = json(regReq(uname, "pass123", Account.ROLE_TEACHER));
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 密码过短校验失败() throws Exception {
        String uname = "short_" + System.nanoTime();
        String body = json(regReq(uname, "123", Account.ROLE_PARENT));
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 无效token访问返回401() throws Exception {
        mockMvc.perform(get("/api/classes").header("X-Token", "not-a-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 合法token跨源写操作被CSRF拒绝403() throws Exception {
        // 登录拿 token（同源请求，合法）
        String body = json(loginReq("admin", "admin123", Account.ROLE_TEACHER));
        MvcResult r = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn();
        String token = om.readTree(r.getResponse().getContentAsString()).at("/data/token").asText();
        // 携带合法 token，但 Origin 为跨源攻击站点 → AuthInterceptor 应 403 拦截写操作
        mockMvc.perform(post("/api/grades")
                        .header("X-Token", token)
                        .header("Origin", "https://evil.example.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 响应携带安全响应头() throws Exception {
        // 登录成功响应上断言全局安全响应头存在（CSP / nosniff / X-Frame-Options）
        String body = json(loginReq("admin", "admin123", Account.ROLE_TEACHER));
        MvcResult r = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy", org.hamcrest.Matchers.containsString("default-src 'self'")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andReturn();
    }

    @Test
    void 已存在数据重复写入返回409冲突() throws Exception {
        // 用 DataSeeder 已播种的真实数据：admin 老师重复录入同 (student,exam,course) 应 409
        String body = json(loginReq("admin", "admin123", Account.ROLE_TEACHER));
        MvcResult r = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn();
        String token = om.readTree(r.getResponse().getContentAsString()).at("/data/token").asText();

        // 先查一门已播种课程，取一个已有成绩对学生/考试/课程，重复 POST 触发唯一约束 → 409
        // 用 MockMvc 查 ranking 拿到真实 studentId/examId/courseId
        MvcResult rr = mockMvc.perform(get("/api/statistics/rank")
                        .param("examId", "1").param("courseId", "1")
                        .header("X-Token", token))
                .andExpect(status().isOk()).andReturn();
        var node = om.readTree(rr.getResponse().getContentAsString());
        var rows = node.at("/data/rows");
        if (rows.isArray() && !rows.isEmpty()) {
            long studentId = rows.get(0).get("studentId").asLong();
            // #P3-4 清理：原 examId 恒为 1L（死代码），保留语义一致的 examId=1（与上方 rank 查询一致）
            String gb = String.format("{\"studentId\":%d,\"examId\":1,\"courseId\":1,\"score\":90,\"fullScore\":100}", studentId);
            mockMvc.perform(post("/api/grades")
                            .header("X-Token", token)
                            .contentType(MediaType.APPLICATION_JSON).content(gb))
                    .andExpect(status().isConflict());
        }
    }
}
