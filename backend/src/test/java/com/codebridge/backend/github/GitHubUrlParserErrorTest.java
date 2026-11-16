
package com.codebridge.backend.github;

import com.codebridge.backend.exception.InvalidGitHubRepositoryUrlException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GitHubUrlParserErrorTest {

    private final GitHubUrlParser parser =
            new GitHubUrlParser();

    @Test
    void shouldAcceptValidGitHubRepository() {

        GitHubRepository repository =
                parser.parse(
                        "https://github.com/apache/commons-lang"
                );

        assertEquals("apache", repository.owner());
        assertEquals("commons-lang", repository.repository());
    }

    @Test
    void shouldRejectMissingUrl() {

        assertThrows(
                InvalidGitHubRepositoryUrlException.class,
                () -> parser.parse(null)
        );

        assertThrows(
                InvalidGitHubRepositoryUrlException.class,
                () -> parser.parse(" ")
        );
    }

    @Test
    void shouldRejectNonHttpsUrl() {

        assertThrows(
                InvalidGitHubRepositoryUrlException.class,
                () -> parser.parse(
                        "http://github.com/apache/commons-lang"
                )
        );
    }

    @Test
    void shouldRejectUntrustedHost() {

        assertThrows(
                InvalidGitHubRepositoryUrlException.class,
                () -> parser.parse(
                        "https://github.com.evil.example/apache/repo"
                )
        );
    }

    @Test
    void shouldRejectCredentialsInUrl() {

        assertThrows(
                InvalidGitHubRepositoryUrlException.class,
                () -> parser.parse(
                        "https://user:secret@github.com/apache/repo"
                )
        );
    }

    @Test
    void shouldRejectAdditionalPathSegments() {

        assertThrows(
                InvalidGitHubRepositoryUrlException.class,
                () -> parser.parse(
                        "https://github.com/apache/repo/tree/main"
                )
        );
    }

    @Test
    void shouldRejectQueryAndFragment() {

        assertThrows(
                InvalidGitHubRepositoryUrlException.class,
                () -> parser.parse(
                        "https://github.com/apache/repo?token=secret"
                )
        );

        assertThrows(
                InvalidGitHubRepositoryUrlException.class,
                () -> parser.parse(
                        "https://github.com/apache/repo#readme"
                )
        );
    }

    @Test
    void shouldRemainCompatibleWithExistingExceptionTests() {

        assertThrows(
                IllegalArgumentException.class,
                () -> parser.parse(
                        "https://example.com/apache/repo"
                )
        );
    }
}
