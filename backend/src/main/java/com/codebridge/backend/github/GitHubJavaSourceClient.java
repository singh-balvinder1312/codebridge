
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
import java.util.regex.Pattern;

@Service
public class GitHubJavaSourceClient {

    private static final int MAX_SOURCE_BYTES = 96 * 1024;
    private static final int MAX_RESPONSE_BYTES = 160 * 1024;

    private static final Pattern SHA_PATTERN =
            Pattern.compile("[a-fA-F0-9]{40,64}");

    private final GitHubUrlParser urlParser = new GitHubUrlParser();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient;

    public GitHubJavaSourceClient() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build());
    }

    GitHubJavaSourceClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public String readJavaBlob(String repositoryUrl, String sha) {

        GitHubRepository repository = urlParser.parse(repositoryUrl);

        if (sha == null || !SHA_PATTERN.matcher(sha).matches()) {
            throw new IllegalArgumentException(
                    "Invalid GitHub blob identifier"
            );
        }

        URI uri = URI.create(
                "https://api.github.com/repos/"
                        + repository.owner()
                        + "/"
                        + repository.repository()
                        + "/git/blobs/"
                        + sha
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
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

                if (status == 429
                        || (status == 403 && (
                        "0".equals(response.headers()
                                .firstValue("X-RateLimit-Remaining")
                                .orElse(null))
                                || response.headers()
                                .firstValue("Retry-After")
                                .isPresent()
                ))) {
                    throw error(
                            GitHubApiException.Reason.RATE_LIMITED,
                            "GitHub API rate limit reached"
                    );
                }

                if (status != 200) {
                    throw error(
                            GitHubApiException.Reason.UPSTREAM_ERROR,
                            "Unable to retrieve Java source"
                    );
                }

                byte[] responseBytes =
                        body.readNBytes(MAX_RESPONSE_BYTES + 1);

                if (responseBytes.length > MAX_RESPONSE_BYTES) {
                    throw error(
                            GitHubApiException.Reason.UPSTREAM_ERROR,
                            "GitHub source response is too large"
                    );
                }

                JsonNode json = objectMapper.readTree(responseBytes);

                if (json == null
                        || !json.isObject()
                        || !"base64".equals(
                        json.path("encoding").asText())
                        || !sha.equalsIgnoreCase(
                        json.path("sha").asText())) {
                    throw error(
                            GitHubApiException.Reason.UPSTREAM_ERROR,
                            "Unexpected GitHub source response"
                    );
                }

                long reportedSize = json.path("size").asLong(-1);

                if (reportedSize < 0
                        || reportedSize > MAX_SOURCE_BYTES) {
                    throw error(
                            GitHubApiException.Reason.UPSTREAM_ERROR,
                            "Java source exceeds the allowed size"
                    );
                }

                byte[] sourceBytes = Base64.getMimeDecoder().decode(
                        json.path("content").asText("")
                );

                if (sourceBytes.length != reportedSize
                        || sourceBytes.length > MAX_SOURCE_BYTES) {
                    throw error(
                            GitHubApiException.Reason.UPSTREAM_ERROR,
                            "Invalid GitHub source size"
                    );
                }

                return new String(
                        sourceBytes,
                        StandardCharsets.UTF_8
                );
            }

        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();

            throw error(
                    GitHubApiException.Reason.UPSTREAM_ERROR,
                    "Java source request interrupted"
            );

        } catch (IOException | IllegalArgumentException exception) {
            throw error(
                    GitHubApiException.Reason.UPSTREAM_ERROR,
                    "Unable to read GitHub Java source"
            );
        }
    }

    private GitHubApiException error(
            GitHubApiException.Reason reason,
            String message) {

        return new GitHubApiException(reason, message);
    }
}
