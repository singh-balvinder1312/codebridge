
package com.codebridge.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "project_analysis_reports")
public class ProjectAnalysisReport {

    @Id
    @Column(name = "project_id")
    private Long projectId;

    @Column(
            name = "report_json",
            nullable = false,
            columnDefinition = "text"
    )
    private String reportJson;

    @Column(name = "analyzed_at", nullable = false)
    private Instant analyzedAt;

    protected ProjectAnalysisReport() {
    }

    public ProjectAnalysisReport(
            Long projectId,
            String reportJson,
            Instant analyzedAt
    ) {
        this.projectId = Objects.requireNonNull(projectId);
        this.reportJson = Objects.requireNonNull(reportJson);
        this.analyzedAt = Objects.requireNonNull(analyzedAt);
    }

    public Long getProjectId() {
        return projectId;
    }

    public String getReportJson() {
        return reportJson;
    }

    public Instant getAnalyzedAt() {
        return analyzedAt;
    }
}
