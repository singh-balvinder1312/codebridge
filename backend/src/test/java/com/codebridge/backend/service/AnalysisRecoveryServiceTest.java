
package com.codebridge.backend.service;

import com.codebridge.backend.entity.ProjectStatus;
import com.codebridge.backend.repository.ProjectRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.boot.ApplicationArguments;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

class AnalysisRecoveryServiceTest {

    private ProjectRepository projectRepository;
    private AnalysisRecoveryService recoveryService;
    private ApplicationArguments applicationArguments;

    @BeforeEach
    void setUp() {

        projectRepository = mock(ProjectRepository.class);

        applicationArguments = mock(ApplicationArguments.class);

        recoveryService = new AnalysisRecoveryService(
                projectRepository
        );
    }

    @Test
    void shouldRecoverInterruptedAnalysesAtStartup() {

        when(projectRepository.markInterruptedAnalysesFailed(
                ProjectStatus.ANALYZING,
                ProjectStatus.FAILED
        )).thenReturn(3);

        assertDoesNotThrow(
                () -> recoveryService.run(applicationArguments)
        );

        verify(projectRepository, times(1))
                .markInterruptedAnalysesFailed(
                        ProjectStatus.ANALYZING,
                        ProjectStatus.FAILED
                );

        verifyNoMoreInteractions(projectRepository);
    }

    @Test
    void shouldHandleStartupWithNoInterruptedAnalyses() {

        when(projectRepository.markInterruptedAnalysesFailed(
                ProjectStatus.ANALYZING,
                ProjectStatus.FAILED
        )).thenReturn(0);

        assertDoesNotThrow(
                () -> recoveryService.run(applicationArguments)
        );

        verify(projectRepository, times(1))
                .markInterruptedAnalysesFailed(
                        ProjectStatus.ANALYZING,
                        ProjectStatus.FAILED
                );
    }

    @Test
    void shouldNotDeleteSavedReportsDuringRecovery() {

        when(projectRepository.markInterruptedAnalysesFailed(
                ProjectStatus.ANALYZING,
                ProjectStatus.FAILED
        )).thenReturn(1);

        recoveryService.run(applicationArguments);

        // Recovery operates only through the status-update
        // repository method.
        //
        // It does not call any report or history repository.
        verify(projectRepository)
                .markInterruptedAnalysesFailed(
                        ProjectStatus.ANALYZING,
                        ProjectStatus.FAILED
                );

        verifyNoMoreInteractions(projectRepository);
    }
}
