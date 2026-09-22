package com.exam;

import com.exam.common.BizException;
import com.exam.common.LoginUserContext;
import com.exam.service.DataScopeService;
import com.exam.dto.CreateClassRequest;
import com.exam.dto.CreateLevelRequest;
import com.exam.dto.CreateSchoolRequest;
import com.exam.dto.StudentRequest;
import com.exam.entity.Account;
import com.exam.entity.ClassEntity;
import com.exam.entity.Course;
import com.exam.entity.Exam;
import com.exam.entity.Grade;
import com.exam.entity.Level;
import com.exam.entity.School;
import com.exam.entity.Student;
import com.exam.entity.SystemSetting;
import com.exam.repository.AccountRepository;
import com.exam.repository.ClassRepository;
import com.exam.repository.CourseRepository;
import com.exam.repository.ExamRepository;
import com.exam.repository.GradeRepository;
import com.exam.repository.LevelRepository;
import com.exam.repository.StudentRepository;
import com.exam.repository.SystemSettingRepository;
import com.exam.service.ComparisonService;
import com.exam.service.DictionaryService;
import com.exam.service.LevelService;
import com.exam.service.RankingService;
import com.exam.service.SchoolService;
import com.exam.service.SettingsService;
import com.exam.service.StudentService;
import com.exam.service.TrendService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 阶段 B（多租户闭环）专项验证：
 * - B1：SQL 层 school 兜底（courseStats / comparison / rank / progress 不跨校）
 * - B3：system_setting 按校隔离（同 key 多校互不覆盖）
 * - B4：写路径 schoolId 强制（登录本校老师创建数据归属本校）
 * - B5：级联删除安全网（级部下有班级拒删 / 学校有数据拒删）
 */
@SpringBootTest
@Transactional
class MultiTenantPhaseBTest {

    @Autowired private SchoolService schoolService;
    @Autowired private LevelService levelService;
    @Autowired private DictionaryService dictionaryService;
    @Autowired private SettingsService settingsService;
    @Autowired private RankingService rankingService;
    @Autowired private ComparisonService comparisonService;
    @Autowired private TrendService trendService;
    @Autowired private AccountRepository accountRepository;
    @Autowired private ClassRepository classRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private ExamRepository examRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private GradeRepository gradeRepository;
    @Autowired private LevelRepository levelRepository;
    @Autowired private SystemSettingRepository settingRepository;
    @Autowired private DataScopeService dataScopeService;
    @Autowired private StudentService studentService;

    private final BCryptPasswordEncoder enc = new BCryptPasswordEncoder();
    private int seq = 0;

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
    }

    @Test
    void B1_统计对比排名进步_SQL层school下推不跨校() {
        // 两校；各一学生、都录了同考试+同课程成绩。
        School a = mkSchool("A校", "B1A");
        School b = mkSchool("B校", "B1B");
        Level la = level(a.getId(), "初一");
        Level lb = level(b.getId(), "初一");
        ClassEntity ca = classRepository.save(classOf("A1班", la.getId(), a.getId()));
        ClassEntity cb = classRepository.save(classOf("B1班", lb.getId(), b.getId()));
        Exam ea = exam("考A", a.getId());
        Course co = course("数A", a.getId());
        // 两校都用同一 course co（课程归属 A 校），且各自录成绩；A 校老师看应只统计 A 校学生
        Student sa = student("SA", ca.getId(), a.getId());
        Student sb = student("SB", cb.getId(), b.getId());
        grade(sa, ea, co, 80, a.getId());
        grade(sb, ea, co, 20, b.getId()); // 学生 B 属 B 校，但其成绩挂 A 校课程/考试

        // A 校 SCOPE_ALL 老师（schoolId=A）：排名/对比/进步应只见 SA
        Account ta = account("BP1", Account.SCOPE_ALL, a.getId());
        LoginUserContext.set(ta);

        Map<String, Object> rk = rankingService.ranking(ea.getId(), co.getId(), null, true, "class");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rkRows = (List<Map<String, Object>>) rk.get("rows");
        assertEquals(1, rkRows.size(), "排名应仅含 A 校学生（SB 属 B 校被 school 兜底排除）");
        assertTrue(String.valueOf(rkRows.get(0).get("studentNo")).startsWith("SA"));

        Map<String, Object> cp = comparisonService.comparison(ea.getId(), co.getId(), null);
        @SuppressWarnings("unchecked")
        Map<String, Object> overall = (Map<String, Object>) cp.get("overall");
        assertTrue(((Number) overall.get("total")).intValue() >= 1);
        // 对比仅 A 校（SA），不应出现 B 校学生额外行——闸：total<=1
        assertTrue(((Number) overall.get("total")).intValue() == 1,
                "对比应仅统计 A 校学生，实际 total=" + overall.get("total"));

        Map<String, Object> pr = trendService.progress(co.getId(), 10, false, null);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) pr.get("items");
        assertTrue(items.stream().noneMatch(i -> String.valueOf(i.get("studentNo")).startsWith("SB")),
                "进步分析不应包含 B 校学生");
    }

    @Test
    void B3_systemSetting按校隔离_同key多校互不覆盖() {
        School a = mkSchool("S设A", "B3A");
        School b = mkSchool("S设B", "B3B");
        // A 校老师把 parent_rank_visible 关掉
        Account ta = account("B3a", Account.SCOPE_ALL, a.getId());
        LoginUserContext.set(ta);
        settingsService.update(SettingsService.KEY_PARENT_RANK_VISIBLE, false);
        assertFalse(settingsService.getBool(SettingsService.KEY_PARENT_RANK_VISIBLE, true), "A 校应读到 false");
        // 切到 B 校老师：应回退全局默认（仍 true），不受 A 校影响
        Account tb = account("B3b", Account.SCOPE_ALL, b.getId());
        LoginUserContext.set(tb);
        assertTrue(settingsService.getBool(SettingsService.KEY_PARENT_RANK_VISIBLE, true), "B 校不应受 A 校影响");
        // 全局超管（schoolId=null）：回退全局默认 true
        LoginUserContext.clear();
        assertTrue(settingsService.getBool(SettingsService.KEY_PARENT_RANK_VISIBLE, true), "无登录上下文应回退全局默认");
        // 各校行需为一对——至少存在 A 校行且其 key 正确
        settingRepository.findBySchoolId(a.getId()).stream()
                .filter(s -> SettingsService.KEY_PARENT_RANK_VISIBLE.equals(s.getKey()))
                .findFirst()
                .ifPresentOrElse(s -> assertEquals("false", s.getValue()),
                        () -> fail("A 校应存在 parent_rank_visible=false 的行"));
    }

    @Test
    void B4_写路径schoolId强制_归属登录校() {
        School a = mkSchool("写A", "B4A");
        School other = mkSchool("写他", "B4B");
        Level la = level(a.getId(), "初一");
        Level lother = level(other.getId(), "初一");
        Account ta = account("B4t", Account.SCOPE_ALL, a.getId());
        LoginUserContext.set(ta);

        // 创建课程/考试/班级：均应归属登录校 A（即使请求体不带 schoolId 或带他校）
        Course c = dictionaryService.createCourse(reqCourse("数B4"));
        assertEquals(a.getId(), c.getSchoolId(), "课程应强制归属登录校");
        Exam e = examServiceCreate("考B4", a.getId(), null);
        assertEquals(a.getId(), e.getSchoolId());

        // 班级：请求体 schoolId 传他校也会被强制为登录校（B4：don't trust request）
        CreateClassRequest cr = new CreateClassRequest();
        cr.setName("B4班" + (++seq));
        cr.setLevelId(la.getId());
        cr.setSchoolId(other.getId()); // 恶意指定他校
        ClassEntity cls = dictionaryService.createClass(cr);
        assertEquals(a.getId(), cls.getSchoolId(), "班级应强制归属登录校，不信任请求体");

        // 级部：强制归属登录校
        CreateLevelRequest lr = new CreateLevelRequest();
        lr.setName("B4级" + (++seq));
        lr.setSchoolId(other.getId()); // 恶意指定他校
        Level lev = levelService.create(lr);
        assertEquals(a.getId(), lev.getSchoolId(), "级部应强制归属登录校");
    }

    @Test
    void B5_级联删除安全网_有引用拒删() {
        School a = mkSchool("级A", "B5A");
        Level l = level(a.getId(), "初一年级");
        ClassEntity cl = classRepository.save(classOf("B5班", l.getId(), a.getId()));
        // 级部下有班级 → 拒删级部
        assertThrows(BizException.class, () -> levelService.delete(l.getId()));
        // 学校下有班级/学生 → 拒删学校
        student("B5s", cl.getId(), a.getId());
        assertThrows(BizException.class, () -> schoolService.delete(a.getId()));
    }

    @Test
    void B6_写路径不信任请求体_学生与课程归属锁定登录校() {
        School a = mkSchool("锁A", "B6A");
        School other = mkSchool("锁他", "B6B");
        Level la = level(a.getId(), "初一");
        ClassEntity ca = classRepository.save(classOf("B6班", la.getId(), a.getId()));
        Account ta = account("B6t", Account.SCOPE_ALL, a.getId());
        LoginUserContext.set(ta);

        // 学生：请求体恶意指定他校，仍强制归属登录校 A（关闭"超管经请求体跨校建学生"缺口）
        StudentRequest sr = new StudentRequest();
        sr.setStudentNo("B6" + System.nanoTime());
        sr.setName("锁生");
        sr.setGender("男");
        sr.setClassId(ca.getId());
        sr.setFatherPhone("13900000071");
        sr.setMotherPhone("13800000072");
        sr.setSchoolId(other.getId());
        Student st = studentService.create(sr);
        assertEquals(a.getId(), st.getSchoolId(), "学生应强制归属登录校，不信任请求体");

        // 课程：SCOPE_ALL 带登录校不可改他校课程（403）
        Course cOther = course("他校课", other.getId());
        com.exam.dto.CourseRequest ur = new com.exam.dto.CourseRequest();
        ur.setName("改课" + System.nanoTime());
        ur.setFullScore(100);
        BizException ex = assertThrows(BizException.class,
                () -> dictionaryService.updateCourse(cOther.getId(), ur));
        assertTrue(ex.getMessage().contains("无权"), "改他校课程应拒绝：" + ex.getMessage());
    }

    // ---------- 装配 ----------

    private com.exam.dto.CourseRequest reqCourse(String name) {
        com.exam.dto.CourseRequest r = new com.exam.dto.CourseRequest();
        r.setName(name + (++seq));
        r.setFullScore(100);
        return r;
    }

    private Exam examServiceCreate(String name, Long schoolId, Long typeId) {
        com.exam.dto.ExamRequest r = new com.exam.dto.ExamRequest();
        r.setName(name + (++seq));
        r.setExamTypeId(typeId);
        r.setExamDate(java.time.LocalDate.of(2026, 6, 1));
        return new com.exam.service.ExamService(examRepository, null, gradeRepository, null, courseRepository, dataScopeService).create(r);
    }

    private School mkSchool(String name, String code) {
        CreateSchoolRequest r = new CreateSchoolRequest();
        r.setName(name + seq);
        r.setCode(code + (++seq));
        return schoolService.create(r);
    }

    private Level level(Long schoolId, String name) {
        // 辅助造数：直写仓库（避免触发 B4 写强制登录），仅被测路径才经 service。
        Level l = new Level();
        l.setSchoolId(schoolId);
        l.setName(name + (++seq));
        return levelRepository.save(l);
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
        s.setName("测");
        s.setGender("男");
        s.setClassId(classId);
        s.setSchoolId(schoolId);
        s.setFatherPhone("13900000061");
        s.setMotherPhone("13800000062");
        return studentRepository.save(s);
    }

    private Exam exam(String name, Long schoolId) {
        Exam e = new Exam();
        e.setName(name + (++seq));
        e.setExamDate(java.time.LocalDate.of(2026, 7, 1));
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
