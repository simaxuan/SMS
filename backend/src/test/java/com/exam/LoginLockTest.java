package com.exam;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 登录失败锁定测试：连续 5 次错误密码后被锁定（硬编码 5 次/15 分钟），
 * 第 6 次起返回 423，即使后续密码正确仍被锁。
 * 使用唯一用户名，避免影响 admin/parent1 等其他用例。
 */
@SpringBootTest
@AutoConfigureMockMvc
class LoginLockTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper om;

    private String json(Object o) throws Exception {
        return om.writeValueAsString(o);
    }

    @Test
    void 连续5次失败密码后锁定_第6次返回423() throws Exception {
        String uname = "lock_" + System.nanoTime();
        // 注册一个家长账号（角色 PARENT）
        RegisterRequest rr = new RegisterRequest();
        rr.setUsername(uname);
        rr.setPassword("right123");
        rr.setRole(Account.ROLE_PARENT);
        rr.setNickname("锁定测试");
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(json(rr)))
                .andExpect(status().isOk());

        LoginRequest wrong = new LoginRequest();
        wrong.setUsername(uname);
        wrong.setPassword("wrongpass");
        wrong.setRole(Account.ROLE_PARENT);

        // 前 5 次错误密码：均 401
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json(wrong)))
                    .andExpect(status().isUnauthorized());
        }

        // 第 6 次（仍错误）：已被锁定，返回 423
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json(wrong)))
                .andExpect(status().is(423));

        // 即使密码正确，锁定期间仍返回 423
        LoginRequest right = new LoginRequest();
        right.setUsername(uname);
        right.setPassword("right123");
        right.setRole(Account.ROLE_PARENT);
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json(right)))
                .andExpect(status().is(423));
    }
}
