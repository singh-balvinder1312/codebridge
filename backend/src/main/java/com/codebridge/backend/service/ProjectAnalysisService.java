
package com.codebridge.backend.service;

import com.codebridge.backend.compatibility.CompatibilityComparisonService;

import com.codebridge.backend.dto.CompatibilityComparisonResponse;
import com.codebridge.backend.dto.ProjectAnalysisHistoryDetail;
import com.codebridge.backend.dto.ProjectAnalysisHistoryItem;
import com.codebridge.backend.dto.ProjectAnalysisResponse;

import com.codebridge.backend.entity.Project;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProjectAnalysisService {

    private final ProjectAnalysisTransactionService transactions;
    private final CompatibilityComparisonService compatibilityService;

    public ProjectAnalysisService(
            ProjectAnalysisTransactionService transactions,
            CompatibilityComparisonService compatibilityService
    ) {
        this.transactions = transactions;
        this.compatibilityService = compatibilityService;
    }

    public ProjectAnalysisResponse analyze(Long projectId) {

        Project project = transactions.begin(projectId);

        try {
            CompatibilityComparisonResponse report =
                    compatibilityService.compare(
                            project.getExistingRepoUrl(),
                            project.getIncomingRepoUrl()
                    );

            return transactions.complete(projectId, report);

        } catch (RuntimeException exception) {

            try {
                transactions.fail(projectId);
            } catch (RuntimeException statusException) {
                exception.addSuppressed(statusException);
            }

            throw exception;
        }
    }

    public ProjectAnalysisResponse getSaved(Long projectId) {
        return transactions.getSaved(projectId);
    }

    public List<ProjectAnalysisHistoryItem> getHistory(
            Long projectId
    ) {
        return transactions.getHistory(projectId);
    }

    public ProjectAnalysisHistoryDetail getHistoricalReport(
            Long projectId,
            Long analysisId
    ) {
        return transactions.getHistoricalReport(
                projectId,
                analysisId
        );
    }
}
