package com.exam.config;

import com.exam.entity.ClassEntity;
import com.exam.entity.Course;
import com.exam.entity.Exam;
import com.exam.entity.Grade;
import com.exam.entity.Student;
import com.exam.repository.ClassRepository;
import com.exam.repository.CourseRepository;
import com.exam.repository.ExamRepository;
import com.exam.repository.GradeRepository;
import com.exam.repository.StudentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Slf4j
@Configuration
public class DataSeeder {

    @Bean
    public CommandLineRunner seedData(ClassRepository classRepository,
                                      CourseRepository courseRepository,
                                      ExamRepository examRepository,
                                      StudentRepository studentRepository,
                                      GradeRepository gradeRepository) {
        return args -> {
            if (classRepository.count() > 0) return;

            String[] classNames = {"一班", "二班", "三班"};
            List<ClassEntity> classes = new ArrayList<>();
            for (String n : classNames) {
                ClassEntity c = new ClassEntity();
                c.setName(n);
                classes.add(classRepository.save(c));
            }

            String[] courseNames = {"语文", "数学", "英语", "物理"};
            List<Course> courses = new ArrayList<>();
            for (String n : courseNames) {
                Course c = new Course();
                c.setName(n);
                courses.add(courseRepository.save(c));
            }

            String[] examNames = {"期中考试", "期末考试"};
            List<Exam> exams = new ArrayList<>();
            for (int i = 0; i < examNames.length; i++) {
                Exam e = new Exam();
                e.setName(examNames[i]);
                e.setExamDate(LocalDate.now().minusMonths(6 - i * 3L));
                exams.add(examRepository.save(e));
            }

            Random random = new Random(42);
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
                    s.setBirthDate(LocalDate.now().minusYears(18 + random.nextInt(3)).minusMonths(random.nextInt(12)).minusDays(random.nextInt(28)));
                    s.setPhone("138" + (10000000 + random.nextInt(89999999)));
                    studentRepository.save(s);
                    seq++;
                }
            }

            List<Student> students = studentRepository.findAll();
            int gradeCount = 0;
            for (Student s : students) {
                for (Exam e : exams) {
                    for (Course c : courses) {
                        if (random.nextInt(10) < 1) continue;
                        Grade g = new Grade();
                        g.setStudentId(s.getId());
                        g.setExamId(e.getId());
                        g.setCourseId(c.getId());
                        g.setScore(40 + random.nextInt(61));
                        gradeRepository.save(g);
                        gradeCount++;
                    }
                }
            }
            log.info("种子数据初始化完成: 班级={}, 课程={}, 考试={}, 学生={}, 成绩={}",
                    classes.size(), courses.size(), exams.size(), students.size(), gradeCount);
        };
    }
}
