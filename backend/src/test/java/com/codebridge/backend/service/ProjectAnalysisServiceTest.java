
package com.codebridge.backend.service;

import com.codebridge.backend.compatibility.CompatibilityComparisonService;
import com.codebridge.backend.dto.CompatibilityComparisonResponse;
import com.codebridge.backend.dto.ProjectAnalysisResponse;
import com.codebridge.backend.entity.Project;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectAnalysisServiceTest {

    private ProjectAnalysisTransactionService transactions;
    private CompatibilityComparisonService compatibilityService;
    private ProjectAnalysisService service;

    private static final Long PROJECT_ID = 1L;

    private static final String EXISTING_URL =
            "https://github.com/junit-team/junit4";

    private static final String INCOMING_URL =
            "https://github.com/apache/commons-lang";

    @BeforeEach
    void setUp() {

        transactions = mock(ProjectAnalysisTransactionService.class);
        compatibilityService =
                mock(CompatibilityComparisonService.class);

        service = new ProjectAnalysisService(
                transactions,
                compatibilityService
        );
    }

    @Test
    void shouldAnalyzeAndSaveProjectReport() {

        Project project = project();
        CompatibilityComparisonResponse report =
                mock(CompatibilityComparisonResponse.class);
        ProjectAnalysisResponse saved =
                mock(ProjectAnalysisResponse.class);

        when(transactions.begin(PROJECT_ID))
                .thenReturn(project);

        when(compatibilityService.compare(
                EXISTING_URL,
                INCOMING_URL
        )).thenReturn(report);

        when(transactions.complete(PROJECT_ID, report))
                .thenReturn(saved);

        ProjectAnalysisResponse result =
                service.analyze(PROJECT_ID);

        assertSame(saved, result);

        var order = inOrder(
                transactions,
                compatibilityService
        );

        order.verify(transactions).begin(PROJECT_ID);
        order.verify(compatibilityService).compare(
                EXISTING_URL,
                INCOMING_URL
        );
        order.verify(transactions).complete(
                PROJECT_ID,
                report
        );

        verify(transactions, never()).fail(anyLong());
    }

    @Test
    void shouldMarkProjectFailedWhenAnalysisThrows() {

        when(transactions.begin(PROJECT_ID))
                .thenReturn(project());

        RuntimeException failure =
                new RuntimeException("Simulated analysis failure");

        when(compatibilityService.compare(
                EXISTING_URL,
                INCOMING_URL
        )).thenThrow(failure);

        RuntimeException actual = assertThrows(
                RuntimeException.class,
                () -> service.analyze(PROJECT_ID)
        );

        assertSame(failure, actual);

        verify(transactions).fail(PROJECT_ID);
        verify(transactions, never()).complete(
                anyLong(),
                any()
        );
    }

    @Test
    void shouldNotRunAnalysisWhenProjectIsAlreadyAnalyzing() {

        when(transactions.begin(PROJECT_ID))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Already analyzing"
                ));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.analyze(PROJECT_ID)
        );

        assertEquals(
                HttpStatus.CONFLICT,
                exception.getStatusCode()
        );

        verifyNoInteractions(compatibilityService);
        verify(transactions, never()).fail(anyLong());
    }

    @Test
    void shouldRetrieveSavedAnalysis() {

        ProjectAnalysisResponse saved =
                mock(ProjectAnalysisResponse.class);

        when(transactions.getSaved(PROJECT_ID))
                .thenReturn(saved);

        assertSame(
                saved,
                service.getSaved(PROJECT_ID)
        );

        verifyNoInteractions(compatibilityService);
    }

    private Project project() {

        Project project = new Project();
        project.setName("CodeBridge Comparison");
        project.setExistingRepoUrl(EXISTING_URL);
        project.setIncomingRepoUrl(INCOMING_URL);

        return project;
    }
}
