
package com.codebridge.backend.github;

import com.codebridge.backend.exception.GitHubApiException;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;

@Service
public class GitHubConfigurationClient {

    public static final String RESOURCES_PATH =
            "src/main/resources/application.properties";

    public static final String ROOT_PATH =
            "application.properties";

    private static final Set<String> ALLOWED_PATHS =
            Set.of(RESOURCES_PATH, ROOT_PATH);

    private static final int MAX_FILE_BYTES = 128 * 1024;
    private static final int MAX_RESPONSE_BYTES = 256 * 1024;

    private final GitHubUrlParser urlParser = new GitHubUrlParser();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient;

    public GitHubConfigurationClient() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build());
    }

    // Allows a mock HTTP client to be supplied in unit tests.
    GitHubConfigurationClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public Optional<String> getPropertiesFile(
            String repositoryUrl,
            String path) {

        if (!ALLOWED_PATHS.contains(path)) {
            throw new IllegalArgumentException(
                    "Unsupported configuration file path"
            );
        }

        GitHubRepository repository = urlParser.parse(repositoryUrl);

        String apiUrl = "https://api.github.com/repos/"
                + repository.owner()
                + "/"
                + repository.repository()
                + "/contents/"
                + path;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .GET()
                .build();

        try {
            HttpResponse<InputStream> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofInputStream()
            );

            try (InputStream body = response.body()) {

                int status = response.statusCode();

                // The file is not present at this location.
                if (status == 404) {
                    return Optional.empty();
                }

                if (status == 429
                        || (status == 403 && (
                        "0".equals(response.headers()
                                .firstValue("X-RateLimit-Remaining")
                                .orElse(null))
                                || response.headers()
                                .firstValue("Retry-After")
                                .isPresent()
                ))) {
                    throw new GitHubApiException(
                            GitHubApiException.Reason.RATE_LIMITED,
                            "GitHub API rate limit reached"
                    );
                }

                if (status != 200) {
                    throw new GitHubApiException(
                            GitHubApiException.Reason.UPSTREAM_ERROR,
                            "Unable to retrieve GitHub configuration file"
                    );
                }

                byte[] responseBytes =
                        body.readNBytes(MAX_RESPONSE_BYTES + 1);

                if (responseBytes.length > MAX_RESPONSE_BYTES) {
                    throw upstreamError(
                            "GitHub configuration response is too large"
                    );
                }

                JsonNode json = objectMapper.readTree(responseBytes);

                if (json == null
                        || !"file".equals(json.path("type").asText())
                        || !"base64".equals(json.path("encoding").asText())
                        || !path.equals(json.path("path").asText())) {

                    throw upstreamError(
                            "Unexpected GitHub configuration response"
                    );
                }

                long reportedSize = json.path("size").asLong(-1);

                if (reportedSize < 0
                        || reportedSize > MAX_FILE_BYTES) {
                    throw upstreamError(
                            "GitHub configuration file exceeds size limit"
                    );
                }

                byte[] fileBytes = Base64.getMimeDecoder().decode(
                        json.path("content").asText("")
                );

                if (fileBytes.length > MAX_FILE_BYTES) {
                    throw upstreamError(
                            "GitHub configuration file exceeds size limit"
                    );
                }

                return Optional.of(
                        new String(fileBytes, StandardCharsets.UTF_8)
                );
            }

        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();

            throw upstreamError(
                    "GitHub configuration request was interrupted"
            );

        } catch (IOException | IllegalArgumentException exception) {
            throw upstreamError(
                    "Unable to read GitHub configuration response"
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
