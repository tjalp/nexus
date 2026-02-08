CREATE TABLE IF NOT EXISTS profiles
(
    id          UUID PRIMARY KEY,
    created_at  TIMESTAMP(9) DEFAULT CURRENT_TIMESTAMP NOT NULL,
    modified_at TIMESTAMP(9) DEFAULT CURRENT_TIMESTAMP NOT NULL
);
CREATE TABLE IF NOT EXISTS general_attachment
(
    profile_id       UUID PRIMARY KEY,
    last_known_name  VARCHAR(16)                 NULL,
    preferred_locale VARCHAR(64) DEFAULT 'en-US' NOT NULL,
    CONSTRAINT fk_general_attachment_profile_id__id FOREIGN KEY (profile_id) REFERENCES profiles (id) ON DELETE CASCADE ON UPDATE RESTRICT
);
