package com.exam;

import com.exam.common.LoginUserContext;
import com.exam.dto.GradeRequest;
import com.exam.entity.Account;
import com.exam.entity.ClassEntity;
import com.exam.entity.Course;
import com.exam.entity.Exam;
import com.exam.entity.ExamCourseGroup;
import com.exam.entity.Student;
import com.exam.repository.AccountRepository;
import com.exam.repository.ClassRepository;
import com.exam.repository.CourseRepository;
import com.exam.repository.ExamCourseGroupRepository;
import com.exam.repository.ExamRepository;
import com.exam.repository.GradeRepository;
import com.exam.repository.StudentRepository;
import com.exam.service.GradeService;
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
 * 标准分权重（equal/full-weighted/custom-weighted）生效测试。
 * 同一学生两科不同得分率与不同满分，验证 studentTrend 的 stdScore 随权重模式变化。
 */
@SpringBootTest
@Transactional
class StatisticsMetricTest {

    @Autowired private GradeService gradeService;
    @Autowired private StatisticsService statisticsService;
    @Autowired private SettingsService settingsService;
    @Autowired private GradeRepository gradeRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private ExamRepository examRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private ClassRepository classRepository;
    @Autowired private ExamCourseGroupRepository examCourseGroupRepository;

    private final BCryptPasswordEncoder enc = new BCryptPasswordEncoder();
    private Account teacher;
    private Student student;
    private Exam exam;
    private Course c1; // 满分100
    private Course c2; // 满分120

    @BeforeEach
    void setUp() {
        gradeRepository.deleteAll();
        ClassEntity cls = classRepository.save(newClass("权重班"));
        student = studentRepository.save(withStudent("W001", "权重生", cls.getId()));
        exam = examRepository.save(newExam("权重考"));
        c1 = courseRepository.save(newCourse("语", 100));
        c2 = courseRepository.save(newCourse("数", 120));
        teacher = account("权重老师");
        LoginUserContext.set(teacher);

        // c1: 得分率 90%（90/100）；c2: 得分率 50%（60/120）
        gradeService.create(req(student.getId(), exam.getId(), c1.getId(), 90, 100));
        gradeService.create(req(student.getId(), exam.getId(), c2.getId(), 60, 120));
    }

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
    }

    @Test
    void equal模式下标准分为得分率等权均值() {
        settingsService.update(SettingsService.KEY_STD_WEIGHT_MODE, "equal");
        double std = stdScore();
        assertEquals(70.0, std, 0.01, "equal: (90+50)/2 = 70");
    }

    @Test
    void fullWeighted模式按满分加权() {
        settingsService.update(SettingsService.KEY_STD_WEIGHT_MODE, "full-weighted");
        // (90*100 + 50*120)/(100+120) = 15000/220 = 68.2
        double std = stdScore();
        assertEquals(68.2, std, 0.05, "full-weighted: 加权得分率");
    }

    @Test
    void customWeighted模式按用户权重() {
        // 权重 c1:1, c2:3 → (90*1 + 50*3)/4 = 240/4 = 60
        settingsService.update(SettingsService.KEY_STD_WEIGHTS,
                String.format("{\"%d\":1,\"%d\":3}", c1.getId(), c2.getId()));
        settingsService.update(SettingsService.KEY_STD_WEIGHT_MODE, "custom-weighted");
        double std = stdScore();
        assertEquals(60.0, std, 0.05, "custom-weighted: (90*1+50*3)/4 = 60");
    }

    @Test
    void 权重JSON非法时回退equal() {
        // 非法 JSON，应回退 equal（70）
        settingsService.update(SettingsService.KEY_STD_WEIGHTS, "not-a-json");
        settingsService.update(SettingsService.KEY_STD_WEIGHT_MODE, "custom-weighted");
        double std = stdScore();
        assertEquals(70.0, std, 0.05, "非法权重 JSON 应回退 equal");
    }

    @Test
    void 多科总分端点返回四项指标() {
        settingsService.update(SettingsService.KEY_STD_WEIGHT_MODE, "equal");
        Map<String, Object> r = statisticsService.studentMultiSubject(exam.getId(), student.getId(), null);
        assertEquals(150, ((Number) r.get("totalScore")).intValue(), "90+60");
        assertEquals(220, ((Number) r.get("totalFull")).intValue(), "100+120");
        assertEquals(68.2, ((Number) r.get("totalPercent")).doubleValue(), 0.05, "150/220*100");
        assertEquals(70.0, ((Number) r.get("stdScore")).doubleValue(), 0.05);
    }

    @Test
    void 多科总分按科目组合过滤_仅统计active科目() {
        settingsService.update(SettingsService.KEY_STD_WEIGHT_MODE, "equal");
        // 配置该考试科目组合：c1 不计入(active=false)，c2 计入(active=true)；
        // 组合按 examId 归属读取（回归：此前误按 examCourseGroupId 查询，组合 id 无对应考试时全部被过滤为 0）
        examCourseGroupRepository.deleteByExamId(exam.getId());
        examCourseGroupRepository.save(group(exam.getId(), c1.getId(), false));
        examCourseGroupRepository.save(group(exam.getId(), c2.getId(), true));
        // 传非 null 第三参数启用组合过滤（组合按 examId 读取）
        Map<String, Object> r = statisticsService.studentMultiSubject(exam.getId(), student.getId(), 1L);
        assertEquals(60, ((Number) r.get("totalScore")).intValue(), "仅 c2: 60");
        assertEquals(120, ((Number) r.get("totalFull")).intValue(), "仅 c2: 120");
        assertEquals(1, ((Number) r.get("courseCount")).intValue(), "仅 c2 一门");
        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> courses = (java.util.List<Map<String, Object>>) r.get("courses");
        assertEquals(1, courses.size(), "课程明细仅 c2 一科");
    }

    private ExamCourseGroup group(Long examId, Long courseId, boolean active) {
        ExamCourseGroup g = new ExamCourseGroup();
        g.setExamId(examId);
        g.setCourseId(courseId);
        g.setActive(active);
        return g;
    }

    private double stdScore() {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> trend =
                (List<Map<String, Object>>) statisticsService.studentTrend(student.getId()).get("trend");
        assertFalse(trend.isEmpty());
        Object v = trend.get(0).get("stdScore");
        assertNotNull(v);
        return ((Number) v).doubleValue();
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
        s.setFatherPhone("13900000021");
        s.setMotherPhone("13800000022");
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
        a.setRole(Account.ROLE_TEACHER);
        a.setNickname(username);
        a.setScopeType(Account.SCOPE_ALL);
        return accountRepository.save(a);
    }
}
