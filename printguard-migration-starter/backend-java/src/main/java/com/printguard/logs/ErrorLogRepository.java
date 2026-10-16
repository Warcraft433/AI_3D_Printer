package com.printguard.logs;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface ErrorLogRepository extends JpaRepository<ErrorLogEntity, Long> {
    List<ErrorLogEntity> findAllByOrderByTimestampDesc();
    List<ErrorLogEntity> findByPrintJobIdOrderByTimestampDesc(Long printJobId);

    @Query("SELECT l FROM ErrorLogEntity l WHERE l.printJob.model.user.id = :userId ORDER BY l.timestamp DESC")
    List<ErrorLogEntity> findByUserIdOrderByTimestampDesc(@Param("userId") Long userId);

    @Query("SELECT COUNT(l) FROM ErrorLogEntity l WHERE l.printJob.model.user.id = :userId")
    long countByUserId(@Param("userId") Long userId);

    @Query("SELECT l FROM ErrorLogEntity l WHERE l.printJob.model.user.id = :userId AND l.timestamp >= :startDate")
    List<ErrorLogEntity> findByUserIdAndTimestampGreaterThanEqual(@Param("userId") Long userId, @Param("startDate") java.time.OffsetDateTime startDate);

    List<ErrorLogEntity> findByTimestampGreaterThanEqual(java.time.OffsetDateTime startDate);
}
