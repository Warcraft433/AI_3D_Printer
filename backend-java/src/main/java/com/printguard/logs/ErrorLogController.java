package com.printguard.logs;

import com.printguard.auth.SecurityUtils;
import com.printguard.auth.UserEntity;
import com.printguard.jobs.PrintJobEntity;
import com.printguard.jobs.PrintJobRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/api/error-logs", "/api/error-logs/"})
public class ErrorLogController {
    private final ErrorLogRepository logRepository;
    private final PrintJobRepository jobRepository;
    private final SecurityUtils securityUtils;
    private boolean inferenceRunning = true;

    public ErrorLogController(ErrorLogRepository logRepository, PrintJobRepository jobRepository, SecurityUtils securityUtils) {
        this.logRepository = logRepository;
        this.jobRepository = jobRepository;
        this.securityUtils = securityUtils;
    }

    @GetMapping({"", "/"})
    public List<ErrorLogResponse> list() {
        UserEntity user = securityUtils.requireCurrentUser();
        List<ErrorLogEntity> logs = securityUtils.isAdmin(user)
                ? logRepository.findAllByOrderByTimestampDesc()
                : logRepository.findByUserIdOrderByTimestampDesc(user.getId());
        return logs.stream()
                .map(ErrorLogResponse::from)
                .toList();
    }

    @PostMapping({"", "/", "/detection-alert", "/detection-alert/"})
    public ResponseEntity<Map<String, String>> detectionAlert(@RequestBody Map<String, Object> body) {
        UserEntity user = securityUtils.requireCurrentUser();
        String errorType = (String) body.getOrDefault("error_type", "spaghetti");
        Double confidence = body.get("confidence") instanceof Number n ? n.doubleValue() : 0.85;

        Long explicitJobId = null;
        if (body.get("print_job_id") instanceof Number num) {
            explicitJobId = num.longValue();
        } else if (body.get("print_job_id") instanceof String s) {
            try { explicitJobId = Long.parseLong(s); } catch (Exception ignored) {}
        }

        PrintJobEntity targetJob = null;
        if (explicitJobId != null) {
            targetJob = jobRepository.findById(explicitJobId).orElse(null);
            if (targetJob != null) {
                securityUtils.verifyOwnershipOrAdmin(targetJob.getModel().getUser(), "log defect for this job");
            }
        }
        if (targetJob == null) {
            targetJob = securityUtils.isAdmin(user)
                    ? jobRepository.findFirstByStatusInOrderByCreatedAtDesc(List.of("printing", "ready_to_print", "paused")).orElse(null)
                    : jobRepository.findFirstByUserIdAndStatusInOrderByCreatedAtDesc(user.getId(), List.of("printing", "ready_to_print", "paused")).orElse(null);
        }

        if (targetJob != null) {
            ErrorLogEntity log = new ErrorLogEntity();
            log.setPrintJob(targetJob);
            log.setErrorType(errorType);
            log.setConfidence(confidence);
            log.setActionTaken("alerted");
            logRepository.save(log);

            if (Boolean.TRUE.equals(targetJob.getAutoPauseEnabled())) {
                targetJob.setStatus("paused");
                jobRepository.save(targetJob);
            }
            return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED).body(Map.of(
                "status", "alert broadcasted",
                "log_id", String.valueOf(log.getId())
            ));
        }

        return ResponseEntity.ok(Map.of("status", "alert broadcasted (no active job)"));
    }

    @GetMapping("/inference-control/")
    public ResponseEntity<Map<String, Object>> getInferenceStatus() {
        UserEntity user = securityUtils.requireCurrentUser();
        PrintJobEntity activeJob = securityUtils.isAdmin(user)
                ? jobRepository.findFirstByStatusInOrderByCreatedAtDesc(List.of("printing", "paused", "ready_to_print")).orElse(null)
                : jobRepository.findFirstByUserIdAndStatusInOrderByCreatedAtDesc(user.getId(), List.of("printing", "paused", "ready_to_print")).orElse(null);

        return ResponseEntity.ok(Map.of(
            "inference_running", inferenceRunning,
            "active_job_id", activeJob != null ? activeJob.getId() : 0,
            "active_job_status", activeJob != null ? activeJob.getStatus() : "idle"
        ));
    }

    @PostMapping("/inference-control/")
    public ResponseEntity<Map<String, Object>> setInferenceStatus(@RequestBody Map<String, Object> body) {
        securityUtils.requireCurrentUser();
        String action = (String) body.getOrDefault("action", "start");
        this.inferenceRunning = "start".equalsIgnoreCase(action);
        return ResponseEntity.ok(Map.of(
            "inference_running", this.inferenceRunning,
            "message", "Inference " + (this.inferenceRunning ? "started" : "stopped")
        ));
    }
}
