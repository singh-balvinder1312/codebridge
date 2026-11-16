
package com.codebridge.backend.maven;

import java.util.Objects;

public record MavenDependencyConflict(
        String groupId,
        String artifactId,
        String existingVersion,
        String incomingVersion,
        ConflictSeverity severity,
        String type,
        String classifier
) {

    public MavenDependencyConflict {

        Objects.requireNonNull(
                groupId,
                "groupId is required"
        );

        Objects.requireNonNull(
                artifactId,
                "artifactId is required"
        );

        Objects.requireNonNull(
                existingVersion,
                "existingVersion is required"
        );

        Objects.requireNonNull(
                incomingVersion,
                "incomingVersion is required"
        );

        Objects.requireNonNull(
                severity,
                "severity is required"
        );

        // Apply Maven's default artifact identity values.
        if (type == null || type.isBlank()) {
            type = "jar";
        }

        if (classifier == null || classifier.isBlank()) {
            classifier = "";
        }
    }

    // Constructor for callers that know the artifact
    // type and classifier but want automatic severity.
    public MavenDependencyConflict(
            String groupId,
            String artifactId,
            String existingVersion,
            String incomingVersion,
            String type,
            String classifier
    ) {
        this(
                groupId,
                artifactId,
                existingVersion,
                incomingVersion,
                new MavenConflictSeverityClassifier().classify(
                        existingVersion,
                        incomingVersion
                ),
                type,
                classifier
        );
    }

    // Preserve the existing constructor that allows
    // callers to specify a severity explicitly.
    public MavenDependencyConflict(
            String groupId,
            String artifactId,
            String existingVersion,
            String incomingVersion,
            ConflictSeverity severity
    ) {
        this(
                groupId,
                artifactId,
                existingVersion,
                incomingVersion,
                severity,
                "jar",
                ""
        );
    }

    // Preserve the original four-argument constructor
    // so existing tests and services still compile.
    public MavenDependencyConflict(
            String groupId,
            String artifactId,
            String existingVersion,
            String incomingVersion
    ) {
        this(
                groupId,
                artifactId,
                existingVersion,
                incomingVersion,
                "jar",
                ""
        );
    }
}
