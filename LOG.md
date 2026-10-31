# LOG.md: AnimeVerse Build Journal

> This is my log: what's been built, what's planned but not built yet, and what I learned along the way.
> CLAUDE.md is the handoff file for agents; this file is for me to look back on and reflect.
> Newest entries go at the top of each section.

---

## 🗺️ Planned (not implemented yet)
Ideas, features, and work we've agreed to do but haven't started.

- [ ] **V1: This Year in Anime.** Show every anime from the 2026 calendar year (Jan 1, 2026 to ongoing). Sort: airing now → finished this year (most recently ended first). Upcoming shows go in their own section under an "Upcoming" header. Every card is clearly labeled with genres, season, episodes, and a rating. No personal filtering in V1.
- [ ] **V2: Keep Up.** Sign in with Google, pick the anime I want to follow, and get an email whenever a new episode airs. Also email followers if a show goes on hiatus or gets cancelled.
- [ ] Later / stretch: phone sign-up and SMS notifications (postponed)
- [ ] Decide how AWS fits in (hosting, storage, DB, CI/CD)
- [ ] Scaffold `frontend/` (React)

## 🚧 In Progress
What's being worked on right now.

- AniList sync job (branch `feat/anilist-sync`): response models ✅, HTTP client ✅, mapper to entities ✅, sync service for shows + genres ✅, airing episodes ✅, 3-hour schedule ☐

## ✅ Changelog (done)
What changed, when, and why.

### 2026-10-31
- Wrote `EpisodeSyncService` and `AiringEpisodePageSaver`: fetch the airing schedule for the whole year so far plus two weeks ahead, save it in pages of 50 (one transaction per page), keep only episodes of shows I already track, and update the air time if AniList reschedules an episode. 2 unit tests and 3 integration tests.
- Replaced the aired-episode count query with `findMaxAiredEpisode`, a JPQL `@Query` returning the highest episode number that has aired. Updated `AnimeRepositoryTest` to match.

### 2026-10-26
- Wrote `AnimePageSaver` and `AnimeSyncService`. The service fetches the releasing, finished-this-year and upcoming lists, merges them by AniList id, cuts them into pages of 50, and saves each page in its own transaction. Shows are inserted or updated, and genres are mirrored from AniList. 3 unit tests (hand-written fakes) and 3 integration tests against Postgres.
- Wrote `AnimeMapper` and `UnmappableMediaException`: turns AniList's JSON shape into my `Anime` entity, with validation before anything is changed. 9 unit tests.
- Wrote `AniListClient`: posts GraphQL queries with `RestClient`, walks every page until `hasNextPage` is false, waits as long as AniList's `Retry-After` says on HTTP 429 (3 attempts), and has connect/read timeouts. 8 tests run against a fake AniList server.
- Checked all my queries against the real AniList API with `curl`.

### 2026-10-25
- Added JPA entities `Anime`, `Genre`, `AiringEpisode` and enums for status, format, season and date precision. `ddl-auto=validate` confirmed they match the tables.
- Added Spring Data repositories with derived queries (`findByAnilistId`, `findByName`, `findByAnimeAndEpisode`, `countByAnimeIdAndAiringAtLessThanEqual`).
- Wrote my first real tests (`AnimeRepositoryTest`): genres save and load back, the aired-episode count is right, and deleting a show cascades to its episodes but keeps the genre.
- Merged PR #3 (V1 schema migration).
- Wrote `V1__create_v1_schema.sql`: the 4 tables with types, NOT NULLs, CHECK rules, cascading deletes, and indexes for the feed queries.
- Merged PR #2 (backend scaffold + local Postgres).

### 2026-10-24
- Added `docker-compose.yml` (Postgres 18 in a container, data kept in a named volume) and the DB connection config in `application.properties`. Credentials live in a gitignored `.env` that both Docker Compose and Spring read. The app now starts, connects, and `./mvnw test` passes.
- Fixed a startup crash: Postgres rejected the time zone `US/Pacific`. The app now runs in UTC (a `static` block in `BackendApplication` for normal runs, Surefire's `-Duser.timezone=UTC` for tests).
- Generated the Spring Boot backend in `backend/` (Maven, Spring Boot 4.1.2, Java 25 LTS) with Web MVC, Data JPA, PostgreSQL driver, Flyway and Validation. Removed unused `static/`, `templates/` and `HELP.md`.
- Merged PR #1 (schema decisions docs) with squash merge, my first pull request.

### 2026-10-17
- Made the first commit and published the repo to GitHub (`abdulrahman1121/AnimeVerse`)
- Initialized a local git repo on `main` with my author identity
- Added `.gitignore` (Java/Spring, Node/React, IDEs, macOS, secrets, AWS/Terraform)
- Added `README.md`, `CLAUDE.md` (agent handoff), and this `LOG.md`

## 🤔 Decisions & Open Questions
Choices made (and why), plus questions still open.

- **Decided (Oct 31):** The episode sync fetches **the whole year (Jan 1, 2026 to two weeks ahead)** on every run, not just a recent window. Airing shows rarely have a long episode history now and finished shows barely pass 25 episodes, so the volume is small, and one rule works for every run with no separate first-time backfill. I'll log the real request count on the first run to check that.
- **Decided (Oct 31):** Episodes are saved **only for shows already in my anime table**. AniList's schedule covers every anime, so rows for other shows are skipped. This also means the episode sync must run after the show sync, and if the show sync fails, the episode sync is **skipped for that run**: no show, no saved episode, and the next run catches up.
- **Decided (Oct 31):** The window is a time range, not "from the beginning". A long runner like One Piece only gets the ~40 episodes that aired inside it, which is why "Ep X" uses the highest aired episode number (`MAX`) and not a row count.

- **Decided (Oct 26):** Each page of up to 50 shows is saved in **one transaction per page**. A database error rolls back only that page and the next sync retries it. One transaction per show would lose less on an error but means far more commits, and per-page is the usual best practice.
- **Decided (Oct 26):** The page saver is its **own Spring bean**. `@Transactional` works through a proxy, so a method calling another method on its own class would skip it. The sync service has no transaction itself, so none stays open while waiting on the network.
- **Decided (Oct 26):** A show that **can't be mapped** (missing title, unknown format, score outside 0..100) is **logged and skipped**, so one bad record never stops the sync. The mapper checks everything before it changes anything.
- **Decided (Oct 26):** Genres **mirror AniList**: a genre AniList drops from a show is unlinked, and a new one is created if it doesn't exist yet. The genre rows themselves are never deleted.
- **Decided (Oct 26):** A score of **0 is treated as unrated** and stored as `NULL`, so the card shows NEW instead of a misleading 0. Episode counts of 0 or less also become `NULL`.
- **Decided (Oct 26):** Every sync is a **full refresh** of the shows V1 displays, because I can't tell which ones changed without fetching them. Duplicates across the three lists are removed by AniList id, first list wins, and an "upcoming" show with no start date is skipped.
- **Open (Oct 26):** AniList's RELEASING filter only returns shows that are airing right now. If a show I stored goes on **hiatus or is cancelled**, it never comes back in my fetch, so its stored status goes stale. V1 doesn't care, but V2's hiatus and cancellation emails will need a way to re-check the shows I already have.
- **Open (Oct 26):** `synced_at` is stamped on every row every sync, so every row counts as changed and gets an UPDATE. I could later skip rows where nothing meaningful changed.

- **Decided (Oct 18):** Data comes from the **AniList GraphQL API**. It has rich metadata plus the exact airing time of each episode, which V2 needs.
- **Decided (Oct 18):** A **scheduled sync job** copies AniList data into my own database, and the app reads only from my DB. This makes pages fast, keeps me under AniList's rate limits, and the site still works if AniList goes down.
- **Decided (Oct 18):** **PostgreSQL** (on AWS RDS). My data is relational (anime↔genres, users↔subscriptions), it's the standard Spring Boot + AWS pairing, and it's a new combo for me to learn.
- **Decided (Oct 18):** V2 starts with **Google sign-in + email**. SMS is postponed because texting costs money per message and needs US carrier registration.
- **Decided (Oct 17):** Backend is Java Spring Boot; frontend is React.
- **Decided (Oct 17):** Commits happen together, after I review the changes and the message.
- **Decided (Oct 25):** The sync calls AniList with **Spring's `RestClient`**: plain HTTP POST with a GraphQL body. I see exactly what goes over the wire, and it needs no new dependency.
- **Decided (Oct 25):** Sync scope: **no adult titles** (friendly for anyone), **every format except MUSIC**, and **Upcoming covers only the next season** (about 3 months ahead).
- **Decided (Oct 26):** Upcoming = not-yet-released shows with a start date **after Dec 31, 2025 and before the end of next season**. The lower bound is what keeps undated shows out, because AniList's `startDate_lesser` alone also matches shows with no date.
- **Decided (Oct 25):** The sync runs **every 3 hours + once at startup**.
- **Decided (Oct 25):** "Ep X" = the **highest episode number that has aired**, not a count of rows. Counting breaks for shows like One Piece (1,000+ episodes) whose full history isn't stored.
- **Decided (Oct 25):** Shows that drop out of AniList's results are **left in place** for V1. It's rare, and the feed queries filter anyway.
- **Decided (Oct 25):** Code is organized **by feature** (`anime/`, later `sync/`), not by layer, so related code sits together.
- **Decided (Oct 25):** `Anime` ↔ `Genre` is a `@ManyToMany` **`Set`**. A Set can't hold duplicates, which matches the join table's primary key, and Hibernate handles Sets better than Lists for many-to-many.
- **Decided (Oct 25):** Episodes link **one way** (`AiringEpisode` → `Anime`). "Ep X / Y" comes from a count query, so the app never loads every episode just to show one number.
- **Decided (Oct 25):** Genres change only through `addGenre` / `removeGenre`; `getGenres()` returns a read-only view.
- **Decided (Oct 25):** IDs are **`bigint GENERATED ALWAYS AS IDENTITY`**: the modern SQL standard, and Postgres refuses hand-picked IDs.
- **Decided (Oct 25):** Text columns use **`text`**, not `varchar(n)`. Same speed in Postgres, and no made-up length limit for a long title to break.
- **Decided (Oct 25):** Fixed lists (status, format, season, precision) are **`text` + `CHECK`**. Easy to extend with a migration, and they map to Java enums.
- **Decided (Oct 25):** Deleting a show **cascades** to its genre links and episodes. Deleting a genre still linked to shows is refused.
- **Decided (Oct 25):** Points in time are **`timestamptz`**, small numbers are `smallint`, columns are `NOT NULL` unless "unknown" is a real state, and indexes match the feed's filters and sorts.
- **Decided (Oct 24):** The backend **runs in UTC**. Store and process times in UTC, convert to the viewer's local time only in the UI. That's the server standard, and the app behaves the same on my Mac and on AWS.
- **Decided (Oct 24):** Hibernate `ddl-auto=validate`: **only Flyway changes the schema**. Hibernate just checks that my Java classes match the tables.
- **Decided (Oct 24):** No `.env.example`. This repo isn't meant for others to clone and run.
- **Decided (Oct 24):** Build with **Maven**. It's the most common in jobs and Spring docs, and `mvnw` (the Maven wrapper) means nobody needs Maven installed.
- **Decided (Oct 24):** **Java 25 (LTS)** and **Spring Boot 4.1.2 (stable)**. I first generated with Java 27 and Boot 4.2.0-RC1. 27 isn't long-term support, and RC means not officially released.
- **Decided (Oct 24):** Local Postgres runs in **Docker Compose**, so the setup lives in the repo.
- **Decided (Oct 24):** Left out Lombok and Docker Compose Support so I see the plain code and the plain DB connection first.
- **Decided (Oct 23):** V1 schema has 4 tables: `anime`, `genre`, `anime_genre`, `airing_episode`. Studios are left out until a feature needs them.
- **Decided (Oct 23):** Primary key is **our own `id`**, with AniList's ID kept as a separate unique `anilist_id` column. I don't want my database tied to AniList's numbering.
- **Decided (Oct 23):** Genres use **join tables** (`genre` + `anime_genre`). It's the cleaner relational design, each genre name is stored once, and it's the same many-to-many pattern V2's follow list will use.
- **Decided (Oct 23):** "Airing now" sorts by **newest premiere first** to highlight brand-new shows.
- **Decided (Oct 23):** **Hiatus and cancelled shows are hidden in V1.** In V2, followers get an email when a show they follow changes to one of those.
- **Decided (Oct 23):** Partial dates use **a normal `date` plus a precision column** (`DAY` / `MONTH` / `YEAR`). Month and year are what matter; this keeps sorting easy and the display honest ("April 2027", not "April 1").
- **Decided (Oct 23):** Every big addition gets its **own branch** with a short, clear name. It merges into `main` through a **GitHub pull request** using **squash merge**: I already review every commit carefully, so `main` gets one clean commit per feature, and the PR page keeps the step-by-step detail.
- **Decided (Oct 18):** Shows that started before 2026 but are **still airing count**. People are watching them right now.
- **Decided (Oct 18):** Cards show the **broadcast season** (e.g. "Spring 2026") plus **aired/total episodes** (e.g. "Ep 8 / 12"). Franchise season numbers ("Season 2") come later.
- **Decided (Oct 18):** Rating uses AniList's **averageScore**. Unrated shows store `NULL` in the DB and the card shows a **NEW** badge. I first wanted to show 100%, but that would rank unrated shows above the best-reviewed ones and mix fake data in with real scores.
- **Open:** AWS hosting for the backend and frontend.

## 💭 Reflections & Lessons Learned
What I learned, what surprised me, what I'd do differently.

- _2026-10-31:_ **`COUNT` vs `MAX` depends on what's stored.** Because I only store this year's episodes, counting rows would show One Piece as "Ep 40". The highest aired episode number shows the real "Ep 1168". Pick the aggregate that matches what the data actually holds.
- _2026-10-31:_ **`@Query` and JPQL.** Spring Data can build a query from a method name, but not something like `max`. A `@Query` takes JPQL, which names the Java entity and its fields (`AiringEpisode`, `e.airingAt`), not the table and columns. An aggregate over zero rows returns null, which Spring turns into `Optional.empty()`.
- _2026-10-31:_ **A bulk fetch returns more than I keep.** AniList's schedule query covers every anime, so many rows belong to shows I don't track. Look each show up once per page and skip the rest, so the cost scales with the page, not with every row.

- _2026-10-26:_ **A transaction is "all or nothing"**, like a bank transfer where both the withdrawal and the deposit happen or neither does. Each commit costs a disk-safety write, so I group shows into pages of 50 and commit once per page, not once per show. A **page** is just a chunk of the fetched list; one sync of about 250 shows is about 5 pages and 5 commits.
- _2026-10-26:_ **A proxy is a stand-in object in front of the real one**, like a receptionist who writes "call started" before putting me through and "call finished" after. Spring wraps `@Transactional` beans in one to open and commit the transaction. Calling a method on my own class skips the receptionist, so no transaction opens. That's why the saver is its own class.
- _2026-10-26:_ **Validate before you change.** The mapper throws before it touches the entity, so catching a skipped show inside a transaction can't leave a half-updated row behind.
- _2026-10-26:_ **Hand-written fakes are a valid way to unit test.** A test subclass overrides just the methods the service calls, so I can test the paging logic with no Spring, database or network.

- _2026-10-26:_ **A mock only proves what I scripted.** My mocked tests passed even though the Upcoming query let undated shows through. Only the real API showed it. Mock for speed, then check once against the real thing.
- _2026-10-26:_ **Unit vs integration tests decide what needs Docker.** `AniListClientTest` and `PartialDateTest` start no Spring and no database. Only `@SpringBootTest` classes start the whole app, which connects to Postgres. `MockRestServiceServer` fakes the other end of the connection (AniList) and replaces the HTTP transport under `RestClient`. There's no web server of mine involved.
- _2026-10-26:_ **Paging and rate limits.** `perPage` is items per page, not the number of pages. AniList caps `total` at 5,000, so loop on `hasNextPage` and keep a safety cap on pages. GraphQL reports bad queries as HTTP 200 with an `errors` list, and rate limiting as HTTP 429 with `Retry-After`. Inject the HTTP builder and the "sleeper" so tests need no network and never really wait.
- _2026-10-25:_ GraphQL: one URL, and you POST a query listing exactly the fields you want; the response has that exact shape. I checked AniList's real response with `curl` before writing any Java. `airingAt` is a Unix timestamp (seconds since 1970 UTC). Airing shows often have `episodes: null` (total not announced) and ONAs can have `season: null`.
- _2026-10-25:_ **Encapsulation is choosing which changes are allowed**, not adding a getter and setter for every field. The constructor takes exactly the NOT NULL fields, there's no `setId`, dates are set in pairs, and genres change only through two methods.
- _2026-10-25:_ JPA needs a no-arg constructor (Hibernate creates an empty object, then fills it). Make it `protected` so my own code can't build an invalid object. With **field access** (annotations on fields), Hibernate uses the fields directly, which is why a read-only getter doesn't break it.
- _2026-10-25:_ `@Enumerated(STRING)`, never ORDINAL: reordering an enum would silently change what old rows mean. `@ManyToOne` is EAGER by default, so set it to LAZY.
- _2026-10-25:_ Spring Data writes the repository code from the method name. In tests, `@Transactional` rolls everything back, and `flush()` + `clear()` force reads to really hit the database.
- _2026-10-25:_ An index isn't an extra column. It's a separate **sorted** copy of the indexed columns plus pointers to the rows (a B-tree), so Postgres jumps to the first match and walks forward. `UNIQUE` creates one automatically because it must check for duplicates on every write. Small tables use Seq Scan anyway because it's cheaper.
- _2026-10-25:_ The database is the last line of defense. Types and `CHECK` rules apply to every write, whatever code does it. A score of 140 or a status typo can never get stored.
- _2026-10-25:_ Postgres auto-indexes primary keys and `UNIQUE` columns, but **not foreign keys**. Index what you filter, sort and join on.
- _2026-10-25:_ A Flyway migration is never edited after it has run. Changes go in a new `V2__` file, the same way commits work.
- _2026-10-24:_ **Time-zone bug.** Java picks up my Mac's zone (`US/Pacific`) and the Postgres driver sends it on every connection. The Postgres 18 image doesn't include old alias names like that one, so it refused the connection. To read a stack trace, go straight to the last `Caused by:` line.
- _2026-10-24:_ **A `static` block only runs when a class is initialized** (e.g. `main()` uses it), not when Spring just reads its annotations. That's why my UTC fix worked for `spring-boot:run` but not for tests, which needed Surefire's `argLine`.
- _2026-10-24:_ **Stale class file bug.** VS Code compiled into `target/` for Java 27 (class file version 71), and my Java 25 runtime only supports up to 69. Fixes: `./mvnw clean`, plus VS Code settings so it follows `pom.xml`. When a build makes no sense after a config change, try `clean` first.
- _2026-10-24:_ `.java` is source; `javac` compiles it to `.class` bytecode, which the JVM runs on any machine. Every class file starts with `cafe babe`. Commit what people write, ignore what tools generate (`target/`).
- _2026-10-24:_ Release suffixes matter: `-SNAPSHOT`, `-M1` and `-RC1` are unreleased builds. LTS Java versions (21, 25…) are what companies run. Spring Boot's `<parent>` picks compatible library versions, so my dependencies list none.
- _2026-10-23:_ Many-to-many relationships (a show has many genres, a genre has many shows) need a third "join" table holding one row per link. A **join** in SQL follows those foreign keys from one table to the next.
- _2026-10-18:_ Don't store made-up values to fill a gap. `NULL` means "unknown" in SQL; keep the data honest and let the UI decide how to display it (the "NEW" badge). Remember `NULLS LAST` when sorting.
- _2026-10-18:_ At ~150–300 anime per year, any database is fast. The real question is which data model fits my queries. Mine are filter-and-join, which points to SQL.
- _2026-10-17:_ Set up `.gitignore` before the first commit; it's much easier than removing build output or secrets from history later.
