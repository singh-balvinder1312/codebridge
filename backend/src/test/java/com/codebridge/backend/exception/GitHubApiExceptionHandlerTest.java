
package com.codebridge.backend.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private GitHubApiExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GitHubApiExceptionHandler();
    }

    @Test
    void shouldReturn400ForInvalidGitHubUrl() {

        InvalidGitHubRepositoryUrlException exception =
                new InvalidGitHubRepositoryUrlException(
                        "Sensitive submitted URL"
                );

        ResponseEntity<ProblemDetail> response =
                handler.handleInvalidGitHubUrl(exception);

        assertEquals(
                HttpStatus.BAD_REQUEST,
                response.getStatusCode()
        );

        assertNotNull(response.getBody());

        assertEquals(
                "INVALID_GITHUB_URL",
                response.getBody()
                        .getProperties()
                        .get("errorCode")
        );

        assertFalse(
                response.getBody()
                        .getDetail()
                        .contains("Sensitive submitted URL")
        );
    }

    @Test
    void shouldReturn404ForMissingGitHubResource() {

        GitHubApiException exception =
                new GitHubApiException(
                        GitHubApiException.Reason.NOT_FOUND,
                        "Private upstream error details"
                );

        ResponseEntity<ProblemDetail> response =
                handler.handleGitHubApiException(exception);

        assertEquals(
                HttpStatus.NOT_FOUND,
                response.getStatusCode()
        );

        assertNotNull(response.getBody());

        assertEquals(
                "NOT_FOUND",
                response.getBody()
                        .getProperties()
                        .get("errorCode")
        );
    }

    @Test
    void shouldReturn429ForGitHubRateLimit() {

        GitHubApiException exception =
                new GitHubApiException(
                        GitHubApiException.Reason.RATE_LIMITED,
                        "Private GitHub rate-limit details"
                );

        ResponseEntity<ProblemDetail> response =
                handler.handleGitHubApiException(exception);

        assertEquals(
                HttpStatus.TOO_MANY_REQUESTS,
                response.getStatusCode()
        );

        assertNotNull(response.getBody());

        assertEquals(
                "RATE_LIMITED",
                response.getBody()
                        .getProperties()
                        .get("errorCode")
        );
    }

    @Test
    void shouldReturn502ForGitHubApiFailure() {

        GitHubApiException exception =
                new GitHubApiException(
                        GitHubApiException.Reason.UPSTREAM_ERROR,
                        "Private upstream response"
                );

        ResponseEntity<ProblemDetail> response =
                handler.handleGitHubApiException(exception);

        assertEquals(
                HttpStatus.BAD_GATEWAY,
                response.getStatusCode()
        );

        assertNotNull(response.getBody());

        assertEquals(
                "UPSTREAM_ERROR",
                response.getBody()
                        .getProperties()
                        .get("errorCode")
        );
    }

    @Test
    void shouldNotExposeSensitiveExceptionMessages() {

        String secret = "example-sensitive-token";

        GitHubApiException exception =
                new GitHubApiException(
                        GitHubApiException.Reason.UPSTREAM_ERROR,
                        "Authorization: Bearer " + secret
                );

        ResponseEntity<ProblemDetail> response =
                handler.handleGitHubApiException(exception);

        assertNotNull(response.getBody());

        assertFalse(
                response.getBody()
                        .getTitle()
                        .contains(secret)
        );

        assertFalse(
                response.getBody()
                        .getDetail()
                        .contains(secret)
        );

        assertFalse(
                response.getBody()
                        .getProperties()
                        .toString()
                        .contains(secret)
        );
    }
}
