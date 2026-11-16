
package com.codebridge.backend.compatibility;

import com.codebridge.backend.dto.CompatibilityRecommendation;
import com.codebridge.backend.dto.ConfigurationComparisonResponse;
import com.codebridge.backend.dto.ConfigurationIssue;
import com.codebridge.backend.dto.JavaClassCollision;
import com.codebridge.backend.dto.JavaCodeComparisonResponse;
import com.codebridge.backend.dto.JavaSourceCollision;
import com.codebridge.backend.dto.MavenComparisonResponse;
import com.codebridge.backend.dto.SpringRouteCollision;

import com.codebridge.backend.maven.MavenDependencyConflict;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CompatibilityRecommendationServiceTest {

    private CompatibilityRecommendationService service;

    @BeforeEach
    void setUp() {
        service = new CompatibilityRecommendationService();
    }

    @Test
    void shouldRecommendReviewingMavenVersionDifferences() {

        MavenDependencyConflict conflict =
                new MavenDependencyConflict(
                        "org.slf4j",
                        "slf4j-api",
                        "1.7.36",
                        "2.0.17"
                );

        List<CompatibilityRecommendation> recommendations =
                service.generate(
                        maven(List.of(conflict)),
                        configuration(List.of()),
                        emptyCode(),
                        AnalysisCoverage.SUPPORTED_CHECKS_COMPLETED,
                        true,
                        true
                );

        assertEquals(1, recommendations.size());

        CompatibilityRecommendation recommendation =
                recommendations.get(0);

        assertEquals("MAVEN", recommendation.category());

        assertTrue(
                recommendation.finding()
                        .contains("org.slf4j:slf4j-api")
        );

        assertTrue(
                recommendation.recommendedAction()
                        .contains("integration tests")
        );
    }

    @Test
    void shouldRecommendReviewingConfigurationDifferences() {

        ConfigurationIssue issue = new ConfigurationIssue(
                "server.port",
                "src/main/resources/application.properties",
                "src/main/resources/application.properties",
                "REVIEW",
                "Different ports may require review."
        );

        List<CompatibilityRecommendation> recommendations =
                service.generate(
                        maven(List.of()),
                        configuration(List.of(issue)),
                        emptyCode(),
                        AnalysisCoverage.SUPPORTED_CHECKS_COMPLETED,
                        true,
                        true
                );

        assertEquals(1, recommendations.size());
        assertEquals(
                "CONFIGURATION",
                recommendations.get(0).category()
        );

        assertTrue(
                recommendations.get(0).recommendedAction()
                        .contains("ports")
        );
    }

    @Test
    void shouldNotRecommendChangesForInformationalDifferences() {

        ConfigurationIssue issue = new ConfigurationIssue(
                "spring.application.name",
                "src/main/resources/application.properties",
                "src/main/resources/application.properties",
                "INFORMATIONAL",
                "Different names may be intentional."
        );

        List<CompatibilityRecommendation> recommendations =
                service.generate(
                        maven(List.of()),
                        configuration(List.of(issue)),
                        emptyCode(),
                        AnalysisCoverage.SUPPORTED_CHECKS_COMPLETED,
                        true,
                        true
                );

        assertTrue(recommendations.isEmpty());
    }

    @Test
    void shouldGenerateRecommendationsForJavaFindings() {

        JavaSourceCollision sourceCollision =
                new JavaSourceCollision(
                        "src/main/java/com/example/UserService.java",
                        "REVIEW",
                        "Different files share a path."
                );

        JavaClassCollision classCollision =
                new JavaClassCollision(
                        "com.example.UserService",
                        "service-a/src/main/java/com/example/UserService.java",
                        "service-b/src/main/java/com/example/UserService.java",
                        "Potential duplicate class."
                );

        SpringRouteCollision routeCollision =
                new SpringRouteCollision(
                        "GET",
                        "/api/users",
                        "UserController.java",
                        "AccountController.java",
                        "Potential duplicate route."
                );

        JavaCodeComparisonResponse code =
                new JavaCodeComparisonResponse(
                        "example/backend-a",
                        "example/backend-b",
                        5,
                        5,
                        1,
                        3,
                        List.of(sourceCollision),
                        List.of(classCollision),
                        List.of(routeCollision),
                        1,
                        1,
                        true
                );

        List<CompatibilityRecommendation> recommendations =
                service.generate(
                        maven(List.of()),
                        configuration(List.of()),
                        code,
                        AnalysisCoverage.SUPPORTED_CHECKS_COMPLETED,
                        true,
                        true
                );

        assertEquals(3, recommendations.size());

        assertTrue(recommendations.stream()
                .anyMatch(item ->
                        "JAVA_SOURCE".equals(item.category())));

        assertTrue(recommendations.stream()
                .anyMatch(item ->
                        "JAVA_CLASS".equals(item.category())));

        assertTrue(recommendations.stream()
                .anyMatch(item ->
                        "SPRING_ROUTE".equals(item.category())));
    }

    @Test
    void shouldWarnWhenRootPomIsMissing() {

        List<CompatibilityRecommendation> recommendations =
                service.generate(
                        maven(List.of()),
                        configuration(List.of()),
                        emptyCode(),
                        AnalysisCoverage.PARTIAL,
                        true,
                        false
                );

        assertTrue(recommendations.stream()
                .anyMatch(item ->
                        item.finding().contains("pom.xml")));

        assertTrue(recommendations.stream()
                .anyMatch(item ->
                        item.finding().contains("partial coverage")));
    }

    @Test
    void shouldWarnAboutInsufficientEvidence() {

        List<CompatibilityRecommendation> recommendations =
                service.generate(
                        maven(List.of()),
                        configuration(List.of()),
                        emptyCode(),
                        AnalysisCoverage.INSUFFICIENT,
                        true,
                        true
                );

        assertTrue(recommendations.stream()
                .anyMatch(item ->
                        "HIGH".equals(item.priority())
                                && item.finding().contains(
                                "Insufficient comparable")
                ));
    }

    @Test
    void shouldWarnWhenRouteScanIsIncomplete() {

        JavaCodeComparisonResponse code =
                new JavaCodeComparisonResponse(
                        "example/backend-a",
                        "example/backend-b",
                        20,
                        25,
                        0,
                        0,
                        List.of(),
                        List.of(),
                        List.of(),
                        8,
                        8,
                        false
                );

        List<CompatibilityRecommendation> recommendations =
                service.generate(
                        maven(List.of()),
                        configuration(List.of()),
                        code,
                        AnalysisCoverage.SUPPORTED_CHECKS_COMPLETED,
                        true,
                        true
                );

        assertEquals(1, recommendations.size());

        assertTrue(
                recommendations.get(0).finding()
                        .contains("incomplete")
        );
    }

    private MavenComparisonResponse maven(
            List<MavenDependencyConflict> conflicts
    ) {
        return new MavenComparisonResponse(
                "example/backend-a",
                "example/backend-b",
                2,
                3,
                conflicts.size(),
                conflicts
        );
    }

    private ConfigurationComparisonResponse configuration(
            List<ConfigurationIssue> issues
    ) {
        int reviews = (int) issues.stream()
                .filter(issue ->
                        "REVIEW".equals(issue.classification()))
                .count();

        int informational = (int) issues.stream()
                .filter(issue ->
                        "INFORMATIONAL".equals(issue.classification()))
                .count();

        return new ConfigurationComparisonResponse(
                "example/backend-a",
                "example/backend-b",
                List.of(),
                List.of(),
                issues.size(),
                reviews,
                informational,
                issues
        );
    }

    private JavaCodeComparisonResponse emptyCode() {
        return new JavaCodeComparisonResponse(
                "example/backend-a",
                "example/backend-b",
                0,
                0,
                0,
                0,
                List.of(),
                List.of(),
                List.of(),
                0,
                0,
                true
        );
    }
}
