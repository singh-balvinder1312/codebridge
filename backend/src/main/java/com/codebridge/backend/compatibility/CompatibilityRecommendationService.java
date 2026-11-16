
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

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class CompatibilityRecommendationService {

    public List<CompatibilityRecommendation> generate(
            MavenComparisonResponse maven,
            ConfigurationComparisonResponse configuration,
            JavaCodeComparisonResponse code,
            AnalysisCoverage coverage,
            boolean existingRootPomFound,
            boolean incomingRootPomFound
    ) {

        Objects.requireNonNull(maven, "Maven result is required");
        Objects.requireNonNull(
                configuration,
                "Configuration result is required"
        );
        Objects.requireNonNull(
                coverage,
                "Coverage is required"
        );

        List<CompatibilityRecommendation> recommendations =
                new ArrayList<>();

        addMavenRecommendations(maven, recommendations);

        addConfigurationRecommendations(
                configuration,
                recommendations
        );

        if (code != null) {
            addCodeRecommendations(code, recommendations);
        }

        addCoverageRecommendations(
                coverage,
                existingRootPomFound,
                incomingRootPomFound,
                code,
                recommendations
        );

        return List.copyOf(recommendations);
    }

    private void addMavenRecommendations(
            MavenComparisonResponse maven,
            List<CompatibilityRecommendation> recommendations
    ) {

        for (MavenDependencyConflict conflict : maven.conflicts()) {

            String coordinate = conflict.groupId()
                    + ":"
                    + conflict.artifactId();

            String priority = switch (conflict.severity()) {
                case HIGH -> "HIGH";
                case MEDIUM -> "MEDIUM";
                case LOW -> "LOW";
                default -> "MEDIUM";
            };

            recommendations.add(new CompatibilityRecommendation(
                    "MAVEN",
                    priority,
                    "Different declared versions of "
                            + coordinate
                            + " (type: "
                            + conflict.type()
                            + ", classifier: "
                            + (conflict.classifier().isEmpty()
                            ? "none"
                            : conflict.classifier())
                            + ").",
                    "Check the required versions in both projects. "
                            + "Review dependency management and "
                            + "transitive dependencies, select a version "
                            + "supported by both applications, and "
                            + "run compilation and integration tests. "
                            + "A version difference alone does not "
                            + "prove incompatibility."
            ));
        }
    }

    private void addConfigurationRecommendations(
            ConfigurationComparisonResponse configuration,
            List<CompatibilityRecommendation> recommendations
    ) {

        for (ConfigurationIssue issue : configuration.issues()) {

            if (!"REVIEW".equals(issue.classification())) {
                continue;
            }

            String action = switch (issue.key()) {

                case "server.port", "management.server.port" ->
                        "Check whether these applications will run "
                                + "as separate services or within one "
                                + "process. Assign appropriate ports "
                                + "or reconcile the configuration.";

                case "spring.jpa.hibernate.ddl-auto" ->
                        "Review database schema-management settings. "
                                + "Avoid unintended schema changes "
                                + "and test the chosen migration strategy.";

                case "spring.flyway.enabled" ->
                        "Decide how database migrations will be "
                                + "managed after integration. Check "
                                + "migration ownership and ordering.";

                case "spring.main.web-application-type" ->
                        "Check whether the applications require "
                                + "different web runtime architectures "
                                + "before combining them.";

                default ->
                        "Review this configuration difference and "
                                + "test the intended deployment setup.";
            };

            recommendations.add(new CompatibilityRecommendation(
                    "CONFIGURATION",
                    "MEDIUM",
                    "Different values were detected for "
                            + issue.key()
                            + ".",
                    action
            ));
        }
    }

    private void addCodeRecommendations(
            JavaCodeComparisonResponse code,
            List<CompatibilityRecommendation> recommendations
    ) {

        for (JavaSourceCollision collision : code.collisions()) {

            recommendations.add(new CompatibilityRecommendation(
                    "JAVA_SOURCE",
                    "MEDIUM",
                    "Different Java files use the same relative path: "
                            + collision.path(),
                    "Review both implementations before merging "
                            + "their source trees. Keep the intended "
                            + "implementation or reconcile changes "
                            + "and run Java compilation tests."
            ));
        }

        for (JavaClassCollision collision : code.classCollisions()) {

            recommendations.add(new CompatibilityRecommendation(
                    "JAVA_CLASS",
                    "HIGH",
                    "Potential duplicate Java class identity: "
                            + collision.inferredClassName(),
                    "Verify the actual package declarations and "
                            + "class names. If both classes would "
                            + "share a runtime classpath, consider "
                            + "renaming, relocating, or consolidating "
                            + "the implementations. The identity "
                            + "is currently inferred from file paths."
            ));
        }

        for (SpringRouteCollision collision : code.routeCollisions()) {

            recommendations.add(new CompatibilityRecommendation(
                    "SPRING_ROUTE",
                    "HIGH",
                    "Potential duplicate endpoint: "
                            + collision.httpMethod()
                            + " "
                            + collision.route(),
                    "Verify both Spring controller mappings and "
                            + "their request conditions. If they will "
                            + "run in one application, consider "
                            + "different route prefixes or consolidating "
                            + "the handlers. Test the resulting API "
                            + "routing behavior."
            ));
        }

        if (!code.routeScanComplete()) {

            recommendations.add(new CompatibilityRecommendation(
                    "ANALYSIS_COVERAGE",
                    "MEDIUM",
                    "Spring REST route scanning was incomplete.",
                    "Review unscanned controllers manually. "
                            + "An incomplete route scan cannot "
                            + "establish that no route conflicts exist."
            ));
        }
    }

    private void addCoverageRecommendations(
            AnalysisCoverage coverage,
            boolean existingRootPomFound,
            boolean incomingRootPomFound,
            JavaCodeComparisonResponse code,
            List<CompatibilityRecommendation> recommendations
    ) {

        if (!existingRootPomFound || !incomingRootPomFound) {

            recommendations.add(new CompatibilityRecommendation(
                    "ANALYSIS_COVERAGE",
                    "MEDIUM",
                    "At least one repository's root pom.xml "
                            + "was not available.",
                    "Check whether the repository uses Gradle, "
                            + "a multi-module Maven layout, or another "
                            + "build system. Analyze the relevant build "
                            + "files before drawing compatibility conclusions."
            ));
        }

        if (coverage == AnalysisCoverage.INSUFFICIENT) {

            recommendations.add(new CompatibilityRecommendation(
                    "ANALYSIS_COVERAGE",
                    "HIGH",
                    "Insufficient comparable repository evidence.",
                    "Verify that both repositories are accessible "
                            + "and identify their build and configuration "
                            + "files. Obtain more evidence before making "
                            + "an integration decision."
            ));

        } else if (coverage == AnalysisCoverage.PARTIAL) {

            recommendations.add(new CompatibilityRecommendation(
                    "ANALYSIS_COVERAGE",
                    "LOW",
                    "The compatibility analysis has partial coverage.",
                    "Review the unexamined dependency, configuration, "
                            + "and source-code areas. Run integration "
                            + "tests before treating the repositories "
                            + "as compatible."
            ));
        }

        if (code == null) {

            recommendations.add(new CompatibilityRecommendation(
                    "ANALYSIS_COVERAGE",
                    "MEDIUM",
                    "Java source analysis was not performed.",
                    "Run Java source and REST route analysis before "
                            + "relying on this report."
            ));
        }
    }
}
