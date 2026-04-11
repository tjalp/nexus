CREATE TABLE IF NOT EXISTS parkour_segment_results
(
    id              uuid PRIMARY KEY,
    profile_id      uuid         NOT NULL,
    parkour_id      uuid         NOT NULL,
    route_key       VARCHAR(64)  NOT NULL,
    route_name      VARCHAR(128),
    segment_id      uuid         NOT NULL,
    segment_name    VARCHAR(128) NOT NULL,
    segment_order   INT          NOT NULL,
    duration_ms     BIGINT       NOT NULL,
    started_at      TIMESTAMP    NOT NULL,
    finished_at     TIMESTAMP    NOT NULL,
    CONSTRAINT fk_parkour_segment_results_profile_id FOREIGN KEY (profile_id) REFERENCES profiles (id) ON DELETE CASCADE ON UPDATE RESTRICT
);
