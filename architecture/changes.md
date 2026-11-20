# Architecture changes

Dated log of changes that affect the architecture or data flow. Newest first. Each entry says what changed, why, and where the decision is written down.

Decisions are recorded in [`../LOG.md`](../LOG.md) under "Decisions & Open Questions" (look for the date). The handoff file `CLAUDE.md` has a Decisions Log too, but it is gitignored and lives only on the developer's machine, so it is not linked here.

Dates are when the decision was recorded or the work was done. PR numbers are on GitHub: `abdulrahman1121/AnimeVerse`.

---

## 2026-11-20: feed API built, architecture docs added (PR #6, then this folder)

- **Changed:** three read-only endpoints (`GET /api/anime/airing`, `/finished`, `/upcoming`) now serve the feed from our own database. They return `AnimeCard` records with display text already prepared. Each section costs exactly 2 SQL statements. `FeedQueryCountTest` guards that number and includes a deliberate N+1 to prove the counter works. This `architecture/` folder was created.
- **Why:** one endpoint per section so one failure does not affect the others and the frontend can render each as it arrives. A DTO instead of the entity keeps internal fields (`anilistId`, `syncedAt`) in the backend. `join fetch` plus one grouped query avoids the N+1 problem. The docs were written after the merge so the diagrams describe what is actually built.
- **Status:** built (feed API). The frontend that consumes it is planned.
- **Decisions:** [`../LOG.md`](../LOG.md), the 2026-10-31 entries on three endpoints, the DTO, ready-made display text, no pagination and batched fetching.
- **Left open:** no CORS configuration and no custom error handler yet (see [`data-flow.md`](./data-flow.md), sections 8 and 10).

## 2026-10-31: AniList sync built (PR #5)

- **Changed:** a scheduled job copies shows and the airing schedule from AniList into Postgres. Shows are fetched first, then episodes. The job runs every 3 hours and once at startup, in the background. It is switched off in tests.
- **Why:**
  - `fixedDelay`, so runs can never overlap.
  - One transaction per page of 50, so a database error costs one page.
  - The airing schedule is fetched in 30-day windows, because AniList refuses more than 5,000 entries per query.
  - Retries with exponential backoff (3 attempts, 5 s then 10 s) only for failures that can fix themselves, because retrying a bad query would fail the same way. Retrying is safe because the requests only read.
  - "Ep X" is the highest aired episode number, not a count, because only this year's episodes are stored.
- **Status:** built, and run against the real AniList.
- **Decisions:** [`../LOG.md`](../LOG.md), the 2026-10-25, 2026-10-26 and 2026-10-31 entries.
- **Left open:** shows that go on hiatus or are cancelled are never re-fetched, so their stored status goes stale. V2's emails need a fix for this.

## 2026-10-25: JPA entities and repositories (PR #4), V1 schema (PR #3)

- **Changed:** the four V1 tables exist as a Flyway migration (`V1__create_v1_schema.sql`) and as Java entities and Spring Data repositories. Hibernate only validates the schema (`ddl-auto=validate`).
- **Why:** identity `bigint` keys and a separate `anilist_id` so we are not tied to AniList's numbering. `text` plus `CHECK` for fixed lists, so they are easy to extend. `timestamptz` for points in time. A date plus a precision column for partial dates. Cascading deletes from `anime` to its genre links and episodes. Indexes that match the feed's filters and sorts.
- **Status:** built.
- **Decisions:** [`../LOG.md`](../LOG.md), the 2026-10-23 and 2026-10-25 entries.

## 2026-10-24: backend scaffold and local database (PR #2)

- **Changed:** Spring Boot 4.1.2 on Java 25 (Maven) in `backend/`, and PostgreSQL 18 in Docker Compose with credentials in a gitignored `.env`. The app runs in UTC.
- **Why:** Flyway owns the schema so nothing changes silently. UTC is the server standard and behaves the same on a laptop and on AWS. Docker Compose keeps the database setup in the repo.
- **Status:** built.
- **Decisions:** [`../LOG.md`](../LOG.md), the 2026-10-24 entries.

## 2026-10-23: V1 schema agreed, branching workflow (PR #1)

- **Changed:** four tables agreed (`anime`, `genre`, `anime_genre`, `airing_episode`). Hiatus and cancelled shows are hidden in V1. Every big addition gets its own branch and merges through a squash-merged pull request.
- **Why:** genres through join tables, the same many-to-many pattern V2's follow list will use. A clean `main` with one commit per feature.
- **Status:** built (as the schema above).
- **Decisions:** [`../LOG.md`](../LOG.md), the 2026-10-23 entries.

## 2026-10-18: architecture direction

- **Changed:** decided to take data from the AniList GraphQL API into our own PostgreSQL through a scheduled sync, instead of calling AniList on every request. V2 starts with Google sign-in and email notifications. SMS is postponed.
- **Why:** fast indexed reads, staying under AniList's rate limits, and the site still works if AniList is down. AniList also has the exact air time of every episode, which V2 needs. SMS costs money per message and needs US carrier registration.
- **Status:** the sync design is built (see above). Frontend, AWS hosting and all of V2 are planned.
- **Decisions:** [`../LOG.md`](../LOG.md), the 2026-10-17 and 2026-10-18 entries.
