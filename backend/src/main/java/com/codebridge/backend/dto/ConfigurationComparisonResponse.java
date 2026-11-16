
package com.codebridge.backend.dto;

import java.util.List;

public record ConfigurationComparisonResponse(
        String existingRepository,
        String incomingRepository,
        List<String> existingFilesFound,
        List<String> incomingFilesFound,
        int comparedKeyCount,
        int reviewCount,
        int informationalCount,
        List<ConfigurationIssue> issues
) {

    public ConfigurationComparisonResponse {
        existingFilesFound = List.copyOf(existingFilesFound);
        incomingFilesFound = List.copyOf(incomingFilesFound);
        issues = List.copyOf(issues);

        if (comparedKeyCount < 0
                || reviewCount < 0
                || informationalCount < 0) {
            throw new IllegalArgumentException(
                    "Comparison counts cannot be negative"
            );
        }

        if (reviewCount + informationalCount != issues.size()) {
            throw new IllegalArgumentException(
                    "Issue counts must match the issue list"
            );
        }
    }
}
