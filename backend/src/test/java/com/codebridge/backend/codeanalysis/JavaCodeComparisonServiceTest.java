
package com.codebridge.backend.codeanalysis;

import com.codebridge.backend.dto.JavaCodeComparisonResponse;
import com.codebridge.backend.dto.JavaSourceCollision;
import com.codebridge.backend.github.GitHubJavaTree;
import com.codebridge.backend.github.GitHubJavaTreeClient;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JavaCodeComparisonServiceTest {

    private static final String EXISTING_URL =
            "https://github.com/example/backend-a";

    private static final String INCOMING_URL =
            "https://github.com/example/backend-b";

    private static final String USER_SERVICE =
            "src/main/java/com/example/UserService.java";

    private static final String ORDER_SERVICE =
            "src/main/java/com/example/OrderService.java";

    private static final String HASH_A =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    private static final String HASH_B =
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    private static final String HASH_C =
            "cccccccccccccccccccccccccccccccccccccccc";

    private GitHubJavaTreeClient githubClient;
    private JavaCodeComparisonService service;

    @BeforeEach
    void setUp() {
        githubClient = mock(GitHubJavaTreeClient.class);

        service = new JavaCodeComparisonService(
                githubClient
        );
    }

    @Test
    void shouldDetectDifferentFilesAtSamePath() {

        mockTrees(
                Map.of(USER_SERVICE, HASH_A),
                Map.of(USER_SERVICE, HASH_B)
        );

        JavaCodeComparisonResponse result =
                service.compare(EXISTING_URL, INCOMING_URL);

        assertEquals(1, result.existingJavaFileCount());
        assertEquals(1, result.incomingJavaFileCount());
        assertEquals(1, result.matchingPathCount());
        assertEquals(1, result.potentialCollisionCount());

        JavaSourceCollision collision =
                result.collisions().get(0);

        assertEquals(USER_SERVICE, collision.path());
        assertEquals("REVIEW", collision.classification());
        assertFalse(collision.explanation().isBlank());
    }

    @Test
    void shouldIgnoreIdenticalFiles() {

        mockTrees(
                Map.of(USER_SERVICE, HASH_A),
                Map.of(USER_SERVICE, HASH_A)
        );

        JavaCodeComparisonResponse result =
                service.compare(EXISTING_URL, INCOMING_URL);

        assertEquals(1, result.matchingPathCount());
        assertEquals(0, result.potentialCollisionCount());
        assertTrue(result.collisions().isEmpty());
    }

    @Test
    void shouldIgnoreDifferentPaths() {

        mockTrees(
                Map.of(USER_SERVICE, HASH_A),
                Map.of(ORDER_SERVICE, HASH_B)
        );

        JavaCodeComparisonResponse result =
                service.compare(EXISTING_URL, INCOMING_URL);

        assertEquals(0, result.matchingPathCount());
        assertTrue(result.collisions().isEmpty());
    }

    @Test
    void shouldDetectOnlyChangedMatchingPaths() {

        mockTrees(
                Map.of(
                        USER_SERVICE, HASH_A,
                        ORDER_SERVICE, HASH_C
                ),
                Map.of(
                        USER_SERVICE, HASH_B,
                        ORDER_SERVICE, HASH_C
                )
        );

        JavaCodeComparisonResponse result =
                service.compare(EXISTING_URL, INCOMING_URL);

        assertEquals(2, result.matchingPathCount());
        assertEquals(1, result.potentialCollisionCount());
        assertEquals(USER_SERVICE, result.collisions().get(0).path());
    }

    @Test
    void shouldReturnEmptyResultsForEmptyTrees() {

        mockTrees(Map.of(), Map.of());

        JavaCodeComparisonResponse result =
                service.compare(EXISTING_URL, INCOMING_URL);

        assertEquals(0, result.existingJavaFileCount());
        assertEquals(0, result.incomingJavaFileCount());
        assertEquals(0, result.matchingPathCount());
        assertEquals(0, result.potentialCollisionCount());
    }

    @Test
    void shouldRejectInvalidUrlsBeforeFetching() {

        assertThrows(
                IllegalArgumentException.class,
                () -> service.compare(
                        "https://evil.com/example/backend",
                        INCOMING_URL
                )
        );

        verifyNoInteractions(githubClient);
    }

    @Test
    void shouldRejectInvalidIncomingUrlBeforeFetching() {

        assertThrows(
                IllegalArgumentException.class,
                () -> service.compare(
                        EXISTING_URL,
                        "http://github.com/example/backend"
                )
        );

        verifyNoInteractions(githubClient);
    }

    @Test
    void shouldReturnRepositoryNames() {

        mockTrees(
                Map.of(USER_SERVICE, HASH_A),
                Map.of(USER_SERVICE, HASH_B)
        );

        JavaCodeComparisonResponse result =
                service.compare(EXISTING_URL, INCOMING_URL);

        assertEquals(
                "example/backend-a",
                result.existingRepository()
        );

        assertEquals(
                "example/backend-b",
                result.incomingRepository()
        );

        verify(githubClient).getJavaTree(EXISTING_URL);
        verify(githubClient).getJavaTree(INCOMING_URL);
    }

    private void mockTrees(
            Map<String, String> existingFiles,
            Map<String, String> incomingFiles) {

        when(githubClient.getJavaTree(EXISTING_URL))
                .thenReturn(new GitHubJavaTree(
                        "example/backend-a",
                        existingFiles
                ));

        when(githubClient.getJavaTree(INCOMING_URL))
                .thenReturn(new GitHubJavaTree(
                        "example/backend-b",
                        incomingFiles
                ));
    }
}
