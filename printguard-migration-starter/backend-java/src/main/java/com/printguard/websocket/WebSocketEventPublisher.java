package com.printguard.websocket;

import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class WebSocketEventPublisher {
    private final DashboardWebSocketHandler dashboardHandler;
    private final PrintJobWebSocketHandler printJobHandler;
    private final ModelGenerationWebSocketHandler modelGenerationHandler;

    public WebSocketEventPublisher(
            DashboardWebSocketHandler dashboardHandler,
            PrintJobWebSocketHandler printJobHandler,
            ModelGenerationWebSocketHandler modelGenerationHandler) {
        this.dashboardHandler = dashboardHandler;
        this.printJobHandler = printJobHandler;
        this.modelGenerationHandler = modelGenerationHandler;
    }

    public void publishDashboardAlert(Map<String, Object> alertData) {
        dashboardHandler.broadcast(Map.of(
            "type", "ai_defect_alert",
            "data", alertData
        ));
    }

    public void publishPrintJobStatus(Long jobId, String status, int progress, Map<String, Object> temperatures, String errorDetail) {
        printJobHandler.sendToJob(jobId, Map.of(
            "status", status != null ? status : "",
            "job_id", jobId,
            "progress", progress,
            "temperatures", temperatures != null ? temperatures : Map.of(),
            "error_detail", errorDetail != null ? errorDetail : ""
        ));
    }

    public void publishModelStatus(Long modelId, String status, Double generationTime, String fileUrl, String errorDetail) {
        modelGenerationHandler.sendToModel(modelId, Map.of(
            "status", status != null ? status : "",
            "model_id", modelId,
            "generation_time", generationTime != null ? generationTime : 0.0,
            "file_url", fileUrl != null ? fileUrl : "",
            "error_detail", errorDetail != null ? errorDetail : ""
        ));
    }
}
