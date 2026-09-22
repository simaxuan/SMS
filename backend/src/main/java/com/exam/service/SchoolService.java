package com.exam.service;

import com.exam.common.BizException;
import com.exam.dto.CreateSchoolRequest;
import com.exam.entity.School;
import com.exam.repository.AccountRepository;
import com.exam.repository.ClassRepository;
import com.exam.repository.CourseRepository;
import com.exam.repository.ExamRepository;
import com.exam.repository.ExamCourseGroupRepository;
import com.exam.repository.ExamTypeRepository;
import com.exam.repository.GradeRepository;
import com.exam.repository.LevelRepository;
import com.exam.repository.SchoolRepository;
import com.exam.repository.StudentRepository;
import com.exam.repository.SystemSettingRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/** 学校（多租户根）的增删改查。 */
@Service
public class SchoolService {

    private final SchoolRepository schoolRepository;
    private final LevelRepository levelRepository;
    private final ClassRepository classRepository;
    private final CourseRepository courseRepository;
    private final ExamTypeRepository examTypeRepository;
    private final ExamRepository examRepository;
    private final ExamCourseGroupRepository examCourseGroupRepository;
    private final StudentRepository studentRepository;
    private final GradeRepository gradeRepository;
    private final AccountRepository accountRepository;
    private final SystemSettingRepository systemSettingRepository;

    public SchoolService(SchoolRepository schoolRepository,
                         LevelRepository levelRepository,
                         ClassRepository classRepository,
                         CourseRepository courseRepository,
                         ExamTypeRepository examTypeRepository,
                         ExamRepository examRepository,
                         ExamCourseGroupRepository examCourseGroupRepository,
                         StudentRepository studentRepository,
                         GradeRepository gradeRepository,
                         AccountRepository accountRepository,
                         SystemSettingRepository systemSettingRepository) {
        this.schoolRepository = schoolRepository;
        this.levelRepository = levelRepository;
        this.classRepository = classRepository;
        this.courseRepository = courseRepository;
        this.examTypeRepository = examTypeRepository;
        this.examRepository = examRepository;
        this.examCourseGroupRepository = examCourseGroupRepository;
        this.studentRepository = studentRepository;
        this.gradeRepository = gradeRepository;
        this.accountRepository = accountRepository;
        this.systemSettingRepository = systemSettingRepository;
    }

    public List<School> list() {
        return schoolRepository.findAll();
    }

    public School create(CreateSchoolRequest req) {
        if (req.getName() == null || req.getName().isBlank()) {
            throw new BizException("学校名称不能为空");
        }
        if (req.getCode() == null || req.getCode().isBlank()) {
            throw new BizException("学校编码不能为空");
        }
        if (schoolRepository.existsByCode(req.getCode())) {
            throw new BizException("学校编码已存在");
        }
        School s = new School();
        s.setName(req.getName());
        s.setCode(req.getCode());
        return schoolRepository.save(s);
    }

    public School update(Long id, CreateSchoolRequest req) {
        School s = schoolRepository.findById(id)
                .orElseThrow(() -> new BizException(404, "学校不存在"));
        if (req.getName() != null && !req.getName().isBlank()) {
            s.setName(req.getName());
        }
        if (req.getCode() != null && !req.getCode().isBlank()) {
            if (!req.getCode().equals(s.getCode()) && schoolRepository.existsByCode(req.getCode())) {
                throw new BizException("学校编码已存在");
            }
            s.setCode(req.getCode());
        }
        return schoolRepository.save(s);
    }

    public void delete(Long id) {
        if (!schoolRepository.existsById(id)) {
            throw new BizException(404, "学校不存在");
        }
        // 阶段 B B5：级联安全——学校仍有归属数据则拒绝删除，防止孤儿数据残留与越权。
        assertNoDependencies(id);
        schoolRepository.deleteById(id);
    }

    /** 校验学校无任何归属数据（级联删除安全网）。 */
    private void assertNoDependencies(Long schoolId) {
        if (levelRepository.existsBySchoolId(schoolId)
                || classRepository.existsBySchoolId(schoolId)
                || courseRepository.existsBySchoolId(schoolId)
                || examTypeRepository.existsBySchoolId(schoolId)
                || examRepository.existsBySchoolId(schoolId)
                || examCourseGroupRepository.existsBySchoolId(schoolId)
                || studentRepository.existsBySchoolIdAndDeletedFalse(schoolId)
                || gradeRepository.existsBySchoolId(schoolId)
                || accountRepository.existsBySchoolId(schoolId)
                || systemSettingRepository.existsBySchoolId(schoolId)) {
            throw new BizException("学校下仍有班级/学生/成绩/账号/设置等数据，无法删除");
        }
    }
}
