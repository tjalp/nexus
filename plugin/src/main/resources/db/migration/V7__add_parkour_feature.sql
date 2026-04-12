CREATE TABLE IF NOT EXISTS parkour_segment_results
(
    id          uuid PRIMARY KEY,
    profile_id  uuid      NOT NULL,
    segment_id  uuid      NOT NULL,
    duration_ms BIGINT    NOT NULL,
    started_at  TIMESTAMP NOT NULL,
    finished_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_parkour_segment_results_profile_id__id FOREIGN KEY (profile_id) REFERENCES profiles (id) ON DELETE CASCADE ON UPDATE RESTRICT
);
CREATE TABLE IF NOT EXISTS parkour_attachments
(
    profile_id     uuid,
    entry_node_id  uuid,
    route_key      VARCHAR(64) NOT NULL,
    route_sequence TEXT        NOT NULL,
    CONSTRAINT pk_parkour_attachments PRIMARY KEY (profile_id, entry_node_id),
    CONSTRAINT fk_parkour_attachments_profile_id__id FOREIGN KEY (profile_id) REFERENCES profiles (id) ON DELETE CASCADE ON UPDATE RESTRICT
);
