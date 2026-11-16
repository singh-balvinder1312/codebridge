
package com.codebridge.backend.dto;

import com.codebridge.backend.entity.ProjectStatus;

import java.time.Instant;
import java.util.Objects;

public record ProjectAnalysisResponse(
        Long projectId,
        ProjectStatus status,
        Instant analyzedAt,
        CompatibilityComparisonResponse report
) {

    public ProjectAnalysisResponse {
        Objects.requireNonNull(projectId, "Project ID is required");
        Objects.requireNonNull(status, "Project status is required");
        Objects.requireNonNull(analyzedAt, "Analysis time is required");
        Objects.requireNonNull(report, "Report is required");
    }
}
