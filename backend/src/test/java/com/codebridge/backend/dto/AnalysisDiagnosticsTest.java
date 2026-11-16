
package com.codebridge.backend.dto;

import com.codebridge.backend.compatibility.AnalysisCoverage;
import com.codebridge.backend.compatibility.CompatibilityAssessment;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AnalysisDiagnosticsTest {

    @Test
    void shouldDescribeAvailableRepositoryEvidence() {

        CompatibilityComparisonResponse response =
                responseWithEvidence(
                        AnalysisCoverage.PARTIAL,
                        true,
                        true,
                        codeResult(true)
                );

        AnalysisDiagnostics diagnostics =
                response.analysisDiagnostics();

        assertNotNull(diagnostics);

        assertTrue(diagnostics.existingRootPomFound());
        assertTrue(diagnostics.incomingRootPomFound());

        assertEquals(
                2,
                diagnostics.existingDependencyCount()
        );

        assertEquals(
                7,
                diagnostics.incomingDependencyCount()
        );

        assertEquals(
                219,
                diagnostics.existingJavaFileCount()
        );

        assertEquals(
                264,
                diagnostics.incomingJavaFileCount()
        );

        assertTrue(diagnostics.javaAnalysisAvailable());
        assertTrue(diagnostics.routeScanComplete());

        assertFalse(diagnostics.limitations().isEmpty());
    }

    @Test
    void shouldExplainMissingConfigurationEvidence() {

        AnalysisDiagnostics diagnostics =
                responseWithEvidence(
                        AnalysisCoverage.PARTIAL,
                        true,
                        true,
                        codeResult(true)
                ).analysisDiagnostics();

        assertEquals(
                0,
                diagnostics.comparedConfigurationKeyCount()
        );

        assertTrue(
                diagnostics.limitations().stream()
                        .anyMatch(message ->
                                message.contains(
                                        "Configuration comparison "
                                                + "is incomplete"
                                )
                        )
        );
    }

    @Test
    void shouldExplainMissingRootPom() {

        AnalysisDiagnostics diagnostics =
                responseWithEvidence(
                        AnalysisCoverage.PARTIAL,
                        true,
                        false,
                        codeResult(true)
                ).analysisDiagnostics();

        assertTrue(diagnostics.existingRootPomFound());
        assertFalse(diagnostics.incomingRootPomFound());

        assertTrue(
                diagnostics.limitations().stream()
                        .anyMatch(message ->
                                message.contains(
                                        "root pom.xml was unavailable"
                                )
                        )
        );
    }

    @Test
    void shouldExplainIncompleteRouteScan() {

        AnalysisDiagnostics diagnostics =
                responseWithEvidence(
                        AnalysisCoverage.PARTIAL,
                        true,
                        true,
                        codeResult(false)
                ).analysisDiagnostics();

        assertFalse(diagnostics.routeScanComplete());

        assertTrue(
                diagnostics.limitations().stream()
                        .anyMatch(message ->
                                message.contains(
                                        "route scanning was incomplete"
                                )
                        )
        );
    }

    @Test
    void shouldDistinguishMissingJavaAnalysisFromZeroFiles() {

        AnalysisDiagnostics diagnostics =
                responseWithEvidence(
                        AnalysisCoverage.PARTIAL,
                        true,
                        true,
                        null
                ).analysisDiagnostics();

        assertFalse(diagnostics.javaAnalysisAvailable());

        assertTrue(
                diagnostics.limitations().stream()
                        .anyMatch(message ->
                                message.contains(
                                        "Java source analysis was "
                                                + "not performed"
                                )
                        )
        );
    }

    @Test
    void shouldGenerateDiagnosticsForOlderConstructor() {

        MavenComparisonResponse maven = mavenResult();
        ConfigurationComparisonResponse configuration =
                configurationResult();

        CompatibilityComparisonResponse response =
                new CompatibilityComparisonResponse(
                        CompatibilityAssessment.NO_ISSUES_DETECTED,
                        "No issues detected by supported checks.",
                        maven,
                        configuration
                );

        assertNotNull(response.analysisDiagnostics());

        assertTrue(
                response.recommendations().isEmpty()
        );

        assertFalse(
                response.analysisDiagnostics()
                        .javaAnalysisAvailable()
        );
    }

    @Test
    void shouldNotExposeConfigurationValues() {

        AnalysisDiagnostics diagnostics =
                responseWithEvidence(
                        AnalysisCoverage.PARTIAL,
                        true,
                        true,
                        codeResult(true)
                ).analysisDiagnostics();

        String output = diagnostics.toString();

        assertFalse(output.contains("password="));
        assertFalse(output.contains("Authorization:"));
        assertFalse(output.contains("Bearer "));
    }

    private CompatibilityComparisonResponse responseWithEvidence(
            AnalysisCoverage coverage,
            boolean existingPomFound,
            boolean incomingPomFound,
            JavaCodeComparisonResponse code
    ) {

        return new CompatibilityComparisonResponse(
                CompatibilityAssessment.NO_ISSUES_DETECTED,
                "No issues detected by supported checks.",
                coverage,
                "This is not a complete compatibility test.",
                mavenResult(),
                configurationResult(),
                existingPomFound,
                incomingPomFound,
                code,
                List.of()
        );
    }

    private MavenComparisonResponse mavenResult() {

        return new MavenComparisonResponse(
                "junit-team/junit4",
                "apache/commons-lang",
                2,
                7,
                0,
                List.of()
        );
    }

    private ConfigurationComparisonResponse configurationResult() {

        return new ConfigurationComparisonResponse(
                "junit-team/junit4",
                "apache/commons-lang",
                List.of(),
                List.of(),
                0,
                0,
                0,
                List.of()
        );
    }

    private JavaCodeComparisonResponse codeResult(
            boolean routeScanComplete
    ) {

        return new JavaCodeComparisonResponse(
                "junit-team/junit4",
                "apache/commons-lang",
                219,
                264,
                0,
                0,
                List.of(),
                List.of(),
                List.of(),
                1,
                0,
                routeScanComplete
        );
    }
}
