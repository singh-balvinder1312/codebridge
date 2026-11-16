
package com.codebridge.backend.service;

import com.codebridge.backend.dto.CompatibilityComparisonResponse;
import com.codebridge.backend.dto.ProjectAnalysisHistoryDetail;
import com.codebridge.backend.dto.ProjectAnalysisHistoryItem;
import com.codebridge.backend.dto.ProjectAnalysisResponse;

import com.codebridge.backend.entity.Project;
import com.codebridge.backend.entity.ProjectAnalysisHistory;
import com.codebridge.backend.entity.ProjectAnalysisReport;
import com.codebridge.backend.entity.ProjectStatus;

import com.codebridge.backend.repository.ProjectAnalysisHistoryRepository;
import com.codebridge.backend.repository.ProjectAnalysisReportRepository;
import com.codebridge.backend.repository.ProjectRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class ProjectAnalysisTransactionService {

    private static final int MAX_SAVED_REPORT_BYTES =
            2 * 1024 * 1024;

    private final ProjectRepository projectRepository;
    private final ProjectAnalysisReportRepository reportRepository;
    private final ProjectAnalysisHistoryRepository historyRepository;
    private final ObjectMapper objectMapper;

    public ProjectAnalysisTransactionService(
            ProjectRepository projectRepository,
            ProjectAnalysisReportRepository reportRepository,
            ProjectAnalysisHistoryRepository historyRepository,
            ObjectMapper objectMapper
    ) {
        this.projectRepository = projectRepository;
        this.reportRepository = reportRepository;
        this.historyRepository = historyRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Project begin(Long projectId) {

        Project project = findProject(projectId);

        int claimed = projectRepository.claimForAnalysis(
                projectId,
                ProjectStatus.ANALYZING
        );

        if (claimed != 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "An analysis is already running for this project"
            );
        }

        // IMPORTANT:
        // Do not delete the last successful report.
        //
        // It remains available while the new analysis runs
        // and if the new analysis fails.
        //
        // The conditional status update above is committed
        // before external GitHub requests begin.

        return project;
    }

    @Transactional
    public ProjectAnalysisResponse complete(
            Long projectId,
            CompatibilityComparisonResponse report
    ) {

        String json = objectMapper.writeValueAsString(report);

        if (json.getBytes(StandardCharsets.UTF_8).length
                > MAX_SAVED_REPORT_BYTES) {

            throw new IllegalStateException(
                    "Compatibility report exceeds storage size limit"
            );
        }

        Project project = findProject(projectId);

        if (project.getStatus() != ProjectStatus.ANALYZING) {
            throw new IllegalStateException(
                    "Project is not currently being analyzed"
            );
        }

        Instant analyzedAt = Instant.now();

        // Upsert the latest successful report.
        // Existing project_id: update the existing row.
        // New project_id: insert a new row.
        //
        // This happens only AFTER analysis succeeds.
        reportRepository.save(new ProjectAnalysisReport(
                projectId,
                json,
                analyzedAt
        ));

        // Append a new history entry for this successful run.
        historyRepository.save(new ProjectAnalysisHistory(
                projectId,
                json,
                analyzedAt
        ));

        project.setStatus(ProjectStatus.COMPLETED);
        projectRepository.save(project);

        // The latest report, history entry, and project status
        // are saved within the same transaction.
        return new ProjectAnalysisResponse(
                projectId,
                ProjectStatus.COMPLETED,
                analyzedAt,
                report
        );
    }

    @Transactional
    public void fail(Long projectId) {

        Project project = findProject(projectId);

        if (project.getStatus() == ProjectStatus.ANALYZING) {
            project.setStatus(ProjectStatus.FAILED);
            projectRepository.save(project);
        }

        // Never delete the latest successful report.
        // Never delete historical reports.
    }

    @Transactional(readOnly = true)
    public ProjectAnalysisResponse getSaved(Long projectId) {

        // Confirm the project still exists.
        findProject(projectId);

        ProjectAnalysisReport saved =
                reportRepository.findById(projectId)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "No successful analysis exists "
                                                + "for this project"
                                )
                        );

        CompatibilityComparisonResponse report =
                deserializeReport(saved.getReportJson());

        // This status describes the saved successful report,
        // not the project's latest processing attempt.
        //
        // A newer failed attempt does not make the older
        // successful report a failed analysis.
        return new ProjectAnalysisResponse(
                projectId,
                ProjectStatus.COMPLETED,
                saved.getAnalyzedAt(),
                report
        );
    }

    @Transactional(readOnly = true)
    public List<ProjectAnalysisHistoryItem> getHistory(
            Long projectId
    ) {

        findProject(projectId);

        List<ProjectAnalysisHistory> records =
                historyRepository
                        .findByProjectIdOrderByAnalyzedAtDescIdDesc(
                                projectId
                        );

        List<ProjectAnalysisHistoryItem> items =
                new ArrayList<>();

        for (ProjectAnalysisHistory record : records) {

            CompatibilityComparisonResponse report =
                    deserializeReport(record.getReportJson());

            items.add(new ProjectAnalysisHistoryItem(
                    record.getId(),
                    record.getProjectId(),
                    record.getAnalyzedAt(),
                    report.assessment(),
                    report.coverage()
            ));
        }

        return List.copyOf(items);
    }

    @Transactional(readOnly = true)
    public ProjectAnalysisHistoryDetail getHistoricalReport(
            Long projectId,
            Long analysisId
    ) {

        findProject(projectId);

        ProjectAnalysisHistory saved =
                historyRepository
                        .findByIdAndProjectId(
                                analysisId,
                                projectId
                        )
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Historical analysis not found"
                                )
                        );

        CompatibilityComparisonResponse report =
                deserializeReport(saved.getReportJson());

        return new ProjectAnalysisHistoryDetail(
                saved.getId(),
                projectId,
                ProjectStatus.COMPLETED,
                saved.getAnalyzedAt(),
                report
        );
    }

    private CompatibilityComparisonResponse deserializeReport(
            String json
    ) {

        return objectMapper.readValue(
                json,
                CompatibilityComparisonResponse.class
        );
    }

    private Project findProject(Long projectId) {

        return projectRepository.findById(projectId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Project not found"
                        )
                );
    }
}
