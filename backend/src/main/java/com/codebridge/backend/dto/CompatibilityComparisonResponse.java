
package com.codebridge.backend.dto;

import com.codebridge.backend.compatibility.AnalysisCoverage;
import com.codebridge.backend.compatibility.CompatibilityAssessment;

import java.util.List;
import java.util.Objects;

public record CompatibilityComparisonResponse(
        CompatibilityAssessment assessment,
        String explanation,
        AnalysisCoverage coverage,
        String coverageExplanation,
        MavenComparisonResponse maven,
        ConfigurationComparisonResponse configuration,
        boolean existingRootPomFound,
        boolean incomingRootPomFound,
        JavaCodeComparisonResponse code,
        List<CompatibilityRecommendation> recommendations,
        AnalysisDiagnostics analysisDiagnostics
) {

    public CompatibilityComparisonResponse {

        Objects.requireNonNull(
                assessment,
                "Assessment is required"
        );

        Objects.requireNonNull(
                explanation,
                "Explanation is required"
        );

        Objects.requireNonNull(
                coverage,
                "Coverage is required"
        );

        Objects.requireNonNull(
                coverageExplanation,
                "Coverage explanation is required"
        );

        Objects.requireNonNull(
                maven,
                "Maven analysis is required"
        );

        Objects.requireNonNull(
                configuration,
                "Configuration analysis is required"
        );

        // Older saved reports may not contain recommendations.
        recommendations = recommendations == null
                ? List.of()
                : List.copyOf(recommendations);

        // Generate diagnostics from the available report evidence.
        //
        // This also supports historical reports saved before
        // the analysisDiagnostics field existed.
        if (analysisDiagnostics == null) {
            analysisDiagnostics = AnalysisDiagnostics.from(
                    coverage,
                    maven,
                    configuration,
                    code,
                    existingRootPomFound,
                    incomingRootPomFound
            );
        }
    }

    // Preserve the previous ten-argument constructor.
    // Your current CompatibilityComparisonService uses this.
    public CompatibilityComparisonResponse(
            CompatibilityAssessment assessment,
            String explanation,
            AnalysisCoverage coverage,
            String coverageExplanation,
            MavenComparisonResponse maven,
            ConfigurationComparisonResponse configuration,
            boolean existingRootPomFound,
            boolean incomingRootPomFound,
            JavaCodeComparisonResponse code,
            List<CompatibilityRecommendation> recommendations
    ) {
        this(
                assessment,
                explanation,
                coverage,
                coverageExplanation,
                maven,
                configuration,
                existingRootPomFound,
                incomingRootPomFound,
                code,
                recommendations,
                null
        );
    }

    // Preserve the previous nine-argument constructor.
    public CompatibilityComparisonResponse(
            CompatibilityAssessment assessment,
            String explanation,
            AnalysisCoverage coverage,
            String coverageExplanation,
            MavenComparisonResponse maven,
            ConfigurationComparisonResponse configuration,
            boolean existingRootPomFound,
            boolean incomingRootPomFound,
            JavaCodeComparisonResponse code
    ) {
        this(
                assessment,
                explanation,
                coverage,
                coverageExplanation,
                maven,
                configuration,
                existingRootPomFound,
                incomingRootPomFound,
                code,
                List.of(),
                null
        );
    }

    // Preserve the previous eight-argument constructor.
    public CompatibilityComparisonResponse(
            CompatibilityAssessment assessment,
            String explanation,
            AnalysisCoverage coverage,
            String coverageExplanation,
            MavenComparisonResponse maven,
            ConfigurationComparisonResponse configuration,
            boolean existingRootPomFound,
            boolean incomingRootPomFound
    ) {
        this(
                assessment,
                explanation,
                coverage,
                coverageExplanation,
                maven,
                configuration,
                existingRootPomFound,
                incomingRootPomFound,
                null,
                List.of(),
                null
        );
    }

    // Preserve the previous six-argument constructor.
    public CompatibilityComparisonResponse(
            CompatibilityAssessment assessment,
            String explanation,
            AnalysisCoverage coverage,
            String coverageExplanation,
            MavenComparisonResponse maven,
            ConfigurationComparisonResponse configuration
    ) {
        this(
                assessment,
                explanation,
                coverage,
                coverageExplanation,
                maven,
                configuration,
                true,
                true,
                null,
                List.of(),
                null
        );
    }

    // Preserve the original four-argument constructor.
    public CompatibilityComparisonResponse(
            CompatibilityAssessment assessment,
            String explanation,
            MavenComparisonResponse maven,
            ConfigurationComparisonResponse configuration
    ) {
        this(
                assessment,
                explanation,
                AnalysisCoverage.PARTIAL,
                "Coverage was not explicitly supplied.",
                maven,
                configuration,
                true,
                true,
                null,
                List.of(),
                null
        );
    }
}
