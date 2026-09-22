package com.exam.service;

import com.exam.common.BizException;
import com.exam.common.LoginUserContext;
import com.exam.entity.Account;
import com.exam.entity.ClassEntity;
import com.exam.entity.TeacherClass;
import com.exam.repository.ClassRepository;
import com.exam.repository.TeacherClassRepository;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 数据范围过滤（问题二核心）。
 * <p>依据 v5 方案：
 * - school 隔离方案 A：account/student/exam/course/exam_type 显式带 schoolId；grade/class/teacher_class/bind 由上层推导。
 * - 老师默认 scope_type=CLASS（可绑多班、可跨级部），ALL 为兼容/管理员。
 * - 排名/统计支持三级范围 class / level / school。
 * <p>关键：Grade 已有 `school_id` 冗余快照（阶段 B 增加），可直接按校过滤；`class_id` 仍依赖 Student 推导
 *        （Grade 无 class_id 冗余，班级范围过滤需 join Student 后才有）。
 */
@Service
public class DataScopeService {

    private final TeacherClassRepository teacherClassRepository;
    private final ClassRepository classRepository;

    public DataScopeService(TeacherClassRepository teacherClassRepository,
                            ClassRepository classRepository) {
        this.teacherClassRepository = teacherClassRepository;
        this.classRepository = classRepository;
    }

    /** 中央取当前登录用户的 schoolId（普通校归属；未登录/全局超管返回 null 或登录用户自身的 schoolId，调用方需结合 isGlobalAdmin 区分）。 */
    public Long currentSchoolId() {
        Account u = LoginUserContext.get();
        return u == null ? null : u.getSchoolId();
    }

    /**
     * 是否全局管理员（SCOPE_ALL 超管）。识别依据是 scopeType 而非 schoolId 是否为空：
     * 全局超管可跨校管理，写路径显式指定 schoolId、读路径放开全校；普通老师（CLASS/SCHOOL）受单校隔离。
     */
    public boolean isGlobalAdmin() {
        Account u = LoginUserContext.get();
        return u != null && Account.SCOPE_ALL.equals(u.getScopeType());
    }

    /**
     * 写路径 schoolId 决策（统一隔离口径，修正"超管信任请求体跨校写入"与"被请求体劫持迁移归属"两难）：
     * - 全局超管（SCOPE_ALL）带登录校：强制锁定登录校，不信任请求体显式指定 schoolId，防止被恶意/误带他校；
     * - 全局超管（SCOPE_ALL）且 schoolId 为空（true-global）：信任请求体，允许显式跨校挂载；
     * - 带归属校的普通老师：强制锁定登录校，防止跨校写入；
     * - 无登录上下文：回退请求体指定。
     * <p>说明：超管向新学校挂载级部/班级等组织，统一通过"前端切换登录校"（切换后 schoolId 即目标校）完成，
     * 而非信任请求体；既满足"级部/班级可挂到新学校"，又满足"写路径不信任请求体、归属锁定登录校"的安全约束。
     */
    public Long resolveWriteSchoolId(Long requestedSchoolId) {
        Account u = LoginUserContext.get();
        if (u == null) {
            return requestedSchoolId;
        }
        if (Account.SCOPE_ALL.equals(u.getScopeType())) {
            // 全局超管：带登录校 → 强制登录校（不信任请求体）；true-global（无登录校）→ 信任请求体
            return u.getSchoolId() != null ? u.getSchoolId() : requestedSchoolId;
        }
        if (u.getSchoolId() != null) {
            return u.getSchoolId();   // 普通老师：锁定登录校
        }
        return requestedSchoolId;
    }

    /** 当前老师可见班级 ID 集合；
     * ALL 返回 null（全校，仍会叠加 schoolId 兜底）；
     * SCHOOL 返回本校全部班级；
     * CLASS 返回老师-班级绑定集合；非老师返回空集合。 */
    public Set<Long> visibleClassIds(Account user) {
        if (Account.ROLE_TEACHER.equals(user.getRole())) {
            if (Account.SCOPE_ALL.equals(user.getScopeType())) {
                return null; // 全校（读路径叠加 schoolId，见各 service 按校下推）
            }
            if (Account.SCOPE_SCHOOL.equals(user.getScopeType())) {
                if (user.getSchoolId() == null) {
                    return null; // 无归属校的 SCHOOL 视同放开
                }
                return classRepository.findBySchoolId(user.getSchoolId()).stream()
                        .map(ClassEntity::getId)
                        .collect(Collectors.toSet());
            }
            List<TeacherClass> tcs = teacherClassRepository.findByTeacherAccountId(user.getId());
            if (tcs.isEmpty()) {
                return Collections.emptySet();
            }
            return tcs.stream().map(TeacherClass::getClassId).collect(Collectors.toSet());
        }
        return Collections.emptySet(); // 非老师无班级范围
    }

    /** 校验某个 classId 是否对当前老师可见（用于写操作/录入校验）。 */
    public boolean canManageClass(Account user, Long classId) {
        if (classId == null) return false;
        if (!Account.ROLE_TEACHER.equals(user.getRole())) return false;
        Set<Long> visible = visibleClassIds(user);
        return visible == null || visible.contains(classId);
    }

    // P1-3：原 filterGradeRowsByClass（取全量后按可见班级内存过滤）已废弃并移除（无调用者，
    // 排名/对比/进步均已改为 SQL 下压 rankRowsByClassIds / courseRowsByExamByClassIds）。

    /** 当前老师为 CLASS 且无任何可见班级时拒权（避免空视图仍可读全校）；SCHOOL/ALL 不约束。 */
    public void requireAnyVisibleClass(Account user) {
        if (!Account.ROLE_TEACHER.equals(user.getRole())) return;
        if (Account.SCOPE_ALL.equals(user.getScopeType())) return;
        if (Account.SCOPE_SCHOOL.equals(user.getScopeType())) return; // 本校全部班级，总有可读范围
        Set<Long> visible = visibleClassIds(user);
        if (visible == null || visible.isEmpty()) {
            throw new BizException(403, "当前老师未绑定任何班级，无权访问该数据");
        }
    }

    /** 便捷：从登录上下文取当前账号（供各 service 复用）。 */
    public Account currentUser() {
        return LoginUserContext.requireLogin();
    }

    /**
     * 读路径 schoolId 统一取值（权威方法）：
     * - SCOPE_ALL 带登录校：按登录校收敛（不默认放开全校，也不被演示学校 schoolId 误锁——登录校即其管辖校）；
     * - SCOPE_ALL 且 schoolId 为空（true-global）：返回 null（放开全校，供全局总览）；
     * - 其他角色：返回其登录归属校（schoolId 可能为 null，表示放开/无归属）。
     * 语义：返回值即"读路径应按校下推的 schoolId"，null 表示不按校过滤。
     * 说明：超管跨校查看统一由调用方显式传入 schoolId 参数（如 listClasses(Long)）或前端切换登录校完成，
     * 而非默认放开全校，避免误读他校数据。
     */
    public Long effectiveSchoolId() {
        Account u = LoginUserContext.get();
        if (u == null) {
            return null;
        }
        return u.getSchoolId();
    }

    /**
     * 统计/对比/进步/排名查询的 SQL 层 school 兜底取值：
     * - 当无班级级收敛（visibleClassIds 返回 null，即 SCOPE_ALL/全局超管）时，按登录校兜底过滤
     *   （SCOPE_ALL 带登录校防跨校混入；true-global 登录校为空则返回 null 放开全校）；
     * - 当已有班级收敛（CLASS/SCHOOL，visibleClassIds 非 null）时返回 null（放开），班级/校级过滤已在上层完成。
     * 语义：有班级收敛 → 放开；无班级收敛 → 按登录校兜底。
     */
    public Long schoolFilterForStatistics(Account user) {
        Set<Long> visible = visibleClassIds(user);
        if (visible == null) {
            // SCOPE_ALL/全局超管：按登录校兜底（true-global 登录校为空 → null 放开全校）
            return user.getSchoolId();
        }
        return null;
    }
}
