package com.exam.service;

import com.exam.common.BizException;
import com.exam.dto.NameRequest;
import com.exam.entity.ClassEntity;
import com.exam.entity.Course;
import com.exam.repository.ClassRepository;
import com.exam.repository.CourseRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DictionaryService {

    private final ClassRepository classRepository;
    private final CourseRepository courseRepository;

    public DictionaryService(ClassRepository classRepository, CourseRepository courseRepository) {
        this.classRepository = classRepository;
        this.courseRepository = courseRepository;
    }

    public List<ClassEntity> listClasses() {
        return classRepository.findAll();
    }

    public ClassEntity createClass(NameRequest req) {
        if (classRepository.existsByName(req.getName())) {
            throw new BizException("班级名称已存在");
        }
        ClassEntity c = new ClassEntity();
        c.setName(req.getName());
        return classRepository.save(c);
    }

    public ClassEntity updateClass(Long id, NameRequest req) {
        ClassEntity c = classRepository.findById(id)
                .orElseThrow(() -> new BizException(404, "班级不存在"));
        c.setName(req.getName());
        return classRepository.save(c);
    }

    public void deleteClass(Long id) {
        classRepository.deleteById(id);
    }

    public List<Course> listCourses() {
        return courseRepository.findAll();
    }

    public Course createCourse(NameRequest req) {
        if (courseRepository.existsByName(req.getName())) {
            throw new BizException("课程名称已存在");
        }
        Course c = new Course();
        c.setName(req.getName());
        return courseRepository.save(c);
    }

    public Course updateCourse(Long id, NameRequest req) {
        Course c = courseRepository.findById(id)
                .orElseThrow(() -> new BizException(404, "课程不存在"));
        c.setName(req.getName());
        return courseRepository.save(c);
    }

    public void deleteCourse(Long id) {
        courseRepository.deleteById(id);
    }
}
