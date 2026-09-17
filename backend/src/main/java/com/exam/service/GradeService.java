package com.exam.service;

import com.exam.common.BizException;
import com.exam.dto.GradeRequest;
import com.exam.entity.Grade;
import com.exam.repository.GradeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class GradeService {

    private final GradeRepository gradeRepository;

    public GradeService(GradeRepository gradeRepository) {
        this.gradeRepository = gradeRepository;
    }

    public List<Grade> list(Long examId, Long courseId, Long studentId) {
        if (examId != null && courseId != null) {
            return gradeRepository.findByExamIdAndCourseId(examId, courseId);
        }
        return gradeRepository.findAll();
    }

    @Transactional
    public Grade create(GradeRequest req) {
        if (gradeRepository.existsByStudentIdAndExamIdAndCourseId(
                req.getStudentId(), req.getExamId(), req.getCourseId())) {
            throw new BizException("该学生该考试该课程的成绩已存在，请勿重复录入");
        }
        return gradeRepository.save(toEntity(req));
    }

    @Transactional
    public Grade update(Long id, GradeRequest req) {
        Grade g = gradeRepository.findById(id)
                .orElseThrow(() -> new BizException(404, "成绩不存在"));
        if (gradeRepository.existsByStudentIdAndExamIdAndCourseIdAndIdNot(
                req.getStudentId(), req.getExamId(), req.getCourseId(), id)) {
            throw new BizException("该学生该考试该课程的成绩已存在，请勿重复录入");
        }
        g.setStudentId(req.getStudentId());
        g.setExamId(req.getExamId());
        g.setCourseId(req.getCourseId());
        g.setScore(req.getScore());
        return gradeRepository.save(g);
    }

    @Transactional
    public void delete(Long id) {
        if (!gradeRepository.existsById(id)) {
            throw new BizException(404, "成绩不存在");
        }
        gradeRepository.deleteById(id);
    }

    @Transactional
    public int batchCreate(List<GradeRequest> reqs) {
        int count = 0;
        for (GradeRequest req : reqs) {
            if (!gradeRepository.existsByStudentIdAndExamIdAndCourseId(
                    req.getStudentId(), req.getExamId(), req.getCourseId())) {
                gradeRepository.save(toEntity(req));
                count++;
            }
        }
        return count;
    }

    private Grade toEntity(GradeRequest req) {
        Grade g = new Grade();
        g.setStudentId(req.getStudentId());
        g.setExamId(req.getExamId());
        g.setCourseId(req.getCourseId());
        g.setScore(req.getScore());
        return g;
    }
}
