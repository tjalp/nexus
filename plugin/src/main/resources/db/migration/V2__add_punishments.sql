CREATE TABLE IF NOT EXISTS punishments
(
    id                   SERIAL PRIMARY KEY,
    case_id              VARCHAR(32) NOT NULL,
    punished_profile_id  uuid        NOT NULL,
    punishment_type      VARCHAR(32) NOT NULL,
    reason               TEXT        NOT NULL,
    punishment_severity  VARCHAR(32) NOT NULL,
    duration             BIGINT      NOT NULL,
    punishment_timestamp TIMESTAMP   NOT NULL,
    issued_by            VARCHAR(36) NOT NULL,
    CONSTRAINT fk_punishments_punished_profile_id__id FOREIGN KEY (punished_profile_id) REFERENCES profiles (id) ON DELETE CASCADE ON UPDATE RESTRICT
);
ALTER TABLE punishments
    ADD CONSTRAINT punishments_case_id_unique UNIQUE (case_id);
CREATE SEQUENCE IF NOT EXISTS punishments_id_seq START WITH 1 MINVALUE 1 MAXVALUE 9223372036854775807;
ALTER TABLE general_attachment
    ADD time_zone VARCHAR(64) DEFAULT 'UTC' NOT NULL;
