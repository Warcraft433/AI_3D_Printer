package com.printguard.models;

import com.printguard.auth.UserEntity;
import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "generated_models_generatedmodel")
public class GeneratedModelEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String prompt = "";

    @Column(name = "file_path", length = 500, nullable = false)
    private String filePath = "";

    @Column(name = "thumbnail_path", length = 500, nullable = false)
    private String thumbnailPath = "";

    @Column(name = "generation_time")
    private Double generationTime;

    @Column(nullable = false, length = 20)
    private String status = "processing";

    @Column(name = "is_repaired", nullable = false)
    private Boolean isRepaired = false;

    @Column(name = "source_type", length = 10, nullable = false)
    private String sourceType = "text";

    @Column(nullable = false)
    private Integer steps = 32;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(name = "error_detail", columnDefinition = "TEXT", nullable = false)
    private String errorDetail = "";

    @Column(name = "celery_task_id", length = 255, nullable = false)
    private String celeryTaskId = "";

    public GeneratedModelEntity() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public String getThumbnailPath() { return thumbnailPath; }
    public void setThumbnailPath(String thumbnailPath) { this.thumbnailPath = thumbnailPath; }

    public Double getGenerationTime() { return generationTime; }
    public void setGenerationTime(Double generationTime) { this.generationTime = generationTime; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Boolean getIsRepaired() { return isRepaired; }
    public void setIsRepaired(Boolean isRepaired) { this.isRepaired = isRepaired; }

    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }

    public Integer getSteps() { return steps; }
    public void setSteps(Integer steps) { this.steps = steps; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public UserEntity getUser() { return user; }
    public void setUser(UserEntity user) { this.user = user; }

    public String getErrorDetail() { return errorDetail; }
    public void setErrorDetail(String errorDetail) { this.errorDetail = errorDetail; }

    public String getCeleryTaskId() { return celeryTaskId; }
    public void setCeleryTaskId(String celeryTaskId) { this.celeryTaskId = celeryTaskId; }
}
