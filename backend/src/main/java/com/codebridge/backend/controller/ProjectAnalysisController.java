
package com.codebridge.backend.controller;

import com.codebridge.backend.dto.ProjectAnalysisHistoryDetail;
import com.codebridge.backend.dto.ProjectAnalysisHistoryItem;
import com.codebridge.backend.dto.ProjectAnalysisResponse;

import com.codebridge.backend.service.ProjectAnalysisService;

import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}")
public class ProjectAnalysisController {

    private final ProjectAnalysisService analysisService;

    public ProjectAnalysisController(
            ProjectAnalysisService analysisService
    ) {
        this.analysisService = analysisService;
    }

    @PostMapping("/analyze")
    public ResponseEntity<ProjectAnalysisResponse> analyze(
            @PathVariable Long projectId
    ) {
        return ResponseEntity.ok(
                analysisService.analyze(projectId)
        );
    }

    @GetMapping("/analysis")
    public ResponseEntity<ProjectAnalysisResponse> getAnalysis(
            @PathVariable Long projectId
    ) {
        return ResponseEntity.ok(
                analysisService.getSaved(projectId)
        );
    }

    @GetMapping("/analyses")
    public ResponseEntity<List<ProjectAnalysisHistoryItem>> getHistory(
            @PathVariable Long projectId
    ) {
        return ResponseEntity.ok(
                analysisService.getHistory(projectId)
        );
    }

    @GetMapping("/analyses/{analysisId}")
    public ResponseEntity<ProjectAnalysisHistoryDetail>
    getHistoricalReport(
            @PathVariable Long projectId,
            @PathVariable Long analysisId
    ) {
        return ResponseEntity.ok(
                analysisService.getHistoricalReport(
                        projectId,
                        analysisId
                )
        );
    }
}
