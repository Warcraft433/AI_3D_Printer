package com.printguard.settings;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping({"/api/settings", "/api/settings/"})
public class SettingsController {

    private final Map<String, Object> settings = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Path settingsFile = Paths.get("data", "settings.json");

    public SettingsController() {
        // Sensible defaults
        settings.put("gpuMode", "local");
        settings.put("storagePath", "media/users/{id}/generated_models");
        settings.put("yoloThreshold", 70);
        settings.put("printerIp", "127.0.0.1:5000");
        settings.put("baudRate", 115200);
        settings.put("cameraFacingMode", "user");
        settings.put("autoSave", true);
        settings.put("darkMode", true);
        settings.put("connectionType", "web_serial"); // direct USB Web Serial (navigator.serial)

        loadPersistedSettings();
    }

    private void loadPersistedSettings() {
        try {
            if (Files.exists(settingsFile)) {
                Map<String, Object> loaded = objectMapper.readValue(
                    settingsFile.toFile(),
                    new TypeReference<Map<String, Object>>() {}
                );
                if (loaded != null) {
                    settings.putAll(loaded);
                }
            }
        } catch (Exception ignored) {}
    }

    private synchronized void persistSettings() {
        try {
            if (settingsFile.getParent() != null) {
                Files.createDirectories(settingsFile.getParent());
            }
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(settingsFile.toFile(), settings);
        } catch (Exception ignored) {}
    }

    @GetMapping({"", "/"})
    public ResponseEntity<Map<String, Object>> getSettings() {
        return ResponseEntity.ok(settings);
    }

    @RequestMapping(value = {"", "/"}, method = {RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH})
    public ResponseEntity<Map<String, Object>> updateSettings(@RequestBody Map<String, Object> updates) {
        if (updates != null) {
            settings.putAll(updates);
            persistSettings();
        }
        return ResponseEntity.ok(settings);
    }
}
