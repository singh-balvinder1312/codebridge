
package com.codebridge.backend.repository;

import com.codebridge.backend.entity.ProjectAnalysisHistory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectAnalysisHistoryRepository
        extends JpaRepository<ProjectAnalysisHistory, Long> {

    List<ProjectAnalysisHistory>
    findByProjectIdOrderByAnalyzedAtDescIdDesc(Long projectId);

    Optional<ProjectAnalysisHistory>
    findByIdAndProjectId(Long id, Long projectId);
}
