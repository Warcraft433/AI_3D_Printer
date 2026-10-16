package com.printguard.models;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.OffsetDateTime;
import java.util.List;

public interface GeneratedModelRepository extends JpaRepository<GeneratedModelEntity, Long> {
    List<GeneratedModelEntity> findAllByOrderByCreatedAtDesc();
    List<GeneratedModelEntity> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<GeneratedModelEntity> findByUserIdAndCreatedAtGreaterThanEqual(Long userId, OffsetDateTime createdAt);
    List<GeneratedModelEntity> findByCreatedAtGreaterThanEqual(OffsetDateTime createdAt);
    long countByStatus(String status);
    long countByUserId(Long userId);
    long countByUserIdAndStatus(Long userId, String status);
}
