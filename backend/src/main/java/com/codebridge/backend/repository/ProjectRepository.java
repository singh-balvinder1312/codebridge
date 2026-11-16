
package com.codebridge.backend.repository;

import com.codebridge.backend.entity.Project;
import com.codebridge.backend.entity.ProjectStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectRepository
        extends JpaRepository<Project, Long> {

    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE Project p
               SET p.status = :analyzing
             WHERE p.id = :projectId
               AND p.status <> :analyzing
            """)
    int claimForAnalysis(
            @Param("projectId") Long projectId,
            @Param("analyzing") ProjectStatus analyzing
    );

    // Recover analyses interrupted by an application restart.
    //
    // Only projects still marked ANALYZING are affected.
    // Completed, failed, and pending projects are unchanged.
    //
    // This query does not touch saved compatibility reports
    // or the analysis-history table.
    @Modifying(
            clearAutomatically = true,
            flushAutomatically = true
    )
    @Query("""
            UPDATE Project p
               SET p.status = :failed
             WHERE p.status = :analyzing
            """)
    int markInterruptedAnalysesFailed(
            @Param("analyzing") ProjectStatus analyzing,
            @Param("failed") ProjectStatus failed
    );
}
