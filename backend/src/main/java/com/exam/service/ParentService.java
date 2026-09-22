package com.exam.service;

import com.exam.common.AesCrypto;
import com.exam.common.BizException;
import com.exam.common.GradeSources;
import com.exam.common.LoginUserContext;
import com.exam.dto.BindRequest;
import com.exam.dto.RegisterRequest;
import com.exam.entity.Account;
import com.exam.entity.Grade;
import com.exam.entity.ParentPreference;
import com.exam.entity.ParentStudentBind;
import com.exam.entity.Student;
import com.exam.repository.AccountRepository;
import com.exam.repository.ClassRepository;
import com.exam.repository.GradeRepository;
import com.exam.repository.ParentPreferenceRepository;
import com.exam.repository.ParentStudentBindRepository;
import com.exam.repository.StudentRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ParentService {

    private final ParentStudentBindRepository bindRepository;
    private final StudentRepository studentRepository;
    private final ClassRepository classRepository;
    private final ParentPreferenceRepository preferenceRepository;
    private final AuthService authService;
    private final AccountRepository accountRepository;
    private final GradeRepository gradeRepository;
    private final AesCrypto aesCrypto;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public ParentService(ParentStudentBindRepository bindRepository,
                         StudentRepository studentRepository,
                         ClassRepository classRepository,
                         ParentPreferenceRepository preferenceRepository,
                         AuthService authService,
                         AccountRepository accountRepository,
                         GradeRepository gradeRepository,
                         AesCrypto aesCrypto) {
        this.bindRepository = bindRepository;
        this.studentRepository = studentRepository;
        this.classRepository = classRepository;
        this.preferenceRepository = preferenceRepository;
        this.authService = authService;
        this.accountRepository = accountRepository;
        this.gradeRepository = gradeRepository;
        this.aesCrypto = aesCrypto;
    }

    /**
     * 绑定校验（问题三核心）：学号 + 姓名 + 手机号 + 身份证后8位 + 密码。
     * 双父母各自账号分别绑定；默认密码=身份证后8位（不强制首改）。
     */
    @Transactional
    public Map<String, Object> bind(Account parent, BindRequest req) {
        Student s = studentRepository.findByStudentNoAndNameAndDeletedFalse(req.getStudentNo(), req.getName())
                .orElseThrow(() -> new BizException("未找到匹配的学生，请核对学号与姓名"));

        // ① 身份证后 8 位比对（解密后比对末 8 位，与加密存储相符）
        if (req.getIdCardLast8() == null || req.getIdCardLast8().isBlank()) {
            throw new BizException("请输入身份证后8位");
        }
        String plainIdCard = aesCrypto.decrypt(s.getIdCard());
        if (plainIdCard == null || !StudentService.last8(plainIdCard).equalsIgnoreCase(req.getIdCardLast8().trim().toUpperCase())) {
            throw new BizException("身份证后8位不匹配");
        }
        // ② 手机号与父/母手机号匹配
        if (req.getPhone() == null || req.getPhone().isBlank()) {
            throw new BizException("请输入家长手机号");
        }
        boolean fatherMatch = s.getFatherPhone() != null && s.getFatherPhone().equals(req.getPhone());
        boolean motherMatch = s.getMotherPhone() != null && s.getMotherPhone().equals(req.getPhone());
        if (!fatherMatch && !motherMatch) {
            throw new BizException("手机号与学生登记的父母手机号不匹配");
        }
        // ③ relation 与手机号口径一致
        String relation = req.getRelation();
        if ("father".equalsIgnoreCase(relation) && !fatherMatch) {
            throw new BizException("该手机号不是登记的父亲手机号");
        }
        if ("mother".equalsIgnoreCase(relation) && !motherMatch) {
            throw new BizException("该手机号不是登记的母亲手机号");
        }
        if (relation == null || relation.isBlank()) {
            relation = fatherMatch ? "father" : "mother";
        }
        // ④ 手机号必须是当前家长账号的手机号（或账号已登记该手机号）
        String accPhone = parent.getPhone();
        if (accPhone == null || !accPhone.equals(req.getPhone())) {
            throw new BizException("绑定手机号与当前家长账号手机号不一致");
        }
        if (bindRepository.existsByParentAccountIdAndStudentId(parent.getId(), s.getId())) {
            throw new BizException("已绑定该学生，请勿重复绑定");
        }
        ParentStudentBind b = new ParentStudentBind();
        b.setParentAccountId(parent.getId());
        b.setStudentId(s.getId());
        b.setRelation(relation);
        // P1-4 并发双绑：唯一约束(uk_parent_student)竞态时捕获转 409，与 Grade 录入行为一致
        try {
            bindRepository.save(b);
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            throw new com.exam.common.BizException(409, "已绑定该学生，请勿重复绑定");
        }

        // ⑤ 默认密码：若家长新注册无密码，则以身份证后8位作为默认密码
        if (parent.getPasswordHash() == null && req.getPassword() != null && !req.getPassword().isBlank()) {
            Account up = accountRepository.findById(parent.getId()).orElseThrow();
            up.setPasswordHash(encoder.encode(req.getPassword()));
            accountRepository.save(up);
        }
        Map<String, Object> m = new HashMap<>();
        m.put("studentId", s.getId());
        m.put("studentNo", s.getStudentNo());
        m.put("name", s.getName());
        m.put("className", classNames(s.getClassId()));
        m.put("relation", relation);
        return m;
    }

    /** 当前家长绑定的学生列表（含学生信息）。 */
    public List<Map<String, Object>> listBinds(Account parent) {
        List<ParentStudentBind> binds = bindRepository.findByParentAccountId(parent.getId());
        List<Long> studentIds = new ArrayList<>();
        for (ParentStudentBind b : binds) studentIds.add(b.getStudentId());
        Map<Long, Student> studentById = new HashMap<>();
        if (!studentIds.isEmpty()) {
            studentRepository.findAllById(studentIds).forEach(s -> studentById.put(s.getId(), s));
        }
        List<Long> classIds = new ArrayList<>();
        Map<Long, String> classNames = new HashMap<>();
        for (Student s : studentById.values()) {
            if (s.getClassId() != null && !classIds.contains(s.getClassId())) {
                classIds.add(s.getClassId());
            }
        }
        if (!classIds.isEmpty()) {
            classRepository.findAllById(classIds).forEach(c -> classNames.put(c.getId(), c.getName()));
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (ParentStudentBind b : binds) {
            Student s = studentById.get(b.getStudentId());
            if (s == null) continue;
            // 修正：认领合并后已并入校园生的自建学生（merged=true）不再单独展示，避免家长视图出现"重复学生"
            if (Boolean.TRUE.equals(s.getMerged())) {
                continue;
            }
            Map<String, Object> m = new HashMap<>();
            m.put("studentId", s.getId());
            m.put("studentNo", s.getStudentNo());
            m.put("name", s.getName());
            m.put("className", s.getClassId() == null ? "-" : classNames.getOrDefault(s.getClassId(), "-"));
            m.put("relation", b.getRelation());
            m.put("origin", s.getOrigin());
            m.put("enrolled", s.getEnrolled());
            result.add(m);
        }
        return result;
    }

    /**
     * 个人版家长自建学生（未入学自测，问题三）。
     * 生成 origin=self 学生，绑定到当前家长，可录入 parent 来源自测成绩。
     */
    @Transactional
    public Map<String, Object> createSelfStudent(Account parent, String name, Long classId, String studentNo) {
        // 修正：自动生成学号加 UUID 短后缀，避免同毫秒并发撞 uk_student_no；并兜底唯一键冲突转 409
        String no = studentNo == null || studentNo.isBlank()
                ? "SELF" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase()
                : studentNo.trim();
        if (studentRepository.existsByStudentNo(no)) {
            throw new BizException("学号已存在，请更换");
        }
        Student s = new Student();
        s.setStudentNo(no);
        s.setName(name);
        s.setClassId(classId);
        s.setOrigin(Student.ORIGIN_SELF);
        s.setEnrolled(false);
        s.setMerged(false);
        s.setParentOwnerAccountId(parent.getId());
        s.setSchoolId(null); // 未入学无校
        try {
            studentRepository.save(s);
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            // 并发下 existsBy... 检查存在 TOCTOU 竞态：唯一键冲突统一转友好提示
            throw new BizException("学号已存在，请更换");
        }
        ParentStudentBind b = new ParentStudentBind();
        b.setParentAccountId(parent.getId());
        b.setStudentId(s.getId());
        b.setRelation("other");
        bindRepository.save(b);
        Map<String, Object> m = new HashMap<>();
        m.put("studentId", s.getId());
        m.put("studentNo", s.getStudentNo());
        m.put("name", s.getName());
        m.put("origin", Student.ORIGIN_SELF);
        return m;
    }

    /**
     * 认领合并（自建→校园）：用 学号+姓名+身份证后8位 命中校园版学生（当前实现未校验手机号，注释据实更正），
     * 建立正式绑定；自建记录置 merged=true（保留审计），校园学生 enrolled=true。
     * A5：将家长名下自建(self)学生的 parent 来源自测成绩，迁移挂接至被认领的校园正式学生，
     * 保证认领后家长仍能看到历史自测成绩；目标已存在同键时跳过（不覆盖既有数据）。
     */
    @Transactional
    public Map<String, Object> claimStudent(Account parent, BindRequest req) {
        Student sch = studentRepository.findByStudentNoAndNameAndDeletedFalse(req.getStudentNo(), req.getName())
                .orElseThrow(() -> new BizException("未找到匹配的在校学生"));
        if (req.getIdCardLast8() == null || req.getIdCardLast8().isBlank()) {
            throw new BizException("请输入身份证后8位");
        }
        String plainIdCard = aesCrypto.decrypt(sch.getIdCard());
        if (plainIdCard == null || !StudentService.last8(plainIdCard).equalsIgnoreCase(req.getIdCardLast8().trim().toUpperCase())) {
            throw new BizException("身份证后8位不匹配");
        }
        if (bindRepository.existsByParentAccountIdAndStudentId(parent.getId(), sch.getId())) {
            return mapOf(sch);
        }
        // 寻找家长名下匹配该生的自建记录：仅迁移「身份证一致」的自建生，防止多自建生张冠李戴
        String schIdCard = safeDecrypt(sch.getIdCard());
        List<ParentStudentBind> myBinds = bindRepository.findByParentAccountId(parent.getId());
        // P1-2 性能优化：一遍批量取家长名下全部绑定学生，替代循环内逐条 findById 的 N+1
        java.util.Map<Long, Student> stuById = studentRepository
                .findStudentsByIdsAndNotDeleted(myBinds.stream()
                        .map(ParentStudentBind::getStudentId)
                        .distinct().collect(java.util.stream.Collectors.toList()))
                .stream().collect(java.util.stream.Collectors.toMap(Student::getId, s -> s));
        for (ParentStudentBind b : myBinds) {
            Student mine = stuById.get(b.getStudentId());
            if (mine != null && Student.ORIGIN_SELF.equals(mine.getOrigin())
                    && !Boolean.TRUE.equals(mine.getMerged())) {
                // 身份强校验：目标校园生与该自建生须为同一人（解密后身份证一致）才允许迁移/合并
                String mineIdCard = safeDecrypt(mine.getIdCard());
                boolean samePerson = mineIdCard != null && mineIdCard.equalsIgnoreCase(schIdCard);
                if (samePerson) {
                    migrateSelfGrades(mine, sch);
                    mine.setMerged(true);
                    studentRepository.save(mine);
                }
            }
        }
        sch.setEnrolled(true);
        studentRepository.save(sch);
        ParentStudentBind nb = new ParentStudentBind();
        nb.setParentAccountId(parent.getId());
        nb.setStudentId(sch.getId());
        nb.setRelation(req.getRelation() == null || req.getRelation().isBlank() ? "other" : req.getRelation());
        bindRepository.save(nb);
        return mapOf(sch);
    }

    /**
     * A5：把自建学生 mine 名下 parent 来源成绩迁移到校园正式学生 sch 名下。
     * 冲突取舍：目标（校园生, exam, course, parent）已存在 → 跳过，保留既有数据、不回退父级；
     * 未冲突 → 直接改挂 studentId，使认领后历史自测成绩对校园生可见。
     */
    private void migrateSelfGrades(Student mine, Student sch) {
        for (Grade g : gradeRepository.findByStudentId(mine.getId())) {
            if (!GradeSources.PARENT.equals(g.getSource())) {
                continue; // 仅迁移家长自测来源，老师来源不受影响
            }
            if (gradeRepository.existsByStudentIdAndExamIdAndCourseIdAndSource(
                    sch.getId(), g.getExamId(), g.getCourseId(), GradeSources.PARENT)) {
                continue; // 唯一键 (student_id,exam_id,course_id,source) 冲突：跳过不覆盖
            }
            g.setStudentId(sch.getId());
            g.setSchoolId(sch.getSchoolId()); // 八轮强约束：改挂到校园生后刷新 school_id 快照
            gradeRepository.save(g);
        }
    }

    /** 解密身份证，失败/为空返回 null（供身份比对，不抛异常打断认领流程）。 */
    private String safeDecrypt(String cipher) {
        try {
            return aesCrypto.decrypt(cipher);
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> mapOf(Student s) {        Map<String, Object> m = new HashMap<>();
        m.put("studentId", s.getId());
        m.put("studentNo", s.getStudentNo());
        m.put("name", s.getName());
        m.put("className", classNames(s.getClassId()));
        m.put("enrolled", s.getEnrolled());
        return m;
    }

    /** 家长自助注册（带手机号）。 */
    @Transactional
    public void unbind(Account parent, Long studentId) {
        if (!bindRepository.existsByParentAccountIdAndStudentId(parent.getId(), studentId)) {
            throw new BizException(404, "绑定关系不存在");
        }
        bindRepository.deleteByParentAccountIdAndStudentId(parent.getId(), studentId);
    }

    private String classNames(Long classId) {
        if (classId == null) return "-";
        return classRepository.findById(classId).map(c -> c.getName()).orElse("-");
    }

    /** 读取家长个性化偏好（排名：只看本班/是否并列）。*/
    public Map<String, Object> getPreference(Account parent) {
        ParentPreference p = preferenceRepository.findByParentAccountId(parent.getId()).orElse(null);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("classScope", p == null ? true : p.isClassScope());
        m.put("showTies", p == null ? true : p.isShowTies());
        return m;
    }

    @Transactional
    public Map<String, Object> savePreference(Account parent, Map<String, Object> body) {
        ParentPreference p = preferenceRepository.findByParentAccountId(parent.getId()).orElseGet(() -> {
            ParentPreference n = new ParentPreference();
            n.setParentAccountId(parent.getId());
            return n;
        });
        Object cs = body.get("classScope");
        Object st = body.get("showTies");
        if (cs != null) p.setClassScope(asBool(cs, p.isClassScope()));
        if (st != null) p.setShowTies(asBool(st, p.isShowTies()));
        // P2-9 并发首建：uk_parent_account_id 唯一冲突时转 409，避免 500
        try {
            preferenceRepository.save(p);
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            throw new com.exam.common.BizException(409, "偏好已存在，请刷新后重试");
        }
        return getPreference(parent);
    }

    private boolean asBool(Object o, boolean def) {
        if (o instanceof Boolean b) return b;
        if (o instanceof String s) {
            if ("true".equalsIgnoreCase(s.trim())) return true;
            if ("false".equalsIgnoreCase(s.trim())) return false;
        }
        return def;
    }
}
