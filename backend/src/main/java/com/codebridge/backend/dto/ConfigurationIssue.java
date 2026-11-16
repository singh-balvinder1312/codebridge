
package com.codebridge.backend.dto;

public record ConfigurationIssue(
        String key,
        String existingFile,
        String incomingFile,
        String classification,
        String explanation
) {
}
