
package com.codebridge.backend.controller;

import com.codebridge.backend.configanalysis.ConfigurationComparisonService;
import com.codebridge.backend.dto.ConfigurationComparisonRequest;
import com.codebridge.backend.dto.ConfigurationComparisonResponse;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/config")
public class ConfigurationComparisonController {

    private final ConfigurationComparisonService comparisonService;

    public ConfigurationComparisonController(
            ConfigurationComparisonService comparisonService) {

        this.comparisonService = comparisonService;
    }

    @PostMapping("/compare")
    public ResponseEntity<ConfigurationComparisonResponse> compare(
            @Valid @RequestBody ConfigurationComparisonRequest request) {

        ConfigurationComparisonResponse result =
                comparisonService.compare(
                        request.existingRepositoryUrl(),
                        request.incomingRepositoryUrl()
                );

        return ResponseEntity.ok(result);
    }
}
