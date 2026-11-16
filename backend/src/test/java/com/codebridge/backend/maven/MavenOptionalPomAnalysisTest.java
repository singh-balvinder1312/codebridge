
package com.codebridge.backend.maven;

import com.codebridge.backend.exception.GitHubApiException;
import com.codebridge.backend.github.GitHubPomClient;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MavenOptionalPomAnalysisTest {

    private static final String EXISTING_URL =
            "https://github.com/example/backend-a";

    private static final String INCOMING_URL =
            "https://github.com/example/backend-b";

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
    void shouldAnalyzeBothRepositoriesWhenBothPomsExist() {

        mockExistingPom("1.0.0");
        mockIncomingPom("2.0.0");

        MavenAnalysisResult result =
                service.analyzeForCompatibility(
                        EXISTING_URL,
                        INCOMING_URL
                );

        assertTrue(result.bothRootPomsFound());
        assertEquals(1, result.comparison().existingDependencyCount());
        assertEquals(1, result.comparison().incomingDependencyCount());
        assertEquals(1, result.comparison().potentialConflictCount());
    }

    @Test
    void shouldKeepAvailableDependenciesWhenOnePomIsMissing() {

        mockExistingPom("1.0.0");
        mockMissingPom(INCOMING_URL);

        MavenAnalysisResult result =
                service.analyzeForCompatibility(
                        EXISTING_URL,
                        INCOMING_URL
                );

        assertTrue(result.existingRootPomFound());
        assertFalse(result.incomingRootPomFound());

        assertEquals(1, result.comparison().existingDependencyCount());
        assertEquals(0, result.comparison().incomingDependencyCount());
        assertTrue(result.comparison().conflicts().isEmpty());
    }

    @Test
    void shouldHandleBothRootPomsMissing() {

        mockMissingPom(EXISTING_URL);
        mockMissingPom(INCOMING_URL);

        MavenAnalysisResult result =
                service.analyzeForCompatibility(
                        EXISTING_URL,
                        INCOMING_URL
                );

        assertFalse(result.existingRootPomFound());
        assertFalse(result.incomingRootPomFound());

        assertEquals(0, result.comparison().existingDependencyCount());
        assertEquals(0, result.comparison().incomingDependencyCount());
    }

    @Test
    void shouldPreserveStrictMavenEndpointBehavior() {

        mockMissingPom(EXISTING_URL);

        assertThrows(
                GitHubApiException.class,
                () -> service.analyzeRepositories(
                        EXISTING_URL,
                        INCOMING_URL
                )
        );
    }

    @Test
    void shouldNotHideGitHubRateLimits() {

        when(githubPomClient.getRootPom(EXISTING_URL))
                .thenThrow(new GitHubApiException(
                        GitHubApiException.Reason.RATE_LIMITED,
                        "Rate limit reached"
                ));

        GitHubApiException exception = assertThrows(
                GitHubApiException.class,
                () -> service.analyzeForCompatibility(
                        EXISTING_URL,
                        INCOMING_URL
                )
        );

        assertEquals(
                GitHubApiException.Reason.RATE_LIMITED,
                exception.getReason()
        );
    }

    @Test
    void shouldRejectInvalidUrlsBeforeGitHubRequests() {

        assertThrows(
                IllegalArgumentException.class,
                () -> service.analyzeForCompatibility(
                        "https://evil.com/example/backend",
                        INCOMING_URL
                )
        );

        verifyNoInteractions(githubPomClient);
    }

    private void mockExistingPom(String version) {

        when(githubPomClient.getRootPom(EXISTING_URL))
                .thenReturn(new MavenPomFile(
                        "example/backend-a",
                        "pom.xml",
                        pom(version)
                ));
    }

    private void mockIncomingPom(String version) {

        when(githubPomClient.getRootPom(INCOMING_URL))
                .thenReturn(new MavenPomFile(
                        "example/backend-b",
                        "pom.xml",
                        pom(version)
                ));
    }

    private void mockMissingPom(String url) {

        when(githubPomClient.getRootPom(url))
                .thenThrow(new GitHubApiException(
                        GitHubApiException.Reason.NOT_FOUND,
                        "Root pom.xml not found"
                ));
    }

    private String pom(String version) {

        return """
                <project>
                    <dependencies>
                        <dependency>
                            <groupId>org.example</groupId>
                            <artifactId>example-library</artifactId>
                            <version>%s</version>
                        </dependency>
                    </dependencies>
                </project>
                """.formatted(version);
    }
}
