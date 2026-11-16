
package com.codebridge.backend.configanalysis;

import com.codebridge.backend.dto.ConfigurationComparisonResponse;
import com.codebridge.backend.dto.ConfigurationIssue;
import com.codebridge.backend.github.GitHubConfigurationClient;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConfigurationComparisonServiceTest {

    private static final String EXISTING_URL =
            "https://github.com/example/backend-a";

    private static final String INCOMING_URL =
            "https://github.com/example/backend-b";

    private static final String PATH =
            GitHubConfigurationClient.RESOURCES_PATH;

    private GitHubConfigurationClient githubClient;
    private ConfigurationComparisonService service;

    @BeforeEach
    void setUp() {
        githubClient = mock(GitHubConfigurationClient.class);

        service = new ConfigurationComparisonService(
                githubClient
        );
    }

    @Test
    void shouldDetectConfigurationDifferences() {

        mockFiles(
                """
                server.port=8080
                spring.application.name=service-a
                """,
                """
                server.port=9090
                spring.application.name=service-b
                """
        );

        ConfigurationComparisonResponse result =
                service.compare(EXISTING_URL, INCOMING_URL);

        assertEquals("example/backend-a",
                result.existingRepository());

        assertEquals("example/backend-b",
                result.incomingRepository());

        assertEquals(2, result.comparedKeyCount());
        assertEquals(1, result.reviewCount());
        assertEquals(1, result.informationalCount());
        assertEquals(2, result.issues().size());

        ConfigurationIssue portIssue = result.issues()
                .stream()
                .filter(issue -> issue.key().equals("server.port"))
                .findFirst()
                .orElseThrow();

        assertEquals("REVIEW", portIssue.classification());
        assertEquals(PATH, portIssue.existingFile());
        assertEquals(PATH, portIssue.incomingFile());
    }

    @Test
    void shouldNotReportIdenticalValues() {

        mockFiles(
                "server.port=8080",
                "server.port=8080"
        );

        ConfigurationComparisonResponse result =
                service.compare(EXISTING_URL, INCOMING_URL);

        assertEquals(1, result.comparedKeyCount());
        assertEquals(0, result.reviewCount());
        assertEquals(0, result.informationalCount());
        assertTrue(result.issues().isEmpty());
    }

    @Test
    void shouldNotExposeSecretsOrUnapprovedKeys() {

        mockFiles(
                """
                server.port=8080
                spring.datasource.password=secret-one
                custom.api.token=token-one
                """,
                """
                server.port=9090
                spring.datasource.password=secret-two
                custom.api.token=token-two
                """
        );

        ConfigurationComparisonResponse result =
                service.compare(EXISTING_URL, INCOMING_URL);

        assertEquals(1, result.comparedKeyCount());
        assertEquals(1, result.reviewCount());
        assertEquals(1, result.issues().size());

        String responseText = result.toString();

        assertFalse(responseText.contains("secret-one"));
        assertFalse(responseText.contains("secret-two"));
        assertFalse(responseText.contains("token-one"));
        assertFalse(responseText.contains("token-two"));
        assertFalse(responseText.contains("password"));
        assertFalse(responseText.contains("custom.api.token"));
    }

    @Test
    void shouldSkipUnresolvedPlaceholders() {

        mockFiles(
                "server.port=${PORT}",
                "server.port=8080"
        );

        ConfigurationComparisonResponse result =
                service.compare(EXISTING_URL, INCOMING_URL);

        assertEquals(0, result.comparedKeyCount());
        assertTrue(result.issues().isEmpty());
    }

    @Test
    void shouldHandleMissingConfigurationFiles() {

        ConfigurationComparisonResponse result =
                service.compare(EXISTING_URL, INCOMING_URL);

        assertTrue(result.existingFilesFound().isEmpty());
        assertTrue(result.incomingFilesFound().isEmpty());
        assertEquals(0, result.comparedKeyCount());
        assertTrue(result.issues().isEmpty());
    }

    @Test
    void shouldRejectInvalidRepositoryUrlsBeforeFetching() {

        assertThrows(
                IllegalArgumentException.class,
                () -> service.compare(
                        "https://evil.com/example/backend",
                        INCOMING_URL
                )
        );

        verifyNoInteractions(githubClient);
    }

    private void mockFiles(
            String existingContent,
            String incomingContent) {

        when(githubClient.getPropertiesFile(
                EXISTING_URL, PATH
        )).thenReturn(Optional.of(existingContent));

        when(githubClient.getPropertiesFile(
                INCOMING_URL, PATH
        )).thenReturn(Optional.of(incomingContent));
    }
}
