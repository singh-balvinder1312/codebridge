
package com.codebridge.backend.controller;

import com.codebridge.backend.compatibility.CompatibilityComparisonService;
import com.codebridge.backend.dto.CompatibilityComparisonResponse;
import com.codebridge.backend.dto.MavenComparisonRequest;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/compatibility")
public class CompatibilityComparisonController {

    private final CompatibilityComparisonService comparisonService;

    public CompatibilityComparisonController(
            CompatibilityComparisonService comparisonService) {

        this.comparisonService = comparisonService;
    }

    @PostMapping("/compare")
    public ResponseEntity<CompatibilityComparisonResponse> compare(
            @Valid @RequestBody MavenComparisonRequest request) {

        return ResponseEntity.ok(
                comparisonService.compare(
                        request.existingRepositoryUrl(),
                        request.incomingRepositoryUrl()
                )
        );
    }
}
