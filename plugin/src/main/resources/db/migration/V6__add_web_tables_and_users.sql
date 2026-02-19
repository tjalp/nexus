CREATE TABLE IF NOT EXISTS users
(
    id            SERIAL PRIMARY KEY,
    profile_id    uuid                                  NULL,
    username      VARCHAR(255)                          NOT NULL,
    password_hash VARCHAR(255)                          NOT NULL,
    "role"        VARCHAR(50) DEFAULT 'PLAYER'          NOT NULL,
    created_at    TIMESTAMP   DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at    TIMESTAMP   DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_users_profile_id__id FOREIGN KEY (profile_id) REFERENCES profiles (id) ON DELETE SET NULL ON UPDATE RESTRICT
);
CREATE INDEX users_profile_id ON users (profile_id);
ALTER TABLE users
    ADD CONSTRAINT users_username_unique UNIQUE (username);
CREATE SEQUENCE IF NOT EXISTS users_id_seq START WITH 1 MINVALUE 1 MAXVALUE 9223372036854775807;
