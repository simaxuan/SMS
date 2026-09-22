package com.exam;

import com.exam.common.BizException;
import com.exam.common.LoginUserContext;
import com.exam.dto.CreateLevelRequest;
import com.exam.dto.CreateSchoolRequest;
import com.exam.dto.GradeRequest;
import com.exam.dto.TeacherClassAssignRequest;
import com.exam.entity.Account;
import com.exam.entity.ClassEntity;
import com.exam.entity.Grade;
import com.exam.entity.Level;
import com.exam.entity.School;
import com.exam.entity.Student;
import com.exam.entity.TeacherClass;
import com.exam.repository.AccountRepository;
import com.exam.repository.ClassRepository;
import com.exam.repository.GradeRepository;
import com.exam.repository.LevelRepository;
import com.exam.repository.SchoolRepository;
import com.exam.repository.StudentRepository;
import com.exam.repository.TeacherClassRepository;
import com.exam.service.DataScopeService;
import com.exam.service.GradeService;
import com.exam.service.LevelService;
import com.exam.service.SchoolService;
import com.exam.service.TeacherClassService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 多租户跨校隔离测试：学校/级部/班级层级归属 + teacher_class 绑定驱动的数据范围隔离。
 * 验证不同学校的班级在 DataScopeService 下对各自老师互不可见。
 */
@SpringBootTest
@Transactional
class SchoolLevelMultiTenantTest {

    @Autowired private SchoolService schoolService;
    @Autowired private LevelService levelService;
    @Autowired private TeacherClassService teacherClassService;
    @Autowired private DataScopeService dataScopeService;
    @Autowired private GradeService gradeService;
    @Autowired private SchoolRepository schoolRepository;
    @Autowired private LevelRepository levelRepository;
    @Autowired private ClassRepository classRepository;
    @Autowired private TeacherClassRepository teacherClassRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private GradeRepository gradeRepository;

    private final BCryptPasswordEncoder enc = new BCryptPasswordEncoder();
    private int seq = 0;

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
    }

    @Test
    void 学校级部班级层级归属正确() {
        School a = schoolService.create(schoolReq("甲校", "A01"));
        School b = schoolService.create(schoolReq("乙校", "B02"));

        Level la = levelService.create(levelReq(a.getId(), "初一"));
        Level lb = levelService.create(levelReq(b.getId(), "高一"));

        // 同校不允许同名级部
        assertThrows(Exception.class, () -> levelService.create(levelReq(a.getId(), "初一")));

        ClassEntity ca = classRepository.save(withClass("1班", la.getId(), a.getId()));
        // 乙校「1班」复用已创建的「高一」级部（同名班级跨校允许，因唯一键含 school_id）
        classRepository.save(withClass("1班", lb.getId(), b.getId()));

        List<Level> aLevels = levelService.listBySchool(a.getId());
        assertEquals(1, aLevels.size());
        assertEquals(la.getId(), aLevels.get(0).getId());
    }

    @Test
    void 跨校班级对老师数据范围互不可见() {
        School a = schoolService.create(schoolReq("甲校", "A03"));
        School b = schoolService.create(schoolReq("乙校", "B04"));
        Level la = levelService.create(levelReq(a.getId(), "初一"));
        Level lb = levelService.create(levelReq(b.getId(), "高一"));

        ClassEntity ca = classRepository.save(withClass("A班", la.getId(), a.getId()));
        ClassEntity cb = classRepository.save(withClass("B班", lb.getId(), b.getId()));

        Account teacherA = account("甲老师", Account.ROLE_TEACHER, a.getId());
        teacherClassService.assign(assignReq(teacherA.getId(), List.of(ca.getId())));

        LoginUserContext.set(teacherA);
        Set<Long> visible = dataScopeService.visibleClassIds(teacherA);
        assertNotNull(visible);
        assertTrue(visible.contains(ca.getId()), "应可见本校 A 班");
        assertFalse(visible.contains(cb.getId()), "不应可见乙校 B 班");

        Account teacherB = account("乙老师", Account.ROLE_TEACHER, b.getId());
        teacherClassService.assign(assignReq(teacherB.getId(), List.of(cb.getId())));
        LoginUserContext.set(teacherB);
        Set<Long> visibleB = dataScopeService.visibleClassIds(teacherB);
        assertTrue(visibleB.contains(cb.getId()));
        assertFalse(visibleB.contains(ca.getId()));
    }

    @Test
    void teacherClass绑定查询与解除() {
        School a = schoolService.create(schoolReq("丙校", "C05"));
        Level la = levelService.create(levelReq(a.getId(), "初一"));
        ClassEntity c1 = classRepository.save(withClass("C1", la.getId(), a.getId()));
        ClassEntity c2 = classRepository.save(withClass("C2", la.getId(), a.getId()));
        Account teacher = account("丙老师", Account.ROLE_TEACHER, a.getId());

        List<TeacherClass> assigned = teacherClassService.assign(assignReq(teacher.getId(), List.of(c1.getId(), c2.getId())));
        assertEquals(2, assigned.size());

        List<TeacherClass> byTeacher = teacherClassService.query(teacher.getId(), null);
        assertEquals(2, byTeacher.size());

        List<TeacherClass> byClass = teacherClassService.query(null, c1.getId());
        assertEquals(1, byClass.size());

        // 重复绑定幂等
        assertEquals(0, teacherClassService.assign(assignReq(teacher.getId(), List.of(c1.getId()))).size());
        assertEquals(2, teacherClassService.query(teacher.getId(), null).size());

        teacherClassService.remove(byTeacher.get(0).getId());
        assertEquals(1, teacherClassService.query(teacher.getId(), null).size());
    }

    @Test
    void 跨校班级绑定被拒绝() {
        School a = schoolService.create(schoolReq("戊校", "E07"));
        School b = schoolService.create(schoolReq("己校", "F08"));
        Level lb = levelService.create(levelReq(b.getId(), "高一"));
        ClassEntity cb = classRepository.save(withClass("己1班", lb.getId(), b.getId()));
        Account teacherA = account("戊老师", Account.ROLE_TEACHER, a.getId());

        // I-4：把乙校班级绑定给甲校老师 → 拒绝
        BizException ex = assertThrows(BizException.class,
                () -> teacherClassService.assign(assignReq(teacherA.getId(), List.of(cb.getId()))));
        assertTrue(ex.getMessage().contains("其他学校"), "应提示跨校不可绑定, 实际:" + ex.getMessage());
    }

    @Test
    void 老师仅可录入其所属班级学生成绩_越班被拒() {
        School a = schoolService.create(schoolReq("庚校", "G09"));
        Level la = levelService.create(levelReq(a.getId(), "初一"));
        ClassEntity c1 = classRepository.save(withClass("庚1班", la.getId(), a.getId()));
        ClassEntity c2 = classRepository.save(withClass("庚2班", la.getId(), a.getId()));
        Account teacher = account("庚老师", Account.ROLE_TEACHER, a.getId()); // CLASS 范围
        teacherClassService.assign(assignReq(teacher.getId(), List.of(c1.getId())));

        Student sIn = student("SIN", c1.getId());
        Student sOut = student("SOUT", c2.getId());

        LoginUserContext.set(teacher);
        // 所属班级学生 → 允许
        assertDoesNotThrow(() -> gradeService.create(gradeReq(sIn.getId())));
        // 越班学生 → 403
        assertThrows(BizException.class, () -> gradeService.create(gradeReq(sOut.getId())));
    }

    @Test
    void 老师录入成绩_冗余school_id快照回填() {
        School a = schoolService.create(schoolReq("辛校", "H10"));
        Level la = levelService.create(levelReq(a.getId(), "初一"));
        ClassEntity c1 = classRepository.save(withClass("辛1班", la.getId(), a.getId()));
        Account teacher = account("辛老师", Account.ROLE_TEACHER, a.getId());
        teacherClassService.assign(assignReq(teacher.getId(), List.of(c1.getId())));

        Student sIn = new Student();
        sIn.setStudentNo("SIN" + (++seq) + System.nanoTime());
        sIn.setName("测试");
        sIn.setClassId(c1.getId());
        sIn.setSchoolId(a.getId());
        studentRepository.save(sIn);

        LoginUserContext.set(teacher);
        Grade g = gradeService.create(gradeReq(sIn.getId()));
        assertNotNull(g.getSchoolId(), "写路径应回填 school_id 快照, 实际为 null");
        assertEquals(a.getId(), g.getSchoolId(), "快照应等于目标学生所属校");
    }

    @Test
    void 按school_id过滤读取成绩_跨校数据不串() {
        School a = schoolService.create(schoolReq("壬校", "I11"));
        School b = schoolService.create(schoolReq("癸校", "J12"));
        Level la = levelService.create(levelReq(a.getId(), "初一"));
        Level lb = levelService.create(levelReq(b.getId(), "高一"));

        // 两校各造一名学生与一份成绩（school_id 快照各自归属）
        Student sa = studentWithSchool("SA", ra(la, a), a.getId());
        Student sb = studentWithSchool("SB", ra(lb, b), b.getId());
        gradeFor(sa, 80);
        gradeFor(sb, 90);

        // Repository 行级 school 过滤：整校拉取互不串
        assertEquals(1, gradeRepository.findBySchoolId(a.getId()).size(), "甲校仅含本校成绩");
        assertEquals(1, gradeRepository.findBySchoolId(b.getId()).size(), "乙校仅含本校成绩");
        assertEquals(0, gradeRepository.findBySchoolId(999999L).size(), "未知校无成绩");
    }

    private ClassEntity ra(Level lv, School sch) {
        return classRepository.save(withClass("R" + (++seq) + System.nanoTime(), lv.getId(), sch.getId()));
    }

    private Student studentWithSchool(String no, ClassEntity clazz, Long schoolId) {
        Student s = new Student();
        s.setStudentNo(no + (++seq) + System.nanoTime());
        s.setName("测试");
        s.setClassId(clazz.getId());
        s.setSchoolId(schoolId);
        return studentRepository.save(s);
    }

    private void gradeFor(Student s, int score) {
        Grade g = new Grade();
        g.setStudentId(s.getId());
        g.setExamId(1L);
        g.setCourseId(1L);
        g.setScore(score);
        g.setFullScore(100);
        g.setSource("teacher");
        g.setSchoolId(s.getSchoolId()); // 模拟八轮强约束写路径回填
        gradeRepository.save(g);
    }

    private CreateSchoolRequest schoolReq(String name, String code) {
        CreateSchoolRequest r = new CreateSchoolRequest();
        r.setName(name);
        r.setCode(code);
        return r;
    }

    private CreateLevelRequest levelReq(Long schoolId, String name) {
        CreateLevelRequest r = new CreateLevelRequest();
        r.setSchoolId(schoolId);
        r.setName(name);
        return r;
    }

    private TeacherClassAssignRequest assignReq(Long teacherId, List<Long> classIds) {
        TeacherClassAssignRequest r = new TeacherClassAssignRequest();
        r.setTeacherAccountId(teacherId);
        r.setClassIds(classIds);
        return r;
    }

    private ClassEntity withClass(String name, Long levelId, Long schoolId) {
        ClassEntity c = new ClassEntity();
        c.setName(name);
        c.setLevelId(levelId);
        c.setSchoolId(schoolId);
        return c;
    }

    private Account account(String username, String role, Long schoolId) {
        Account a = new Account();
        a.setUsername(username + (++seq) + System.nanoTime());
        a.setPasswordHash(enc.encode("pass123"));
        a.setRole(role);
        a.setNickname(username);
        a.setSchoolId(schoolId);
        a.setScopeType(Account.SCOPE_CLASS);
        return accountRepository.save(a);
    }

    private Student student(String no, Long classId) {
        Student s = new Student();
        s.setStudentNo(no + (++seq) + System.nanoTime());
        s.setName("测试");
        s.setClassId(classId);
        return studentRepository.save(s);
    }

    private GradeRequest gradeReq(Long studentId) {
        GradeRequest r = new GradeRequest();
        r.setStudentId(studentId);
        r.setExamId(1L);
        r.setCourseId(1L);
        r.setScore(80);
        r.setFullScore(100);
        return r;
    }
}
