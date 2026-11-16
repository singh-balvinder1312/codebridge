package com.codebridge.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "project_analysis_history")
public class ProjectAnalysisHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(
            name = "report_json",
            nullable = false,
            columnDefinition = "text"
    )
    private String reportJson;

    @Column(name = "analyzed_at", nullable = false)
    private Instant analyzedAt;

    protected ProjectAnalysisHistory() {
    }

    public ProjectAnalysisHistory(
            Long projectId,
            String reportJson,
            Instant analyzedAt
    ) {
        this.projectId = Objects.requireNonNull(projectId);
        this.reportJson = Objects.requireNonNull(reportJson);
        this.analyzedAt = Objects.requireNonNull(analyzedAt);
    }

    public Long getId() {
        return id;
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
