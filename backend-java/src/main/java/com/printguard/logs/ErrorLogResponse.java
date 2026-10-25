package com.printguard.logs;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

public record ErrorLogResponse(
    Long id,
    @JsonProperty("error_type") String errorType,
    Double confidence,
    @JsonProperty("action_taken") String actionTaken,
    OffsetDateTime timestamp,
    @JsonProperty("print_job_id") Long printJobId,
    @JsonProperty("frame_image") String frameImage
) {
    public static ErrorLogResponse from(ErrorLogEntity log) {
        return new ErrorLogResponse(
            log.getId(),
            log.getErrorType(),
            log.getConfidence(),
            log.getActionTaken(),
            log.getTimestamp(),
            log.getPrintJob() != null ? log.getPrintJob().getId() : null,
            log.getFrameImage()
        );
    }
}
