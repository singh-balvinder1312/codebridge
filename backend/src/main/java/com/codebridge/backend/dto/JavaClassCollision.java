
package com.codebridge.backend.dto;

public record JavaClassCollision(
        String inferredClassName,
        String existingPath,
        String incomingPath,
        String explanation
) {
}
