
package com.codebridge.backend.dto;

import com.codebridge.backend.compatibility.AnalysisCoverage;
import com.codebridge.backend.compatibility.CompatibilityAssessment;

import java.time.Instant;

public record ProjectAnalysisHistoryItem(
        Long analysisId,
        Long projectId,
        Instant analyzedAt,
        CompatibilityAssessment assessment,
        AnalysisCoverage coverage
) {
}
