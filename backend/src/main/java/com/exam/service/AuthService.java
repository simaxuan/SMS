package com.exam.service;

import com.exam.common.BizException;
import com.exam.common.AesCrypto;
import com.exam.common.LoginUserContext;
import com.exam.config.JwtUtil;
import com.exam.dto.CreateTeacherRequest;
import com.exam.dto.LoginRequest;
import com.exam.dto.RegisterRequest;
import com.exam.entity.Account;
import com.exam.entity.ClassEntity;
import com.exam.entity.ParentStudentBind;
import com.exam.entity.Student;
import com.exam.entity.TeacherClass;
import com.exam.repository.AccountRepository;
import com.exam.repository.ClassRepository;
import com.exam.repository.ParentStudentBindRepository;
import com.exam.repository.SchoolRepository;
import com.exam.repository.StudentRepository;
import com.exam.repository.TeacherClassRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AuthService {

    /** 审计日志（P2-1）：认证/授权关键事件留痕，不含口令明文。 */
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AuthService.class);

    /** 失败锁定（v5 确认：固定 5 次 / 15 分钟，硬编码） */
    private static final int MAX_FAIL = 5;
    private static final long LOCK_MS = 15L * 60 * 1000;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    private final AccountRepository accountRepository;
    private final JwtUtil jwtUtil;
    private final ClassRepository classRepository;
    private final TeacherClassRepository teacherClassRepository;
    private final ParentStudentBindRepository bindRepository;
    private final StudentRepository studentRepository;
    private final AesCrypto aesCrypto;
    private final SchoolRepository schoolRepository;
    /** 会话失效状态存储（默认内存实现；可通过注入 Redis 实现水平扩展）。 */
    private final RevocationStore revocationStore;

    public AuthService(AccountRepository accountRepository, JwtUtil jwtUtil,
                       ClassRepository classRepository,
                       TeacherClassRepository teacherClassRepository,
                       ParentStudentBindRepository bindRepository,
                       StudentRepository studentRepository,
                       AesCrypto aesCrypto,
                       SchoolRepository schoolRepository,
                       RevocationStore revocationStore) {
        this.accountRepository = accountRepository;
        this.jwtUtil = jwtUtil;
        this.classRepository = classRepository;
        this.teacherClassRepository = teacherClassRepository;
        this.bindRepository = bindRepository;
        this.studentRepository = studentRepository;
        this.aesCrypto = aesCrypto;
        this.schoolRepository = schoolRepository;
        this.revocationStore = revocationStore;
    }

    public Account register(RegisterRequest req) {
        String role = req.getRole();
        // 安全默认：不允许客户端自助注册老师账号
        if (!Account.ROLE_PARENT.equals(role)) {
            throw new BizException("暂不支持自助注册老师账号，老师账号由系统管理员创建；请使用家长角色注册");
        }
        if (accountRepository.existsByUsername(req.getUsername())) {
            throw new BizException("用户名已存在");
        }
        if (req.getPhone() != null && !req.getPhone().isBlank()
                && accountRepository.existsByPhone(req.getPhone())) {
            throw new BizException("该手机号已注册");
        }
        Account a = new Account();
        a.setUsername(req.getUsername());
        a.setPhone(req.getPhone() == null || req.getPhone().isBlank() ? null : req.getPhone());
        a.setPasswordHash(encoder.encode(req.getPassword()));
        a.setRole(role);
        a.setNickname(req.getNickname());
        a.setAuthType(Account.AUTH_PASSWORD);
        a.setScopeType(Account.SCOPE_CLASS);
        a.setLastPasswordChange(LocalDateTime.now());
        return accountRepository.save(a);
    }

    public Map<String, Object> login(LoginRequest req) {
        // 先解析账号：拿到稳定 accountId 作为锁定键，避免 username/phone 双通道各自计数绕过锁定
        Account a = findByLoginName(req.getUsername())
                .orElse(null);

        // 失败锁键：优先 accountId（稳定），账号不存在时回退到登录串（防用户枚举计数）
        final String lockKey;
        if (a != null) {
            lockKey = "acc:" + a.getId();
        } else {
            lockKey = "name:" + req.getUsername();
        }
        long[] cnt = revocationStore.failureCount(lockKey);
        if (cnt[0] >= MAX_FAIL && System.currentTimeMillis() - cnt[1] < LOCK_MS) {
            log.warn("[审计]登录锁定拒绝 lockKey={} 失败次数={} 用户名={}", lockKey, cnt[0], req.getUsername());
            throw new BizException(423, "登录失败次数过多，请 " + ((LOCK_MS - (System.currentTimeMillis() - cnt[1])) / 60000 + 1) + " 分钟后再试");
        }
        if (a == null) {
            revocationStore.recordFailure(lockKey);
            log.warn("[审计]登录失败-账号不存在 用户名={}", req.getUsername());
            throw new BizException(401, "用户名或密码错误");
        }
        if (!req.getRole().equals(a.getRole())) {
            revocationStore.recordFailure(lockKey);
            log.warn("[审计]登录失败-角色不符 uid={} 期望={} 实际={}", a.getId(), req.getRole(), a.getRole());
            throw new BizException(401, "请选择与账号匹配的角色登录");
        }
        if (!encoder.matches(req.getPassword(), a.getPasswordHash())) {
            revocationStore.recordFailure(lockKey);
            log.warn("[审计]登录失败-密码错误 uid={} 用户名={}", a.getId(), req.getUsername());
            throw new BizException(401, "用户名或密码错误");
        }
        revocationStore.clearFailure(lockKey); // 成功清除
        log.info("[审计]登录成功 uid={} 用户名={} 角色={}", a.getId(), req.getUsername(), a.getRole());
        // 签发无状态 JWT：携带账号 token 版本（改密失效）与唯一 jti（登出失效）
        Long curVer = revocationStore.tokenVersion(a.getId());
        long ver = curVer == null ? 0L : curVer;
        String token = jwtUtil.sign(a.getId(), ver, UUID.randomUUID().toString());
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("token", token);
        result.put("user", toView(a));
        return result;
    }

    /** 优先按用户名，其次按手机号（家长手机号登录）。 */
    private java.util.Optional<Account> findByLoginName(String login) {
        java.util.Optional<Account> byUser = accountRepository.findByUsername(login);
        if (byUser.isPresent()) return byUser;
        return accountRepository.findByPhone(login);
    }

    public void logout(String token) {
        String jti = jwtUtil.parseJti(token);
        if (jti != null) {
            long exp = System.currentTimeMillis() + 24L * 60 * 60 * 1000; // 兜底保留，避免时间差
            JwtUtil.Payload p = jwtUtil.verify(token);
            if (p != null) exp = p.exp;
            revocationStore.blacklist(jti, exp);
        }
        com.exam.entity.Account cur = com.exam.common.LoginUserContext.get();
        log.info("[审计]登出 uid={}", cur == null ? "?" : cur.getId());
    }

    /**
     * 切换当前登录校（仅 SCOPE_ALL 全局超管可用）。
     * <p>语义：将"活动学校"写入 account.schoolId；sid 为 null 表示"全部学校总览"（true-global 放开全校读、写路径信任请求体）。
     * 切换后重签令牌（新 jti），使后续请求按新归属校收敛读、锁定写（与 DataScopeService 统一口径一致）。
     * <p>注意：登录态的 schoolId 始终从数据库账号解析（AuthInterceptor），故此处只需更新库并重新下发令牌即可生效。
     */
    @Transactional
    public Map<String, Object> switchSchool(Account caller, Long targetSchoolId) {
        if (!Account.SCOPE_ALL.equals(caller.getScopeType())) {
            throw new BizException(403, "仅全局超管可切换登录校");
        }
        if (targetSchoolId != null) {
            schoolRepository.findById(targetSchoolId)
                    .orElseThrow(() -> new BizException(404, "目标学校不存在"));
        }
        // 重新按 id 取持久化实体，避免直接使用线程上下文里的游离对象
        Account acc = accountRepository.findById(caller.getId())
                .orElseThrow(() -> new BizException(404, "账号不存在"));
        acc.setSchoolId(targetSchoolId);
        accountRepository.save(acc);
        // 重签令牌：新 jti 使浏览器拿到新的 HttpOnly Cookie；token 版本保持不变（无需踢掉其他端）
        Long curVer = revocationStore.tokenVersion(acc.getId());
        long ver = curVer == null ? 0L : curVer;
        String token = jwtUtil.sign(acc.getId(), ver, UUID.randomUUID().toString());
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("token", token);
        result.put("user", toView(acc));
        log.info("[审计]切换登录校 操作者uid={} 目标schoolId={}", acc.getId(), targetSchoolId);
        return result;
    }

    /** 家长自行修改密码（登录后）。改密后该账号全部旧令牌立即失效。 */
    public void changePassword(Account user, String newPassword) {
        if (newPassword == null || newPassword.length() < 6 || newPassword.length() > 64) {
            throw new BizException("新密码长度需在6-64之间");
        }
        warnIfWeak(newPassword);   // #20 弱口令提示（不拦截，兼容既有 6 位策略）
        Account a = accountRepository.findById(user.getId()).orElseThrow(() -> new BizException(404, "账号不存在"));
        a.setPasswordHash(encoder.encode(newPassword));
        a.setLastPasswordChange(LocalDateTime.now());
        accountRepository.save(a);
        revocationStore.bumpTokenVersion(a.getId(), 1L); // 旧令牌全部失效
        log.info("[审计]家长修改密码 uid={}", a.getId());
    }

    /**
     * #20 弱口令日志提示：密码过短或过于简单时告警（供审计），不强制拦截，
     * 避免破坏既有 6 位密码策略与历史账号。生产可根据需要自行提高下限。
     */
    private void warnIfWeak(String password) {
        if (password == null || password.length() < 8) {
            org.slf4j.LoggerFactory.getLogger(AuthService.class)
                    .warn("检测到弱口令（长度<8）。建议生产环境启用更严格的密码策略。");
        } else if (password.matches("[0-9]+") || password.matches("[a-zA-Z]+")) {
            org.slf4j.LoggerFactory.getLogger(AuthService.class)
                    .warn("检测到弱口令（纯数字/纯字母）。建议混合字母、数字与符号。");
        }
    }

    /**
     * 老师端：将家长账号重置为默认密码（默认密码=该生身份证后 8 位，由后端统一计算，不信任调用方传入）。
     * #4 越权加固：仅允许重置本校/同校家长；SCOPE_ALL（全局超管）放开；重置后旧令牌失效。
     */
    public void resetParentPassword(Account caller, Long parentAccountId) {
        Account p = accountRepository.findById(parentAccountId)
                .orElseThrow(() -> new BizException(404, "家长账号不存在"));
        if (!Account.ROLE_PARENT.equals(p.getRole())) {
            throw new BizException(400, "只能重置家长账号");
        }
        // 归属校验：调用者为 SCOPE_ALL（全局超管）→ 放开；否则要求家长与调用者同校（且家长必有归属校）
        if (!Account.SCOPE_ALL.equals(caller.getScopeType())) {
            if (caller.getSchoolId() == null || p.getSchoolId() == null
                    || !caller.getSchoolId().equals(p.getSchoolId())) {
                throw new BizException(403, "无权重置其他学校家长的密码");
            }
            // 同校仍须是该校数据管理者（SCHOOL/ALL），CLASS 老师不得随意重置
            if (Account.SCOPE_CLASS.equals(caller.getScopeType())) {
                throw new BizException(403, "仅校级/全校管理员可重置家长密码");
            }
        }
        // 后端计算默认密码：取该家长绑定学生的身份证后 8 位（不再信任调用方 defaultPassword）
        String defaultPassword = deriveDefaultPassword(p.getId());
        if (defaultPassword == null) {
            throw new BizException(400, "该家长未绑定学生或无身份证信息，无法生成默认密码");
        }
        p.setPasswordHash(encoder.encode(defaultPassword));
        p.setLastPasswordChange(LocalDateTime.now());
        accountRepository.save(p);
        revocationStore.bumpTokenVersion(p.getId(), 1L); // 旧令牌全部失效
        log.info("[审计]老师重置家长密码 操作者uid={} 目标家长uid={}", caller.getId(), p.getId());
    }

    /** 由家长绑定的任一学生身份证后 8 位推导默认密码（AES 解密后取末 8 位）。 */
    private String deriveDefaultPassword(Long parentAccountId) {
        return bindRepository.findByParentAccountId(parentAccountId).stream()
                .map(ParentStudentBind::getStudentId)
                .map(studentRepository::findById)
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .filter(s -> !Boolean.TRUE.equals(s.getDeleted()) && s.getIdCard() != null)
                .map(s -> aesCrypto.decryptSafe(s.getIdCard())) // 解密降级：单条密文损坏不抛 500
                .filter(idCard -> idCard != null)
                .findFirst()
                .map(StudentService::last8)
                .orElse(null);
    }

    /**
     * 列出老师账号（组织管理：老师-班级归属绑定选人用）。
     * 多租户：仅返回当前登录者归属校的老师；null(全局超管)全量。
     * 隐私：仅本人或 SCOPE_SCHOOL/ALL 管理者可见他人手机号。
     */
    public java.util.List<Map<String, Object>> listTeachers() {
        Account viewer = LoginUserContext.requireLogin();
        // B4 修正：全局超管（SCOPE_ALL）放开全校老师列表；普通老师仅列本校老师。
        Long sid = Account.SCOPE_ALL.equals(viewer.getScopeType()) ? null : viewer.getSchoolId();
        java.util.List<Account> list = sid == null
                ? accountRepository.findByRole(Account.ROLE_TEACHER)
                : accountRepository.findByRoleAndSchoolId(Account.ROLE_TEACHER, sid);
        boolean isManager = Account.SCOPE_ALL.equals(viewer.getScopeType())
                || Account.SCOPE_SCHOOL.equals(viewer.getScopeType());
        return list.stream()
                .map(a -> toView(a, isManager || a.getId().equals(viewer.getId())))
                .collect(Collectors.toList());
    }

    /**
     * 列出家长账号（老师端：重置家长密码 / 家长管理用）。
     * <p>多租户：仅返回当前登录者归属校的家长；SCOPE_ALL（全局超管）放开全校。
     * 隐私：仅 SCOPE_SCHOOL/ALL 管理者可见他人手机号（与 listTeachers 口径一致）。
     */
    public java.util.List<Map<String, Object>> listParents() {
        Account viewer = LoginUserContext.requireLogin();
        Long sid = Account.SCOPE_ALL.equals(viewer.getScopeType()) ? null : viewer.getSchoolId();
        java.util.List<Account> list = sid == null
                ? accountRepository.findByRole(Account.ROLE_PARENT)
                : accountRepository.findByRoleAndSchoolId(Account.ROLE_PARENT, sid);
        boolean isManager = Account.SCOPE_ALL.equals(viewer.getScopeType())
                || Account.SCOPE_SCHOOL.equals(viewer.getScopeType());
        return list.stream()
                .map(a -> toView(a, isManager))
                .collect(Collectors.toList());
    }

    /**
     * 创建教师账号（多租户：强制归属学校；CLASS 范围须绑定本校班级）。
     * 仅 SCOPE_SCHOOL/ALL 老师或 admin 可调用（由 Controller 校验）。
     */
    @Transactional
    public Account createTeacher(CreateTeacherRequest req) {
        String username = req.getUsername() == null ? "" : req.getUsername().trim();
        if (username.isBlank()) throw new BizException("用户名不能为空");
        if (req.getPassword() == null || req.getPassword().length() < 6 || req.getPassword().length() > 64) {
            throw new BizException("密码长度需在6-64之间");
        }
        if (req.getSchoolId() == null) throw new BizException("教师必须归属学校");
        String scopeType = req.getScopeType() == null ? Account.SCOPE_CLASS : req.getScopeType();
        if (!Set.of(Account.SCOPE_CLASS, Account.SCOPE_SCHOOL, Account.SCOPE_ALL).contains(scopeType)) {
            throw new BizException("非法的数据范围类型");
        }
        if (accountRepository.existsByUsername(username)) throw new BizException("用户名已存在");
        if (req.getPhone() != null && !req.getPhone().isBlank()
                && accountRepository.existsByPhone(req.getPhone())) {
            throw new BizException("该手机号已注册");
        }
        // CLASS 范围：必选班级，且班级须属于指定学校（I-4 跨校绑定拒绝）
        if (Account.SCOPE_CLASS.equals(scopeType)) {
            if (req.getClassIds() == null || req.getClassIds().isEmpty()) {
                throw new BizException("CLASS 范围教师必须绑定至少一个班级");
            }
            for (Long cid : req.getClassIds()) {
                ClassEntity c = classRepository.findById(cid)
                        .orElseThrow(() -> new BizException(404, "班级不存在"));
                if (!req.getSchoolId().equals(c.getSchoolId())) {
                    throw new BizException("绑定班级不属于指定学校，无法创建教师");
                }
            }
        }
        Account a = new Account();
        a.setUsername(username);
        a.setPhone(req.getPhone() == null || req.getPhone().isBlank() ? null : req.getPhone());
        a.setPasswordHash(encoder.encode(req.getPassword()));
        a.setRole(Account.ROLE_TEACHER);
        a.setNickname(req.getNickname());
        a.setSchoolId(req.getSchoolId());
        a.setScopeType(scopeType);
        a.setAuthType(Account.AUTH_PASSWORD);
        a.setLastPasswordChange(LocalDateTime.now());
        Account saved = accountRepository.save(a);
        if (Account.SCOPE_CLASS.equals(scopeType)) {
            for (Long cid : req.getClassIds()) {
                TeacherClass tc = new TeacherClass();
                tc.setTeacherAccountId(saved.getId());
                tc.setClassId(cid);
                teacherClassRepository.save(tc);
            }
        }
        com.exam.entity.Account cur = com.exam.common.LoginUserContext.get();
        log.info("[审计]创建教师 操作者uid={} 新教师uid={} 用户名={} 范围={}",
                cur == null ? "?" : cur.getId(), saved.getId(), saved.getUsername(), scopeType);
        return saved;
    }

    /** 删除教师账号（联动清理其班级归属）。仅 SCOPE_SCHOOL/ALL 老师或 admin 可调用。 */
    @Transactional
    public void deleteTeacher(Long id) {
        Account a = accountRepository.findById(id)
                .orElseThrow(() -> new BizException(404, "账号不存在"));
        if (!Account.ROLE_TEACHER.equals(a.getRole())) {
            throw new BizException("仅可删除教师账号");
        }
        teacherClassRepository.deleteByTeacherAccountId(id);
        accountRepository.delete(a);
        com.exam.entity.Account cur = com.exam.common.LoginUserContext.get();
        log.info("[审计]删除教师 操作者uid={} 被删uid={} 用户名={}", cur == null ? "?" : cur.getId(), a.getId(), a.getUsername());
    }

    /** 微信登录占位（本期不实现）：返回未启用。 */
    public Map<String, Object> wechatLoginPlaceholder() {
        throw new BizException("微信登录尚未启用，请使用手机号+密码登录");
    }

    /** 根据 token 解析账号：验签 + 过期 + 版本 + 黑名单；无效返回 null */
    public Account findByToken(String token) {
        if (token == null) return null;
        JwtUtil.Payload p = jwtUtil.verify(token);
        if (p == null) return null;
        Long curVer = revocationStore.tokenVersion(p.uid);
        if (curVer != null && curVer.longValue() != p.ver) return null; // 改密/重置后旧令牌失效
        if (revocationStore.isBlacklisted(p.jti)) return null; // 已登出
        return accountRepository.findById(p.uid).orElse(null);
    }

    public static Map<String, Object> toView(Account a) {
        return toView(a, true);
    }

    /** 隐私版：showPhone=false 时隐藏他人手机号（他人可见为空）。 */
    public static Map<String, Object> toView(Account a, boolean showPhone) {
        Map<String, Object> m = new java.util.HashMap<>();
        m.put("id", a.getId());
        m.put("username", a.getUsername());
        m.put("role", a.getRole());
        m.put("nickname", a.getNickname());
        m.put("phone", showPhone ? a.getPhone() : null);
        m.put("schoolId", a.getSchoolId());
        m.put("scopeType", a.getScopeType());
        m.put("authType", a.getAuthType());
        return m;
    }
}
