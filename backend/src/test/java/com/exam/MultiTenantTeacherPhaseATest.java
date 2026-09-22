package com.exam;

import com.exam.common.BizException;
import com.exam.common.LoginUserContext;
import com.exam.dto.CreateLevelRequest;
import com.exam.dto.CreateSchoolRequest;
import com.exam.dto.CreateTeacherRequest;
import com.exam.entity.Account;
import com.exam.entity.ClassEntity;
import com.exam.entity.Course;
import com.exam.entity.Exam;
import com.exam.entity.Grade;
import com.exam.entity.Level;
import com.exam.entity.School;
import com.exam.entity.Student;
import com.exam.repository.AccountRepository;
import com.exam.repository.ClassRepository;
import com.exam.repository.CourseRepository;
import com.exam.repository.ExamRepository;
import com.exam.repository.GradeRepository;
import com.exam.repository.StudentRepository;
import com.exam.service.AuthService;
import com.exam.service.DictionaryService;
import com.exam.service.ExamService;
import com.exam.service.LevelService;
import com.exam.service.RankingService;
import com.exam.service.SchoolService;
import com.exam.service.StudentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 阶段 A（多租户改造）专项验证：
 * - 教师账号创建（CLASS 绑本校班 / SCOPE_SCHOOL；跨校绑定被拒）
 * - 读路径 school 下推（学生/考试/字典按校隔离）
 * - 排名 scope 三级语义（class 按班 / level 按级部）
 */
@SpringBootTest
@Transactional
class MultiTenantTeacherPhaseATest {

    @Autowired private AuthService authService;
    @Autowired private SchoolService schoolService;
    @Autowired private LevelService levelService;
    @Autowired private DictionaryService dictionaryService;
    @Autowired private StudentService studentService;
    @Autowired private ExamService examService;
    @Autowired private RankingService rankingService;
    @Autowired private AccountRepository accountRepository;
    @Autowired private ClassRepository classRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private ExamRepository examRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private GradeRepository gradeRepository;

    private final BCryptPasswordEncoder enc = new BCryptPasswordEncoder();
    private int seq = 0;

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
    }

    @Test
    void 教师创建_CLASS绑定本校班成功_跨校班级被拒() {
        Ctx x = twoLevelSchool("甲校", "T01");
        School other = mkSchool("他校", "T02");
        // 本校班 → 成功
        Account t = authService.createTeacher(teacherReq("clsteach", x.school.getId(), Account.SCOPE_CLASS, List.of(x.c1.getId())));
        assertNotNull(t.getId());
        assertEquals(Account.SCOPE_CLASS, t.getScopeType());
        assertEquals(x.school.getId(), t.getSchoolId());
        // 跨校班级绑定 → 被拒
        Long otherClassId = classRepository.save(classOf("他班", x.l1.getId(), other.getId())).getId();
        assertThrows(BizException.class,
                () -> authService.createTeacher(teacherReq("xteach", x.school.getId(), Account.SCOPE_CLASS, List.of(otherClassId))));
    }

    @Test
    void 教师创建_SCHOOL范围_可见本校全部班级() {
        Ctx x = twoLevelSchool("乙校", "T03");
        Account t = authService.createTeacher(teacherReq("schteach", x.school.getId(), Account.SCOPE_SCHOOL, null));
        assertEquals(Account.SCOPE_SCHOOL, t.getScopeType());
        LoginUserContext.set(t);
        // CLASS 需绑班校验由 createTeacher 保证；此处直接校验可见集合=本校全部班
        java.util.Set<Long> visible = new com.exam.service.DataScopeService(null, classRepository).visibleClassIds(t);
        assertTrue(visible.contains(x.c1.getId()));
        assertTrue(visible.contains(x.c2.getId()));
    }

    @Test
    void 读路径按校下推_学生考试字典隔离() {
        Ctx a = twoLevelSchool("丙校", "T05");
        Ctx b = twoLevelSchool("丁校", "T06");
        // 各校造学生/考试/课程（成绩非本测试断言点，故不造成绩）
        student("SA", a.c1.getId(), a.school.getId());
        student("SB", b.c1.getId(), b.school.getId());
        exam("考A", a.school.getId());
        exam("考B", b.school.getId());
        course("课A", a.school.getId());
        course("课B", b.school.getId());

        Account ta = account("阅甲", Account.SCOPE_ALL, a.school.getId());
        LoginUserContext.set(ta);
        // 考试：仅 A 校
        List<Exam> exams = examService.list();
        assertTrue(exams.stream().allMatch(e -> a.school.getId().equals(e.getSchoolId())));
        assertFalse(exams.stream().anyMatch(e -> b.school.getId().equals(e.getSchoolId())));
        // 班级字典：仅 A 校
        assertTrue(dictionaryService.listClasses().stream().allMatch(c -> a.school.getId().equals(c.getSchoolId())));
        // 学生：仅 A 校
        Page<Student> st = studentService.list(null, PageRequest.of(0, 100));
        assertTrue(st.getContent().stream().allMatch(s -> a.school.getId().equals(s.getSchoolId())));
        assertFalse(st.getContent().stream().anyMatch(s -> b.school.getId().equals(s.getSchoolId())));
    }

    @Test
    void 排名scope_class按班_level按级部() {
        // 一个学校、两个级部、各一班、两生同考试同课程
        School s = mkSchool("排名校", "T08");
        Level l1 = level(s.getId(), "初一");
        Level l2 = level(s.getId(), "初二");
        ClassEntity ca = classRepository.save(classOf("初一1班", l1.getId(), s.getId()));
        ClassEntity cb = classRepository.save(classOf("初二1班", l2.getId(), s.getId()));
        Account teacher = account("排师", Account.SCOPE_SCHOOL, s.getId());
        LoginUserContext.set(teacher);

        Exam e = exam("排名考", s.getId());
        Course c = course("排课", s.getId());
        // 初一学生 50 分、初二学生 90 分 → 班级内各自唯一名次
        Student s1 = student("R1", ca.getId(), s.getId());
        Student s2 = student("R2", cb.getId(), s.getId());
        grade(s1, e, c, 50, s.getId());
        grade(s2, e, c, 90, s.getId());

        // scope=class：分班分组，各 1 名
        Map<String, Object> rClass = rankingService.ranking(e.getId(), c.getId(), null, true, "class");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> r1 = (List<Map<String, Object>>) rClass.get("rows");
        assertEquals(2, r1.size());
        for (Map<String, Object> row : r1) {
            assertEquals(1, ((Number) row.get("rank")).longValue(), "班内各自第 1 名");
        }
        // scope=level：按级部分组，两生分属不同级部，各第 1 名
        Map<String, Object> rLevel = rankingService.ranking(e.getId(), c.getId(), null, true, "level");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> r2 = (List<Map<String, Object>>) rLevel.get("rows");
        assertEquals(2, r2.size());
        for (Map<String, Object> row : r2) {
            assertNotNull(row.get("scopeGroup"), "level 分组应有 scopeGroup 级部标签");
            assertEquals(1, ((Number) row.get("rank")).longValue(), "级部内各自第 1 名");
        }
    }

    // ---------- 装配 ----------

    private static class Ctx {
        School school; Level l1; ClassEntity c1; ClassEntity c2;
    }

    private Ctx twoLevelSchool(String name, String code) {
        Ctx x = new Ctx();
        x.school = mkSchool(name, code);
        x.l1 = level(x.school.getId(), "初一");
        Level l2 = level(x.school.getId(), "初二");
        x.c1 = classRepository.save(classOf("初1班", x.l1.getId(), x.school.getId()));
        x.c2 = classRepository.save(classOf("初2班", l2.getId(), x.school.getId()));
        return x;
    }

    private School mkSchool(String name, String code) {
        CreateSchoolRequest r = new CreateSchoolRequest();
        r.setName(name + seq);
        r.setCode(code + (++seq));
        return schoolService.create(r);
    }

    private Level level(Long schoolId, String name) {
        CreateLevelRequest r = new CreateLevelRequest();
        r.setSchoolId(schoolId);
        r.setName(name + (++seq));
        return levelService.create(r);
    }

    private CreateTeacherRequest teacherReq(String uname, Long schoolId, String scope, List<Long> classIds) {
        CreateTeacherRequest r = new CreateTeacherRequest();
        r.setUsername(uname + (++seq) + System.nanoTime());
        r.setPassword("pass123456");
        r.setNickname(uname);
        r.setSchoolId(schoolId);
        r.setScopeType(scope);
        r.setClassIds(classIds);
        return r;
    }

    private ClassEntity classOf(String name, Long levelId, Long schoolId) {
        ClassEntity c = new ClassEntity();
        c.setName(name + (++seq));
        c.setLevelId(levelId);
        c.setSchoolId(schoolId);
        return c;
    }

    private Account account(String uname, String scope, Long schoolId) {
        Account a = new Account();
        a.setUsername(uname + (++seq) + System.nanoTime());
        a.setPasswordHash(enc.encode("pass123"));
        a.setRole(Account.ROLE_TEACHER);
        a.setNickname(uname);
        a.setSchoolId(schoolId);
        a.setScopeType(scope);
        return accountRepository.save(a);
    }

    private Student student(String no, Long classId, Long schoolId) {
        Student s = new Student();
        s.setStudentNo(no + (++seq) + System.nanoTime());
        s.setName("测试");
        s.setGender("男");
        s.setClassId(classId);
        s.setSchoolId(schoolId);
        s.setFatherPhone("13900000051");
        s.setMotherPhone("13800000052");
        return studentRepository.save(s);
    }

    private Exam exam(String name, Long schoolId) {
        Exam e = new Exam();
        e.setName(name + (++seq));
        e.setExamDate(java.time.LocalDate.of(2026, 5, 1));
        e.setSchoolId(schoolId);
        return examRepository.save(e);
    }

    private Course course(String name, Long schoolId) {
        Course c = new Course();
        c.setName(name + (++seq));
        c.setFullScore(100);
        c.setSchoolId(schoolId);
        return courseRepository.save(c);
    }

    private void grade(Student s, Exam e, Course c, int score, Long schoolId) {
        Grade g = new Grade();
        g.setStudentId(s.getId());
        g.setExamId(e.getId());
        g.setCourseId(c.getId());
        g.setScore(score);
        g.setFullScore(100);
        g.setSource("teacher");
        g.setSchoolId(schoolId);
        gradeRepository.save(g);
    }
}
