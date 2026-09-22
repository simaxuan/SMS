package com.exam;

import com.exam.common.LoginUserContext;
import com.exam.dto.GradeRequest;
import com.exam.entity.Account;
import com.exam.entity.ClassEntity;
import com.exam.entity.Course;
import com.exam.entity.Exam;
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
import com.exam.service.ParentService;
import com.exam.service.SettingsService;
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
 * 家长排名隐私档位（aggregate/limited/full）测试 + 认领 merged 标记。
 * 同一班级两名学生（A 已绑定、B 未绑定）均有老师成绩，验证不同档位下家长可见行数差异。
 */
@SpringBootTest
@Transactional
class ParentRankPrivacyTest {

    @Autowired private GradeService gradeService;
    @Autowired private StatisticsService statisticsService;
    @Autowired private SettingsService settingsService;
    @Autowired private ParentService parentService;
    @Autowired private com.exam.common.AesCrypto aesCrypto;
    @Autowired private GradeRepository gradeRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private ExamRepository examRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private ClassRepository classRepository;
    @Autowired private ParentStudentBindRepository bindRepository;
    @Autowired private com.exam.repository.TeacherClassRepository teacherClassRepository;

    private final BCryptPasswordEncoder enc = new BCryptPasswordEncoder();
    private Account teacher;
    private Account parent1;
    private Student studentA;
    private Student studentB;
    private Exam exam;
    private Course course;

    @BeforeEach
    void setUp() {
        gradeRepository.deleteAll();
        bindRepository.deleteAll();
        ClassEntity cls = classRepository.save(newClass("隐私班"));
        studentA = studentRepository.save(withStudent("P001", "隐私甲", cls.getId()));
        studentB = studentRepository.save(withStudent("P002", "隐私乙", cls.getId()));
        exam = examRepository.save(newExam("隐私考"));
        course = courseRepository.save(newCourse("语", 100));
        teacher = account("隐私老师");

        // 老师给两生录入成绩
        LoginUserContext.set(teacher);
        gradeService.create(req(studentA.getId(), exam.getId(), course.getId(), 90, 100));
        gradeService.create(req(studentB.getId(), exam.getId(), course.getId(), 80, 100));

        parent1 = account("隐私家长");
        bind(parent1, studentA.getId()); // 仅绑定 A
    }

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
    }

    @Test
    void limited档位仅可见本人() {
        settingsService.update(SettingsService.KEY_PARENT_RANK_DETAIL, "limited");
        LoginUserContext.set(parent1);
        Map<String, Object> r = statisticsService.ranking(exam.getId(), course.getId(), false, true, null);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) r.get("rows");
        assertEquals(1, rows.size(), "limited 仅本人");
        assertEquals(studentA.getId(), rows.get(0).get("studentId"));
    }

    @Test
    void full档位可见全班() {
        settingsService.update(SettingsService.KEY_PARENT_RANK_DETAIL, "full");
        LoginUserContext.set(parent1);
        // 班内范围(classScope=true)：隐私档位粒度按「班级」计，full 返回全班
        Map<String, Object> r = statisticsService.ranking(exam.getId(), course.getId(), true, true, null);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) r.get("rows");
        assertEquals(2, rows.size(), "full 可见全班两生");
    }

    @Test
    void aggregate档位含班级聚合() {
        settingsService.update(SettingsService.KEY_PARENT_RANK_DETAIL, "aggregate");
        LoginUserContext.set(parent1);
        // 班内范围(classScope=true)：aggregate 返回本人 + 班级聚合
        Map<String, Object> r = statisticsService.ranking(exam.getId(), course.getId(), true, true, null);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) r.get("rows");
        assertEquals(1, rows.size(), "aggregate 仅本人");
        assertNotNull(r.get("classAgg"), "应返回班级聚合");
        @SuppressWarnings("unchecked")
        Map<String, Object> agg = (Map<String, Object>) r.get("classAgg");
        assertEquals(2, ((Number) agg.get("total")).intValue(), "班级聚合含两生");
    }

    @Test
    void 排名不可见开关关闭时返回空() {
        settingsService.update(SettingsService.KEY_PARENT_RANK_VISIBLE, false);
        LoginUserContext.set(parent1);
        Map<String, Object> r = statisticsService.ranking(exam.getId(), course.getId(), false, true, null);
        assertEquals(true, r.get("disabled"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) r.get("rows");
        assertTrue(rows.isEmpty());
    }

    @Test
    void 认领并迁移自测成绩() {
        // 校园学生：身份证后8位 03071234，供认领四要素比对
        Student school = studentRepository.save(withStudent("SC001", "校园生", studentA.getClassId()));
        school.setIdCard(aesCrypto.encrypt("110101199003071234"));
        school.setIdCardHash(enc.encode("03071234"));
        school.setOrigin(Student.ORIGIN_SCHOOL);
        school.setEnrolled(true);
        school = studentRepository.save(school);

        // 家长自建学生（origin=self，未合并）；身份证与校园生一致才可认领迁移
        Student self = studentRepository.save(withStudent("SELF001", "自测生", studentA.getClassId()));
        self.setOrigin(Student.ORIGIN_SELF);
        self.setEnrolled(false);
        self.setMerged(false);
        self.setParentOwnerAccountId(parent1.getId());
        self.setIdCard(aesCrypto.encrypt("110101199003071234")); // 与校园生同一身份证
        self = studentRepository.save(self);
        bind(parent1, self.getId());

        // 家长为自建学生录入一条自测成绩（source=parent）
        LoginUserContext.set(parent1);
        gradeService.create(req(self.getId(), exam.getId(), course.getId(), 85, 100));
        assertTrue(gradeRepository.findByStudentId(self.getId()).stream()
                        .anyMatch(g -> g.getSource().equals("parent")),
                "自测成绩应归属自建学生");

        // 认领：按 studentNo+name+身份证后8位 命中校园生
        com.exam.dto.BindRequest breq = new com.exam.dto.BindRequest();
        breq.setStudentNo("SC001");
        breq.setName("校园生");
        breq.setIdCardLast8("03071234");
        parentService.claimStudent(parent1, breq);

        // 认领：自建记录标记 merged=true，且建立对校园生的正式绑定
        Student reSelf = studentRepository.findById(self.getId()).orElseThrow();
        assertTrue(Boolean.TRUE.equals(reSelf.getMerged()), "认领应标记自建记录 merged=true");
        assertTrue(bindRepository.existsByParentAccountIdAndStudentId(parent1.getId(), school.getId()),
                "应建立对校园生的正式绑定");

        // A5：自测成绩应迁移挂接至校园正式学生，Source 保持 parent，认领后可查历史自测成绩
        assertTrue(gradeRepository.findByStudentId(school.getId()).stream()
                        .anyMatch(g -> g.getSource().equals("parent")
                                && g.getExamId().equals(exam.getId())
                                && g.getCourseId().equals(course.getId())
                                && g.getScore().equals(85)),
                "自测成绩应已迁移至校园正式学生名下");
        assertTrue(gradeRepository.findByStudentId(self.getId()).isEmpty(),
                "迁移后自建学生名下不应再残留该成绩");
    }

    @Test
    void 身份证不一致时认领不迁移自测成绩() {
        // 校园学生：身份证后8位 03071234
        Student school = studentRepository.save(withStudent("SC002", "校园生乙", studentA.getClassId()));
        school.setIdCard(aesCrypto.encrypt("110101199003071234"));
        school.setIdCardHash(enc.encode("03071234"));
        school.setOrigin(Student.ORIGIN_SCHOOL);
        school.setEnrolled(true);
        school = studentRepository.save(school);

        // 自建学生身份证与校园生不同（另一号码末8位 12341234）
        Student selfDiff = studentRepository.save(withStudent("SELF002", "他校自测", studentA.getClassId()));
        selfDiff.setOrigin(Student.ORIGIN_SELF);
        selfDiff.setEnrolled(false);
        selfDiff.setMerged(false);
        selfDiff.setParentOwnerAccountId(parent1.getId());
        selfDiff.setIdCard(aesCrypto.encrypt("110101199503121234"));
        selfDiff = studentRepository.save(selfDiff);
        bind(parent1, selfDiff.getId());

        LoginUserContext.set(parent1);
        gradeService.create(req(selfDiff.getId(), exam.getId(), course.getId(), 75, 100));

        com.exam.dto.BindRequest breq = new com.exam.dto.BindRequest();
        breq.setStudentNo("SC002");
        breq.setName("校园生乙");
        breq.setIdCardLast8("03071234");
        parentService.claimStudent(parent1, breq);

        // 建立绑定成功，但因自建生身份证与校园生不一致，其自测成绩不得误迁到校园生名下
        assertTrue(bindRepository.existsByParentAccountIdAndStudentId(parent1.getId(), school.getId()),
                "仍应建立对校园生的正式绑定");
        assertFalse(gradeRepository.findByStudentId(school.getId()).stream()
                        .anyMatch(g -> g.getSource().equals("parent") && g.getScore().equals(75)),
                "身份证不一致的自建生成绩不得迁移到校园生");
        Student reSelf = studentRepository.findById(selfDiff.getId()).orElseThrow();
        assertFalse(Boolean.TRUE.equals(reSelf.getMerged()), "身份不一致不应标记 merged");
    }

    @Test
    void 老师越班查询学生趋势被拒() {
        // 两个班级：当前老师仅绑 A 班，B 班学生对其不可见
        ClassEntity clsA = classRepository.save(newClass("越权A班"));
        ClassEntity clsB = classRepository.save(newClass("越权B班"));
        LoginUserContext.clear();
        Account t2 = account("越权老师");
        t2.setScopeType(Account.SCOPE_CLASS);
        t2 = accountRepository.save(t2);
        com.exam.entity.TeacherClass tc = new com.exam.entity.TeacherClass();
        tc.setTeacherAccountId(t2.getId());
        tc.setClassId(clsA.getId());
        teacherClassRepository.save(tc);
        Student sA = studentRepository.save(withStudent("INA001", "班内生", clsA.getId()));
        Student sB = studentRepository.save(withStudent("OUT001", "外班生", clsB.getId()));
        exam = examRepository.save(newExam("越权考"));
        course = courseRepository.save(newCourse("数", 100));
        LoginUserContext.set(t2);
        // 同班(A)成绩经老师写入；越班(B)成绩路由绕过写校验直存，模拟库中已存在的越班数据
        gradeService.create(req(sA.getId(), exam.getId(), course.getId(), 85, 100));
        com.exam.entity.Grade gOut = new com.exam.entity.Grade();
        gOut.setStudentId(sB.getId());
        gOut.setExamId(exam.getId());
        gOut.setCourseId(course.getId());
        gOut.setScore(80);
        gOut.setFullScore(100);
        gOut.setSource("teacher");
        gradeRepository.save(gOut);

        // 学生趋势走 visibleGrades → 老师分支按 canManageClass 校验：同班可看、越班 403
        LoginUserContext.set(t2);
        assertDoesNotThrow(() -> statisticsService.studentTrend(sA.getId()));
        assertThrows(com.exam.common.BizException.class,
                () -> statisticsService.studentTrend(sB.getId()),
                "CLASS 老师越班查询学生趋势应被拒");

        // 成绩 list 读路径同样应被班域收敛：越班成绩不返回
        assertFalse(gradeService.list(exam.getId(), course.getId(), null).stream()
                        .anyMatch(g -> g.getStudentId().equals(sB.getId())),
                "CLASS 老师成绩查询不应含越班学生成绩");
    }

    private GradeRequest req(Long studentId, Long examId, Long courseId, int score, int full) {
        GradeRequest r = new GradeRequest();
        r.setStudentId(studentId);
        r.setExamId(examId);
        r.setCourseId(courseId);
        r.setScore(score);
        r.setFullScore(full);
        return r;
    }

    private Student withStudent(String no, String name, Long classId) {
        Student s = new Student();
        s.setStudentNo(no);
        s.setName(name);
        s.setGender("男");
        s.setClassId(classId);
        s.setFatherPhone("13900000031");
        s.setMotherPhone("13800000032");
        return s;
    }

    private Exam newExam(String name) {
        Exam e = new Exam();
        e.setName(name);
        e.setExamDate(java.time.LocalDate.of(2026, 1, 1));
        return e;
    }

    private Course newCourse(String name, int full) {
        Course c = new Course();
        c.setName(name);
        c.setFullScore(full);
        return c;
    }

    private ClassEntity newClass(String name) {
        ClassEntity c = new ClassEntity();
        c.setName(name);
        return c;
    }

    private Account account(String username) {
        Account a = new Account();
        a.setUsername(username + System.nanoTime());
        a.setPasswordHash(enc.encode("pass123"));
        a.setRole(username.contains("家长") ? Account.ROLE_PARENT : Account.ROLE_TEACHER);
        a.setNickname(username);
        a.setScopeType(Account.SCOPE_ALL);
        return accountRepository.save(a);
    }

    private void bind(Account parent, Long studentId) {
        ParentStudentBind b = new ParentStudentBind();
        b.setParentAccountId(parent.getId());
        b.setStudentId(studentId);
        b.setRelation("家长");
        bindRepository.save(b);
    }
}
