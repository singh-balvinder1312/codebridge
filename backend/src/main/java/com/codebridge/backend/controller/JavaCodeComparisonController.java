
package com.codebridge.backend.controller;

import com.codebridge.backend.codeanalysis.JavaCodeComparisonService;
import com.codebridge.backend.dto.JavaCodeComparisonResponse;
import com.codebridge.backend.dto.MavenComparisonRequest;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/code")
public class JavaCodeComparisonController {

    private final JavaCodeComparisonService comparisonService;

    public JavaCodeComparisonController(
            JavaCodeComparisonService comparisonService) {

        this.comparisonService = comparisonService;
    }

    @PostMapping("/compare")
    public ResponseEntity<JavaCodeComparisonResponse> compare(
            @Valid @RequestBody MavenComparisonRequest request) {

        JavaCodeComparisonResponse response =
                comparisonService.compare(
                        request.existingRepositoryUrl(),
                        request.incomingRepositoryUrl()
                );

        return ResponseEntity.ok(response);
    }
}
