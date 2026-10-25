-- V1: AnimeVerse feed schema (anime, genres, airing schedule).
-- Flyway runs this file exactly once and records it in flyway_schema_history.
-- Never edit this file after it has run anywhere; make changes in a new V2__ file.

-- ---------------------------------------------------------------------------
-- anime: one row per show, copied from AniList by the sync job
-- ---------------------------------------------------------------------------
CREATE TABLE anime (
    id               bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    anilist_id       integer     NOT NULL UNIQUE,

    title_romaji     text        NOT NULL,
    title_english    text,                      -- NULL: no official English title yet
    cover_image_url  text,

    format           text        NOT NULL
        CHECK (format IN ('TV', 'TV_SHORT', 'MOVIE', 'SPECIAL', 'OVA', 'ONA', 'MUSIC')),
    status           text        NOT NULL
        CHECK (status IN ('RELEASING', 'FINISHED', 'NOT_YET_RELEASED', 'HIATUS', 'CANCELLED')),
    season           text
        CHECK (season IN ('WINTER', 'SPRING', 'SUMMER', 'FALL')),
    season_year      smallint,
    episodes         smallint    CHECK (episodes > 0),   -- NULL: total not announced

    -- Partial dates: rounded to the 1st, with a precision saying what's really known.
    start_date       date,
    start_precision  text        CHECK (start_precision IN ('DAY', 'MONTH', 'YEAR')),
    end_date         date,                      -- NULL while still airing
    end_precision    text        CHECK (end_precision IN ('DAY', 'MONTH', 'YEAR')),

    average_score    smallint    CHECK (average_score BETWEEN 0 AND 100),  -- NULL: unrated ("NEW")
    synced_at        timestamptz NOT NULL,

    -- A date and its precision are either both present or both missing.
    CONSTRAINT start_date_has_precision CHECK ((start_date IS NULL) = (start_precision IS NULL)),
    CONSTRAINT end_date_has_precision   CHECK ((end_date IS NULL)   = (end_precision IS NULL))
);

-- Feed sections: "Airing now" / "Upcoming" filter by status and sort by start_date;
-- "Finished this year" filters by status and sorts by end_date.
CREATE INDEX idx_anime_status_start_date ON anime (status, start_date DESC);
CREATE INDEX idx_anime_status_end_date   ON anime (status, end_date DESC);

-- ---------------------------------------------------------------------------
-- genre + anime_genre: many-to-many (a show has many genres, a genre many shows)
-- ---------------------------------------------------------------------------
CREATE TABLE genre (
    id    bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name  text NOT NULL UNIQUE
);

CREATE TABLE anime_genre (
    -- Deleting a show removes its genre links. Deleting a genre that is still
    -- linked to a show is refused (the default, RESTRICT-like NO ACTION).
    anime_id  bigint NOT NULL REFERENCES anime (id) ON DELETE CASCADE,
    genre_id  bigint NOT NULL REFERENCES genre (id),
    PRIMARY KEY (anime_id, genre_id)
);

-- The primary key already covers lookups by anime_id (its first column);
-- this one covers lookups by genre.
CREATE INDEX idx_anime_genre_genre_id ON anime_genre (genre_id);

-- ---------------------------------------------------------------------------
-- airing_episode: one row per episode with its exact air time (UTC)
-- ---------------------------------------------------------------------------
CREATE TABLE airing_episode (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    anime_id   bigint      NOT NULL REFERENCES anime (id) ON DELETE CASCADE,
    episode    smallint    NOT NULL CHECK (episode > 0),
    airing_at  timestamptz NOT NULL,
    UNIQUE (anime_id, episode)
);

-- V2: "which episodes aired since the last check?"
CREATE INDEX idx_airing_episode_airing_at ON airing_episode (airing_at);
