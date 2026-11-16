
package com.codebridge.backend.service;

import com.codebridge.backend.compatibility.AnalysisCoverage;
import com.codebridge.backend.compatibility.CompatibilityAssessment;

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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProjectAnalysisTransactionServiceTest {

    private static final Long PROJECT_ID = 1L;
    private static final Long ANALYSIS_ID = 10L;

    private static final String REPORT_JSON =
            "{\"assessment\":\"NO_ISSUES_DETECTED\"}";

    private static final Instant ANALYZED_AT =
            Instant.parse("2026-11-10T12:00:00Z");

    private ProjectRepository projectRepository;
    private ProjectAnalysisReportRepository reportRepository;
    private ProjectAnalysisHistoryRepository historyRepository;
    private ObjectMapper objectMapper;

    private ProjectAnalysisTransactionService service;

    @BeforeEach
    void setUp() {

        projectRepository = mock(ProjectRepository.class);

        reportRepository =
                mock(ProjectAnalysisReportRepository.class);

        historyRepository =
                mock(ProjectAnalysisHistoryRepository.class);

        objectMapper = mock(ObjectMapper.class);

        service = new ProjectAnalysisTransactionService(
                projectRepository,
                reportRepository,
                historyRepository,
                objectMapper
        );
    }

    @Test
    void shouldClaimProjectWithoutDeletingPreviousReport() {

        Project project = new Project();

        when(projectRepository.findById(PROJECT_ID))
                .thenReturn(Optional.of(project));

        when(projectRepository.claimForAnalysis(
                PROJECT_ID,
                ProjectStatus.ANALYZING
        )).thenReturn(1);

        assertSame(project, service.begin(PROJECT_ID));

        verify(reportRepository, never())
                .deleteById(anyLong());

        verifyNoInteractions(reportRepository);
        verifyNoInteractions(historyRepository);
    }

    @Test
    void shouldRejectSimultaneousAnalysis() {

        when(projectRepository.findById(PROJECT_ID))
                .thenReturn(Optional.of(new Project()));

        when(projectRepository.claimForAnalysis(
                PROJECT_ID,
                ProjectStatus.ANALYZING
        )).thenReturn(0);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.begin(PROJECT_ID)
        );

        assertEquals(
                HttpStatus.CONFLICT,
                exception.getStatusCode()
        );

        verifyNoInteractions(reportRepository);
        verifyNoInteractions(historyRepository);
    }

    @Test
    void shouldRejectUnknownProject() {

        when(projectRepository.findById(PROJECT_ID))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.begin(PROJECT_ID)
        );

        assertEquals(
                HttpStatus.NOT_FOUND,
                exception.getStatusCode()
        );
    }

    @Test
    void shouldSaveBothLatestAndHistoricalReport() {

        Project project = new Project();
        project.setStatus(ProjectStatus.ANALYZING);

        CompatibilityComparisonResponse report = mockReport();

        when(projectRepository.findById(PROJECT_ID))
                .thenReturn(Optional.of(project));

        when(objectMapper.writeValueAsString(report))
                .thenReturn(REPORT_JSON);

        ProjectAnalysisResponse response =
                service.complete(PROJECT_ID, report);

        assertEquals(
                ProjectStatus.COMPLETED,
                response.status()
        );

        assertEquals(
                ProjectStatus.COMPLETED,
                project.getStatus()
        );

        assertSame(report, response.report());

        verify(reportRepository).save(
                any(ProjectAnalysisReport.class)
        );

        verify(historyRepository).save(
                any(ProjectAnalysisHistory.class)
        );

        verify(projectRepository).save(project);

        verify(reportRepository, never())
                .deleteById(anyLong());
    }

    @Test
    void shouldPreserveBothReportsWhenAnalysisFails() {

        Project project = new Project();
        project.setStatus(ProjectStatus.ANALYZING);

        when(projectRepository.findById(PROJECT_ID))
                .thenReturn(Optional.of(project));

        service.fail(PROJECT_ID);

        assertEquals(
                ProjectStatus.FAILED,
                project.getStatus()
        );

        verify(projectRepository).save(project);

        verifyNoInteractions(reportRepository);
        verifyNoInteractions(historyRepository);
    }

    @Test
    void shouldPreserveLatestReportWhenReanalysisBegins() {

        Project project = new Project();
        project.setStatus(ProjectStatus.COMPLETED);

        when(projectRepository.findById(PROJECT_ID))
                .thenReturn(Optional.of(project));

        when(projectRepository.claimForAnalysis(
                PROJECT_ID,
                ProjectStatus.ANALYZING
        )).thenReturn(1);

        service.begin(PROJECT_ID);

        // An existing successful report must remain stored
        // until a later analysis completes successfully.
        verifyNoInteractions(reportRepository);
        verifyNoInteractions(historyRepository);
    }

    @Test
    void shouldReturnCompletedStatusForSavedReportAfterFailure() {

        Project project = new Project();
        project.setStatus(ProjectStatus.FAILED);

        ProjectAnalysisReport saved =
                new ProjectAnalysisReport(
                        PROJECT_ID,
                        REPORT_JSON,
                        ANALYZED_AT
                );

        CompatibilityComparisonResponse report = mockReport();

        when(projectRepository.findById(PROJECT_ID))
                .thenReturn(Optional.of(project));

        when(reportRepository.findById(PROJECT_ID))
                .thenReturn(Optional.of(saved));

        when(objectMapper.readValue(
                REPORT_JSON,
                CompatibilityComparisonResponse.class
        )).thenReturn(report);

        ProjectAnalysisResponse response =
                service.getSaved(PROJECT_ID);

        assertEquals(PROJECT_ID, response.projectId());

        assertEquals(
                ProjectStatus.COMPLETED,
                response.status()
        );

        assertEquals(ANALYZED_AT, response.analyzedAt());
        assertSame(report, response.report());

        // The project retains the status of its latest attempt.
        assertEquals(
                ProjectStatus.FAILED,
                project.getStatus()
        );
    }

    @Test
    void shouldReturnCompletedStatusForSavedReportWhileAnalyzing() {

        Project project = new Project();
        project.setStatus(ProjectStatus.ANALYZING);

        ProjectAnalysisReport saved =
                new ProjectAnalysisReport(
                        PROJECT_ID,
                        REPORT_JSON,
                        ANALYZED_AT
                );

        CompatibilityComparisonResponse report = mockReport();

        when(projectRepository.findById(PROJECT_ID))
                .thenReturn(Optional.of(project));

        when(reportRepository.findById(PROJECT_ID))
                .thenReturn(Optional.of(saved));

        when(objectMapper.readValue(
                REPORT_JSON,
                CompatibilityComparisonResponse.class
        )).thenReturn(report);

        ProjectAnalysisResponse response =
                service.getSaved(PROJECT_ID);

        assertEquals(
                ProjectStatus.COMPLETED,
                response.status()
        );

        assertEquals(
                ProjectStatus.ANALYZING,
                project.getStatus()
        );
    }

    @Test
    void shouldRetrieveLatestSavedReport() {

        Project project = new Project();
        project.setStatus(ProjectStatus.COMPLETED);

        ProjectAnalysisReport saved =
                new ProjectAnalysisReport(
                        PROJECT_ID,
                        REPORT_JSON,
                        ANALYZED_AT
                );

        CompatibilityComparisonResponse report = mockReport();

        when(projectRepository.findById(PROJECT_ID))
                .thenReturn(Optional.of(project));

        when(reportRepository.findById(PROJECT_ID))
                .thenReturn(Optional.of(saved));

        when(objectMapper.readValue(
                REPORT_JSON,
                CompatibilityComparisonResponse.class
        )).thenReturn(report);

        ProjectAnalysisResponse response =
                service.getSaved(PROJECT_ID);

        assertEquals(PROJECT_ID, response.projectId());
        assertEquals(ANALYZED_AT, response.analyzedAt());
        assertSame(report, response.report());

        assertEquals(
                ProjectStatus.COMPLETED,
                response.status()
        );
    }

    @Test
    void shouldReturnHistorySummaries() {

        ProjectAnalysisHistory history =
                mock(ProjectAnalysisHistory.class);

        when(history.getId()).thenReturn(ANALYSIS_ID);
        when(history.getProjectId()).thenReturn(PROJECT_ID);
        when(history.getAnalyzedAt()).thenReturn(ANALYZED_AT);
        when(history.getReportJson()).thenReturn(REPORT_JSON);

        when(projectRepository.findById(PROJECT_ID))
                .thenReturn(Optional.of(new Project()));

        when(historyRepository
                .findByProjectIdOrderByAnalyzedAtDescIdDesc(
                        PROJECT_ID
                ))
                .thenReturn(List.of(history));

        CompatibilityComparisonResponse report = mockReport();

        when(objectMapper.readValue(
                REPORT_JSON,
                CompatibilityComparisonResponse.class
        )).thenReturn(report);

        List<ProjectAnalysisHistoryItem> result =
                service.getHistory(PROJECT_ID);

        assertEquals(1, result.size());
        assertEquals(ANALYSIS_ID, result.get(0).analysisId());

        assertEquals(
                CompatibilityAssessment.NO_ISSUES_DETECTED,
                result.get(0).assessment()
        );

        assertEquals(
                AnalysisCoverage.PARTIAL,
                result.get(0).coverage()
        );
    }

    @Test
    void shouldRetrieveSpecificHistoricalReport() {

        ProjectAnalysisHistory history =
                mock(ProjectAnalysisHistory.class);

        when(history.getId()).thenReturn(ANALYSIS_ID);
        when(history.getAnalyzedAt()).thenReturn(ANALYZED_AT);
        when(history.getReportJson()).thenReturn(REPORT_JSON);

        when(projectRepository.findById(PROJECT_ID))
                .thenReturn(Optional.of(new Project()));

        when(historyRepository.findByIdAndProjectId(
                ANALYSIS_ID,
                PROJECT_ID
        )).thenReturn(Optional.of(history));

        CompatibilityComparisonResponse report = mockReport();

        when(objectMapper.readValue(
                REPORT_JSON,
                CompatibilityComparisonResponse.class
        )).thenReturn(report);

        ProjectAnalysisHistoryDetail result =
                service.getHistoricalReport(
                        PROJECT_ID,
                        ANALYSIS_ID
                );

        assertEquals(ANALYSIS_ID, result.analysisId());
        assertEquals(PROJECT_ID, result.projectId());

        assertEquals(
                ProjectStatus.COMPLETED,
                result.status()
        );

        assertSame(report, result.report());
    }

    @Test
    void shouldRejectHistoryFromAnotherProject() {

        when(projectRepository.findById(PROJECT_ID))
                .thenReturn(Optional.of(new Project()));

        when(historyRepository.findByIdAndProjectId(
                ANALYSIS_ID,
                PROJECT_ID
        )).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.getHistoricalReport(
                        PROJECT_ID,
                        ANALYSIS_ID
                )
        );

        assertEquals(
                HttpStatus.NOT_FOUND,
                exception.getStatusCode()
        );
    }

    @Test
    void shouldRejectMissingLatestReport() {

        when(projectRepository.findById(PROJECT_ID))
                .thenReturn(Optional.of(new Project()));

        when(reportRepository.findById(PROJECT_ID))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.getSaved(PROJECT_ID)
        );

        assertEquals(
                HttpStatus.NOT_FOUND,
                exception.getStatusCode()
        );
    }

    private CompatibilityComparisonResponse mockReport() {

        CompatibilityComparisonResponse report =
                mock(CompatibilityComparisonResponse.class);

        when(report.assessment())
                .thenReturn(
                        CompatibilityAssessment.NO_ISSUES_DETECTED
                );

        when(report.coverage())
                .thenReturn(AnalysisCoverage.PARTIAL);

        return report;
    }
}
