CREATE TABLE IF NOT EXISTS notices_attachments
(
    profile_id             uuid PRIMARY KEY,
    accepted_rules_version INT DEFAULT 0 NOT NULL,
    CONSTRAINT fk_notices_attachments_profile_id__id FOREIGN KEY (profile_id) REFERENCES profiles (id) ON DELETE CASCADE ON UPDATE RESTRICT
);
