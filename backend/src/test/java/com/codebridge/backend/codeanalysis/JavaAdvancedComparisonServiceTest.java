
package com.codebridge.backend.codeanalysis;

import com.codebridge.backend.dto.JavaCodeComparisonResponse;
import com.codebridge.backend.dto.JavaClassCollision;
import com.codebridge.backend.dto.SpringRouteCollision;
import com.codebridge.backend.github.GitHubJavaSourceClient;
import com.codebridge.backend.github.GitHubJavaTree;
import com.codebridge.backend.github.GitHubJavaTreeClient;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JavaAdvancedComparisonServiceTest {

    private static final String EXISTING_URL =
            "https://github.com/example/backend-a";

    private static final String INCOMING_URL =
            "https://github.com/example/backend-b";

    private static final String HASH_A =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    private static final String HASH_B =
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    private GitHubJavaTreeClient treeClient;
    private GitHubJavaSourceClient sourceClient;
    private JavaCodeComparisonService service;

    @BeforeEach
    void setUp() {
        treeClient = mock(GitHubJavaTreeClient.class);
        sourceClient = mock(GitHubJavaSourceClient.class);

        service = new JavaCodeComparisonService(
                treeClient,
                sourceClient
        );
    }

    @Test
    void shouldDetectClassIdentityAcrossDifferentModules() {

        String existingPath =
                "service-a/src/main/java/com/example/UserService.java";

        String incomingPath =
                "service-b/src/main/java/com/example/UserService.java";

        mockTrees(
                Map.of(existingPath, HASH_A),
                Map.of(incomingPath, HASH_B)
        );

        JavaCodeComparisonResponse result =
                service.compare(EXISTING_URL, INCOMING_URL);

        assertEquals(0, result.matchingPathCount());
        assertEquals(1, result.classCollisions().size());
        assertEquals(1, result.potentialCollisionCount());

        JavaClassCollision collision =
                result.classCollisions().get(0);

        assertEquals(
                "com.example.UserService",
                collision.inferredClassName()
        );

        assertEquals(existingPath, collision.existingPath());
        assertEquals(incomingPath, collision.incomingPath());

        verifyNoInteractions(sourceClient);
    }

    @Test
    void shouldDetectMatchingSpringRoutes() {

        String existingPath =
                "src/main/java/com/example/UserController.java";

        String incomingPath =
                "service/src/main/java/com/example/AccountController.java";

        mockTrees(
                Map.of(existingPath, HASH_A),
                Map.of(incomingPath, HASH_B)
        );

        when(sourceClient.readJavaBlob(
                EXISTING_URL, HASH_A
        )).thenReturn("""
                package com.example;

                @RestController
                @RequestMapping("/api/users")
                public class UserController {
                    @GetMapping("/{id}")
                    public String getUser() {
                        return "user";
                    }
                }
                """);

        when(sourceClient.readJavaBlob(
                INCOMING_URL, HASH_B
        )).thenReturn("""
                package com.example;

                @RestController
                @RequestMapping("/api/users")
                public class AccountController {
                    @GetMapping("/{id}")
                    public String findAccount() {
                        return "account";
                    }
                }
                """);

        JavaCodeComparisonResponse result =
                service.compare(EXISTING_URL, INCOMING_URL);

        assertEquals(1, result.routeCollisions().size());
        assertEquals(1, result.potentialCollisionCount());
        assertEquals(1, result.existingControllersScanned());
        assertEquals(1, result.incomingControllersScanned());
        assertTrue(result.routeScanComplete());

        SpringRouteCollision collision =
                result.routeCollisions().get(0);

        assertEquals("GET", collision.httpMethod());
        assertEquals("/api/users/{id}", collision.route());
        assertEquals(existingPath, collision.existingFile());
        assertEquals(incomingPath, collision.incomingFile());
    }

    @Test
    void shouldNotReportDifferentHttpMethodsAsRouteCollision() {

        String existingPath =
                "src/main/java/com/example/UserController.java";

        String incomingPath =
                "src/main/java/com/example/AccountController.java";

        mockTrees(
                Map.of(existingPath, HASH_A),
                Map.of(incomingPath, HASH_B)
        );

        when(sourceClient.readJavaBlob(
                EXISTING_URL, HASH_A
        )).thenReturn("""
                @RestController
                @RequestMapping("/api/users")
                public class UserController {
                    @GetMapping("/list")
                    public String list() { return ""; }
                }
                """);

        when(sourceClient.readJavaBlob(
                INCOMING_URL, HASH_B
        )).thenReturn("""
                @RestController
                @RequestMapping("/api/users")
                public class AccountController {
                    @PostMapping("/list")
                    public String create() { return ""; }
                }
                """);

        JavaCodeComparisonResponse result =
                service.compare(EXISTING_URL, INCOMING_URL);

        assertTrue(result.routeCollisions().isEmpty());
        assertEquals(0, result.potentialCollisionCount());
    }

    @Test
    void shouldReportIncompleteScanWhenControllerLimitIsExceeded() {

        Map<String, String> existingFiles = new HashMap<>();

        for (int i = 0; i < 9; i++) {
            existingFiles.put(
                    "src/main/java/com/example/Test"
                            + i + "Controller.java",
                    HASH_A
            );
        }

        mockTrees(existingFiles, Map.of());

        when(sourceClient.readJavaBlob(
                EXISTING_URL, HASH_A
        )).thenReturn(
                "public class EmptyController {}"
        );

        JavaCodeComparisonResponse result =
                service.compare(EXISTING_URL, INCOMING_URL);

        assertEquals(8, result.existingControllersScanned());
        assertEquals(0, result.incomingControllersScanned());
        assertFalse(result.routeScanComplete());
    }

    private void mockTrees(
            Map<String, String> existingFiles,
            Map<String, String> incomingFiles
    ) {

        when(treeClient.getJavaTree(EXISTING_URL))
                .thenReturn(new GitHubJavaTree(
                        "example/backend-a",
                        existingFiles
                ));

        when(treeClient.getJavaTree(INCOMING_URL))
                .thenReturn(new GitHubJavaTree(
                        "example/backend-b",
                        incomingFiles
                ));
    }
}
