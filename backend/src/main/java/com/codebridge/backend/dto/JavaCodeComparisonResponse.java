
package com.codebridge.backend.dto;

import java.util.List;
import java.util.Objects;

public record JavaCodeComparisonResponse(
        String existingRepository,
        String incomingRepository,
        int existingJavaFileCount,
        int incomingJavaFileCount,
        int matchingPathCount,
        int potentialCollisionCount,
        List<JavaSourceCollision> collisions,
        List<JavaClassCollision> classCollisions,
        List<SpringRouteCollision> routeCollisions,
        int existingControllersScanned,
        int incomingControllersScanned,
        boolean routeScanComplete
) {

    public JavaCodeComparisonResponse {

        Objects.requireNonNull(
                existingRepository,
                "Existing repository is required"
        );

        Objects.requireNonNull(
                incomingRepository,
                "Incoming repository is required"
        );

        collisions = List.copyOf(
                Objects.requireNonNull(collisions)
        );

        classCollisions = List.copyOf(
                Objects.requireNonNull(classCollisions)
        );

        routeCollisions = List.copyOf(
                Objects.requireNonNull(routeCollisions)
        );

        if (existingJavaFileCount < 0
                || incomingJavaFileCount < 0
                || matchingPathCount < 0
                || existingControllersScanned < 0
                || incomingControllersScanned < 0
                || potentialCollisionCount < 0) {
            throw new IllegalArgumentException(
                    "Comparison counts cannot be negative"
            );
        }

        if (matchingPathCount > existingJavaFileCount
                || matchingPathCount > incomingJavaFileCount) {
            throw new IllegalArgumentException(
                    "Invalid matching path count"
            );
        }

        int total = collisions.size()
                + classCollisions.size()
                + routeCollisions.size();

        if (potentialCollisionCount != total) {
            throw new IllegalArgumentException(
                    "Collision count must equal the finding count"
            );
        }
    }

    // Preserve compatibility with earlier tests and callers.
    public JavaCodeComparisonResponse(
            String existingRepository,
            String incomingRepository,
            int existingJavaFileCount,
            int incomingJavaFileCount,
            int matchingPathCount,
            int potentialCollisionCount,
            List<JavaSourceCollision> collisions
    ) {
        this(
                existingRepository,
                incomingRepository,
                existingJavaFileCount,
                incomingJavaFileCount,
                matchingPathCount,
                potentialCollisionCount,
                collisions,
                List.of(),
                List.of(),
                0,
                0,
                false
        );
    }
}
