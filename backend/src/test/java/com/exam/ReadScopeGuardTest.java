package com.exam;

import com.exam.common.LoginUserContext;
import com.exam.dto.CreateLevelRequest;
import com.exam.dto.CreateSchoolRequest;
import com.exam.dto.GradeRequest;
import com.exam.dto.TeacherClassAssignRequest;
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
import com.exam.repository.LevelRepository;
import com.exam.repository.SchoolRepository;
import com.exam.repository.StudentRepository;
import com.exam.service.GradeService;
import com.exam.service.LevelService;
import com.exam.service.SchoolService;
import com.exam.service.StatisticsService;
import com.exam.service.TeacherClassService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 老师读路径越权收敛回归：CLASS 老师仅可见其绑定班级的数据，
 * 未绑定班级的 comparison/progress/list/student 一律不得透出越班数据。
 */
@SpringBootTest
@Transactional
class ReadScopeGuardTest {

    @Autowired private SchoolService schoolService;
    @Autowired private LevelService levelService;
    @Autowired private TeacherClassService teacherClassService;
    @Autowired private GradeService gradeService;
    @Autowired private StatisticsService statisticsService;
    @Autowired private AccountRepository accountRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private GradeRepository gradeRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private ExamRepository examRepository;
    @Autowired private SchoolRepository schoolRepository;
    @Autowired private LevelRepository levelRepository;
    @Autowired private ClassRepository classRepository;

    private final BCryptPasswordEncoder enc = new BCryptPasswordEncoder();
    private int seq = 0;

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
    }

    @Test
    void CLASS老师comparison仅含可见班级() {
        var ctx = buildTwoClassCtx();
        LoginUserContext.set(ctx.teacher);
        // 为本班学生录成绩（本班已入库，越班不入库，comparison 理应不含越班班级）
        gradeService.create(gradeReq(ctx.sIn.getId(), ctx.examId, ctx.course));

        Map<String, Object> cmp = statisticsService.comparison(ctx.examId, ctx.course.getId(), null);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> classes = (List<Map<String, Object>>) cmp.get("classes");
        Set<Long> shownClassIds = new java.util.HashSet<>();
        for (Map<String, Object> c : classes) {
            if (c.get("classId") != null) shownClassIds.add(((Number) c.get("classId")).longValue());
        }
        // CLASS 老师绑定 A 班：不应出现 B 班
        assertFalse(shownClassIds.contains(ctx.cB.getId()), "comparison 不应含越班 B 班");
        // 本班应出现（本班已有成绩，因此 overall 非空、A 班应在列表中）
        assertTrue(shownClassIds.contains(ctx.cA.getId()), "comparison 应含本班 A 班");
    }

    @Test
    void CLASS老师progress仅含可见班级() {
        var ctx = buildTwoClassCtx();
        LoginUserContext.set(ctx.teacher);
        // 本班学生成绩由老师正常写入；越班学生成绩整枝入库（绕过班域校验制造越班数据）
        gradeService.create(gradeReq(ctx.sIn.getId(), ctx.examId, ctx.course));
        gradeService.create(gradeReq(ctx.sIn.getId(), ctx.examId2, ctx.course));
        saveGrade(ctx.sOut.getId(), ctx.examId, ctx.course);
        saveGrade(ctx.sOut.getId(), ctx.examId2, ctx.course);

        Map<String, Object> prog = statisticsService.progress(ctx.course.getId(), 100, false, null);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) prog.get("items");
        boolean containsOut = items.stream().anyMatch(i -> ctx.sOut.getId().equals(((Number) i.get("studentId")).longValue()));
        assertFalse(containsOut, "progress 不应含越班 S_OUT 学生");
        boolean containsIn = items.stream().anyMatch(i -> ctx.sIn.getId().equals(((Number) i.get("studentId")).longValue()));
        assertTrue(containsIn, "progress 应含本班 S_IN 学生");
    }

    @Test
    void CLASS老师list仅含可见班级成绩() {
        var ctx = buildTwoClassCtx();
        LoginUserContext.set(ctx.teacher);
        gradeService.create(gradeReq(ctx.sIn.getId(), ctx.examId, ctx.course));
        saveGrade(ctx.sOut.getId(), ctx.examId, ctx.course);

        var grades = gradeService.list(ctx.examId, ctx.course.getId(), null);
        boolean containsOut = grades.stream().anyMatch(g -> g.getStudentId().equals(ctx.sOut.getId()));
        boolean containsIn = grades.stream().anyMatch(g -> g.getStudentId().equals(ctx.sIn.getId()));
        assertFalse(containsOut, "list 不应返回越班学生成绩");
        assertTrue(containsIn, "list 应返回本班学生成绩");
    }

    @Test
    void 无绑定班级老师list返回空() {
        var ctx = buildTwoClassCtx();
        // 老师未绑定任何班级（CLASS 且空绑定）
        Account noBind = account("未绑定老师", Account.ROLE_TEACHER, ctx.school.getId());
        LoginUserContext.set(noBind);
        saveGrade(ctx.sIn.getId(), ctx.examId, ctx.course); // 直接造库中数据（写操作本会被拒，故绕过）
        // 无绑定老师查询应为空（其可见班级为空）
        var grades = gradeService.list(ctx.examId, ctx.course.getId(), null);
        assertTrue(grades.isEmpty(), "无绑定班级老师 list 应为空");
    }

    // ---------- 装配 ----------

    private TwoClass ctx; // 占位避免歧义
    private TwoClass buildTwoClassCtx() {
        School school = schoolService.create(schoolReq("读域校", "RD" + (++seq)));
        Level level = levelService.create(levelReq(school.getId(), "初一年级"));
        ClassEntity cA = classRepository.save(withClass("读A班", level.getId(), school.getId()));
        ClassEntity cB = classRepository.save(withClass("读B班", level.getId(), school.getId()));
        Account teacher = account("读域老师", Account.ROLE_TEACHER, school.getId()); // SCOPE_CLASS
        teacherClassService.assign(assignReq(teacher.getId(), List.of(cA.getId())));
        Student sIn = student("SIN" + seq, cA.getId());
        Student sOut = student("SOUT" + seq, cB.getId());
        Course course = courseRepository.save(newCourse("科" + seq, 100));
        Exam e1 = examRepository.save(newExam("考" + seq + "a"));
        Exam e2 = examRepository.save(newExam("考" + seq + "b"));
        TwoClass t = new TwoClass();
        t.school = school; t.cA = cA; t.cB = cB; t.teacher = teacher;
        t.sIn = sIn; t.sOut = sOut; t.course = course;
        t.examId = e1.getId(); t.examId2 = e2.getId();
        return t;
    }

    private boolean overallEmpty(Map<String, Object> cmp) {
        Object o = cmp.get("overall");
        return o instanceof Map && ((Number) ((Map<?, ?>) o).get("total")).intValue() == 0;
    }

    private static class TwoClass {
        School school; ClassEntity cA; ClassEntity cB; Account teacher;
        Student sIn; Student sOut; Course course; Long examId; Long examId2;
    }

    private CreateSchoolRequest schoolReq(String name, String code) {
        CreateSchoolRequest r = new CreateSchoolRequest();
        r.setName(name); r.setCode(code); return r;
    }

    private CreateLevelRequest levelReq(Long schoolId, String name) {
        CreateLevelRequest r = new CreateLevelRequest();
        r.setSchoolId(schoolId); r.setName(name); return r;
    }

    private TeacherClassAssignRequest assignReq(Long teacherId, List<Long> classIds) {
        TeacherClassAssignRequest r = new TeacherClassAssignRequest();
        r.setTeacherAccountId(teacherId); r.setClassIds(classIds); return r;
    }

    private ClassEntity withClass(String name, Long levelId, Long schoolId) {
        ClassEntity c = new ClassEntity();
        c.setName(name); c.setLevelId(levelId); c.setSchoolId(schoolId); return c;
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

    private Course newCourse(String name, int full) {
        Course c = new Course();
        c.setName(name); c.setFullScore(full); return c;
    }

    private Exam newExam(String name) {
        Exam e = new Exam();
        e.setName(name);
        e.setExamDate(java.time.LocalDate.of(2026, 3, 1));
        return e;
    }

    private GradeRequest gradeReq(Long studentId, Long examId, Course course) {
        GradeRequest r = new GradeRequest();
        r.setStudentId(studentId);
        r.setExamId(examId);
        r.setCourseId(course.getId());
        r.setScore(80);
        r.setFullScore(course.getFullScore());
        return r;
    }

    /** 直接构造并保存越班成绩（绕过班域校验，模拟库中已存在的越班数据）。 */
    private void saveGrade(Long studentId, Long examId, Course course) {
        Grade g = new Grade();
        g.setStudentId(studentId);
        g.setExamId(examId);
        g.setCourseId(course.getId());
        g.setScore(80);
        g.setFullScore(course.getFullScore());
        g.setSource("teacher");
        gradeRepository.save(g);
    }
}
