
package com.codebridge.backend.compatibility;

import com.codebridge.backend.codeanalysis.JavaCodeComparisonService;
import com.codebridge.backend.configanalysis.ConfigurationComparisonService;

import com.codebridge.backend.dto.CompatibilityComparisonResponse;
import com.codebridge.backend.dto.CompatibilityRecommendation;
import com.codebridge.backend.dto.ConfigurationComparisonResponse;
import com.codebridge.backend.dto.JavaCodeComparisonResponse;
import com.codebridge.backend.dto.MavenComparisonResponse;

import com.codebridge.backend.maven.MavenAnalysisResult;
import com.codebridge.backend.maven.MavenRepositoryComparisonService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CompatibilityComparisonService {

    private final MavenRepositoryComparisonService mavenService;
    private final ConfigurationComparisonService configurationService;
    private final JavaCodeComparisonService codeService;
    private final CompatibilityRecommendationService recommendationService;

    @Autowired
    public CompatibilityComparisonService(
            MavenRepositoryComparisonService mavenService,
            ConfigurationComparisonService configurationService,
            JavaCodeComparisonService codeService,
            CompatibilityRecommendationService recommendationService
    ) {
        this.mavenService = mavenService;
        this.configurationService = configurationService;
        this.codeService = codeService;
        this.recommendationService = recommendationService;
    }

    // Preserve the previous constructor used by existing tests.
    public CompatibilityComparisonService(
            MavenRepositoryComparisonService mavenService,
            ConfigurationComparisonService configurationService,
            JavaCodeComparisonService codeService
    ) {
        this(
                mavenService,
                configurationService,
                codeService,
                new CompatibilityRecommendationService()
        );
    }

    public CompatibilityComparisonResponse compare(
            String existingRepositoryUrl,
            String incomingRepositoryUrl
    ) {

        MavenAnalysisResult mavenAnalysis =
                mavenService.analyzeForCompatibility(
                        existingRepositoryUrl,
                        incomingRepositoryUrl
                );

        MavenComparisonResponse maven =
                mavenAnalysis.comparison();

        ConfigurationComparisonResponse configuration =
                configurationService.compare(
                        existingRepositoryUrl,
                        incomingRepositoryUrl
                );

        JavaCodeComparisonResponse code =
                codeService.compare(
                        existingRepositoryUrl,
                        incomingRepositoryUrl
                );

        AnalysisCoverage coverage = determineCoverage(
                mavenAnalysis,
                configuration,
                code
        );

        String coverageExplanation = explainCoverage(
                coverage,
                mavenAnalysis
        );

        boolean hasReviewIssues =
                maven.potentialConflictCount() > 0
                        || configuration.reviewCount() > 0
                        || code.potentialCollisionCount() > 0;

        CompatibilityAssessment assessment;
        String explanation;

        if (hasReviewIssues) {

            assessment = CompatibilityAssessment.REVIEW_REQUIRED;

            explanation =
                    "Potential integration issues were detected "
                            + "in the Maven, configuration, or Java source "
                            + "analysis. Review the detailed findings and "
                            + "recommendations before integrating.";

        } else if (coverage == AnalysisCoverage.INSUFFICIENT) {

            assessment = CompatibilityAssessment.INSUFFICIENT_DATA;

            explanation =
                    "Too little comparable information was available "
                            + "for a meaningful integration assessment.";

        } else {

            assessment = CompatibilityAssessment.NO_ISSUES_DETECTED;

            explanation =
                    "No potential issues were detected by the "
                            + "supported checks. This does not establish "
                            + "that the repositories are compatible.";
        }

        List<CompatibilityRecommendation> recommendations =
                recommendationService.generate(
                        maven,
                        configuration,
                        code,
                        coverage,
                        mavenAnalysis.existingRootPomFound(),
                        mavenAnalysis.incomingRootPomFound()
                );

        return new CompatibilityComparisonResponse(
                assessment,
                explanation,
                coverage,
                coverageExplanation,
                maven,
                configuration,
                mavenAnalysis.existingRootPomFound(),
                mavenAnalysis.incomingRootPomFound(),
                code,
                recommendations
        );
    }

    private AnalysisCoverage determineCoverage(
            MavenAnalysisResult mavenAnalysis,
            ConfigurationComparisonResponse configuration,
            JavaCodeComparisonResponse code
    ) {

        MavenComparisonResponse maven =
                mavenAnalysis.comparison();

        boolean hasMavenEvidence =
                mavenAnalysis.bothRootPomsFound()
                        && maven.existingDependencyCount() > 0
                        && maven.incomingDependencyCount() > 0;

        boolean hasConfigurationEvidence =
                !configuration.existingFilesFound().isEmpty()
                        && !configuration.incomingFilesFound().isEmpty()
                        && configuration.comparedKeyCount() > 0;

        boolean hasCodeEvidence =
                code.existingJavaFileCount() > 0
                        && code.incomingJavaFileCount() > 0;

        if (hasMavenEvidence
                && hasConfigurationEvidence
                && hasCodeEvidence) {

            return AnalysisCoverage.SUPPORTED_CHECKS_COMPLETED;
        }

        if (!hasMavenEvidence
                && !hasConfigurationEvidence
                && !hasCodeEvidence) {

            return AnalysisCoverage.INSUFFICIENT;
        }

        return AnalysisCoverage.PARTIAL;
    }

    private String explainCoverage(
            AnalysisCoverage coverage,
            MavenAnalysisResult mavenAnalysis
    ) {

        String explanation = switch (coverage) {

            case INSUFFICIENT ->
                    "The supported analyzers found too little "
                            + "comparable evidence from the repositories.";

            case PARTIAL ->
                    "Some Maven, configuration, or Java source "
                            + "information was available, but at least one "
                            + "analysis area lacked usable evidence.";

            case SUPPORTED_CHECKS_COMPLETED ->
                    "All three current analysis areas returned "
                            + "usable evidence. The checks remain limited "
                            + "to root Maven POM dependencies, selected "
                            + "application.properties settings, and "
                            + "Java source analysis.";
        };

        if (!mavenAnalysis.bothRootPomsFound()) {

            explanation +=
                    " At least one root pom.xml was unavailable. "
                            + "This may mean the file is missing, the "
                            + "repository is inaccessible, or the project "
                            + "uses another build layout.";
        }

        return explanation
                + " This is not a complete compatibility test.";
    }
}
