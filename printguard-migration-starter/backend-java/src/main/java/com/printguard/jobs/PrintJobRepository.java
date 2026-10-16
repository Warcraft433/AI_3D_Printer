package com.printguard.jobs;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface PrintJobRepository extends JpaRepository<PrintJobEntity, Long> {
    List<PrintJobEntity> findAllByOrderByCreatedAtDesc();
    List<PrintJobEntity> findByStatusIn(List<String> statuses);
    Optional<PrintJobEntity> findFirstByStatusInOrderByCreatedAtDesc(List<String> statuses);
    long countByStatus(String status);

    @Query("SELECT j FROM PrintJobEntity j WHERE j.model.user.id = :userId ORDER BY j.createdAt DESC")
    List<PrintJobEntity> findByUserIdOrderByCreatedAtDesc(@Param("userId") Long userId);

    @Query("SELECT j FROM PrintJobEntity j WHERE j.model.user.id = :userId AND j.status IN :statuses ORDER BY j.createdAt DESC")
    List<PrintJobEntity> findByUserIdAndStatusInOrderByCreatedAtDesc(@Param("userId") Long userId, @Param("statuses") List<String> statuses);

    default Optional<PrintJobEntity> findFirstByUserIdAndStatusInOrderByCreatedAtDesc(Long userId, List<String> statuses) {
        List<PrintJobEntity> jobs = findByUserIdAndStatusInOrderByCreatedAtDesc(userId, statuses);
        return jobs.isEmpty() ? Optional.empty() : Optional.of(jobs.get(0));
    }

    @Query("SELECT COUNT(j) FROM PrintJobEntity j WHERE j.model.user.id = :userId")
    long countByUserId(@Param("userId") Long userId);

    @Query("SELECT COUNT(j) FROM PrintJobEntity j WHERE j.model.user.id = :userId AND j.status = :status")
    long countByUserIdAndStatus(@Param("userId") Long userId, @Param("status") String status);

    @Query("SELECT j FROM PrintJobEntity j WHERE j.model.user.id = :userId AND j.createdAt >= :startDate")
    List<PrintJobEntity> findByUserIdAndCreatedAtGreaterThanEqual(@Param("userId") Long userId, @Param("startDate") java.time.OffsetDateTime startDate);

    @Query("SELECT COUNT(j) FROM PrintJobEntity j WHERE j.model.user.id = :userId AND (j.status IN ('ready_to_print', 'paused', 'printing', 'completed') OR (j.gcodePath IS NOT NULL AND j.gcodePath != ''))")
    long countSlicedByUserId(@Param("userId") Long userId);

    @Query("SELECT COUNT(j) FROM PrintJobEntity j WHERE j.status IN ('ready_to_print', 'paused', 'printing', 'completed') OR (j.gcodePath IS NOT NULL AND j.gcodePath != '')")
    long countSliced();

    List<PrintJobEntity> findByCreatedAtGreaterThanEqual(java.time.OffsetDateTime startDate);
}
