CREATE TABLE IF NOT EXISTS game_templates
(
    id                  SERIAL PRIMARY KEY,
    template_key        VARCHAR(64)  NOT NULL UNIQUE,
    name                VARCHAR(255) NOT NULL,
    implementation_key  VARCHAR(64)  NOT NULL
);

CREATE TABLE IF NOT EXISTS game_definitions
(
    id           SERIAL PRIMARY KEY,
    definition_key VARCHAR(64)  NOT NULL UNIQUE,
    template_id  INTEGER      NOT NULL,
    name         VARCHAR(255) NOT NULL,
    min_players  INTEGER      NOT NULL,
    max_players  INTEGER      NOT NULL,
    points_to_win INTEGER     NOT NULL DEFAULT 1,
    team_assignment_mode VARCHAR(32) NOT NULL DEFAULT 'MANUAL',
    CONSTRAINT fk_game_definitions_template_id__id
        FOREIGN KEY (template_id) REFERENCES game_templates (id) ON DELETE CASCADE ON UPDATE RESTRICT
);

CREATE TABLE IF NOT EXISTS game_teams
(
    id            SERIAL PRIMARY KEY,
    definition_id INTEGER      NOT NULL,
    team_key      VARCHAR(64)  NOT NULL,
    name          VARCHAR(255) NOT NULL,
    min_players   INTEGER      DEFAULT 0 NOT NULL,
    max_players   INTEGER      DEFAULT 2147483647 NOT NULL,
    CONSTRAINT fk_game_teams_definition_id__id
        FOREIGN KEY (definition_id) REFERENCES game_definitions (id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT game_teams_definition_key_unique UNIQUE (definition_id, team_key)
);

CREATE TABLE IF NOT EXISTS game_scoring_rules
(
    id            SERIAL PRIMARY KEY,
    definition_id INTEGER      NOT NULL,
    scoring_key   VARCHAR(64)  NOT NULL,
    name          VARCHAR(255) NOT NULL,
    points        INTEGER      NOT NULL,
    trigger       VARCHAR(32)  NOT NULL DEFAULT 'MANUAL',
    CONSTRAINT fk_game_scoring_definition_id__id
        FOREIGN KEY (definition_id) REFERENCES game_definitions (id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT game_scoring_definition_key_unique UNIQUE (definition_id, scoring_key)
);

CREATE TABLE IF NOT EXISTS game_phases
(
    id                SERIAL PRIMARY KEY,
    definition_id     INTEGER     NOT NULL,
    phase_key         VARCHAR(64) NOT NULL,
    implementation_key VARCHAR(64) NOT NULL,
    order_index       INTEGER     NOT NULL,
    CONSTRAINT fk_game_phases_definition_id__id
        FOREIGN KEY (definition_id) REFERENCES game_definitions (id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT game_phases_definition_key_unique UNIQUE (definition_id, phase_key)
);

INSERT INTO game_templates (template_key, name, implementation_key)
VALUES ('frostball_frenzy', 'Frostball Frenzy', 'frostball_frenzy')
ON CONFLICT (template_key) DO NOTHING;

INSERT INTO game_definitions (definition_key, template_id, name, min_players, max_players, points_to_win, team_assignment_mode)
SELECT 'frostball_frenzy', id, 'Frostball Frenzy', 2, 16, 10, 'AUTOMATIC'
FROM game_templates
WHERE template_key = 'frostball_frenzy'
  AND NOT EXISTS (
    SELECT 1 FROM game_definitions WHERE definition_key = 'frostball_frenzy'
  );

INSERT INTO game_teams (definition_id, team_key, name)
SELECT id, 'red', 'Red' FROM game_definitions WHERE definition_key = 'frostball_frenzy'
ON CONFLICT (definition_id, team_key) DO NOTHING;
INSERT INTO game_teams (definition_id, team_key, name)
SELECT id, 'blue', 'Blue' FROM game_definitions WHERE definition_key = 'frostball_frenzy'
ON CONFLICT (definition_id, team_key) DO NOTHING;

INSERT INTO game_scoring_rules (definition_id, scoring_key, name, points, trigger)
SELECT id, 'snowball_hit', 'Snowball hit', 1, 'PROJECTILE_HIT'
FROM game_definitions WHERE definition_key = 'frostball_frenzy'
ON CONFLICT (definition_id, scoring_key) DO NOTHING;
INSERT INTO game_scoring_rules (definition_id, scoring_key, name, points, trigger)
SELECT id, 'manual', 'Manual adjustment', 1, 'MANUAL'
FROM game_definitions WHERE definition_key = 'frostball_frenzy'
ON CONFLICT (definition_id, scoring_key) DO NOTHING;

INSERT INTO game_phases (definition_id, phase_key, implementation_key, order_index)
SELECT id, 'waiting', 'frostball_waiting', 0
FROM game_definitions WHERE definition_key = 'frostball_frenzy'
ON CONFLICT (definition_id, phase_key) DO NOTHING;
INSERT INTO game_phases (definition_id, phase_key, implementation_key, order_index)
SELECT id, 'fight', 'frostball_fight', 1
FROM game_definitions WHERE definition_key = 'frostball_frenzy'
ON CONFLICT (definition_id, phase_key) DO NOTHING;
