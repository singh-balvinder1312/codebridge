
package com.codebridge.backend.compatibility;

import com.codebridge.backend.codeanalysis.JavaCodeComparisonService;
import com.codebridge.backend.configanalysis.ConfigurationComparisonService;
import com.codebridge.backend.dto.CompatibilityComparisonResponse;
import com.codebridge.backend.dto.ConfigurationComparisonResponse;
import com.codebridge.backend.dto.ConfigurationIssue;
import com.codebridge.backend.dto.JavaCodeComparisonResponse;
import com.codebridge.backend.dto.JavaSourceCollision;
import com.codebridge.backend.dto.MavenComparisonResponse;
import com.codebridge.backend.maven.MavenAnalysisResult;
import com.codebridge.backend.maven.MavenDependencyConflict;
import com.codebridge.backend.maven.MavenRepositoryComparisonService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CompatibilityComparisonServiceTest {

    private static final String EXISTING_URL =
            "https://github.com/example/backend-a";

    private static final String INCOMING_URL =
            "https://github.com/example/backend-b";

    private MavenRepositoryComparisonService mavenService;
    private ConfigurationComparisonService configurationService;
    private JavaCodeComparisonService codeService;
    private CompatibilityComparisonService service;

    @BeforeEach
    void setUp() {
        mavenService =
                mock(MavenRepositoryComparisonService.class);

        configurationService =
                mock(ConfigurationComparisonService.class);

        codeService =
                mock(JavaCodeComparisonService.class);

        service = new CompatibilityComparisonService(
                mavenService,
                configurationService,
                codeService
        );
    }

    @Test
    void shouldReturnPartialCoverageWhenOnlyMavenDataExists() {

        mockResults(
                mavenResponse(2, 7, List.of()),
                true,
                true,
                configurationResponse(false, false, 0, List.of()),
                codeResponse(0, 0, 0, List.of())
        );

        CompatibilityComparisonResponse result = compare();

        assertEquals(AnalysisCoverage.PARTIAL, result.coverage());

        assertEquals(
                CompatibilityAssessment.NO_ISSUES_DETECTED,
                result.assessment()
        );

        assertTrue(result.existingRootPomFound());
        assertTrue(result.incomingRootPomFound());
    }

    @Test
    void shouldReturnInsufficientCoverageWhenNoEvidenceExists() {

        mockResults(
                mavenResponse(0, 0, List.of()),
                false,
                false,
                configurationResponse(false, false, 0, List.of()),
                codeResponse(0, 0, 0, List.of())
        );

        CompatibilityComparisonResponse result = compare();

        assertEquals(
                AnalysisCoverage.INSUFFICIENT,
                result.coverage()
        );

        assertEquals(
                CompatibilityAssessment.INSUFFICIENT_DATA,
                result.assessment()
        );
    }

    @Test
    void shouldCompleteSupportedChecksWhenAllHaveEvidence() {

        mockResults(
                mavenResponse(3, 4, List.of()),
                true,
                true,
                configurationResponse(true, true, 2, List.of()),
                codeResponse(8, 12, 0, List.of())
        );

        CompatibilityComparisonResponse result = compare();

        assertEquals(
                AnalysisCoverage.SUPPORTED_CHECKS_COMPLETED,
                result.coverage()
        );

        assertEquals(
                CompatibilityAssessment.NO_ISSUES_DETECTED,
                result.assessment()
        );
    }

    @Test
    void shouldRequireReviewForMavenConflict() {

        MavenDependencyConflict conflict =
                new MavenDependencyConflict(
                        "org.slf4j",
                        "slf4j-api",
                        "1.7.36",
                        "2.0.17"
                );

        mockResults(
                mavenResponse(2, 3, List.of(conflict)),
                true,
                true,
                configurationResponse(false, false, 0, List.of()),
                codeResponse(5, 6, 0, List.of())
        );

        CompatibilityComparisonResponse result = compare();

        assertEquals(
                CompatibilityAssessment.REVIEW_REQUIRED,
                result.assessment()
        );

        assertEquals(1, result.maven().potentialConflictCount());
    }

    @Test
    void shouldRequireReviewForConfigurationIssue() {

        ConfigurationIssue issue = new ConfigurationIssue(
                "server.port",
                "src/main/resources/application.properties",
                "src/main/resources/application.properties",
                "REVIEW",
                "Different ports may need reconciliation."
        );

        mockResults(
                mavenResponse(0, 0, List.of()),
                false,
                false,
                configurationResponse(true, true, 1, List.of(issue)),
                codeResponse(0, 0, 0, List.of())
        );

        CompatibilityComparisonResponse result = compare();

        assertEquals(
                CompatibilityAssessment.REVIEW_REQUIRED,
                result.assessment()
        );

        assertEquals(AnalysisCoverage.PARTIAL, result.coverage());
    }

    @Test
    void shouldNotRequireReviewForInformationalDifference() {

        ConfigurationIssue issue = new ConfigurationIssue(
                "spring.application.name",
                "src/main/resources/application.properties",
                "src/main/resources/application.properties",
                "INFORMATIONAL",
                "Different names may be intentional."
        );

        mockResults(
                mavenResponse(2, 3, List.of()),
                true,
                true,
                configurationResponse(true, true, 1, List.of(issue)),
                codeResponse(5, 8, 0, List.of())
        );

        CompatibilityComparisonResponse result = compare();

        assertEquals(
                CompatibilityAssessment.NO_ISSUES_DETECTED,
                result.assessment()
        );

        assertEquals(
                AnalysisCoverage.SUPPORTED_CHECKS_COMPLETED,
                result.coverage()
        );
    }

    @Test
    void shouldReportInsufficientDataWhenPomIsMissingAndNoOtherEvidence() {

        mockResults(
                mavenResponse(3, 0, List.of()),
                true,
                false,
                configurationResponse(false, false, 0, List.of()),
                codeResponse(0, 0, 0, List.of())
        );

        CompatibilityComparisonResponse result = compare();

        assertEquals(
                CompatibilityAssessment.INSUFFICIENT_DATA,
                result.assessment()
        );

        assertFalse(result.incomingRootPomFound());

        assertTrue(
                result.coverageExplanation().contains("pom.xml")
        );
    }

    @Test
    void shouldRequireReviewWhenJavaSourceCollisionExists() {

        JavaSourceCollision collision = new JavaSourceCollision(
                "src/main/java/com/example/UserService.java",
                "REVIEW",
                "Different Java files share the same source path."
        );

        mockResults(
                mavenResponse(0, 0, List.of()),
                false,
                false,
                configurationResponse(false, false, 0, List.of()),
                codeResponse(10, 12, 1, List.of(collision))
        );

        CompatibilityComparisonResponse result = compare();

        assertEquals(
                CompatibilityAssessment.REVIEW_REQUIRED,
                result.assessment()
        );

        assertEquals(
                AnalysisCoverage.PARTIAL,
                result.coverage()
        );

        assertEquals(1, result.code().potentialCollisionCount());

        assertEquals(
                "src/main/java/com/example/UserService.java",
                result.code().collisions().get(0).path()
        );
    }

    @Test
    void shouldRecognizeCodeEvidenceEvenWithoutMatchingPaths() {

        mockResults(
                mavenResponse(0, 0, List.of()),
                false,
                false,
                configurationResponse(false, false, 0, List.of()),
                codeResponse(219, 264, 0, List.of())
        );

        CompatibilityComparisonResponse result = compare();

        assertEquals(
                AnalysisCoverage.PARTIAL,
                result.coverage()
        );

        assertEquals(
                CompatibilityAssessment.NO_ISSUES_DETECTED,
                result.assessment()
        );

        assertEquals(219, result.code().existingJavaFileCount());
        assertEquals(264, result.code().incomingJavaFileCount());
    }

    private CompatibilityComparisonResponse compare() {
        return service.compare(EXISTING_URL, INCOMING_URL);
    }

    private void mockResults(
            MavenComparisonResponse maven,
            boolean existingPomFound,
            boolean incomingPomFound,
            ConfigurationComparisonResponse configuration,
            JavaCodeComparisonResponse code
    ) {

        when(mavenService.analyzeForCompatibility(
                EXISTING_URL,
                INCOMING_URL
        )).thenReturn(new MavenAnalysisResult(
                maven,
                existingPomFound,
                incomingPomFound
        ));

        when(configurationService.compare(
                EXISTING_URL,
                INCOMING_URL
        )).thenReturn(configuration);

        when(codeService.compare(
                EXISTING_URL,
                INCOMING_URL
        )).thenReturn(code);
    }

    private MavenComparisonResponse mavenResponse(
            int existingCount,
            int incomingCount,
            List<MavenDependencyConflict> conflicts
    ) {
        return new MavenComparisonResponse(
                "example/backend-a",
                "example/backend-b",
                existingCount,
                incomingCount,
                conflicts.size(),
                conflicts
        );
    }

    private ConfigurationComparisonResponse configurationResponse(
            boolean existingHasFile,
            boolean incomingHasFile,
            int comparedKeyCount,
            List<ConfigurationIssue> issues
    ) {

        String path = "src/main/resources/application.properties";

        int reviewCount = (int) issues.stream()
                .filter(issue ->
                        "REVIEW".equals(issue.classification()))
                .count();

        int informationalCount = (int) issues.stream()
                .filter(issue ->
                        "INFORMATIONAL".equals(issue.classification()))
                .count();

        return new ConfigurationComparisonResponse(
                "example/backend-a",
                "example/backend-b",
                existingHasFile ? List.of(path) : List.of(),
                incomingHasFile ? List.of(path) : List.of(),
                comparedKeyCount,
                reviewCount,
                informationalCount,
                issues
        );
    }

    private JavaCodeComparisonResponse codeResponse(
            int existingCount,
            int incomingCount,
            int matchingPaths,
            List<JavaSourceCollision> collisions
    ) {
        return new JavaCodeComparisonResponse(
                "example/backend-a",
                "example/backend-b",
                existingCount,
                incomingCount,
                matchingPaths,
                collisions.size(),
                collisions
        );
    }
}
