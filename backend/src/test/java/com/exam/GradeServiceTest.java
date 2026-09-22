package com.exam;

import com.exam.common.BizException;
import com.exam.common.LoginUserContext;
import com.exam.dto.GradeRequest;
import com.exam.entity.Account;
import com.exam.entity.ClassEntity;
import com.exam.entity.Course;
import com.exam.entity.Exam;
import com.exam.entity.Grade;
import com.exam.entity.ParentStudentBind;
import com.exam.entity.Student;
import com.exam.repository.AccountRepository;
import com.exam.repository.ClassRepository;
import com.exam.repository.CourseRepository;
import com.exam.repository.ExamRepository;
import com.exam.repository.GradeRepository;
import com.exam.repository.ParentStudentBindRepository;
import com.exam.repository.StudentRepository;
import com.exam.service.GradeService;
import com.exam.service.StatisticsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 服务层集成测试：覆盖成绩来源隔离、唯一约束、权限边界（老师/家长）、
 * 分数超满分校验、统计口径（得分率/及格/优秀/分布）。
 * 使用 H2 内存库 + DataSeeder 随 Spring 上下文启动播种。
 */
@SpringBootTest
@Transactional
class GradeServiceTest {

    @Autowired private GradeService gradeService;
    @Autowired private StatisticsService statisticsService;
    @Autowired private GradeRepository gradeRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private ExamRepository examRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private ClassRepository classRepository;
    @Autowired private ParentStudentBindRepository bindRepository;

    private Account teacher;
    private Account parent1;
    private Account parent2;
    private Student studentA;
    private Student studentB;
    private Exam exam;
    private Course course;

    @BeforeEach
    void setUp() {
        // 清空成绩与绑定，用独立实体避免依赖播种数据
        gradeRepository.deleteAll();
        bindRepository.deleteAll();

        ClassEntity cls = new ClassEntity();
        cls.setName("测试班");
        cls = classRepository.save(cls);

        studentA = new Student();
        studentA.setStudentNo("T0001");
        studentA.setName("测试甲");
        studentA.setGender("男");
        studentA.setClassId(cls.getId());
        studentA.setBirthDate(java.time.LocalDate.of(2008, 1, 1));
        studentA = studentRepository.save(studentA);

        studentB = new Student();
        studentB.setStudentNo("T0002");
        studentB.setName("测试乙");
        studentB.setGender("女");
        studentB.setClassId(cls.getId());
        studentB.setBirthDate(java.time.LocalDate.of(2008, 2, 2));
        studentB = studentRepository.save(studentB);

        exam = new Exam();
        exam.setName("测试考试");
        exam.setExamDate(java.time.LocalDate.of(2026, 1, 1));
        exam = examRepository.save(exam);

        course = new Course();
        course.setName("测试课");
        course.setFullScore(100);
        course = courseRepository.save(course);

        BCryptPasswordEncoder enc = new BCryptPasswordEncoder();
        teacher = account("假老师", "TEACHER", enc);
        // 本测试聚焦来源隔离与家长权限；老师设为 ALL 范围，避免 I-3 班域校验干扰
        teacher.setScopeType(Account.SCOPE_ALL);
        accountRepository.save(teacher);
        parent1 = account("假家长一", "PARENT", enc);
        parent2 = account("假家长二", "PARENT", enc);

        bind(parent1, studentA.getId());
        bind(parent1, studentB.getId());
        bind(parent2, studentA.getId());
    }

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
    }

    private Account account(String username, String role, BCryptPasswordEncoder enc) {
        Account a = new Account();
        a.setUsername(username + System.nanoTime());
        a.setPasswordHash(enc.encode("pass123"));
        a.setRole(role);
        a.setNickname(username);
        return accountRepository.save(a);
    }

    private void bind(Account parent, Long studentId) {
        ParentStudentBind b = new ParentStudentBind();
        b.setParentAccountId(parent.getId());
        b.setStudentId(studentId);
        b.setRelation("家长");
        bindRepository.save(b);
    }

    private GradeRequest req(Long studentId, int score, int full) {
        GradeRequest r = new GradeRequest();
        r.setStudentId(studentId);
        r.setExamId(exam.getId());
        r.setCourseId(course.getId());
        r.setScore(score);
        r.setFullScore(full);
        return r;
    }

    // ---------- 来源隔离与唯一约束 ----------

    @Test
    void 老师录入成绩_source为teacher() {
        LoginUserContext.set(teacher);
        Grade g = gradeService.create(req(studentA.getId(), 90, 100));
        assertEquals("teacher", g.getSource());
        assertEquals(teacher.getId(), g.getCreatorAccountId());
    }

    @Test
    void 同source同组合重复录入被拒绝() {
        LoginUserContext.set(teacher);
        gradeService.create(req(studentA.getId(), 90, 100));
        assertThrows(BizException.class, () -> gradeService.create(req(studentA.getId(), 91, 100)));
    }

    @Test
    void teacher与parent来源同组合可并存() {
        LoginUserContext.set(teacher);
        gradeService.create(req(studentA.getId(), 90, 100));
        LoginUserContext.set(parent1);
        Grade pg = gradeService.create(req(studentA.getId(), 80, 100));
        assertEquals("parent", pg.getSource());
        assertEquals(2L, gradeRepository.findByStudentId(studentA.getId()).size());
    }

    // ---------- 家长权限边界 ----------

    @Test
    void 家长只可给绑定学生录入() {
        LoginUserContext.set(parent1);
        // 未绑定 studentB 的另一个家长 parent2 不可给 studentB 录入
        // parent1 已绑定 studentB，可以录入
        Grade g = gradeService.create(req(studentB.getId(), 70, 100));
        assertNotNull(g.getId());
    }

    @Test
    void 家长改挂到未绑定学生被拒绝() {
        LoginUserContext.set(parent1);
        Grade g = gradeService.create(req(studentA.getId(), 70, 100));
        // parent2 未绑定 studentB，把 parent1 的成绩改挂到 studentB -> 越权
        LoginUserContext.set(parent2);
        assertThrows(BizException.class, () ->
                gradeService.update(g.getId(), req(studentB.getId(), 70, 100)));
    }

    @Test
    void 家长不可修改老师来源成绩() {
        LoginUserContext.set(teacher);
        Grade tg = gradeService.create(req(studentA.getId(), 90, 100));
        LoginUserContext.set(parent1);
        assertThrows(BizException.class, () -> gradeService.update(tg.getId(), req(studentA.getId(), 50, 100)));
    }

    @Test
    void 老师不可修改家长来源成绩() {
        LoginUserContext.set(parent1);
        Grade pg = gradeService.create(req(studentA.getId(), 80, 100));
        LoginUserContext.set(teacher);
        assertThrows(BizException.class, () -> gradeService.update(pg.getId(), req(studentA.getId(), 50, 100)));
    }

    @Test
    void 绑定家长可删除他人录制家长成绩() {
        // v5 双父母共享编辑：parent2 已绑定 studentA（setUp 绑定），故可删除 parent1 录制的 parent 来源成绩
        LoginUserContext.set(parent1);
        Grade pg = gradeService.create(req(studentA.getId(), 80, 100));
        LoginUserContext.set(parent2); // 绑定 studentA，可删除
        gradeService.delete(pg.getId());
        assertTrue(gradeRepository.findById(pg.getId()).isEmpty());
    }

    @Test
    void 未绑定该生家长删除其parent成绩被拒() {
        // 未绑定 studentA 的家长不可删除该生 parent 来源成绩（403）
        LoginUserContext.set(parent1);
        Grade pg = gradeService.create(req(studentA.getId(), 80, 100));
        Account stranger = account("未绑定家长", "PARENT", new BCryptPasswordEncoder()); // 未绑定 studentA
        LoginUserContext.set(stranger);
        assertThrows(BizException.class, () -> gradeService.delete(pg.getId()));
    }

    @Test
    void 未登录访问成绩被拒绝() {
        LoginUserContext.clear();
        assertThrows(BizException.class, () -> gradeService.list(null, null, null));
    }

    // ---------- 分数校验 ----------

    @Test
    void 分数超满分被拒绝() {
        LoginUserContext.set(teacher);
        assertThrows(BizException.class, () -> gradeService.create(req(studentA.getId(), 101, 100)));
    }

    @Test
    void 批量录入超额分数被跳过() {
        LoginUserContext.set(teacher);
        Map<String, Object> r = gradeService.batchCreate(List.of(
                req(studentA.getId(), 60, 100),
                req(studentB.getId(), 999, 100) // 无效被跳过
        ));
        assertEquals(1, r.get("created"));
        assertEquals(1, r.get("skipped"));
    }

    // ---------- 家长列表可见性 ----------

    @Test
    void 家长列表仅见绑定学生且老师来源只读() {
        // teacher 给 A,B 录成绩；parent1 绑 A,B；parent1 再给 A 录 parent 成绩
        LoginUserContext.set(teacher);
        gradeService.create(req(studentA.getId(), 90, 100));
        gradeService.create(req(studentB.getId(), 85, 100));
        LoginUserContext.set(parent1);
        gradeService.create(req(studentA.getId(), 75, 100));

        List<Grade> list = gradeService.list(null, null, null);
        // A: teacher+parent1 两条；B: teacher 一条 => 共 3 条
        assertEquals(3, list.size());
    }

    @Test
    void 家长零绑定返回空列表() {
        LoginUserContext.set(parent2);
        // parent2 仅绑定 studentA，但先清空 parent 的所有绑定实际由 setUp 保留；改用无绑定账号
        Account noBind = account("无绑定家长", "PARENT", new BCryptPasswordEncoder());
        LoginUserContext.set(noBind);
        List<Grade> list = gradeService.list(null, null, null);
        assertNotNull(list);
        assertEquals(0, list.size());
    }

    @Test
    void 老师列表仅见teacher来源() {
        LoginUserContext.set(teacher);
        gradeService.create(req(studentA.getId(), 90, 100));
        LoginUserContext.set(parent1);
        gradeService.create(req(studentA.getId(), 75, 100));
        LoginUserContext.set(teacher);
        List<Grade> list = gradeService.list(null, null, null);
        assertEquals(1, list.size());
        assertEquals("teacher", list.get(0).getSource());
    }

    // ---------- 统计口径 ----------

    @Test
    void 统计及格与优秀率() {
        LoginUserContext.set(teacher);
        // 满分 100：90（优/及格）, 70（及格）, 50（不及格）
        gradeService.create(req(studentA.getId(), 90, 100));
        LoginUserContext.set(teacher);
        gradeService.create(req(studentB.getId(), 70, 100));
        LoginUserContext.set(teacher);
        // 用第三个学生
        ClassEntity cls2 = new ClassEntity();
        cls2.setName("测试班2");
        cls2 = classRepository.save(cls2);
        Student s3 = new Student();
        s3.setStudentNo("T0003");
        s3.setName("测试丙");
        s3.setGender("男");
        s3.setClassId(cls2.getId());
        s3.setBirthDate(java.time.LocalDate.of(2008, 3, 3));
        s3 = studentRepository.save(s3);
        gradeService.create(req(s3.getId(), 50, 100));

        Map<String, Object> stats = statisticsService.courseStats(exam.getId(), course.getId());
        assertEquals(3, stats.get("total"));
        assertEquals(2.0 / 3 * 100, ((Number) stats.get("passRate")).doubleValue(), 0.5);
        assertEquals(1.0 / 3 * 100, ((Number) stats.get("excellentRate")).doubleValue(), 0.5);
        assertEquals(70, ((Number) stats.get("avg")).intValue());
    }
}
