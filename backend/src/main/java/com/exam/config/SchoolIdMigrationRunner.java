package com.exam.config;

import com.exam.repository.AccountRepository;
import com.exam.repository.CourseRepository;
import com.exam.repository.ExamRepository;
import com.exam.repository.ExamTypeRepository;
import com.exam.repository.GradeRepository;
import com.exam.repository.StudentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * 阶段 B（B2）：历史 school_id 补全的可选启动组件。
 * <p>默认关闭（{@code app.migration.backfill-school-id.enabled=false}），避免生产自动改写数据；
 * 正式迁移优先走 {@code db/migration/V2__backfill_school_id.sql}。仅当运维显式开启且提供了
 * {@code app.migration.default-school-id} 时，才将全库 school_id IS NULL 的归属数据统一补齐到默认学校。
 * <p>说明：grade 表由写路径依学生回填快照，此处仅按 SQL 脚本口径处理 student/exam/course/exam_type/account
 * 的五类 NULL 行（grade 的 school_id 回填由 {@code V2__backfill_school_id.sql} 承担，避免运行时逐行改成绩）。
 */
@Slf4j
@Configuration
public class SchoolIdMigrationRunner implements CommandLineRunner {

    private final PlatformTransactionManager ptm;
    private final StudentRepository studentRepository;
    private final ExamRepository examRepository;
    private final CourseRepository courseRepository;
    private final ExamTypeRepository examTypeRepository;
    private final AccountRepository accountRepository;
    private final GradeRepository gradeRepository;
    private final boolean migrationEnabled;
    private final Long defaultSchoolId;

    public SchoolIdMigrationRunner(PlatformTransactionManager ptm,
                                   StudentRepository studentRepository,
                                   ExamRepository examRepository,
                                   CourseRepository courseRepository,
                                   ExamTypeRepository examTypeRepository,
                                   AccountRepository accountRepository,
                                   GradeRepository gradeRepository,
                                   @Value("${app.migration.backfill-school-id.enabled:false}") boolean migrationEnabled,
                                   @Value("${app.migration.default-school-id:}") Long defaultSchoolId) {
        this.ptm = ptm;
        this.studentRepository = studentRepository;
        this.examRepository = examRepository;
        this.courseRepository = courseRepository;
        this.examTypeRepository = examTypeRepository;
        this.accountRepository = accountRepository;
        this.gradeRepository = gradeRepository;
        this.migrationEnabled = migrationEnabled;
        this.defaultSchoolId = defaultSchoolId;
    }

    @Override
    public void run(String... args) {
        if (!migrationEnabled) {
            return;
        }
        if (defaultSchoolId == null) {
            log.warn("历史 school_id 迁移已开启但未配置 app.migration.default-school-id，跳过");
            return;
        }
        TransactionTemplate tx = new TransactionTemplate(ptm);
        Integer updated = tx.execute(s -> {
            int n = backfill(defaultSchoolId);
            return n;
        });
        log.info("历史 school_id 回填完成：目标学校 id={}，共补全 {} 行", defaultSchoolId, updated);
    }

    private int backfill(Long sid) {
        log.info("历史 school_id 回填：目标学校 id={}", sid);
        // 学生
        var students = studentRepository.findAll().stream().filter(s -> s.getSchoolId() == null).toList();
        students.forEach(s -> s.setSchoolId(sid));
        studentRepository.saveAll(students);
        // 考试
        var exams = examRepository.findAll().stream().filter(e -> e.getSchoolId() == null).toList();
        exams.forEach(e -> e.setSchoolId(sid));
        examRepository.saveAll(exams);
        // 课程
        var courses = courseRepository.findAll().stream().filter(c -> c.getSchoolId() == null).toList();
        courses.forEach(c -> c.setSchoolId(sid));
        courseRepository.saveAll(courses);
        // 考试类型
        var examTypes = examTypeRepository.findAll().stream().filter(t -> t.getSchoolId() == null).toList();
        examTypes.forEach(t -> t.setSchoolId(sid));
        examTypeRepository.saveAll(examTypes);
        // 账号（老师等归属）
        var accounts = accountRepository.findAll().stream().filter(a -> a.getSchoolId() == null).toList();
        accounts.forEach(a -> a.setSchoolId(sid));
        accountRepository.saveAll(accounts);
        // (#9) 成绩：依据学生归属回填 grade.school_id 冗余快照（与其统计/列表坚持同一基准 s.schoolId）
        var grades = gradeRepository.findAll().stream().filter(g -> g.getSchoolId() == null).toList();
        int gradeFixed = 0;
        for (var g : grades) {
            var stu = studentRepository.findById(g.getStudentId()).orElse(null);
            if (stu != null && stu.getSchoolId() != null) {
                g.setSchoolId(stu.getSchoolId());
                gradeFixed++;
            } else {
                g.setSchoolId(sid);
                gradeFixed++;
            }
        }
        gradeRepository.saveAll(grades);
        return students.size() + exams.size() + courses.size() + examTypes.size() + accounts.size() + gradeFixed;
    }
}
