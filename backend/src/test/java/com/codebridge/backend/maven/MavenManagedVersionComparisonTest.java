
package com.codebridge.backend.maven;

import com.codebridge.backend.dto.MavenComparisonResponse;
import com.codebridge.backend.github.GitHubPomClient;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MavenManagedVersionComparisonTest {

    private static final String EXISTING_URL =
            "https://github.com/example/existing-app";

    private static final String INCOMING_URL =
            "https://github.com/example/incoming-app";

    private GitHubPomClient githubPomClient;
    private MavenRepositoryComparisonService service;

    @BeforeEach
    void setUp() {
        githubPomClient = mock(GitHubPomClient.class);

        service = new MavenRepositoryComparisonService(
                githubPomClient,
                new MavenPomParser(),
                new MavenDependencyComparisonService()
        );
    }

    @Test
    void shouldDetectConflictUsingManagedVersion() {

        String existingPom = """
                <project>
                    <dependencies>
                        <dependency>
                            <groupId>org.slf4j</groupId>
                            <artifactId>slf4j-api</artifactId>
                            <version>1.7.36</version>
                        </dependency>
                    </dependencies>
                </project>
                """;

        String incomingPom = """
                <project>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>org.slf4j</groupId>
                                <artifactId>slf4j-api</artifactId>
                                <version>2.0.17</version>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>

                    <dependencies>
                        <dependency>
                            <groupId>org.slf4j</groupId>
                            <artifactId>slf4j-api</artifactId>
                        </dependency>
                    </dependencies>
                </project>
                """;

        mockPomFiles(existingPom, incomingPom);

        MavenComparisonResponse result =
                service.analyzeRepositories(
                        EXISTING_URL,
                        INCOMING_URL
                );

        assertEquals(1, result.existingDependencyCount());
        assertEquals(1, result.incomingDependencyCount());
        assertEquals(1, result.potentialConflictCount());

        MavenDependencyConflict conflict =
                result.conflicts().get(0);

        assertEquals("org.slf4j", conflict.groupId());
        assertEquals("slf4j-api", conflict.artifactId());
        assertEquals("1.7.36", conflict.existingVersion());
        assertEquals("2.0.17", conflict.incomingVersion());
        assertEquals(ConflictSeverity.HIGH, conflict.severity());

        assertEquals(1, result.severitySummary().high());
        assertEquals(0, result.severitySummary().medium());
        assertEquals(0, result.severitySummary().low());
        assertEquals(0, result.severitySummary().unknown());

        verify(githubPomClient).getRootPom(EXISTING_URL);
        verify(githubPomClient).getRootPom(INCOMING_URL);
    }

    @Test
    void shouldNotReportConflictWhenManagedVersionsMatch() {

        String existingPom = pomWithManagedDependency("2.0.17");
        String incomingPom = pomWithManagedDependency("2.0.17");

        mockPomFiles(existingPom, incomingPom);

        MavenComparisonResponse result =
                service.analyzeRepositories(
                        EXISTING_URL,
                        INCOMING_URL
                );

        assertEquals(1, result.existingDependencyCount());
        assertEquals(1, result.incomingDependencyCount());
        assertEquals(0, result.potentialConflictCount());
        assertTrue(result.conflicts().isEmpty());
        assertEquals(0, result.severitySummary().total());
    }

    @Test
    void shouldNotInventConflictWhenManagedVersionIsUnknown() {

        String existingPom = """
                <project>
                    <dependencies>
                        <dependency>
                            <groupId>org.slf4j</groupId>
                            <artifactId>slf4j-api</artifactId>
                            <version>1.7.36</version>
                        </dependency>
                    </dependencies>
                </project>
                """;

        String incomingPom = """
                <project>
                    <dependencies>
                        <dependency>
                            <groupId>org.slf4j</groupId>
                            <artifactId>slf4j-api</artifactId>
                        </dependency>
                    </dependencies>
                </project>
                """;

        mockPomFiles(existingPom, incomingPom);

        MavenComparisonResponse result =
                service.analyzeRepositories(
                        EXISTING_URL,
                        INCOMING_URL
                );

        assertEquals(0, result.potentialConflictCount());
        assertTrue(result.conflicts().isEmpty());

        // An empty conflict list does not prove compatibility:
        // the incoming dependency version is unknown.
    }

    private String pomWithManagedDependency(String version) {
        return """
                <project>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>org.slf4j</groupId>
                                <artifactId>slf4j-api</artifactId>
                                <version>%s</version>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>org.slf4j</groupId>
                            <artifactId>slf4j-api</artifactId>
                        </dependency>
                    </dependencies>
                </project>
                """.formatted(version);
    }

    private void mockPomFiles(
            String existingPom,
            String incomingPom) {

        when(githubPomClient.getRootPom(EXISTING_URL))
                .thenReturn(new MavenPomFile(
                        "example/existing-app",
                        "pom.xml",
                        existingPom
                ));

        when(githubPomClient.getRootPom(INCOMING_URL))
                .thenReturn(new MavenPomFile(
                        "example/incoming-app",
                        "pom.xml",
                        incomingPom
                ));
    }
}
