package com.printguard.jobs;

import com.printguard.models.GeneratedModelEntity;
import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "print_jobs_printjob")
public class PrintJobEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "status", length = 20, nullable = false)
    private String status = "queued";

    @Column(name = "gcode_path", length = 500, nullable = false)
    private String gcodePath = "";

    @Column(name = "estimated_time")
    private Integer estimatedTime;

    @Column(name = "progress", nullable = false)
    private Integer progress = 0;

    @Column(name = "start_time")
    private OffsetDateTime startTime;

    @Column(name = "end_time")
    private OffsetDateTime endTime;

    @Column(name = "printer_ip", length = 39)
    private String printerIp;

    @Column(name = "auto_pause_enabled", nullable = false)
    private Boolean autoPauseEnabled = true;

    @Column(name = "error_detail", columnDefinition = "TEXT", nullable = false)
    private String errorDetail = "";

    @Column(name = "celery_task_id", length = 255, nullable = false)
    private String celeryTaskId = "";

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "model_id", nullable = false)
    private GeneratedModelEntity model;

    public PrintJobEntity() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getGcodePath() { return gcodePath; }
    public void setGcodePath(String gcodePath) { this.gcodePath = gcodePath; }

    public Integer getEstimatedTime() { return estimatedTime; }
    public void setEstimatedTime(Integer estimatedTime) { this.estimatedTime = estimatedTime; }

    public Integer getProgress() { return progress; }
    public void setProgress(Integer progress) { this.progress = progress; }

    public OffsetDateTime getStartTime() { return startTime; }
    public void setStartTime(OffsetDateTime startTime) { this.startTime = startTime; }

    public OffsetDateTime getEndTime() { return endTime; }
    public void setEndTime(OffsetDateTime endTime) { this.endTime = endTime; }

    public String getPrinterIp() { return printerIp; }
    public void setPrinterIp(String printerIp) { this.printerIp = printerIp; }

    public Boolean getAutoPauseEnabled() { return autoPauseEnabled; }
    public void setAutoPauseEnabled(Boolean autoPauseEnabled) { this.autoPauseEnabled = autoPauseEnabled; }

    public String getErrorDetail() { return errorDetail; }
    public void setErrorDetail(String errorDetail) { this.errorDetail = errorDetail; }

    public String getCeleryTaskId() { return celeryTaskId; }
    public void setCeleryTaskId(String celeryTaskId) { this.celeryTaskId = celeryTaskId; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public GeneratedModelEntity getModel() { return model; }
    public void setModel(GeneratedModelEntity model) { this.model = model; }
}
