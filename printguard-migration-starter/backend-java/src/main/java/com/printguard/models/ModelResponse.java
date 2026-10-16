package com.printguard.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

public record ModelResponse(
    Long id,
    String prompt,
    @JsonProperty("file_path") String filePath,
    @JsonProperty("thumbnail_path") String thumbnailPath,
    @JsonProperty("generation_time") Double generationTime,
    String status,
    @JsonProperty("is_repaired") Boolean isRepaired,
    @JsonProperty("source_type") String sourceType,
    Integer steps,
    @JsonProperty("created_at") OffsetDateTime createdAt,
    @JsonProperty("error_detail") String errorDetail,
    @JsonProperty("file_url") String fileUrl,
    @JsonProperty("is_fallback") Boolean isFallback,
    @JsonProperty("generation_method") String generationMethod,
    @JsonProperty("is_procedural") Boolean isProcedural
) {
    public static ModelResponse from(GeneratedModelEntity entity) {
        String url = (entity.getFilePath() != null && !entity.getFilePath().isBlank())
            ? (entity.getId() != null ? "/api/models/" + entity.getId() + "/file" : (entity.getFilePath().startsWith("/") ? entity.getFilePath() : "/media/" + entity.getFilePath()))
            : "";
        boolean fallback = entity.getErrorDetail() != null && entity.getErrorDetail().contains("[DEMO FALLBACK]");
        boolean procedural = entity.getErrorDetail() != null && entity.getErrorDetail().contains("[PROCEDURAL CAD]");
        String method = procedural ? "procedural_cad" : (fallback ? "demo_fallback" : "ai_inference");
        return new ModelResponse(
            entity.getId(),
            entity.getPrompt(),
            entity.getFilePath(),
            entity.getThumbnailPath(),
            entity.getGenerationTime(),
            entity.getStatus(),
            entity.getIsRepaired(),
            entity.getSourceType(),
            entity.getSteps(),
            entity.getCreatedAt(),
            entity.getErrorDetail(),
            url,
            fallback,
            method,
            procedural
        );
    }
}
