package com.printguard.jobs;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Service
public class OctoPrintService {
    private final String octoprintUrl;
    private final String octoprintApiKey;
    private final RestClient restClient;

    public record CommandResult(
        boolean success,
        boolean hardwareConfirmed,
        String limitationNote,
        String error
    ) {}

    public OctoPrintService(
            @Value("${printguard.octoprint-url:http://127.0.0.1:5000}") String octoprintUrl,
            @Value("${printguard.octoprint-api-key:}") String octoprintApiKey) {
        this.octoprintUrl = octoprintUrl;
        this.octoprintApiKey = octoprintApiKey;
        this.restClient = RestClient.builder().baseUrl(octoprintUrl).build();
    }

    public String getOctoprintUrl() {
        return octoprintUrl;
    }

    public boolean isConnected() {
        try {
            var req = restClient.get().uri("/api/version");
            if (octoprintApiKey != null && !octoprintApiKey.isBlank()) {
                req.header("X-Api-Key", octoprintApiKey);
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> version = req.retrieve().body(Map.class);
            return version != null && (version.containsKey("server") || version.containsKey("api"));
        } catch (Exception e) {
            return false;
        }
    }

    public Map<String, Object> probe() {
        try {
            var req = restClient.get().uri("/api/version");
            if (octoprintApiKey != null && !octoprintApiKey.isBlank()) {
                req.header("X-Api-Key", octoprintApiKey);
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> version = req.retrieve().body(Map.class);
            if (version != null && (version.containsKey("server") || version.containsKey("api"))) {
                var printerReq = restClient.get().uri("/api/printer");
                if (octoprintApiKey != null && !octoprintApiKey.isBlank()) {
                    printerReq.header("X-Api-Key", octoprintApiKey);
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> printerData = printerReq.retrieve().body(Map.class);

                String text = String.valueOf(version.getOrDefault("text", ""));
                boolean isVirtual = text.toLowerCase().contains("mock") || text.toLowerCase().contains("virtual");

                return Map.of(
                    "online", true,
                    "simulated", isVirtual,
                    "data", printerData != null ? printerData : Map.of(),
                    "version", version
                );
            }
        } catch (Exception ignored) {}
        return Map.of("online", false, "simulated", true);
    }

    public CommandResult sendJobCommand(String command, String action) {
        if (!isConnected()) {
            return new CommandResult(
                false,
                false,
                "Physical printer / OctoPrint is offline at " + octoprintUrl + ". Real hardware printer operations cannot be executed.",
                "Printer offline"
            );
        }

        try {
            Map<String, Object> payload = action != null
                    ? Map.of("command", command, "action", action)
                    : Map.of("command", command);

            var req = restClient.post()
                    .uri("/api/job")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload);
            if (octoprintApiKey != null && !octoprintApiKey.isBlank()) {
                req.header("X-Api-Key", octoprintApiKey);
            }
            ResponseEntity<Void> res = req.retrieve().toBodilessEntity();
            if (res.getStatusCode().is2xxSuccessful()) {
                return new CommandResult(true, true, "", null);
            } else {
                return new CommandResult(
                    false,
                    false,
                    "OctoPrint rejected command: HTTP " + res.getStatusCode(),
                    "Printer rejected command"
                );
            }
        } catch (Exception e) {
            return new CommandResult(
                false,
                false,
                "Failed to send command to OctoPrint at " + octoprintUrl + ": " + e.getMessage(),
                e.getMessage()
            );
        }
    }
}
