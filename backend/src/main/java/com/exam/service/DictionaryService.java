package com.exam.service;

import com.exam.common.BizException;
import com.exam.common.LoginUserContext;
import com.exam.dto.CourseRequest;
import com.exam.dto.CreateClassRequest;
import com.exam.dto.NameRequest;
import com.exam.entity.ClassEntity;
import com.exam.entity.Course;
import com.exam.entity.ExamType;
import com.exam.entity.Account;
import com.exam.entity.Level;
import com.exam.repository.ClassRepository;
import com.exam.repository.CourseRepository;
import com.exam.repository.ExamRepository;
import com.exam.repository.ExamTypeRepository;
import com.exam.repository.GradeRepository;
import com.exam.repository.LevelRepository;
import com.exam.repository.StudentRepository;
import com.exam.repository.TeacherClassRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DictionaryService {

    private final ClassRepository classRepository;
    private final CourseRepository courseRepository;
    private final ExamTypeRepository examTypeRepository;
    private final ExamRepository examRepository;
    private final StudentRepository studentRepository;
    private final GradeRepository gradeRepository;
    private final LevelRepository levelRepository;
    private final TeacherClassRepository teacherClassRepository;
    private final DataScopeService dataScopeService;

    public DictionaryService(ClassRepository classRepository,
                             CourseRepository courseRepository,
                             ExamTypeRepository examTypeRepository,
                             ExamRepository examRepository,
                             StudentRepository studentRepository,
                             GradeRepository gradeRepository,
                             LevelRepository levelRepository,
                             TeacherClassRepository teacherClassRepository,
                             DataScopeService dataScopeService) {
        this.classRepository = classRepository;
        this.courseRepository = courseRepository;
        this.examTypeRepository = examTypeRepository;
        this.examRepository = examRepository;
        this.studentRepository = studentRepository;
        this.gradeRepository = gradeRepository;
        this.levelRepository = levelRepository;
        this.teacherClassRepository = teacherClassRepository;
        this.dataScopeService = dataScopeService;
    }

    /** 读路径默认按登录校收敛（无参入口，供内部/测试/前端默认调用）。 */
    public List<ClassEntity> listClasses() {
        return listClasses(dataScopeService.effectiveSchoolId());
    }

    /** 跨校查看入口：仅 SCOPE_ALL 且显式传入 schoolId 时按该参数查；否则按登录校收敛；true-global 放开全校。 */
    public List<ClassEntity> listClasses(Long schoolId) {
        // 跨校查看：仅 SCOPE_ALL 且显式传入 schoolId 时按该参数查；否则按登录校收敛
        Long sid = (schoolId != null && dataScopeService.isGlobalAdmin())
                ? schoolId : dataScopeService.effectiveSchoolId();
        return sid == null ? classRepository.findAll() : classRepository.findBySchoolId(sid);
    }

    public ClassEntity createClass(CreateClassRequest req) {
        if (req.getName() == null || req.getName().isBlank()) {
            throw new BizException("班级名称不能为空");
        }
        // 阶段 B B4 修正：写路径统一决策——全局超管可跨校建班并显式指定 schoolId/levelId；
        // 普通老师锁定登录校，防跨校写入。
        Long schoolId = dataScopeService.resolveWriteSchoolId(req.getSchoolId());
        // 三遗留处理：防止真全局超管在"全部学校"总览态下误建无归属孤儿班级；
        // 切换/指定具体学校后 schoolId 即目标校，创建正常收敛。
        if (schoolId == null) {
            throw new BizException("请先切换到具体学校后再创建班级，或在请求中指定归属学校");
        }
        if (schoolId != null) {
            if (classRepository.existsByNameAndSchoolId(req.getName(), schoolId)) {
                throw new BizException("该学校下班级名称已存在");
            }
        } else {
            if (classRepository.existsByName(req.getName())) {
                throw new BizException("班级名称已存在");
            }
        }
        ClassEntity c = new ClassEntity();
        c.setName(req.getName());
        // 强制归属：非登录校的 level 不允许跨校引用（level.schoolId 须与目标 schoolId 一致）
        if (req.getLevelId() != null && schoolId != null) {
            Level lv = levelRepository.findById(req.getLevelId()).orElse(null);
            if (lv == null || lv.getSchoolId() == null || !lv.getSchoolId().equals(schoolId)) {
                throw new BizException("级部不属于该学校，无法建立关联");
            }
        }
        c.setLevelId(req.getLevelId());
        c.setSchoolId(schoolId);
        return classRepository.save(c);
    }

    public ClassEntity updateClass(Long id, CreateClassRequest req) {
        ClassEntity c = classRepository.findById(id)
                .orElseThrow(() -> new BizException(404, "班级不存在"));
        // #6 写路径 schoolId 决策（对齐 create 的 B4 口径）：全局超管信任请求体可迁移班级归属；
        // 普通老师锁定登录校，防止迁到他校；无登录校保留原值。
        Long schoolId = dataScopeService.resolveWriteSchoolId(req.getSchoolId());
        if (schoolId == null) {
            schoolId = c.getSchoolId();
        }
        if (req.getName() != null && !req.getName().isBlank()) {
            // 校名唯一重校（以生效 schoolId 为准）
            if (schoolId != null) {
                if (classRepository.existsByNameAndSchoolId(req.getName(), schoolId)) {
                    throw new BizException("该学校下班级名称已存在");
                }
            } else if (classRepository.existsByName(req.getName())) {
                throw new BizException("班级名称已存在");
            }
            c.setName(req.getName());
        }
        if (req.getLevelId() != null) {
            // level 归属须与班级生效归属校一致（防跨校级部引用）
            Level lv = levelRepository.findById(req.getLevelId()).orElse(null);
            if (lv == null || lv.getSchoolId() == null
                    || (schoolId != null && !lv.getSchoolId().equals(schoolId))) {
                throw new BizException("级部不属于该学校，无法建立关联");
            }
            c.setLevelId(req.getLevelId());
        }
        if (schoolId != null) {
            c.setSchoolId(schoolId);
        }
        return classRepository.save(c);
    }

    public void deleteClass(Long id) {
        if (studentRepository.existsByClassIdAndDeletedFalse(id)) {
            throw new BizException("该班级下仍有学生，无法删除");
        }
        // 级联安全（P2-8）：先清理该班级的全部老师-班级归属，防止删班后 teacher_class 悬空成孤儿。
        teacherClassRepository.deleteByClassId(id);
        classRepository.deleteById(id);
    }

    public List<Course> listCourses() {
        // 读路径 school 下推：按登录校收敛（SCOPE_ALL 带登录校同样按校过滤；true-global 放开全校）
        Long sid = dataScopeService.effectiveSchoolId();
        return sid == null ? courseRepository.findAll() : courseRepository.findBySchoolId(sid);
    }

    public Course createCourse(CourseRequest req) {
        // B4 修正：全局超管可跨校建课程并显式指定 schoolId；普通老师锁定登录校。
        Long schoolId = dataScopeService.resolveWriteSchoolId(req.getSchoolId());
        if (schoolId == null) {
            throw new BizException("schoolId 不能为空");
        }
        if (courseRepository.existsByNameAndSchoolId(req.getName(), schoolId)) {
            throw new BizException("课程名称已存在");
        }
        Course c = new Course();
        c.setName(req.getName());
        c.setFullScore(req.getFullScore());
        c.setSchoolId(schoolId);
        return courseRepository.save(c);
    }

    public Course updateCourse(Long id, CourseRequest req) {
        // P3-8 归属加固（B4 修正）：全局超管放开跨校编辑；普通老师按当前登录校校验被改课程归属，防止跨校改他人课程。
        Course c = courseRepository.findById(id)
                .orElseThrow(() -> new BizException(404, "课程不存在"));
        // 归属加固（统一口径）：SCOPE_ALL 带登录校 → 仅可改登录校课程；true-global（effectiveSchoolId 为空）放开全校。
        Long eff = dataScopeService.effectiveSchoolId();
        if (eff != null && !eff.equals(c.getSchoolId())) {
            throw new BizException(403, "无权修改其他学校的课程");
        }
        if (req.getName() != null && !req.getName().equals(c.getName())
                && courseRepository.existsByNameAndSchoolId(req.getName(), c.getSchoolId())) {
            throw new BizException("课程名称已存在");
        }
        c.setName(req.getName());
        c.setFullScore(req.getFullScore());
        return courseRepository.save(c);
    }

    public void deleteCourse(Long id) {
        if (gradeRepository.existsByCourseId(id)) {
            throw new BizException("该课程已被成绩引用，无法删除");
        }
        courseRepository.deleteById(id);
    }

    public List<ExamType> listExamTypes() {
        // 读路径 school 下推：按登录校收敛（SCOPE_ALL 带登录校同样按校过滤；true-global 放开全校）
        Long sid = dataScopeService.effectiveSchoolId();
        return sid == null ? examTypeRepository.findAll() : examTypeRepository.findBySchoolId(sid);
    }

    public ExamType createExamType(NameRequest req) {
        // B4 修正：全局超管可跨校建考试类型并显式指定 schoolId；普通老师锁定登录校。
        Long schoolId = dataScopeService.resolveWriteSchoolId(req.getSchoolId());
        if (schoolId == null) {
            if (examTypeRepository.existsByName(req.getName())) {
                throw new BizException("考试类型已存在");
            }
        } else if (examTypeRepository.existsBySchoolIdAndName(schoolId, req.getName())) {
            throw new BizException("该学校下考试类型已存在");
        }
        ExamType t = new ExamType();
        t.setName(req.getName());
        t.setSchoolId(schoolId);
        return examTypeRepository.save(t);
    }

    public ExamType updateExamType(Long id, NameRequest req) {
        // P3-8 归属加固（B4 修正）：全局超管放开跨校编辑；普通老师按当前登录校校验。
        ExamType t = examTypeRepository.findById(id)
                .orElseThrow(() -> new BizException(404, "考试类型不存在"));
        // 归属加固（统一口径）：SCOPE_ALL 带登录校 → 仅可改登录校考试类型；true-global 放开全校；全局类型（schoolId 空）可改。
        Long eff = dataScopeService.effectiveSchoolId();
        if (eff != null && t.getSchoolId() != null && !eff.equals(t.getSchoolId())) {
            throw new BizException(403, "无权修改其他学校的考试类型");
        }
        if (req.getName() != null && !req.getName().equals(t.getName())
                && (t.getSchoolId() == null
                        ? examTypeRepository.existsByName(req.getName())
                        : examTypeRepository.existsBySchoolIdAndName(t.getSchoolId(), req.getName()))) {
            throw new BizException("该学校下考试类型已存在");
        }
        t.setName(req.getName());
        return examTypeRepository.save(t);
    }

    public void deleteExamType(Long id) {
        if (examRepository.existsByExamTypeId(id)) {
            throw new BizException("该考试类型已被考试引用，无法删除");
        }
        examTypeRepository.deleteById(id);
    }
}
