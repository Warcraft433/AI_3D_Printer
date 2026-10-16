package com.printguard.jobs;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.printguard.models.ModelResponse;
import java.time.OffsetDateTime;

public record PrintJobResponse(
    Long id,
    String status,
    @JsonProperty("gcode_path") String gcodePath,
    @JsonProperty("estimated_time") Integer estimatedTime,
    Integer progress,
    @JsonProperty("printer_ip") String printerIp,
    @JsonProperty("auto_pause_enabled") Boolean autoPauseEnabled,
    @JsonProperty("error_detail") String errorDetail,
    @JsonProperty("created_at") OffsetDateTime createdAt,
    ModelResponse model,
    @JsonProperty("model_prompt") String modelPrompt,
    @JsonProperty("gcode_url") String gcodeUrl,
    @JsonProperty("hardware_confirmed") Boolean hardwareConfirmed,
    @JsonProperty("limitation_note") String limitationNote,
    @JsonProperty("printer_profile_verified") Boolean printerProfileVerified,
    @JsonProperty("gcode_size") Long gcodeSize,
    @JsonProperty("is_real_gcode") Boolean isRealGcode
) {
    public static PrintJobResponse from(PrintJobEntity job) {
        return from(job, null, null);
    }

    public static PrintJobResponse from(PrintJobEntity job, Boolean hardwareConfirmed, String limitationNote) {
        String prompt = job.getModel() != null ? job.getModel().getPrompt() : "Print Job #" + job.getId();
        boolean realGcode = job.getGcodePath() != null
                && !job.getGcodePath().isBlank()
                && !job.getGcodePath().contains("demo_print.gcode");
        String gcodeUrl = realGcode
            ? "/api/print-jobs/" + job.getId() + "/download-gcode/"
            : null;
        boolean profileVerified = realGcode && "ready_to_print".equals(job.getStatus());

        return new PrintJobResponse(
            job.getId(),
            job.getStatus(),
            job.getGcodePath(),
            job.getEstimatedTime(),
            job.getProgress(),
            job.getPrinterIp(),
            job.getAutoPauseEnabled(),
            job.getErrorDetail(),
            job.getCreatedAt(),
            job.getModel() != null ? ModelResponse.from(job.getModel()) : null,
            prompt,
            gcodeUrl,
            hardwareConfirmed,
            limitationNote,
            profileVerified,
            null,
            realGcode
        );
    }
}
