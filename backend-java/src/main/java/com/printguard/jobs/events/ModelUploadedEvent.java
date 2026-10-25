package com.printguard.jobs.events;

import java.nio.file.Path;

/**
 * Event published when a 3D model is submitted for background slicing.
 */
public record ModelUploadedEvent(
        Long jobId,
        Long modelId,
        Long userId,
        String modelPrompt,
        Path modelPath,
        Path destGcode,
        String gcodeFilename,
        String printerProfile
) {}
