
package com.codebridge.backend.maven;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class MavenDependencyComparisonService {

    private record DependencyKey(
            String groupId,
            String artifactId,
            String type,
            String classifier
    ) {
    }

    public List<MavenDependencyConflict> compare(
            List<MavenDependency> existingDependencies,
            List<MavenDependency> incomingDependencies) {

        Objects.requireNonNull(
                existingDependencies,
                "Existing dependencies cannot be null"
        );

        Objects.requireNonNull(
                incomingDependencies,
                "Incoming dependencies cannot be null"
        );

        Map<DependencyKey, MavenDependency> existingMap =
                new HashMap<>();

        for (MavenDependency dependency : existingDependencies) {

            Objects.requireNonNull(
                    dependency,
                    "Existing dependency cannot be null"
            );

            DependencyKey key = dependencyKey(dependency);

            if (key != null) {
                existingMap.put(key, dependency);
            }
        }

        List<MavenDependencyConflict> conflicts =
                new ArrayList<>();

        for (MavenDependency incoming : incomingDependencies) {

            Objects.requireNonNull(
                    incoming,
                    "Incoming dependency cannot be null"
            );

            DependencyKey key = dependencyKey(incoming);

            if (key == null) {
                continue;
            }

            MavenDependency existing = existingMap.get(key);

            if (existing == null) {
                continue;
            }

            String existingVersion = existing.version();
            String incomingVersion = incoming.version();

            // Unknown versions cannot be reliably compared.
            if (existingVersion == null || incomingVersion == null) {
                continue;
            }

            // Do not compare unresolved Maven property expressions.
            if (existingVersion.contains("${")
                    || incomingVersion.contains("${")) {
                continue;
            }

            if (!existingVersion.equals(incomingVersion)) {

                // Include the actual Maven artifact type
                // and classifier in each reported conflict.
                conflicts.add(new MavenDependencyConflict(
                        incoming.groupId(),
                        incoming.artifactId(),
                        existingVersion,
                        incomingVersion,
                        incoming.type(),
                        incoming.classifier()
                ));
            }
        }

        return List.copyOf(conflicts);
    }

    private DependencyKey dependencyKey(
            MavenDependency dependency) {

        String groupId = dependency.groupId();
        String artifactId = dependency.artifactId();
        String type = dependency.type();
        String classifier = dependency.classifier();

        // Skip artifact identities containing unresolved properties.
        if (containsUnresolvedProperty(groupId)
                || containsUnresolvedProperty(artifactId)
                || containsUnresolvedProperty(type)
                || containsUnresolvedProperty(classifier)) {
            return null;
        }

        return new DependencyKey(
                groupId,
                artifactId,
                type,
                classifier
        );
    }

    private boolean containsUnresolvedProperty(String value) {
        return value != null && value.contains("${");
    }
}
