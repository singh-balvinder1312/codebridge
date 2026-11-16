
package com.codebridge.backend.maven;

import com.codebridge.backend.dto.MavenComparisonResponse;

import java.util.Objects;

public record MavenAnalysisResult(
        MavenComparisonResponse comparison,
        boolean existingRootPomFound,
        boolean incomingRootPomFound
) {

    public MavenAnalysisResult {
        Objects.requireNonNull(
                comparison,
                "Maven comparison result is required"
        );
    }

    public boolean bothRootPomsFound() {
        return existingRootPomFound && incomingRootPomFound;
    }
}
