
package com.codebridge.backend.dto;

public record SpringRouteCollision(
        String httpMethod,
        String route,
        String existingFile,
        String incomingFile,
        String explanation
) {
}
