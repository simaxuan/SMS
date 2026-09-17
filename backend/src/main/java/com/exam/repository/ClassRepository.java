package com.exam.repository;

import com.exam.entity.ClassEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ClassRepository extends JpaRepository<ClassEntity, Long> {

    boolean existsByName(String name);

    Optional<ClassEntity> findByName(String name);
}
