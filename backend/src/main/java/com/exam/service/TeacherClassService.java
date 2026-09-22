package com.exam.service;

import com.exam.common.BizException;
import com.exam.dto.TeacherClassAssignRequest;
import com.exam.entity.Account;
import com.exam.entity.ClassEntity;
import com.exam.entity.TeacherClass;
import com.exam.repository.AccountRepository;
import com.exam.repository.ClassRepository;
import com.exam.repository.TeacherClassRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/** 老师-班级归属关系绑定/查询/解绑（多对多，天然支持跨年级）。 */
@Service
public class TeacherClassService {

    private final TeacherClassRepository tcRepository;
    private final AccountRepository accountRepository;
    private final ClassRepository classRepository;

    public TeacherClassService(TeacherClassRepository tcRepository,
                               AccountRepository accountRepository,
                               ClassRepository classRepository) {
        this.tcRepository = tcRepository;
        this.accountRepository = accountRepository;
        this.classRepository = classRepository;
    }

    /** 按老师或班级查询归属关系；两者皆空则返回全部。 */
    public List<TeacherClass> query(Long teacherAccountId, Long classId) {
        if (teacherAccountId != null) {
            return tcRepository.findByTeacherAccountId(teacherAccountId);
        }
        if (classId != null) {
            return tcRepository.findByClassIdIn(List.of(classId));
        }
        return tcRepository.findAll();
    }

    /** 将某老师批量绑定到多个班级（已绑定跳过，不重复）。 */
    @Transactional
    public List<TeacherClass> assign(TeacherClassAssignRequest req) {
        Account teacher = accountRepository.findById(req.getTeacherAccountId())
                .orElseThrow(() -> new BizException(404, "教师账号不存在"));
        List<TeacherClass> result = new ArrayList<>();
        for (Long classId : req.getClassIds()) {
            ClassEntity c = classRepository.findById(classId)
                    .orElseThrow(() -> new BizException(404, "班级不存在"));
            // I-4 跨校一致性：绑定班级必须与老师同校园，杜绝越校绑定造成的数据越权
            if (teacher.getSchoolId() != null && c.getSchoolId() != null
                    && !teacher.getSchoolId().equals(c.getSchoolId())) {
                throw new BizException(403, "不能将其他学校的班级绑定给该老师");
            }
            if (!tcRepository.existsByTeacherAccountIdAndClassId(req.getTeacherAccountId(), classId)) {
                TeacherClass tc = new TeacherClass();
                tc.setTeacherAccountId(req.getTeacherAccountId());
                tc.setClassId(classId);
                result.add(tcRepository.save(tc));
            }
        }
        return result;
    }

    public void remove(Long id) {
        if (!tcRepository.existsById(id)) {
            throw new BizException(404, "绑定关系不存在");
        }
        tcRepository.deleteById(id);
    }
}
