package com.printguard.jobs;

import com.printguard.jobs.events.ModelUploadedEvent;
import com.printguard.slicer.SlicerService;
import com.printguard.storage.StorageService;
import com.printguard.storage.StoredFile;
import com.printguard.websocket.WebSocketEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.util.Map;

/**
 * Background event listener that consumes ModelUploadedEvent asynchronously,
 * offloading PrusaSlicer execution from the HTTP request threads and broadcasting
 * real-time stage updates to WebSocket subscribers (/ws/jobs).
 */
@Service
public class AsyncSlicingEventListener {
    private static final Logger log = LoggerFactory.getLogger(AsyncSlicingEventListener.class);

    private final PrintJobRepository jobRepository;
    private final SlicerService slicerService;
    private final StorageService storageService;
    private final WebSocketEventPublisher eventPublisher;

    public AsyncSlicingEventListener(
            PrintJobRepository jobRepository,
            SlicerService slicerService,
            StorageService storageService,
            WebSocketEventPublisher eventPublisher) {
        this.jobRepository = jobRepository;
        this.slicerService = slicerService;
        this.storageService = storageService;
        this.eventPublisher = eventPublisher;
    }

    @Async("taskExecutor")
    @EventListener
    public void handleModelUploadedEvent(ModelUploadedEvent event) {
        log.info("Processing async slicing event for Job #{} (Model #{}) on thread {}",
                event.jobId(), event.modelId(), Thread.currentThread().getName());

        PrintJobEntity job = jobRepository.findById(event.jobId()).orElse(null);
        if (job == null) {
            log.error("Job #{} not found during async slicing processing", event.jobId());
            return;
        }

        try {
            // Slicing workflow stages for WebSocket listeners
            eventPublisher.publishPrintJobStatus(job.getId(), "slicing_started", 10, Map.of(), "Initializing Slicer...");
            eventPublisher.publishPrintJobStatus(job.getId(), "preparing_model", 30, Map.of(), "Preparing 3D Model: " + event.modelPrompt());
            eventPublisher.publishPrintJobStatus(job.getId(), "running_prusaslicer", 60, Map.of(), "Running PrusaSlicer in background...");

            SlicerService.SlicingResult result = slicerService.slice(event.modelPath(), event.destGcode(), event.printerProfile());

            if (result.success()) {
                job.setStatus("ready_to_print");
                job.setProgress(100);
                job.setEstimatedTime(result.estimatedTime());

                try {
                    byte[] gcodeBytes = Files.readAllBytes(event.destGcode());
                    StoredFile stored = storageService.storeGCodeFile(
                            event.userId(),
                            job.getId(),
                            event.gcodeFilename(),
                            gcodeBytes
                    );
                    job.setGcodePath(stored.storagePath());

                    String notice = result.warnings().isBlank() ? "" : "[Slicer Notice] " + result.warnings();
                    if (stored.isCloudStored()) {
                        notice += (notice.isBlank() ? "" : " ") + "[Storage: Supabase " + stored.bucket() + "]";
                    }
                    job.setErrorDetail(notice);
                } catch (Exception e) {
                    job.setGcodePath("gcode/" + event.gcodeFilename());
                    job.setErrorDetail(result.warnings().isBlank() ? "" : "[Slicer Notice] " + result.warnings());
                }

                PrintJobEntity saved = jobRepository.save(job);
                eventPublisher.publishPrintJobStatus(saved.getId(), "generating_gcode", 90, Map.of(), "Generating G-Code...");
                eventPublisher.publishPrintJobStatus(saved.getId(), "ready_to_print", 100, Map.of(), saved.getErrorDetail());
                log.info("Async slicing completed successfully for Job #{}", saved.getId());
            } else {
                job.setStatus("failed");
                job.setGcodePath("");
                job.setProgress(0);
                job.setErrorDetail(result.errorDetail().isBlank() ? "Slicing failed: " + result.warnings() : result.errorDetail());
                PrintJobEntity saved = jobRepository.save(job);
                eventPublisher.publishPrintJobStatus(saved.getId(), "failed", 0, Map.of(), saved.getErrorDetail());
                log.warn("Async slicing failed for Job #{}: {}", saved.getId(), saved.getErrorDetail());
            }
        } catch (Exception ex) {
            log.error("Exception during async slicing of Job #{}: {}", event.jobId(), ex.getMessage(), ex);
            job.setStatus("failed");
            job.setErrorDetail("Unexpected error during slicing: " + ex.getMessage());
            jobRepository.save(job);
            eventPublisher.publishPrintJobStatus(job.getId(), "failed", 0, Map.of(), job.getErrorDetail());
        }
    }
}
