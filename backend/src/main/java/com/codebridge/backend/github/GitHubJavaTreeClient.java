
package com.codebridge.backend.github;

import com.codebridge.backend.exception.GitHubApiException;

import org.springframework.stereotype.Service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class GitHubJavaTreeClient {

    private static final int MAX_METADATA_BYTES = 128 * 1024;
    private static final int MAX_TREE_BYTES = 2 * 1024 * 1024;
    private static final int MAX_TREE_ENTRIES = 15000;

    private static final Pattern SAFE_BRANCH =
            Pattern.compile("^[A-Za-z0-9._/-]{1,200}$");

    private static final Pattern GIT_SHA =
            Pattern.compile("^[a-fA-F0-9]{40,64}$");

    private final GitHubUrlParser urlParser = new GitHubUrlParser();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient;

    public GitHubJavaTreeClient() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build());
    }

    // Constructor for offline HTTP client tests.
    GitHubJavaTreeClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public GitHubJavaTree getJavaTree(String repositoryUrl) {

        GitHubRepository repository = urlParser.parse(repositoryUrl);

        String baseUrl = "https://api.github.com/repos/"
                + repository.owner()
                + "/"
                + repository.repository();

        JsonNode metadata = fetchJson(
                URI.create(baseUrl),
                MAX_METADATA_BYTES
        );

        String defaultBranch =
                metadata.path("default_branch").asText("");

        if (!SAFE_BRANCH.matcher(defaultBranch).matches()
                || defaultBranch.equals(".")
                || defaultBranch.equals("..")
                || defaultBranch.contains("..")
                || defaultBranch.startsWith("/")
                || defaultBranch.endsWith("/")) {

            throw upstreamError(
                    "GitHub returned an unsupported default branch"
            );
        }

        String encodedBranch = URLEncoder.encode(
                defaultBranch,
                StandardCharsets.UTF_8
        ).replace("+", "%20");

        URI treeUri = URI.create(
                baseUrl
                        + "/git/trees/"
                        + encodedBranch
                        + "?recursive=1"
        );

        JsonNode treeJson = fetchJson(treeUri, MAX_TREE_BYTES);

        JsonNode truncatedNode = treeJson.path("truncated");

        if (!truncatedNode.isBoolean()) {
            throw upstreamError(
                    "GitHub did not report tree completeness"
            );
        }

        if (truncatedNode.asBoolean()) {
            throw upstreamError(
                    "GitHub repository tree was truncated; "
                            + "source comparison cannot be completed safely"
            );
        }

        JsonNode entries = treeJson.path("tree");

        if (!entries.isArray()) {
            throw upstreamError(
                    "Invalid GitHub repository tree response"
            );
        }

        Map<String, String> javaFiles = new HashMap<>();

        int entryCount = 0;

        for (JsonNode entry : entries) {

            entryCount++;

            if (entryCount > MAX_TREE_ENTRIES) {
                throw upstreamError(
                        "Repository tree exceeds the supported limit"
                );
            }

            if (!"blob".equals(entry.path("type").asText())) {
                continue;
            }

            String path = entry.path("path").asText("");

            if (!isJavaSourcePath(path)) {
                continue;
            }

            String sha = entry.path("sha").asText("");

            if (!GIT_SHA.matcher(sha).matches()) {
                throw upstreamError(
                        "Invalid GitHub Java file identifier"
                );
            }

            if (javaFiles.putIfAbsent(path, sha) != null) {
                throw upstreamError(
                        "Duplicate Java file path in GitHub tree"
                );
            }
        }

        return new GitHubJavaTree(
                repository.owner()
                        + "/"
                        + repository.repository(),
                javaFiles
        );
    }

    private boolean isJavaSourcePath(String path) {

        // Include root-level Java source files and source
        // files in Maven-style submodules.
        return path.endsWith(".java")
                && (
                path.startsWith("src/main/java/")
                        || path.contains("/src/main/java/")
        )
                && !path.contains("..")
                && !path.startsWith("/");
    }

    private JsonNode fetchJson(
            URI uri,
            int maxBytes) {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .GET()
                .build();

        try {
            HttpResponse<InputStream> response =
                    httpClient.send(
                            request,
                            HttpResponse.BodyHandlers.ofInputStream()
                    );

            try (InputStream body = response.body()) {

                int status = response.statusCode();

                if (status == 404) {
                    throw new GitHubApiException(
                            GitHubApiException.Reason.NOT_FOUND,
                            "GitHub repository or Git tree not found"
                    );
                }

                boolean rateLimited =
                        status == 429
                                || (
                                status == 403
                                        && (
                                        "0".equals(
                                                response.headers()
                                                        .firstValue(
                                                                "X-RateLimit-Remaining"
                                                        )
                                                        .orElse(null)
                                        )
                                                || response.headers()
                                                .firstValue("Retry-After")
                                                .isPresent()
                                )
                        );

                if (rateLimited) {
                    throw new GitHubApiException(
                            GitHubApiException.Reason.RATE_LIMITED,
                            "GitHub API rate limit reached"
                    );
                }

                if (status != 200) {
                    throw upstreamError(
                            "Unable to retrieve GitHub repository tree"
                    );
                }

                byte[] bytes = body.readNBytes(maxBytes + 1);

                if (bytes.length > maxBytes) {
                    throw upstreamError(
                            "GitHub response exceeds the allowed size"
                    );
                }

                JsonNode json = objectMapper.readTree(bytes);

                if (json == null || !json.isObject()) {
                    throw upstreamError(
                            "Unexpected GitHub JSON response"
                    );
                }

                return json;
            }

        } catch (InterruptedException exception) {

            Thread.currentThread().interrupt();

            throw upstreamError(
                    "GitHub repository request was interrupted"
            );

        } catch (IOException | IllegalArgumentException exception) {

            throw upstreamError(
                    "Unable to read GitHub repository response"
            );
        }
    }

    private GitHubApiException upstreamError(String message) {
        return new GitHubApiException(
                GitHubApiException.Reason.UPSTREAM_ERROR,
                message
        );
    }
}
