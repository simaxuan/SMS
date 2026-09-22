package com.exam.config;

import com.exam.entity.ClassEntity;
import com.exam.entity.Course;
import com.exam.entity.Exam;
import com.exam.entity.ExamType;
import com.exam.entity.Grade;
import com.exam.entity.Student;
import com.exam.entity.Account;
import com.exam.entity.ParentStudentBind;
import com.exam.entity.School;
import com.exam.entity.Level;
import com.exam.entity.TeacherClass;
import com.exam.repository.AccountRepository;
import com.exam.repository.ClassRepository;
import com.exam.repository.CourseRepository;
import com.exam.repository.ExamRepository;
import com.exam.repository.ExamTypeRepository;
import com.exam.repository.GradeRepository;
import com.exam.repository.ParentStudentBindRepository;
import com.exam.repository.StudentRepository;
import com.exam.repository.SystemSettingRepository;
import com.exam.repository.SchoolRepository;
import com.exam.repository.LevelRepository;
import com.exam.repository.TeacherClassRepository;
import com.exam.common.AesCrypto;
import com.exam.common.GradeSources;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Slf4j
@Configuration
public class DataSeeder implements CommandLineRunner {

    private final ClassRepository classRepository;
    private final CourseRepository courseRepository;
    private final ExamTypeRepository examTypeRepository;
    private final ExamRepository examRepository;
    private final StudentRepository studentRepository;
    private final GradeRepository gradeRepository;
    private final AccountRepository accountRepository;
    private final ParentStudentBindRepository bindRepository;
    private final SystemSettingRepository settingRepository;
    private final SchoolRepository schoolRepository;
    private final LevelRepository levelRepository;
    private final TeacherClassRepository teacherClassRepository;
    private final TransactionTemplate tx;
    /** I-7：生产可用开关关闭播种，避免演示数据污染真实库。 */
    private final boolean seedEnabled;
    private final AesCrypto aesCrypto;

    public DataSeeder(ClassRepository classRepository,
                      CourseRepository courseRepository,
                      ExamTypeRepository examTypeRepository,
                      ExamRepository examRepository,
                      StudentRepository studentRepository,
                      GradeRepository gradeRepository,
                      AccountRepository accountRepository,
                      ParentStudentBindRepository bindRepository,
                      SystemSettingRepository settingRepository,
                      SchoolRepository schoolRepository,
                      LevelRepository levelRepository,
                      TeacherClassRepository teacherClassRepository,
                      PlatformTransactionManager ptm,
                      AesCrypto aesCrypto,
                      @Value("${app.seed.enabled:true}") boolean seedEnabled) {
        this.classRepository = classRepository;
        this.courseRepository = courseRepository;
        this.examTypeRepository = examTypeRepository;
        this.examRepository = examRepository;
        this.studentRepository = studentRepository;
        this.gradeRepository = gradeRepository;
        this.accountRepository = accountRepository;
        this.bindRepository = bindRepository;
        this.settingRepository = settingRepository;
        this.schoolRepository = schoolRepository;
        this.levelRepository = levelRepository;
        this.teacherClassRepository = teacherClassRepository;
        this.tx = new TransactionTemplate(ptm);
        this.aesCrypto = aesCrypto;
        this.seedEnabled = seedEnabled;
    }

    @Override
    public void run(String... args) {
        if (!seedEnabled) {
            log.info("数据播种已禁用（app.seed.enabled=false），跳过演示数据初始化");
            return;
        }
        // 整体事务 + 幂等闸：已播种过（class 存在）则跳过，避免多实例/重启重复播种
        tx.executeWithoutResult(status -> {
            if (!seedEnabled) return;
            if (classRepository.count() > 0) {
                log.info("数据已存在，跳过播种（幂等闸触发）");
                return;
            }

            // ===== 多租户根：学校 + 级部（演示数据）=====
            School school = new School();
            school.setName("示范学校");
            school.setCode("DEMO2026");
            school = schoolRepository.save(school);

            Level level = new Level();
            level.setSchoolId(school.getId());
            level.setName("初中一年级");
            level = levelRepository.save(level);

            String[] classNames = {"一班", "二班", "三班"};
            List<ClassEntity> classes = new ArrayList<>();
            for (String n : classNames) {
                ClassEntity c = new ClassEntity();
                c.setName(n);
                c.setLevelId(level.getId());
                c.setSchoolId(school.getId());
                classes.add(classRepository.save(c));
            }

            // 课程默认满分（支持 >100，如数学/英语 120）
            Map<String, Integer> courseFullScores = new LinkedHashMap<>();
            courseFullScores.put("语文", 100);
            courseFullScores.put("数学", 120);
            courseFullScores.put("英语", 120);
            courseFullScores.put("物理", 100);
            Map<String, Course> courseByName = new LinkedHashMap<>();
            for (Map.Entry<String, Integer> en : courseFullScores.entrySet()) {
                Course c = new Course();
                c.setName(en.getKey());
                c.setFullScore(en.getValue());
                c.setSchoolId(school.getId());   // S3：种子课程归属演示学校，避免 school_id 为 NULL 进入各校列表
                courseByName.put(en.getKey(), courseRepository.save(c));
            }

            // 考试类型（基础数据，可增删）
            String[] typeNames = {"期中", "期末", "月考", "模拟考"};
            Map<String, ExamType> typeByName = new LinkedHashMap<>();
            for (String n : typeNames) {
                ExamType t = new ExamType();
                t.setName(n);
                t.setSchoolId(school.getId());   // S3：种子考试类型归属演示学校
                typeByName.put(n, examTypeRepository.save(t));
            }

            // 考试并关联类型
            Map<String, Exam> examByName = new LinkedHashMap<>();
            String[] examDefs = {"期中考试:期中", "期末考试:期末", "第一次月考:月考"};
            List<Exam> exams = new ArrayList<>();
            for (int i = 0; i < examDefs.length; i++) {
                String[] parts = examDefs[i].split(":");
                Exam e = new Exam();
                e.setName(parts[0]);
                e.setExamTypeId(typeByName.get(parts[1]).getId());
                e.setExamDate(LocalDate.now().minusMonths(6 - i * 2L));
                e.setSchoolId(school.getId());   // S3：种子考试归属演示学校
                examByName.put(parts[0], examRepository.save(e));
                exams.add(examByName.get(parts[0]));
            }

            Random random = new Random(42);
            // 复用容器注入的 AES 单例，确保与运行时解密使用同一密钥（避免 session bean 与运行时 key 不一致）
            final AesCrypto aesCrypto = this.aesCrypto;
            // 种子学生统一身份证（演示用），末 8 位固定为 03071234，供四要素绑定比对
            final String SEED_ID_CARD = "110101199003071234";
            long seq = 1;
            String[] surnames = {"张", "李", "王", "刘", "陈", "杨", "赵", "黄", "周", "吴"};
            String[] given = {"伟", "芳", "娜", "敏", "静", "磊", "军", "洋", "勇", "杰"};
            for (int ci = 0; ci < classes.size(); ci++) {
                for (int i = 0; i < 15; i++) {
                    Student s = new Student();
                    s.setStudentNo(String.format("S%04d", seq));
                    s.setName(surnames[random.nextInt(surnames.length)] + given[random.nextInt(given.length)]);
                    s.setGender(random.nextBoolean() ? "男" : "女");
                    s.setClassId(classes.get(ci).getId());
                    s.setSchoolId(school.getId());   // S3：种子学生归属演示学校，回填成绩 school_id 快照
                    s.setBirthDate(LocalDate.now().minusYears(18 + random.nextInt(3)).minusMonths(random.nextInt(12)).minusDays(random.nextInt(28)));
                    s.setPhone("138" + (10000000 + random.nextInt(89999999)));
                    // 四要素绑定所需：父母手机号（唯一，格式 1[3-9]\d{9}）+ AES 加密身份证
                    s.setFatherPhone(String.format("139%08d", seq));
                    s.setMotherPhone(String.format("138%08d", seq));
                    s.setIdCard(aesCrypto.encrypt(SEED_ID_CARD));
                    studentRepository.save(s);
                    seq++;
                }
            }

            List<Student> students = studentRepository.findAll();
            // 演示用：绑定最后一个学生，该生不生成老师成绩，供家长来源成绩使用（避免唯一约束冲突）
            Student demoStudent = students.get(students.size() - 1);
            // 绑定/排名测试目标固定手机号（与四要素绑定契约一致）
            demoStudent.setFatherPhone("13800000001");
            demoStudent.setMotherPhone("13900000002");
            studentRepository.save(demoStudent);
            int gradeCount = 0;
            for (Student s : students) {
                if (s.getId().equals(demoStudent.getId())) continue; // 绑定学生不生成老师成绩
                for (Exam e : exams) {
                    for (Course c : courseByName.values()) {
                        if (random.nextInt(10) < 1) continue;
                        int full = c.getFullScore();
                        Grade g = new Grade();
                        g.setStudentId(s.getId());
                        g.setExamId(e.getId());
                        g.setCourseId(c.getId());
                        // 分数 = 满分的 40%~100%
                        g.setScore(full - (int) Math.round(full * 0.60 * random.nextDouble()));
                        g.setFullScore(full);
                        g.setSource(GradeSources.TEACHER);
                        g.setSchoolId(s.getSchoolId()); // 八轮强约束：播种回填 school_id 快照
                        gradeRepository.save(g);
                        gradeCount++;
                    }
                }
            }

            // ===== 账号：1 个老师 + 1 个家长 =====
            BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
            Account teacher = new Account();
            teacher.setUsername("admin");
            teacher.setPasswordHash(encoder.encode("admin123"));
            teacher.setRole(Account.ROLE_TEACHER);
            teacher.setNickname("系统管理员");
            // 种子 admin 设为 SCOPE_ALL：无班级绑定也能读取全校学生/成绩/排名（避免 requireAnyVisibleClass 抛 403）
            teacher.setScopeType(Account.SCOPE_ALL);
            // 三遗留处理（2026-09-21）：admin 改为 true-global（schoolId=null）。
            // 统一口径下 SCOPE_ALL 且 schoolId=null 即"真全局超管"——读放开全校总览、写信任请求体；
            // 切换具体校（前端登录校切换器 / 本端点）后 schoolId 即目标校，读按校收敛、写锁定该校。
            // 现有库经迁移脚本 V4__admin_true_global.sql 把 scope_type='ALL' 的账号 school_id 置空。
            teacher.setSchoolId(null);
            accountRepository.save(teacher);

            // 演示用：将 admin 绑定到全部种子班级（admin 已是 SCOPE_ALL，此绑定仅作 teacher-class 关系示例）
            for (ClassEntity c : classes) {
                if (!teacherClassRepository.existsByTeacherAccountIdAndClassId(teacher.getId(), c.getId())) {
                    TeacherClass tc = new TeacherClass();
                    tc.setTeacherAccountId(teacher.getId());
                    tc.setClassId(c.getId());
                    teacherClassRepository.save(tc);
                }
            }

            Account parent = new Account();
            parent.setUsername("parent1");
            parent.setPasswordHash(encoder.encode("parent123"));
            parent.setRole(Account.ROLE_PARENT);
            parent.setNickname("测试家长");
            parent.setPhone("13900000002");   // S5：与 demoStudent 的父亲手机号一致，手机号登录/绑定可走通
            accountRepository.save(parent);

            // ===== 绑定：家长绑定最后一个学生（该生无老师成绩）=====
            ParentStudentBind bind = new ParentStudentBind();
            bind.setParentAccountId(parent.getId());
            bind.setStudentId(demoStudent.getId());
            bind.setRelation("father");   // 与 relation 枚举(father/mother/other) 保持一致
            bindRepository.save(bind);

            // ===== 家长来源成绩（home 小测，仅供演示来源隔离）=====
            int parentGradeCount = 0;
            for (Course c : courseByName.values()) {
                int full = c.getFullScore();
                Grade pg = new Grade();
                pg.setStudentId(demoStudent.getId());
                pg.setExamId(exams.get(0).getId());
                pg.setCourseId(c.getId());
                pg.setScore(full - (int) Math.round(full * 0.70 * random.nextDouble()));
                pg.setFullScore(full);
                pg.setSource(GradeSources.PARENT);
                pg.setCreatorAccountId(parent.getId());
                pg.setSchoolId(demoStudent.getSchoolId()); // 八轮强约束：播种回填 school_id 快照
                gradeRepository.save(pg);
                parentGradeCount++;
            }

            log.info("种子数据初始化完成: 班级={}, 课程={}, 考试类型={}, 考试={}, 学生={}, 成绩={}, 家长成绩={}, 账号={}",
                    classes.size(), courseByName.size(), typeByName.size(), exams.size(), students.size(), gradeCount,
                    parentGradeCount, accountRepository.count());
        });
    }
}
