package com.exam.controller;

import com.exam.common.CookieUtil;
import com.exam.common.LoginUserContext;
import com.exam.common.R;
import com.exam.dto.CreateTeacherRequest;
import com.exam.dto.LoginRequest;
import com.exam.dto.RegisterRequest;
import com.exam.entity.Account;
import com.exam.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /** HttpOnly 会话 Cookie 名（凭证不落 JS 可读存储，防 XSS 窃取）。 */
    public static final String TOKEN_COOKIE = "exam_token";

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public R<Map<String, Object>> register(@Valid @RequestBody RegisterRequest req) {
        // 返回新建账号信息（家长自助注册），方便前端确认
        return R.ok(AuthService.toView(authService.register(req)));
    }

    @PostMapping("/login")
    public R<Map<String, Object>> login(@Valid @RequestBody LoginRequest req,
                                       HttpServletRequest request, HttpServletResponse response) {
        Map<String, Object> result = authService.login(req);
        String token = (String) result.get("token");
        // 凭证写入 HttpOnly + SameSite=Lax Cookie：JS 不可读(SameSite 遏制 CSRF)；
        // 响应体仍返回 token 供非浏览器客户端(测试/脚本)与无 Cookie 场景使用。
        // 仅当本次请求经 TLS 终结（request.isSecure()）时追加 Secure 标志，
        // 避免纯 HTTP(8082) 调试环境下 Cookie 无法下发；反向代理以 X-Forwarded-Proto=https
        // 终结 TLS 时容器已将 isSecure() 置真，自动获得 Secure 保护（防令牌经明文 HTTP 嗅探）。
        setTokenCookie(response, token, request.isSecure());
        return R.ok(result);
    }

    @PostMapping("/logout")
    public R<Void> logout(@RequestHeader(value = "X-Token", required = false) String token,
                          HttpServletRequest request, HttpServletResponse response) {
        String tk = token;
        if (tk == null || tk.isBlank()) {
            tk = CookieUtil.readCookie(request, TOKEN_COOKIE);
        }
        authService.logout(tk);
        // 清除浏览器 Cookie：HTTPS 场景追加 Secure 标志，确保与下发时一致（否则无法清除）
        String clearFlags = "; Path=/; HttpOnly; SameSite=Lax"
                + (request.isSecure() ? "; Secure" : "") + "; Max-Age=0";
        response.addHeader("Set-Cookie", TOKEN_COOKIE + "=" + clearFlags);
        return R.ok();
    }

    /**
     * 切换登录校（仅 SCOPE_ALL 全局超管）。
     * <p>请求体 {"schoolId": 数字 或 null}：null 表示"全部学校总览"。
     * 后端校验权限并写库后重签令牌，本端点覆写同名 HttpOnly Cookie，返回 {token, user} 供前端刷新会话。
     */
    @PostMapping("/switch-school")
    public R<Map<String, Object>> switchSchool(
            @RequestBody(required = false) Map<String, Long> body,
            HttpServletRequest request, HttpServletResponse response) {
        Account caller = LoginUserContext.requireLogin();
        Long targetSchoolId = (body == null) ? null : body.get("schoolId");
        Map<String, Object> result = authService.switchSchool(caller, targetSchoolId);
        String token = (String) result.get("token");
        // 覆写 HttpOnly + SameSite=Lax Cookie（与登录下发同口径；安全标志随 TLS 终结状态）
        setTokenCookie(response, token, request.isSecure());
        return R.ok(result);
    }

    /**
     * 下发 HttpOnly + SameSite=Lax 会话 Cookie。
     * <p>secure=true 时追加 Secure 标志（仅经 HTTPS 下发，防止令牌经明文 HTTP 被嗅探）。
     * 判定依据为请求是否经 TLS 终结（request.isSecure()）：纯 HTTP 调试场景不加 Secure，
     * 避免 Cookie 在该环境下无法下发；反向代理以 X-Forwarded-Proto=https 终结 TLS 时，
     * 容器已将 isSecure() 置真，自动获得 Secure 保护。
     */
    public static void setTokenCookie(HttpServletResponse response, String token, boolean secure) {
        if (token == null || token.isBlank()) return;
        String flags = "; Path=/; HttpOnly; SameSite=Lax" + (secure ? "; Secure" : "");
        response.addHeader("Set-Cookie", TOKEN_COOKIE + "=" + token + flags);
    }

    @GetMapping("/me")
    public R<Map<String, Object>> me() {
        Account a = LoginUserContext.requireLogin();
        return R.ok(AuthService.toView(a));
    }

    /** 老师端：列出老师账号（组织管理-老师班级归属绑定选人；按校隔离 + 手机号隐私）。 */
    @GetMapping("/teachers")
    public R<java.util.List<Map<String, Object>>> teachers() {
        LoginUserContext.requireRole(Account.ROLE_TEACHER);
        return R.ok(authService.listTeachers());
    }

    /** 老师端：列出家长账号（组织管理-家长管理/重置家长密码选人；按校隔离 + 手机号隐私）。 */
    @GetMapping("/parents")
    public R<java.util.List<Map<String, Object>>> parents() {
        LoginUserContext.requireRole(Account.ROLE_TEACHER);
        return R.ok(authService.listParents());
    }

    /** 教师管理：创建教师账号（仅 SCOPE_SCHOOL/ALL 老师或 admin）。 */
    @PostMapping("/teachers")
    public R<Map<String, Object>> createTeacher(@Valid @RequestBody CreateTeacherRequest req) {
        requireTeacherManager();
        return R.ok(AuthService.toView(authService.createTeacher(req)));
    }

    /** 教师管理：删除教师账号（联动清理班级归属）。 */
    @DeleteMapping("/teachers/{id}")
    public R<Void> deleteTeacher(@PathVariable Long id) {
        requireTeacherManager();
        authService.deleteTeacher(id);
        return R.ok();
    }

    /** 教师管理权限：仅 SCOPE_SCHOOL/ALL 老师（含 admin）可创建/删除教师。 */
    private void requireTeacherManager() {
        Account a = LoginUserContext.requireRole(Account.ROLE_TEACHER);
        if (!Account.SCOPE_ALL.equals(a.getScopeType())
                && !Account.SCOPE_SCHOOL.equals(a.getScopeType())) {
            throw new com.exam.common.BizException(403,
                    "仅校级/全校管理员可创建或删除教师账号");
        }
    }

    /** 当前登录用户修改自己的密码（家长/老师）。 */
    @PutMapping("/password")
    public R<Void> changePassword(@RequestBody Map<String, String> body) {
        Account a = LoginUserContext.requireLogin();
        authService.changePassword(a, body.get("newPassword"));
        return R.ok();
    }

    /** 老师端：将家长账号重置为默认密码（默认密码=该生身份证后8位，由后端统一计算，归属校验防跨校接管）。 */
    @PutMapping("/password/reset/{parentAccountId}")
    public R<Void> resetParentPassword(@PathVariable Long parentAccountId) {
        Account caller = LoginUserContext.requireRole(Account.ROLE_TEACHER);
        authService.resetParentPassword(caller, parentAccountId);
        return R.ok();
    }

    /** 微信登录占位（本期不实现）。 */
    @PostMapping("/wechat")
    public R<Void> wechatLogin() {
        authService.wechatLoginPlaceholder();
        return R.ok(); // 不会走到：占位方法内部抛"未启用"
    }
}
