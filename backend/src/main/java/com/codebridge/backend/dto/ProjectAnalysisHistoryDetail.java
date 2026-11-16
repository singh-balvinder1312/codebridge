
package com.codebridge.backend.dto;

import com.codebridge.backend.entity.ProjectStatus;

import java.time.Instant;
import java.util.Objects;

public record ProjectAnalysisHistoryDetail(
        Long analysisId,
        Long projectId,
        ProjectStatus status,
        Instant analyzedAt,
        CompatibilityComparisonResponse report
) {

    public ProjectAnalysisHistoryDetail {
        Objects.requireNonNull(analysisId);
        Objects.requireNonNull(projectId);
        Objects.requireNonNull(status);
        Objects.requireNonNull(analyzedAt);
        Objects.requireNonNull(report);
    }
}
