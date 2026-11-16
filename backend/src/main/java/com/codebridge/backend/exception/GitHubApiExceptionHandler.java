
package com.codebridge.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GitHubApiExceptionHandler {

    @ExceptionHandler(
            InvalidGitHubRepositoryUrlException.class
    )
    public ResponseEntity<ProblemDetail> handleInvalidGitHubUrl(
            InvalidGitHubRepositoryUrlException exception
    ) {

        return createResponse(
                HttpStatus.BAD_REQUEST,
                "Invalid GitHub repository URL",
                "Provide a valid HTTPS GitHub repository URL "
                        + "in the format "
                        + "https://github.com/owner/repository.",
                "INVALID_GITHUB_URL"
        );
    }

    @ExceptionHandler(GitHubApiException.class)
    public ResponseEntity<ProblemDetail> handleGitHubApiException(
            GitHubApiException exception
    ) {

        return switch (exception.getReason()) {

            case NOT_FOUND -> createResponse(
                    HttpStatus.NOT_FOUND,
                    "GitHub resource not found",
                    "The requested GitHub resource "
                            + "could not be found or accessed.",
                    "NOT_FOUND"
            );

            case RATE_LIMITED -> createResponse(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "GitHub API rate limit exceeded",
                    "GitHub has temporarily limited API requests. "
                            + "Please retry later.",
                    "RATE_LIMITED"
            );

            case UPSTREAM_ERROR -> createResponse(
                    HttpStatus.BAD_GATEWAY,
                    "GitHub API error",
                    "CodeBridge could not complete its "
                            + "request to GitHub. Please retry later.",
                    "UPSTREAM_ERROR"
            );
        };
    }

    private ResponseEntity<ProblemDetail> createResponse(
            HttpStatus status,
            String title,
            String detail,
            String errorCode
    ) {

        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(
                        status,
                        detail
                );

        problem.setTitle(title);
        problem.setProperty("errorCode", errorCode);

        // Deliberately exclude exception messages,
        // upstream response bodies, raw URLs, and
        // authentication information.

        return ResponseEntity
                .status(status)
                .body(problem);
    }
}
