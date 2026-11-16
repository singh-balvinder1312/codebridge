package com.codebridge.backend.maven;

import java.util.Objects;

public record MavenDependency(
        String groupId,
        String artifactId,
        String version,
        String scope,
        String type,
        String classifier
) {

    public MavenDependency {

        Objects.requireNonNull(
                groupId,
                "Dependency groupId is required"
        );

        Objects.requireNonNull(
                artifactId,
                "Dependency artifactId is required"
        );

        // Maven's default dependency scope is compile.
        if (scope == null || scope.isBlank()) {
            scope = "compile";
        }

        // Maven's default artifact type is jar.
        if (type == null || type.isBlank()) {
            type = "jar";
        }

        // An absent classifier is represented by an empty string.
        if (classifier == null || classifier.isBlank()) {
            classifier = "";
        }
    }

    // Preserve compatibility with existing code and tests.
    public MavenDependency(
            String groupId,
            String artifactId,
            String version,
            String scope
    ) {
        this(
                groupId,
                artifactId,
                version,
                scope,
                "jar",
                ""
        );
    }
}
