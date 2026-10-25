package com.printguard.jobs;

import com.printguard.auth.SecurityUtils;
import com.printguard.auth.UserEntity;
import com.printguard.models.GeneratedModelEntity;
import com.printguard.models.GeneratedModelRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Tag(name = "Print Jobs", description = "Slicing, queueing, and monitoring 3D print jobs")
@RestController
@RequestMapping({"/api/print-jobs", "/api/print-jobs/"})
public class PrintJobController {
    private final PrintJobRepository repository;
    private final GeneratedModelRepository modelRepository;
    private final SecurityUtils securityUtils;
    private final com.printguard.websocket.WebSocketEventPublisher eventPublisher;
    private final OctoPrintService octoPrintService;
    private final com.printguard.slicer.SlicerService slicerService;
    private final com.printguard.storage.StorageService storageService;
    private org.springframework.context.ApplicationEventPublisher applicationEventPublisher;

    @Value("${printguard.media-dir:c:/Projects01/AI_3D_Printer/media}")
    private String mediaDir;

    @org.springframework.beans.factory.annotation.Autowired
    public PrintJobController(
            PrintJobRepository repository,
            GeneratedModelRepository modelRepository,
            SecurityUtils securityUtils,
            com.printguard.websocket.WebSocketEventPublisher eventPublisher,
            OctoPrintService octoPrintService,
            com.printguard.slicer.SlicerService slicerService,
            com.printguard.storage.StorageService storageService,
            org.springframework.context.ApplicationEventPublisher applicationEventPublisher) {
        this.repository = repository;
        this.modelRepository = modelRepository;
        this.securityUtils = securityUtils;
        this.eventPublisher = eventPublisher;
        this.octoPrintService = octoPrintService;
        this.slicerService = slicerService;
        this.storageService = storageService;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    public PrintJobController(
            PrintJobRepository repository,
            GeneratedModelRepository modelRepository,
            SecurityUtils securityUtils,
            com.printguard.websocket.WebSocketEventPublisher eventPublisher,
            OctoPrintService octoPrintService,
            com.printguard.slicer.SlicerService slicerService,
            com.printguard.storage.StorageService storageService) {
        this(repository, modelRepository, securityUtils, eventPublisher, octoPrintService, slicerService, storageService, null);
    }

    @Operation(summary = "List Print Jobs", description = "Retrieves all print jobs belonging to the authenticated user")
    @GetMapping({"", "/"})
    public List<PrintJobResponse> list() {
        UserEntity user = securityUtils.requireCurrentUser();
        List<PrintJobEntity> jobs = securityUtils.isAdmin(user)
                ? repository.findAllByOrderByCreatedAtDesc()
                : repository.findByUserIdOrderByCreatedAtDesc(user.getId());
        return jobs.stream()
                .map(PrintJobResponse::from)
                .toList();
    }

    @Operation(summary = "Create Print Job", description = "Creates a print job and slices the model with PrusaSlicer (sync or async)")
    @PostMapping({"", "/"})
    public ResponseEntity<PrintJobResponse> create(@RequestBody Map<String, Object> body) {
        Long modelId;
        Object modelObj = body.get("model");
        if (modelObj instanceof Number num) {
            modelId = num.longValue();
        } else if (modelObj instanceof String str) {
            modelId = Long.parseLong(str);
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Model ID required");
        }

        GeneratedModelEntity model = modelRepository.findById(modelId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Model not found"));

        securityUtils.verifyOwnershipOrAdmin(model.getUser(), "create a print job for this model");

        PrintJobEntity job = new PrintJobEntity();
        job.setModel(model);
        job.setCeleryTaskId(UUID.randomUUID().toString());
        job.setAutoPauseEnabled(true);
        job.setProgress(0);

        // Guard: Demo meshes must never be marked printer-ready or sliced
        boolean isDemo = model.getErrorDetail() != null && model.getErrorDetail().contains("[DEMO FALLBACK]");
        if (isDemo) {
            job.setStatus("failed");
            job.setGcodePath("");
            job.setErrorDetail("Demo placeholder meshes cannot be sliced or marked printer-ready. Generate a genuine 3D model first.");
            PrintJobEntity saved = repository.save(job);
            eventPublisher.publishPrintJobStatus(saved.getId(), "failed", 0, Map.of(), saved.getErrorDetail());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(PrintJobResponse.from(saved));
        }

        // Guard: Slicer engine availability
        if (!slicerService.isAvailable()) {
            job.setStatus("failed");
            job.setGcodePath("");
            job.setErrorDetail("Slicer unavailable: No slicing engine binary found at [" + slicerService.getSlicerPath() + "]. Real G-code could not be generated.");
            PrintJobEntity saved = repository.save(job);
            eventPublisher.publishPrintJobStatus(saved.getId(), "failed", 0, Map.of(), saved.getErrorDetail());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(PrintJobResponse.from(saved));
        }

        // Ensure model file is locally present for slicing (download from Supabase Storage if needed)
        Path modelPath;
        try {
            modelPath = storageService.ensureLocalModelFile(model);
        } catch (Exception e) {
            job.setStatus("failed");
            job.setGcodePath("");
            job.setErrorDetail("Model file not found in storage: " + model.getFilePath());
            PrintJobEntity saved = repository.save(job);
            eventPublisher.publishPrintJobStatus(saved.getId(), "failed", 0, Map.of(), saved.getErrorDetail());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(PrintJobResponse.from(saved));
        }

        if (!java.nio.file.Files.isRegularFile(modelPath)) {
            job.setStatus("failed");
            job.setGcodePath("");
            job.setErrorDetail("Model file not found on disk: " + model.getFilePath());
            PrintJobEntity saved = repository.save(job);
            eventPublisher.publishPrintJobStatus(saved.getId(), "failed", 0, Map.of(), saved.getErrorDetail());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(PrintJobResponse.from(saved));
        }

        // Create initial job record in slicing state
        job.setStatus("slicing");
        job.setGcodePath("");
        PrintJobEntity initial = repository.save(job);

        // Slicing workflow stages for WebSocket listeners (matching SlicingModal STATUS_LABELS)
        eventPublisher.publishPrintJobStatus(initial.getId(), "slicing_started", 10, Map.of(), "Initializing Slicer...");
        eventPublisher.publishPrintJobStatus(initial.getId(), "preparing_model", 30, Map.of(), "Preparing 3D Model: " + model.getPrompt());
        eventPublisher.publishPrintJobStatus(initial.getId(), "running_prusaslicer", 60, Map.of(), "Running PrusaSlicer...");

        String gcodeFilename = "job_" + initial.getId() + "_" + UUID.randomUUID().toString().substring(0, 8) + ".gcode";
        Path destGcode = Paths.get(mediaDir, "gcode", gcodeFilename);

        boolean isAsync = Boolean.TRUE.equals(body.get("async"));
        if (isAsync && applicationEventPublisher != null) {
            applicationEventPublisher.publishEvent(new com.printguard.jobs.events.ModelUploadedEvent(
                    initial.getId(),
                    model.getId(),
                    model.getUser().getId(),
                    model.getPrompt(),
                    modelPath,
                    destGcode,
                    gcodeFilename,
                    slicerService.getPrinterProfile()
            ));
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(PrintJobResponse.from(initial));
        }

        com.printguard.slicer.SlicerService.SlicingResult result = slicerService.slice(modelPath, destGcode, slicerService.getPrinterProfile());

        if (result.success()) {
            initial.setStatus("ready_to_print");
            initial.setProgress(100);
            initial.setEstimatedTime(result.estimatedTime());

            try {
                byte[] gcodeBytes = java.nio.file.Files.readAllBytes(destGcode);
                com.printguard.storage.StoredFile stored = storageService.storeGCodeFile(
                        model.getUser().getId(),
                        initial.getId(),
                        gcodeFilename,
                        gcodeBytes
                );
                initial.setGcodePath(stored.storagePath());

                String notice = result.warnings().isBlank() ? "" : "[Slicer Notice] " + result.warnings();
                if (stored.isCloudStored()) {
                    notice += (notice.isBlank() ? "" : " ") + "[Storage: Supabase " + stored.bucket() + "]";
                }
                initial.setErrorDetail(notice);
            } catch (Exception e) {
                initial.setGcodePath("gcode/" + gcodeFilename);
                initial.setErrorDetail(result.warnings().isBlank() ? "" : "[Slicer Notice] " + result.warnings());
            }

            PrintJobEntity saved = repository.save(initial);
            eventPublisher.publishPrintJobStatus(saved.getId(), "generating_gcode", 90, Map.of(), "Generating G-Code...");
            eventPublisher.publishPrintJobStatus(saved.getId(), "ready_to_print", 100, Map.of(), saved.getErrorDetail());
            return ResponseEntity.status(HttpStatus.CREATED).body(PrintJobResponse.from(saved));
        } else {
            initial.setStatus("failed");
            initial.setGcodePath("");
            initial.setProgress(0);
            initial.setErrorDetail(result.errorDetail());
            PrintJobEntity saved = repository.save(initial);
            eventPublisher.publishPrintJobStatus(saved.getId(), "failed", 0, Map.of(), saved.getErrorDetail());
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(PrintJobResponse.from(saved));
        }
    }

    @Operation(summary = "Create Print Job Asynchronously", description = "Non-blocking background slicing job creation with real-time WebSocket progress updates")
    @PostMapping({"/async", "/async/"})
    public ResponseEntity<PrintJobResponse> createAsync(@RequestBody Map<String, Object> body) {
        Map<String, Object> asyncBody = new java.util.HashMap<>(body);
        asyncBody.put("async", true);
        return create(asyncBody);
    }

    @GetMapping({"/{id}", "/{id}/"})
    public PrintJobResponse get(@PathVariable Long id) {
        PrintJobEntity job = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Print job not found"));
        securityUtils.verifyOwnershipOrAdmin(job.getModel().getUser(), "view this print job");
        return PrintJobResponse.from(job);
    }

    @PatchMapping({"/{id}", "/{id}/"})
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody Map<String, Object> updates) {
        PrintJobEntity job = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Print job not found"));
        securityUtils.verifyOwnershipOrAdmin(job.getModel().getUser(), "update this print job");

        Boolean hardwareConfirmed = null;
        String limitationNote = null;

        if (updates.containsKey("status")) {
            String newStatus = (String) updates.get("status");
            boolean wasHardwareActive = "printing".equals(job.getStatus()) || "paused".equals(job.getStatus());

            if (wasHardwareActive && "cancelled".equals(newStatus)) {
                OctoPrintService.CommandResult result = octoPrintService.sendJobCommand("cancel", null);
                if (!result.hardwareConfirmed()) {
                    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                        "error", "Cannot cancel active print on hardware: " + result.error(),
                        "hardware_confirmed", false,
                        "limitation_note", result.limitationNote()
                    ));
                }
                hardwareConfirmed = true;
            } else if (wasHardwareActive && "paused".equals(newStatus)) {
                OctoPrintService.CommandResult result = octoPrintService.sendJobCommand("pause", "pause");
                if (!result.hardwareConfirmed()) {
                    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                        "error", "Cannot pause active print on hardware: " + result.error(),
                        "hardware_confirmed", false,
                        "limitation_note", result.limitationNote()
                    ));
                }
                hardwareConfirmed = true;
            } else if ("printing".equals(newStatus) || "completed".equals(newStatus)) {
                Map<String, Object> probe = octoPrintService.probe();
                boolean isOnline = Boolean.TRUE.equals(probe.get("online"));
                if (!isOnline) {
                    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                        "error", "Cannot report print job as [" + newStatus + "]: printer hardware / OctoPrint is unreachable.",
                        "hardware_confirmed", false,
                        "limitation_note", "Physical printer / OctoPrint is offline. Real hardware printer operations cannot be executed."
                    ));
                }
                hardwareConfirmed = true;
            } else {
                hardwareConfirmed = false;
            }
            if ("cancelled".equals(newStatus)) {
                job.setErrorDetail("Print job was cancelled by user.");
            }
            job.setStatus(newStatus);
        }

        if (updates.containsKey("progress") && updates.get("progress") instanceof Number p) {
            job.setProgress(p.intValue());
        }
        if (updates.containsKey("auto_pause_enabled") && updates.get("auto_pause_enabled") instanceof Boolean ap) {
            job.setAutoPauseEnabled(ap);
        }

        PrintJobEntity saved = repository.save(job);
        eventPublisher.publishPrintJobStatus(saved.getId(), saved.getStatus(), saved.getProgress(), Map.of(), saved.getErrorDetail());
        return ResponseEntity.ok(PrintJobResponse.from(saved, hardwareConfirmed, limitationNote));
    }

    @PostMapping({"/{id}/start-print", "/{id}/start-print/"})
    public ResponseEntity<?> startPrint(@PathVariable Long id) {
        PrintJobEntity job = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Print job not found"));
        securityUtils.verifyOwnershipOrAdmin(job.getModel().getUser(), "start this print job");

        if (!"ready_to_print".equals(job.getStatus()) && !"paused".equals(job.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot start print: Job is in status [" + job.getStatus() + "]. Real G-code slicing must succeed first.");
        }
        if (job.getGcodePath() == null || job.getGcodePath().isBlank() || job.getGcodePath().contains("demo_print.gcode")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot start print: No genuine sliced G-code exists for this job.");
        }

        OctoPrintService.CommandResult result = octoPrintService.sendJobCommand("start", null);
        if (!result.hardwareConfirmed()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "error", "Cannot start print: printer hardware / OctoPrint is unreachable.",
                "hardware_confirmed", false,
                "limitation_note", result.limitationNote()
            ));
        }

        job.setStatus("printing");
        job.setStartTime(OffsetDateTime.now());
        PrintJobEntity saved = repository.save(job);

        eventPublisher.publishPrintJobStatus(
            saved.getId(),
            "printing",
            saved.getProgress(),
            Map.of(
                "tool0", Map.of("actual", 215.0, "target", 215.0),
                "bed", Map.of("actual", 60.0, "target", 60.0)
            ),
            ""
        );

        return ResponseEntity.ok(Map.of(
            "status", "print_started",
            "job_id", job.getId(),
            "hardware_confirmed", true
        ));
    }

    @PostMapping({"/{id}/pause", "/{id}/pause/"})
    public ResponseEntity<?> pausePrint(@PathVariable Long id) {
        PrintJobEntity job = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Print job not found"));
        securityUtils.verifyOwnershipOrAdmin(job.getModel().getUser(), "pause this print job");

        OctoPrintService.CommandResult result = octoPrintService.sendJobCommand("pause", "pause");
        if (!result.hardwareConfirmed()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "error", "Cannot pause print: printer hardware / OctoPrint is unreachable.",
                "hardware_confirmed", false,
                "limitation_note", result.limitationNote()
            ));
        }

        job.setStatus("paused");
        PrintJobEntity saved = repository.save(job);
        eventPublisher.publishPrintJobStatus(saved.getId(), "paused", saved.getProgress(), Map.of(), "Job paused");

        return ResponseEntity.ok(Map.of(
            "status", "paused",
            "job_id", saved.getId(),
            "hardware_confirmed", true
        ));
    }

    @PostMapping({"/{id}/resume", "/{id}/resume/"})
    public ResponseEntity<?> resumePrint(@PathVariable Long id) {
        PrintJobEntity job = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Print job not found"));
        securityUtils.verifyOwnershipOrAdmin(job.getModel().getUser(), "resume this print job");

        OctoPrintService.CommandResult result = octoPrintService.sendJobCommand("pause", "resume");
        if (!result.hardwareConfirmed()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "error", "Cannot resume print: printer hardware / OctoPrint is unreachable.",
                "hardware_confirmed", false,
                "limitation_note", result.limitationNote()
            ));
        }

        job.setStatus("printing");
        PrintJobEntity saved = repository.save(job);
        eventPublisher.publishPrintJobStatus(saved.getId(), "printing", saved.getProgress(), Map.of(), "Job resumed");

        return ResponseEntity.ok(Map.of(
            "status", "resumed",
            "job_id", saved.getId(),
            "hardware_confirmed", true
        ));
    }

    @PostMapping({"/{id}/cancel", "/{id}/cancel/"})
    public ResponseEntity<?> cancelPrint(@PathVariable Long id) {
        PrintJobEntity job = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Print job not found"));
        securityUtils.verifyOwnershipOrAdmin(job.getModel().getUser(), "cancel this print job");

        boolean wasActive = "printing".equals(job.getStatus()) || "paused".equals(job.getStatus());
        if (wasActive) {
            OctoPrintService.CommandResult result = octoPrintService.sendJobCommand("cancel", null);
            if (!result.hardwareConfirmed()) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                    "error", "Cannot cancel active print: printer hardware / OctoPrint is unreachable.",
                    "hardware_confirmed", false,
                    "limitation_note", result.limitationNote()
                ));
            }
        }

        job.setStatus("cancelled");
        job.setErrorDetail("Print job was cancelled by user.");
        PrintJobEntity saved = repository.save(job);
        eventPublisher.publishPrintJobStatus(saved.getId(), "cancelled", saved.getProgress(), Map.of(), "Job cancelled");

        return ResponseEntity.ok(Map.of(
            "status", "cancelled",
            "job_id", saved.getId(),
            "hardware_confirmed", wasActive
        ));
    }

    @GetMapping({"/{id}/download-gcode", "/{id}/download-gcode/"})
    public ResponseEntity<Resource> downloadGCode(@PathVariable Long id) {
        PrintJobEntity job = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Print job not found"));
        securityUtils.verifyOwnershipOrAdmin(job.getModel().getUser(), "download G-code for this print job");

        String pathStr = job.getGcodePath();
        if (pathStr == null || pathStr.isBlank() || pathStr.contains("demo_print.gcode")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No genuine sliced G-code file is available for this job");
        }

        byte[] gcodeBytes;
        try {
            gcodeBytes = storageService.loadGCodeContent(job);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "G-code file not found in storage: " + e.getMessage());
        }

        if (gcodeBytes.length < 500) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "G-code file is incomplete (< 500 bytes)");
        }

        String filename = Paths.get(pathStr).getFileName().toString();
        Resource resource = new org.springframework.core.io.ByteArrayResource(gcodeBytes);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/x-gcode"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(resource);
    }

    @GetMapping({"/{id}/gcode", "/{id}/gcode/"})
    public ResponseEntity<Resource> previewGCode(@PathVariable Long id) {
        PrintJobEntity job = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Print job not found"));
        securityUtils.verifyOwnershipOrAdmin(job.getModel().getUser(), "preview G-code for this print job");

        String pathStr = job.getGcodePath();
        if (pathStr == null || pathStr.isBlank() || pathStr.contains("demo_print.gcode")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No genuine sliced G-code file is available for this job");
        }

        byte[] gcodeBytes;
        try {
            gcodeBytes = storageService.loadGCodeContent(job);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "G-code file not found in storage: " + e.getMessage());
        }

        if (gcodeBytes.length < 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "G-code file is incomplete or empty (< 50 bytes)");
        }

        String filename = Paths.get(pathStr).getFileName().toString();
        Resource resource = new org.springframework.core.io.ByteArrayResource(gcodeBytes);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/plain; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .body(resource);
    }

    @PostMapping({"/clear-finished", "/clear-finished/"})
    public ResponseEntity<Map<String, Object>> clearFinished() {
        UserEntity user = securityUtils.requireCurrentUser();
        List<PrintJobEntity> finished = securityUtils.isAdmin(user)
                ? repository.findByStatusIn(List.of("completed", "failed", "cancelled"))
                : repository.findByUserIdAndStatusInOrderByCreatedAtDesc(user.getId(), List.of("completed", "failed", "cancelled"));
        repository.deleteAll(finished);
        return ResponseEntity.ok(Map.of("cleared", finished.size()));
    }

    @GetMapping({"/active-job", "/active-job/"})
    public ResponseEntity<?> getActiveJob() {
        UserEntity user = securityUtils.requireCurrentUser();
        var opt = securityUtils.isAdmin(user)
                ? repository.findFirstByStatusInOrderByCreatedAtDesc(List.of("printing", "paused", "ready_to_print"))
                : repository.findFirstByUserIdAndStatusInOrderByCreatedAtDesc(user.getId(), List.of("printing", "paused", "ready_to_print"));
        if (opt.isPresent()) {
            return ResponseEntity.ok(PrintJobResponse.from(opt.get()));
        }
        return ResponseEntity.ok(Map.of("active", false));
    }
}
