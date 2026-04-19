CREATE TABLE IF NOT EXISTS parkour_segment_results
(
    id          SERIAL PRIMARY KEY,
    profile_id  uuid        NOT NULL,
    segment_key VARCHAR(32) NOT NULL,
    duration    BIGINT      NOT NULL,
    started_at  TIMESTAMP   NOT NULL,
    finished_at TIMESTAMP   NOT NULL,
    CONSTRAINT fk_parkour_segment_results_profile_id__id FOREIGN KEY (profile_id) REFERENCES profiles (id) ON DELETE CASCADE ON UPDATE RESTRICT
);
CREATE SEQUENCE IF NOT EXISTS parkour_segment_results_id_seq START WITH 1 MINVALUE 1 MAXVALUE 9223372036854775807;
