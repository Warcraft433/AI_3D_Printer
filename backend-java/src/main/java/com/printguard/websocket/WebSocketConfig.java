package com.printguard.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final DashboardWebSocketHandler dashboardHandler;
    private final PrintJobWebSocketHandler printJobHandler;
    private final ModelGenerationWebSocketHandler modelGenerationHandler;

    public WebSocketConfig(
            DashboardWebSocketHandler dashboardHandler,
            PrintJobWebSocketHandler printJobHandler,
            ModelGenerationWebSocketHandler modelGenerationHandler) {
        this.dashboardHandler = dashboardHandler;
        this.printJobHandler = printJobHandler;
        this.modelGenerationHandler = modelGenerationHandler;
    }

    @Override
    public void registerWebSocketHandlers(@NonNull WebSocketHandlerRegistry registry) {
        registry.addHandler(dashboardHandler, "/ws/dashboard", "/ws/dashboard/")
                .setAllowedOrigins("*");

        registry.addHandler(printJobHandler, "/ws/print-jobs/{jobId}", "/ws/print-jobs/{jobId}/")
                .setAllowedOrigins("*");

        registry.addHandler(modelGenerationHandler, "/ws/generation/{modelId}", "/ws/generation/{modelId}/")
                .setAllowedOrigins("*");
    }
}
