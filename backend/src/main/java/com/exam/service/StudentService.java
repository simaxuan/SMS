package com.exam.service;

import com.exam.common.BizException;
import com.exam.common.AesCrypto;
import com.exam.common.LoginUserContext;
import com.exam.dto.StudentRequest;
import com.exam.entity.Account;
import com.exam.entity.Student;
import com.exam.repository.ParentStudentBindRepository;
import com.exam.repository.StudentRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentService {

    private final StudentRepository studentRepository;
    private final ParentStudentBindRepository bindRepository;
    private final AesCrypto aesCrypto;
    private final DataScopeService dataScopeService;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public StudentService(StudentRepository studentRepository,
                          ParentStudentBindRepository bindRepository,
                          AesCrypto aesCrypto,
                          DataScopeService dataScopeService) {
        this.studentRepository = studentRepository;
        this.bindRepository = bindRepository;
        this.aesCrypto = aesCrypto;
        this.dataScopeService = dataScopeService;
    }

    public Page<Student> list(String keyword, Pageable pageable) {
        Account user = LoginUserContext.requireLogin();
        dataScopeService.requireAnyVisibleClass(user);
        // 读路径 school 下推：effectiveSchoolId 对 SCOPE_ALL 带登录校返回登录校（按校收敛），true-global 返回 null 放开全校；普通老师返回登录校
        return studentRepository.search(dataScopeService.effectiveSchoolId(), keyword, pageable);
    }

    public Student getById(Long id) {
        Account user = LoginUserContext.requireLogin();
        Student s = studentRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BizException(404, "学生不存在"));
        // #3 多租户读防越权：登录用户有归属校时，目标学生必须同校（防 IDOR 跨校读取学生详情）。
        // 注：本方法被 update/delete 复用，故 write 路径同样获得归属保护。
        // SCOPE_ALL 带登录校同样受归属约束（按登录校收敛）；true-global（schoolId 为空）放开全校。
        if (user.getSchoolId() != null
                && (s.getSchoolId() == null || !user.getSchoolId().equals(s.getSchoolId()))) {
            throw new BizException(403, "无权访问该学生");
        }
        // 班级可见性：CLASS 老师仅能访问其可见班级学生的详情
        if (!dataScopeService.canManageClass(user, s.getClassId())) {
            throw new BizException(403, "无权访问该学生");
        }
        return s;
    }

    @Transactional
    public Student create(StudentRequest req) {
        if (studentRepository.existsByStudentNo(req.getStudentNo())) {
            throw new BizException("学号已存在");
        }
        Account user = LoginUserContext.requireLogin();
        if (!dataScopeService.canManageClass(user, req.getClassId())) {
            throw new BizException(403, "无权在所选班级创建学生");
        }
        Student s = new Student();
        s.setOrigin(Student.ORIGIN_SCHOOL);
        s.setEnrolled(true);
        apply(s, req, user);
        return studentRepository.save(s);
    }

    @Transactional
    public Student update(Long id, StudentRequest req) {
        Student s = getById(id);
        if (studentRepository.existsByStudentNoAndIdNot(req.getStudentNo(), id)) {
            throw new BizException("学号已被其他学生使用");
        }
        Account user = LoginUserContext.requireLogin();
        if (!dataScopeService.canManageClass(user, s.getClassId())) {
            throw new BizException(403, "无权修改该班级学生");
        }
        apply(s, req, user);
        return studentRepository.save(s);
    }

    @Transactional
    public void delete(Long id) {
        Account user = LoginUserContext.requireLogin();
        Student s = getById(id);
        if (!dataScopeService.canManageClass(user, s.getClassId())) {
            throw new BizException(403, "无权删除该班级学生");
        }
        int updated = studentRepository.softDelete(id);
        if (updated == 0) {
            throw new BizException(404, "学生不存在");
        }
        // 修复删除安全网：软删学生时清理其家长绑定（孤儿数据兜底）
        bindRepository.deleteByStudentId(id);
    }

    private void apply(Student s, StudentRequest req, Account user) {
        s.setStudentNo(req.getStudentNo());
        s.setName(req.getName());
        s.setGender(req.getGender());
        s.setClassId(req.getClassId());
        s.setBirthDate(req.getBirthDate());
        s.setPhone(req.getPhone());
        // 新字段：身份证（AES 加密 + 后8位哈希）、父母手机号
        if (req.getIdCard() != null && !req.getIdCard().isBlank()) {
            String idCard = req.getIdCard().trim().toUpperCase();
            s.setIdCard(aesCrypto.encrypt(idCard));
            s.setIdCardHash(encoder.encode(last8(idCard)));
        }
        s.setFatherPhone(req.getFatherPhone());
        s.setMotherPhone(req.getMotherPhone());
        if (s.getSchoolId() == null) {
            // 统一写路径决策（对齐 resolveWriteSchoolId）：SCOPE_ALL 带登录校 → 强制登录校（不信任请求体，防跨校写入）；
            // true-global（无登录校）→ 信任请求体显式指定，或保持 null 放开全校。关闭"超管经请求体跨校建学生"缺口。
            s.setSchoolId(dataScopeService.resolveWriteSchoolId(req.getSchoolId()));
        }
        // 双父母手机号至少一个，且格式校验
        if ((s.getFatherPhone() == null || s.getFatherPhone().isBlank())
                && (s.getMotherPhone() == null || s.getMotherPhone().isBlank())) {
            throw new BizException("父方/母方手机号至少填写一个");
        }
        validatePhone(s.getFatherPhone());
        validatePhone(s.getMotherPhone());
    }

    /** 身份证后 8 位（末位 X 转大写）。 */
    public static String last8(String idCard) {
        String c = idCard.trim().toUpperCase();
        return c.length() > 8 ? c.substring(c.length() - 8) : c;
    }

    private void validatePhone(String phone) {
        if (phone == null || phone.isBlank()) return;
        if (!phone.matches("1[3-9]\\d{9}")) {
            throw new BizException("手机号格式不正确: " + phone);
        }
    }
}
