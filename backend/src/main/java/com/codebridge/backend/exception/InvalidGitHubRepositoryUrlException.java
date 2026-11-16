package com.codebridge.backend.exception;

public class InvalidGitHubRepositoryUrlException
        extends IllegalArgumentException {

    public InvalidGitHubRepositoryUrlException(
            String message
    ) {
        super(message);
    }

    public InvalidGitHubRepositoryUrlException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}
