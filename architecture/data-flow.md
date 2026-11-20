# Data flow

Each section has one diagram and a short explanation of what it shows and why it is shaped that way. Status words: **built** = in `main` and run, **planned** = not written yet (dashed in the diagrams). See [`README.md`](./README.md) for how to keep this file honest.

Contents: [1. System overview](#1-system-overview) · [2. The sync run](#2-the-sync-run) · [3. Saving one page](#3-saving-one-page) · [4. One AniList request](#4-one-anilist-request-and-its-retries) · [5. One feed request](#5-one-feed-request) · [6. Feed sections over time](#6-how-a-show-moves-between-feed-sections) · [7. Data model](#7-data-model) · [8. Who reads and writes what](#8-who-reads-and-writes-what) · [9. When things fail](#9-when-things-fail) · [10. Known gaps](#10-known-gaps)

---

## 1. System overview

```mermaid
flowchart TB
    AL["AniList GraphQL API<br/>(external, we only read from it)"]

    subgraph backend["Spring Boot backend (built)"]
        SJ["SyncJob<br/>every 3 h and at startup<br/>(built)"]
        FA["Feed API<br/>GET /api/anime/airing, /finished, /upcoming<br/>(built)"]
        NJ["Notification job<br/>(planned, V2)"]
    end

    DB[("PostgreSQL 18<br/>built: Docker container<br/>planned: AWS RDS")]
    FE["React frontend<br/>(planned)"]
    USER(["Visitor"])
    GS["Google sign-in<br/>(planned, V2)"]
    EM["Email service, likely SES<br/>(planned, V2)"]

    AL -->|"pull: GraphQL over HTTPS"| SJ
    SJ -->|"write: upsert shows and episodes"| DB
    DB -->|"read: 2 queries per section"| FA
    FA -->|"JSON list of cards"| FE
    FE -->|"page"| USER

    DB -.->|"newly aired episodes"| NJ
    NJ -.->|"send mail"| EM
    USER -.->|"sign in"| GS

    classDef planned stroke-dasharray: 6 4
    class FE,GS,EM,NJ planned
```

**What it shows.** Data moves in one direction: from AniList, through the sync job, into our own Postgres, and out through a read-only API. A visitor's request never reaches AniList.

**Why it is shaped this way.** The sync job is the only writer, and the feed API only reads. That gives fast, indexed reads from our own database, keeps us far below AniList's rate limits, and means the site still shows data when AniList is down. The cost is that data can be up to about 3 hours old (the job waits 3 hours after each run finishes).

**Built vs planned.** The whole backend path from AniList to the three JSON endpoints is built and was run against the real AniList and a real Postgres. Nothing exists yet for the frontend, the AWS deployment, or any of V2. Today the database is a Docker container started from `docker-compose.yml`, with credentials in the gitignored `.env`.

---

## 2. The sync run

```mermaid
sequenceDiagram
    autonumber
    participant T as Spring scheduler
    participant J as SyncJob
    participant S as AnimeSyncService
    participant E as EpisodeSyncService
    participant A as AniList API
    participant D as PostgreSQL

    T->>J: run() at startup, then 3 h after the previous run finishes
    J->>S: syncAnime()
    S->>A: fetch releasing, finished since 2026-01-01, and upcoming (50 per page)
    alt a download still fails after its retries
        A--xS: error (AniListException)
        S-->>J: throws
        J->>J: log the error, skip the episode sync, wait for the next run
    else downloads succeed
        A-->>S: lists of shows
        S->>S: merge by AniList id (first list wins), drop undated upcoming shows
        loop each page of 50 shows
            S->>D: upsert shows and genres in one transaction
            alt database error
                D--xS: roll back this page only
                S->>S: count a failed page and continue with the next page
            end
        end
        S-->>J: report (fetched, saved, skipped, failedPages)
        J->>E: syncEpisodes()
        loop each 30-day window from 2026-01-01 up to now plus 14 days
            E->>A: fetch the airing schedule of this window
            alt this window fails after its retries
                A--xE: error
                E->>E: count a failed window and skip it
            else ok
                A-->>E: episodes of ALL anime on AniList
                loop each page of 50 episodes
                    E->>D: save episodes of tracked shows in one transaction
                end
            end
        end
        E-->>J: report (fetched, saved, skipped, failedPages, failedWindows)
    end
```

**What it shows.** One run of the job: first the shows, then their episodes. Each download is broken into small steps, and each save is a short transaction, so a failure costs a small piece and never the whole run.

**Why it is shaped this way.**
- **Shows before episodes**, because an episode row needs its show (`airing_episode.anime_id` is a foreign key). If the show download fails, the episode sync is skipped, since there is nothing safe to attach episodes to. The next run catches up.
- **Windows of 30 days**, because AniList refuses more than 5,000 entries per query and the whole year is more than that. Each window is below the cap and is independent, so one bad month does not discard the rest.
- **`fixedDelay`**: the next run starts 3 hours after the previous one finishes, so two runs can never overlap and write the same rows.
- **No transaction is open while waiting on the network.** The services only fetch and split. Each page is saved by a separate bean (`AnimePageSaver`, `AiringEpisodePageSaver`) so Spring's `@Transactional` proxy applies.
- A failed database save of a page does **not** throw out of the show sync, so a run with a few failed pages still goes on to the episode sync. Only a failed **download** skips it.

**Built.** All of it. The run is also what writes `synced_at`, and tests switch it off with `animeverse.sync.enabled=false` so tests never call AniList.

---

## 3. Saving one page

Two small diagrams, one per saver. Each page is up to 50 rows and is one database transaction: **all of it is saved, or none of it.** Like a bank transfer, where both the withdrawal and the deposit happen or neither does.

### 3a. A page of shows (`AnimePageSaver`)

```mermaid
flowchart TD
    P["Page of up to 50 shows from AniList"] --> TX["BEGIN transaction"]
    TX --> M{"Map and validate the next show:<br/>id, romaji title, known format and status,<br/>score 0 to 100, valid dates, numbers fit"}
    M -->|"unusable"| SK["Log a warning and skip this show<br/>(nothing was changed yet)"]
    M -->|"ok"| UP["anilist_id already stored? UPDATE the row, else INSERT<br/>score 0 and episodes of 0 or less are stored as NULL<br/>synced_at is set"]
    UP --> G["Mirror genres: unlink the ones AniList dropped,<br/>create missing genre rows, link the current ones"]
    G --> MORE{"More shows on the page?"}
    SK --> MORE
    MORE -->|"yes"| M
    MORE -->|"no"| C["COMMIT the whole page"]
    TX -.->|"database error at any point"| R["ROLLBACK the whole page<br/>the sync logs it and goes to the next page"]
```

**Why.** The mapper validates a show **before** it changes anything, so skipping one bad show inside the transaction cannot leave a half-written row. A real database error rolls back only that page of 50. Saving is an **upsert** (update if known, insert if new), so running the same page twice gives the same data and a retry never creates duplicates. The database backs this up with `UNIQUE (anilist_id)`.

### 3b. A page of episodes (`AiringEpisodePageSaver`)

```mermaid
flowchart TD
    P["Page of up to 50 schedule rows<br/>(episodes of ALL anime on AniList)"] --> TX["BEGIN transaction"]
    TX --> V{"Row complete and episode<br/>between 1 and 32767?"}
    V -->|"no"| SK["Count as skipped"]
    V -->|"yes"| K{"Is the show in our anime table?<br/>(looked up once per show per page)"}
    K -->|"no, not one of ours"| SK
    K -->|"yes"| X{"Is this episode already stored?"}
    X -->|"no"| I["INSERT airing_episode"]
    X -->|"yes"| U["UPDATE airing_at<br/>(AniList rescheduled the episode)"]
    I --> C["COMMIT the whole page"]
    U --> C
    SK --> C
    TX -.->|"database error at any point"| R["ROLLBACK the whole page<br/>the sync logs it and goes to the next page"]
```

**Why.** AniList's schedule covers every anime it knows, so most rows are for shows we do not store, and those are skipped. Rescheduled episodes are updated in place, and `UNIQUE (anime_id, episode)` makes duplicates impossible.

**Built.** Both savers, with unit and integration tests.

---

## 4. One AniList request and its retries

```mermaid
flowchart TD
    S["POST one GraphQL request<br/>connect timeout 10 s, read timeout 30 s"] --> R{"What came back?"}
    R -->|"HTTP 200"| G{"errors list in the body?"}
    G -->|"no"| OK["Return the data"]
    G -->|"yes"| F1["AniListException, not retried<br/>(the query itself is wrong)"]
    R -->|"429 rate limited"| W1{"Was this attempt 3 of 3?"}
    W1 -->|"no"| P1["Wait the Retry-After seconds<br/>(60 s if missing, never more than 120 s)"]
    P1 --> S
    W1 -->|"yes"| F2["AniListException"]
    R -->|"no response: timeout, refused or dropped connection"| W2{"Was this attempt 3 of 3?"}
    R -->|"502, 503 or 504"| W2
    W2 -->|"no"| P2["Wait 5 s after the 1st failure, 10 s after the 2nd"]
    P2 --> S
    W2 -->|"yes"| F3["AniListException"]
    R -->|"any other 4xx or 5xx"| F4["AniListException right away<br/>with the first 500 characters of AniList's reply"]
    R -->|"reply cannot be read, for example broken JSON"| F5["AniListException right away"]
```

**What it shows.** What `AniListClient` does with one request. Every failure leaves as the same exception type (`AniListException`), so callers handle one thing. A long list is many such requests, one per page of 50, with a safety stop after 200 pages.

**Why it is shaped this way.** Retry only what can change. A timeout or a 503 may work a few seconds later, so those wait and retry (exponential backoff: the wait doubles, like waiting longer before redialing a busy line, so we do not hammer a struggling server). A 400 for a bad query would fail the same way forever, so it fails immediately. Retrying is safe because these requests only **read** data, so a repeat cannot create duplicates. There are at most **3 attempts in total**, so at most 2 waits and none after the last try.

**Built.** Verified with 15 tests against a fake AniList server (no real waiting), and against the real AniList once.

---

## 5. One feed request

```mermaid
sequenceDiagram
    autonumber
    participant C as Client (curl today, React planned)
    participant FC as FeedController
    participant FS as FeedService
    participant AR as AnimeRepository
    participant ER as AiringEpisodeRepository
    participant D as PostgreSQL

    C->>FC: GET /api/anime/airing (or /finished or /upcoming)
    FC->>FS: airing()
    Note over FS: one read-only transaction
    FS->>AR: findAiring()
    AR->>D: query 1, shows left join genres, filtered and sorted
    D-->>AR: rows (one per show and genre)
    AR-->>FS: Anime list with genres loaded
    alt no shows in this section
        FS-->>FC: empty list (query 2 is skipped)
    else shows found
        FS->>ER: findMaxAiredEpisodes(ids, now)
        ER->>D: query 2, highest episode per show with airing_at not after now, grouped
        D-->>ER: one row per show that has an aired episode
        ER-->>FS: list of MaxAiredEpisode
        FS->>FS: AnimeCard.from(anime, maxEpisode) with display text from CardLabels
        FS-->>FC: list of AnimeCard
    end
    FC-->>C: 200 with a JSON array
```

**What it shows.** The only read path in the system. The controller only maps a URL to a service call. The service runs **exactly two SQL statements per section, however many cards there are**, then turns each row into an `AnimeCard` (a DTO, the shape promised to the frontend) with the display text already prepared ("Ep 4 / 12", "Fall 2026", "Oct 4, 2026").

**Why it is shaped this way.**
- **Two queries, not one per card.** Fetching each show's genres and latest episode one by one is the N+1 problem: 1 query for the list plus 1 per card, so 60 cards would cost 121 queries. `left join fetch` loads shows with their genres in one query, and one grouped query answers "latest aired episode" for all shows at once. `FeedQueryCountTest` fails if this ever slips, and it includes a deliberate N+1 to prove the counter works.
- **`Ep X` is the MAX aired episode, not a count**, because only this year's episodes are stored, so counting would show One Piece as "Ep 40".
- **Three endpoints**, so one failing does not affect the others and the frontend can render each section as it arrives.
- **A DTO, not the entity**, so internal fields (`anilistId`, `syncedAt`) never leave the backend. A test asserts they are absent from the JSON.
- **No pagination** in V1 (at most about 240 KB of JSON per section, an estimate). `join fetch` on a collection would force in-memory paging, so adding pagination later means changing the query approach.

**Built.** Verified by tests and by `curl` against real data: 217 airing, 436 finished and 163 upcoming shows when last checked.

---

## 6. How a show moves between feed sections

The database rows change over time as the sync overwrites `status` with what AniList currently says. This shows which section a show is in, and where the design leaks.

```mermaid
stateDiagram-v2
    [*] --> Upcoming: stored as NOT_YET_RELEASED with a start date in the next 3 months
    Upcoming --> Airing: a later sync sees RELEASING
    Airing --> Finished: a later sync sees FINISHED with end_date in 2026 or later
    Airing --> StaleAiring: AniList puts the show on hiatus or cancels it
    Upcoming --> Hidden: the start date passes but AniList still says NOT_YET_RELEASED
    StaleAiring: Still shown under Airing with an outdated status
    Hidden: Matches no section
    Finished --> [*]
    Hidden --> [*]
```

**What it shows.** A show is placed by its `status` (and dates), so it moves **only when a sync runs**, never when a visitor opens a page.
- **Upcoming:** `NOT_YET_RELEASED`, start date from today up to 3 months ahead, soonest first.
- **Airing:** `RELEASING`, newest premiere first (shows with no date last).
- **Finished:** `FINISHED` with `end_date` on or after 2026-01-01, most recently ended first.

**Where it leaks (both are known limits, not built fixes).**
- AniList's `RELEASING` filter stops returning a show that goes on hiatus or is cancelled, so its stored row never updates and it stays under **Airing**. V1 accepts this. V2's hiatus and cancellation emails will need a way to re-check shows we already have.
- A show stuck as `NOT_YET_RELEASED` whose start date is in the past matches no query, so it disappears from every section.

`HIATUS` and `CANCELLED` are allowed in the schema but nothing in V1 fetches or shows them.

---

## 7. Data model

```mermaid
erDiagram
    ANIME ||--o{ ANIME_GENRE : "has"
    GENRE ||--o{ ANIME_GENRE : "tagged on"
    ANIME ||--o{ AIRING_EPISODE : "airs as"

    ANIME {
        bigint id PK "identity, our own key"
        integer anilist_id UK "AniList's own id"
        text title_romaji "NOT NULL"
        text title_english "NULL means no English title"
        text cover_image_url
        text format "TV, TV_SHORT, MOVIE, SPECIAL, OVA, ONA, MUSIC"
        text status "RELEASING, FINISHED, NOT_YET_RELEASED, HIATUS, CANCELLED"
        text season "WINTER, SPRING, SUMMER, FALL"
        smallint season_year
        smallint episodes "NULL means total not announced"
        date start_date "rounded to the 1st"
        text start_precision "DAY, MONTH or YEAR"
        date end_date "NULL while still airing"
        text end_precision "DAY, MONTH or YEAR"
        smallint average_score "NULL means unrated, shown as NEW"
        timestamptz synced_at "NOT NULL"
    }
    GENRE {
        bigint id PK
        text name UK
    }
    ANIME_GENRE {
        bigint anime_id PK, FK "ON DELETE CASCADE"
        bigint genre_id PK, FK "delete refused while linked"
    }
    AIRING_EPISODE {
        bigint id PK
        bigint anime_id FK "ON DELETE CASCADE"
        smallint episode "UNIQUE together with anime_id"
        timestamptz airing_at "UTC"
    }
```

**What it shows.** The four V1 tables, all **built** (`V1__create_v1_schema.sql`, applied by Flyway). `ANIME_GENRE` is the join table that makes the many-to-many between shows and genres: one row per link.

**Why it is shaped this way.**
- **Own `id`, separate `anilist_id`**, so the database is not tied to AniList's numbering.
- **Partial dates** are a `date` plus a precision, because AniList often knows only a month or a year. The display stays honest ("April 2027", not "April 1"). A `CHECK` makes the date and its precision both present or both missing.
- **`NULL` means unknown**, never a made-up value: no score shows a NEW badge, no episode total shows "?".
- **`ON DELETE CASCADE`** from `anime`, because genre links and episodes mean nothing without their show. A genre still linked to a show cannot be deleted.
- **The database is the last line of defense.** Types and `CHECK` rules apply to every write, so a score of 140 or a status typo can never be stored whatever code writes it.
- **Indexes** match the feed: `(status, start_date DESC)` and `(status, end_date DESC)` for the sections, `airing_at` for V2's "what aired since the last check", and `anime_genre(genre_id)` for lookups by genre. Postgres does not index foreign keys on its own.
- **Flyway owns the schema** and Hibernate only validates it (`ddl-auto=validate`). A migration is never edited after it has run. Changes go in a new `V2__` file.

**Planned, not designed yet:** V2 tables for users, follows and sent notifications. The idea on record is a sent-notifications table with a unique (user, anime, episode) constraint so an email is never sent twice. The names and columns are not decided, so they are not drawn.

---

## 8. Who reads and writes what

| Data | Written by | Read by | Notes |
|---|---|---|---|
| `anime`, `genre`, `anime_genre` | `AnimePageSaver`, called only by the sync job | `FeedService` through `AnimeRepository` | No API endpoint writes anything. |
| `airing_episode` | `AiringEpisodePageSaver`, called only by the sync job | `FeedService` (highest aired episode). V2 notifications (planned) | |
| Schema (tables, indexes) | Flyway, at app startup | Hibernate checks it with `validate` | `flyway_schema_history` records what ran. |
| AniList data | AniList (external) | `AniListClient` over HTTPS | Read-only for us. No AniList account or key is used. |
| Database credentials | You, in the root `.env` (gitignored) | Docker Compose and Spring | Never committed. No `.env.example` by decision. |
| Feed JSON | `FeedController` | Anyone who can reach the server | See access below. |

**Access today (built):**
- The API has **only three `GET` routes**, no write endpoint, and **no authentication**. There is no Spring Security in the project. That is fine for public, read-only news data in V1. V2's sign-in will need it.
- The app uses **one database user** for migrations and for queries. There is no separate read-only role.
- It is **not deployed**. In development, Postgres is published on the laptop's port 5432.
- There is **no CORS configuration**. A browser blocks a page on another origin (for example a React dev server) from calling the API, so the frontend branch must settle this, for example with a dev proxy or a CORS setting. This is a decision still to make, not made here.

---

## 9. When things fail

| What fails | What happens | Where it is handled |
|---|---|---|
| AniList is slow, drops the connection or answers 502/503/504 | Retried up to 3 attempts in total, waiting 5 s then 10 s, then `AniListException`. | `AniListClient` |
| AniList rate limit (429) | Waits the `Retry-After` seconds (default 60 s, capped at 120 s), up to 3 attempts. | `AniListClient` |
| AniList answers a bad query or other error (400, 500, broken JSON) | Fails right away with AniList's own message. Retrying would not change the result. | `AniListClient` |
| The show download fails | Logged. Episode sync is skipped this run. The feed keeps serving the last saved data. Next run is in 3 hours. | `SyncJob` |
| One show is unusable (no title, unknown format, score out of range) | Logged and skipped. The rest of the page is saved. | `AnimeMapper`, `AnimePageSaver` |
| A database error while saving a page | That page of 50 is rolled back, counted in `failedPages`, and the next page continues. The next run saves it again. | `AnimeSyncService`, `EpisodeSyncService` |
| One 30-day episode window fails to download | Logged, counted in `failedWindows`, skipped. The other windows still run. | `EpisodeSyncService` |
| The sync runs again over the same data | Safe. Saves are upserts, and `UNIQUE (anilist_id)` and `UNIQUE (anime_id, episode)` stop duplicates. | `AnimePageSaver`, schema |
| A sync takes very long | The next run waits for it to finish, so runs never overlap. | `@Scheduled(fixedDelay)` |
| AniList is down while visitors browse | No effect. The feed reads only our database. | Architecture |
| Postgres is down during a feed request | The exception is not caught anywhere, so Spring's default error response (HTTP 500) is returned. | Not handled on purpose yet, see gaps |
| Tests | The sync is switched off, so tests never call AniList or write to the real database. | `src/test/resources/config/application.properties` |

---

## 10. Known gaps

Things that are true today and would surprise a reader of the diagrams. Each is recorded in [`../LOG.md`](../LOG.md) under "Decisions & Open Questions" or is a finding from checking the code.

- `YEAR_START` is hard-coded to 2026-01-01, so in 2027 "Finished this year" keeps growing instead of resetting.
- Shows that go on hiatus or are cancelled, and shows stuck as "not yet released" past their start date, are handled as described in section 6.
- `synced_at` is set on every row on every sync, so every row counts as changed and gets an `UPDATE`.
- Nothing stores or exposes whether the last sync worked. The only record is the log, and the API does not show how fresh the data is.
- No custom error handler and no CORS configuration (section 8).
- The episode sync downloads the schedule of every anime on AniList and keeps only ours. It is the slowest part. Asking AniList only for our shows (`mediaId_in`) is the noted option if it becomes a problem.
- Many shows have no English title, so the React card must fall back to the romaji title.
