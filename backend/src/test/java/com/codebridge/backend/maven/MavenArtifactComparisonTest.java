
package com.codebridge.backend.maven;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MavenArtifactComparisonTest {

    private MavenDependencyComparisonService service;

    @BeforeEach
    void setUp() {
        service = new MavenDependencyComparisonService();
    }

    @Test
    void shouldDetectConflictForMatchingJarArtifacts() {

        MavenDependency existing = dependency(
                "1.0.0", "jar", ""
        );

        MavenDependency incoming = dependency(
                "2.0.0", "jar", ""
        );

        List<MavenDependencyConflict> conflicts =
                service.compare(
                        List.of(existing),
                        List.of(incoming)
                );

        assertEquals(1, conflicts.size());
        assertEquals("1.0.0", conflicts.get(0).existingVersion());
        assertEquals("2.0.0", conflicts.get(0).incomingVersion());
        assertEquals(
                ConflictSeverity.HIGH,
                conflicts.get(0).severity()
        );
    }

    @Test
    void shouldNotCompareJarAgainstTestJar() {

        MavenDependency existing = dependency(
                "1.0.0", "jar", ""
        );

        MavenDependency incoming = dependency(
                "2.0.0", "test-jar", "tests"
        );

        assertTrue(
                service.compare(
                        List.of(existing),
                        List.of(incoming)
                ).isEmpty()
        );
    }

    @Test
    void shouldDetectConflictForMatchingTestJars() {

        MavenDependency existing = dependency(
                "1.0.0", "test-jar", "tests"
        );

        MavenDependency incoming = dependency(
                "2.0.0", "test-jar", "tests"
        );

        List<MavenDependencyConflict> conflicts =
                service.compare(
                        List.of(existing),
                        List.of(incoming)
                );

        assertEquals(1, conflicts.size());
        assertEquals(
                ConflictSeverity.HIGH,
                conflicts.get(0).severity()
        );
    }

    @Test
    void shouldNotCompareDifferentClassifiers() {

        MavenDependency existing = dependency(
                "1.0.0", "jar", "tests"
        );

        MavenDependency incoming = dependency(
                "2.0.0", "jar", "sources"
        );

        assertTrue(
                service.compare(
                        List.of(existing),
                        List.of(incoming)
                ).isEmpty()
        );
    }

    @Test
    void shouldMatchDefaultJarAndEmptyClassifier() {

        MavenDependency existing =
                new MavenDependency(
                        "org.example",
                        "example-library",
                        "1.0.0",
                        "compile"
                );

        MavenDependency incoming =
                new MavenDependency(
                        "org.example",
                        "example-library",
                        "2.0.0",
                        "compile",
                        null,
                        null
                );

        List<MavenDependencyConflict> conflicts =
                service.compare(
                        List.of(existing),
                        List.of(incoming)
                );

        assertEquals(1, conflicts.size());
    }

    @Test
    void shouldSkipUnknownVersions() {

        MavenDependency existing = dependency(
                null, "jar", ""
        );

        MavenDependency incoming = dependency(
                "2.0.0", "jar", ""
        );

        assertTrue(
                service.compare(
                        List.of(existing),
                        List.of(incoming)
                ).isEmpty()
        );
    }

    @Test
    void shouldSkipUnresolvedArtifactIdentity() {

        MavenDependency existing =
                new MavenDependency(
                        "org.example",
                        "example-library",
                        "1.0.0",
                        "compile",
                        "jar",
                        "${unknown.classifier}"
                );

        MavenDependency incoming = dependency(
                "2.0.0", "jar", ""
        );

        assertTrue(
                service.compare(
                        List.of(existing),
                        List.of(incoming)
                ).isEmpty()
        );
    }

    @Test
    void shouldDetectOnlyConflictsForMatchingArtifactIdentities() {

        MavenDependency existingJar = dependency(
                "1.0.0", "jar", ""
        );

        MavenDependency existingTestJar = dependency(
                "3.0.0", "test-jar", "tests"
        );

        MavenDependency incomingJar = dependency(
                "2.0.0", "jar", ""
        );

        MavenDependency incomingDifferentClassifier = dependency(
                "4.0.0", "test-jar", "sources"
        );

        List<MavenDependencyConflict> conflicts =
                service.compare(
                        List.of(existingJar, existingTestJar),
                        List.of(incomingJar, incomingDifferentClassifier)
                );

        assertEquals(1, conflicts.size());
        assertEquals("1.0.0", conflicts.get(0).existingVersion());
        assertEquals("2.0.0", conflicts.get(0).incomingVersion());
    }

    private MavenDependency dependency(
            String version,
            String type,
            String classifier
    ) {
        return new MavenDependency(
                "org.example",
                "example-library",
                version,
                "compile",
                type,
                classifier
        );
    }
}
