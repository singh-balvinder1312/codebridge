
package com.codebridge.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record ConfigurationComparisonRequest(
        @NotBlank String existingRepositoryUrl,
        @NotBlank String incomingRepositoryUrl
) {
}
