package com.exam.service;

import com.exam.common.BizException;
import com.exam.dto.StudentRequest;
import com.exam.entity.Student;
import com.exam.repository.StudentRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentService {

    private final StudentRepository studentRepository;

    public StudentService(StudentRepository studentRepository) {
        this.studentRepository = studentRepository;
    }

    public Page<Student> list(String keyword, Pageable pageable) {
        return studentRepository.search(keyword, pageable);
    }

    public Student getById(Long id) {
        return studentRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BizException(404, "学生不存在"));
    }

    @Transactional
    public Student create(StudentRequest req) {
        if (studentRepository.existsByStudentNo(req.getStudentNo())) {
            throw new BizException("学号已存在");
        }
        Student s = new Student();
        apply(s, req);
        return studentRepository.save(s);
    }

    @Transactional
    public Student update(Long id, StudentRequest req) {
        Student s = getById(id);
        if (studentRepository.existsByStudentNoAndIdNot(req.getStudentNo(), id)) {
            throw new BizException("学号已被其他学生使用");
        }
        apply(s, req);
        return studentRepository.save(s);
    }

    @Transactional
    public void delete(Long id) {
        int updated = studentRepository.softDelete(id);
        if (updated == 0) {
            throw new BizException(404, "学生不存在");
        }
    }

    private void apply(Student s, StudentRequest req) {
        s.setStudentNo(req.getStudentNo());
        s.setName(req.getName());
        s.setGender(req.getGender());
        s.setClassId(req.getClassId());
        s.setBirthDate(req.getBirthDate());
        s.setPhone(req.getPhone());
    }
}
