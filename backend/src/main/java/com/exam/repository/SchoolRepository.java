package com.exam.repository;

import com.exam.entity.School;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SchoolRepository extends JpaRepository<School, Long> {

    boolean existsByCode(String code);
}
