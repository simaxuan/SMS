package com.exam.service;

import com.exam.common.BizException;
import com.exam.dto.ExamRequest;
import com.exam.entity.Exam;
import com.exam.repository.ExamRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ExamService {

    private final ExamRepository examRepository;

    public ExamService(ExamRepository examRepository) {
        this.examRepository = examRepository;
    }

    public List<Exam> list() {
        return examRepository.findAll();
    }

    public Exam getById(Long id) {
        return examRepository.findById(id)
                .orElseThrow(() -> new BizException(404, "考试不存在"));
    }

    public Exam create(ExamRequest req) {
        Exam e = new Exam();
        e.setName(req.getName());
        e.setExamDate(req.getExamDate());
        return examRepository.save(e);
    }

    public Exam update(Long id, ExamRequest req) {
        Exam e = getById(id);
        e.setName(req.getName());
        e.setExamDate(req.getExamDate());
        return examRepository.save(e);
    }

    public void delete(Long id) {
        examRepository.deleteById(id);
    }
}
