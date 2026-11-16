
package com.codebridge.backend.dto;

import java.util.Objects;

public record CompatibilityRecommendation(
        String category,
        String priority,
        String finding,
        String recommendedAction
) {

    public CompatibilityRecommendation {
        Objects.requireNonNull(category, "Category is required");
        Objects.requireNonNull(priority, "Priority is required");
        Objects.requireNonNull(finding, "Finding is required");
        Objects.requireNonNull(
                recommendedAction,
                "Recommended action is required"
        );

        if (category.isBlank()
                || priority.isBlank()
                || finding.isBlank()
                || recommendedAction.isBlank()) {
            throw new IllegalArgumentException(
                    "Recommendation fields cannot be blank"
            );
        }
    }
}
