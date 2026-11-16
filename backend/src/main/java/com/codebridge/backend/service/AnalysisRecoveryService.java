
package com.codebridge.backend.service;

import com.codebridge.backend.entity.ProjectStatus;
import com.codebridge.backend.repository.ProjectRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalysisRecoveryService implements ApplicationRunner {

    private static final Logger logger =
            LoggerFactory.getLogger(
                    AnalysisRecoveryService.class
            );

    private final ProjectRepository projectRepository;

    public AnalysisRecoveryService(
            ProjectRepository projectRepository
    ) {
        this.projectRepository = projectRepository;
    }

    // Spring Boot automatically executes ApplicationRunner
    // during application startup.
    //
    // At this point, the previous application process is
    // assumed to have stopped.
    //
    // This implementation is intended for a single-instance
    // deployment.
    @Override
    @Transactional
    public void run(ApplicationArguments args) {

        int recovered = projectRepository
                .markInterruptedAnalysesFailed(
                        ProjectStatus.ANALYZING,
                        ProjectStatus.FAILED
                );

        if (recovered > 0) {

            logger.warn(
                    "Recovered {} interrupted project analyses. "
                            + "Their status was changed from "
                            + "ANALYZING to FAILED. "
                            + "Previously saved reports were preserved.",
                    recovered
            );

        } else {

            logger.info(
                    "Startup analysis recovery completed. "
                            + "No interrupted analyses were found."
            );
        }
    }
}
