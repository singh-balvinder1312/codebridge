CREATE TABLE project_analysis_reports (
                                          project_id BIGINT PRIMARY KEY,
                                          report_json TEXT NOT NULL,
                                          analyzed_at TIMESTAMPTZ NOT NULL,

                                          CONSTRAINT fk_project_analysis_project
                                              FOREIGN KEY (project_id)
                                                  REFERENCES projects (id)
                                                  ON DELETE CASCADE
);
