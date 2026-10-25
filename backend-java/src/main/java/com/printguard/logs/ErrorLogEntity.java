package com.printguard.logs;

import com.printguard.jobs.PrintJobEntity;
import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "error_logs_errorlog")
public class ErrorLogEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "error_type", length = 30, nullable = false)
    private String errorType;

    @Column(nullable = false)
    private Double confidence;

    @Column(name = "action_taken", length = 20, nullable = false)
    private String actionTaken = "alerted";

    @Column(nullable = false)
    private OffsetDateTime timestamp = OffsetDateTime.now();

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "print_job_id", nullable = false)
    private PrintJobEntity printJob;

    @Column(name = "frame_image", length = 100)
    private String frameImage;

    public ErrorLogEntity() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getErrorType() { return errorType; }
    public void setErrorType(String errorType) { this.errorType = errorType; }

    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }

    public String getActionTaken() { return actionTaken; }
    public void setActionTaken(String actionTaken) { this.actionTaken = actionTaken; }

    public OffsetDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(OffsetDateTime timestamp) { this.timestamp = timestamp; }

    public PrintJobEntity getPrintJob() { return printJob; }
    public void setPrintJob(PrintJobEntity printJob) { this.printJob = printJob; }

    public String getFrameImage() { return frameImage; }
    public void setFrameImage(String frameImage) { this.frameImage = frameImage; }
}
