
package com.codebridge.backend.github;

import java.util.Map;
import java.util.Objects;

public record GitHubJavaTree(
        String repository,
        Map<String, String> javaFiles
) {

    public GitHubJavaTree {
        Objects.requireNonNull(
                repository,
                "Repository name is required"
        );

        Objects.requireNonNull(
                javaFiles,
                "Java files are required"
        );

        javaFiles = Map.copyOf(javaFiles);
    }
}
