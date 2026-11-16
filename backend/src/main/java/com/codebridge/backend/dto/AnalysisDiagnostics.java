
package com.codebridge.backend.dto;

import com.codebridge.backend.compatibility.AnalysisCoverage;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record AnalysisDiagnostics(
        boolean existingRootPomFound,
        boolean incomingRootPomFound,
        int existingDependencyCount,
        int incomingDependencyCount,
        List<String> existingConfigurationFilesFound,
        List<String> incomingConfigurationFilesFound,
        int comparedConfigurationKeyCount,
        boolean javaAnalysisAvailable,
        int existingJavaFileCount,
        int incomingJavaFileCount,
        int existingControllersScanned,
        int incomingControllersScanned,
        boolean routeScanComplete,
        List<String> limitations
) {

    public AnalysisDiagnostics {
        existingConfigurationFilesFound = List.copyOf(
                existingConfigurationFilesFound
        );
        incomingConfigurationFilesFound = List.copyOf(
                incomingConfigurationFilesFound
        );
        limitations = List.copyOf(limitations);
    }

    public static AnalysisDiagnostics from(
            AnalysisCoverage coverage,
            MavenComparisonResponse maven,
            ConfigurationComparisonResponse configuration,
            JavaCodeComparisonResponse code,
            boolean existingRootPomFound,
            boolean incomingRootPomFound
    ) {

        Objects.requireNonNull(coverage, "Coverage is required");
        Objects.requireNonNull(maven, "Maven result is required");
        Objects.requireNonNull(
                configuration,
                "Configuration result is required"
        );

        List<String> limitations = new ArrayList<>();

        if (!existingRootPomFound || !incomingRootPomFound) {
            limitations.add(
                    "At least one root pom.xml was unavailable. "
                            + "Maven comparison coverage is incomplete."
            );
        }

        limitations.add(
                "Maven analysis compares supported declarations "
                        + "from root pom.xml files. It does not "
                        + "resolve the complete dependency graph "
                        + "or verify binary compatibility."
        );

        if (configuration.existingFilesFound().isEmpty()
                || configuration.incomingFilesFound().isEmpty()) {

            limitations.add(
                    "Supported application.properties files "
                            + "were not found in both repositories. "
                            + "Configuration comparison is incomplete."
            );

        } else if (configuration.comparedKeyCount() == 0) {

            limitations.add(
                    "Configuration files were found, but no "
                            + "supported configuration keys "
                            + "were compared."
            );
        }

        limitations.add(
                "Configuration analysis covers selected "
                        + "application.properties keys only. "
                        + "Other configuration formats, profiles, "
                        + "and runtime values are not fully assessed."
        );

        boolean javaAvailable = code != null;

        int existingJavaFiles = javaAvailable
                ? code.existingJavaFileCount()
                : 0;

        int incomingJavaFiles = javaAvailable
                ? code.incomingJavaFileCount()
                : 0;

        int existingControllers = javaAvailable
                ? code.existingControllersScanned()
                : 0;

        int incomingControllers = javaAvailable
                ? code.incomingControllersScanned()
                : 0;

        boolean routesComplete = javaAvailable
                && code.routeScanComplete();

        if (!javaAvailable) {

            limitations.add(
                    "Java source analysis was not performed."
            );

        } else {

            if (existingJavaFiles == 0 || incomingJavaFiles == 0) {
                limitations.add(
                        "No Java files were identified in at "
                                + "least one repository. Java "
                                + "comparison has limited evidence."
                );
            }

            if (!routesComplete) {
                limitations.add(
                        "Spring REST route scanning was incomplete. "
                                + "Some endpoint collisions may "
                                + "remain undetected."
                );
            }

            limitations.add(
                    "Java class identity and Spring REST route "
                            + "checks use source-based heuristics. "
                            + "They do not replace compilation, "
                            + "runtime routing tests, or integration tests."
            );
        }

        if (coverage == AnalysisCoverage.PARTIAL) {

            limitations.add(
                    "Overall analysis coverage is partial. "
                            + "Some supported checks lacked "
                            + "comparable evidence."
            );

        } else if (coverage == AnalysisCoverage.INSUFFICIENT) {

            limitations.add(
                    "Overall evidence is insufficient for "
                            + "a meaningful compatibility assessment."
            );
        }

        limitations.add(
                "No detected issues does not establish that "
                        + "the repositories can be safely integrated."
        );

        return new AnalysisDiagnostics(
                existingRootPomFound,
                incomingRootPomFound,
                maven.existingDependencyCount(),
                maven.incomingDependencyCount(),
                configuration.existingFilesFound(),
                configuration.incomingFilesFound(),
                configuration.comparedKeyCount(),
                javaAvailable,
                existingJavaFiles,
                incomingJavaFiles,
                existingControllers,
                incomingControllers,
                routesComplete,
                limitations
        );
    }
}
