# Architecture

How AnimeVerse works, with the focus on **data flow and how data changes over time**: where data enters, how it moves between components, where it is stored, who can read or write it, and what happens when something fails.

Everything here is marked **`built`** (exists in `main` and was run) or **`planned`** (decided or intended, not written yet). Nothing planned is drawn as if it exists. In diagrams, planned parts have dashed outlines or dashed arrows and the word "planned" in their label, so the meaning never depends on color.

## Files

| File | Covers |
|---|---|
| [`data-flow.md`](./data-flow.md) | Mermaid diagrams with a plain-language explanation of each: system overview, the sync run, saving one page, one AniList request with retries, one feed request, how a show moves between feed sections, and the data model (ERD). Also who reads and writes what, what happens on failure, and known gaps. |
| [`changes.md`](./changes.md) | Dated log of architecture changes: what changed, why, and where the decision is recorded. |

## Status

- Last checked against the code: **2026-11-20**, `main` at `aa0ef57` (PR #6, the feed API).
- Built: the Postgres schema, the AniList sync, the three feed endpoints.
- Planned: the React frontend, deployment on AWS, and everything in V2 (Google sign-in, follow list, email notifications).

## How to update these docs

1. **Same PR as the change.** When a decision is made or something that affects data flow is built, update these files in the same commit or PR as the code.
2. **Check against the code, not memory.** Each diagram below was written from these files; reopen them when you change a diagram.

   | Diagram | Checked against |
   |---|---|
   | System overview | `docker-compose.yml`, `application.properties`, `SyncJob`, `FeedController` |
   | Sync run | `SyncJob`, `AnimeSyncService`, `EpisodeSyncService` |
   | Saving one page | `AnimePageSaver`, `AiringEpisodePageSaver`, `AnimeMapper` |
   | AniList request | `AniListClient` |
   | Feed request | `FeedController`, `FeedService`, `AnimeRepository`, `AiringEpisodeRepository`, `AnimeCard` |
   | Feed sections over time | `AnimeRepository` queries, `AnimeSyncService` |
   | Data model | `V1__create_v1_schema.sql` (never edit it, add `V2__` files instead) |

3. **Flip the status** from `planned` to `built` only when the code is merged and has been run.
4. **Log it** in [`changes.md`](./changes.md) with the date, the reason and where the decision is written down.
5. **One diagram, one subject.** Split a diagram that grows past one screen instead of making it bigger.

All diagrams are Mermaid inside Markdown: GitHub renders them, and they diff cleanly in git.

> Note: `CLAUDE.md` (the agent handoff file with the Decisions Log) is listed in `.gitignore`, so it lives only on the developer's machine. The decisions are therefore also recorded in [`../LOG.md`](../LOG.md) under "Decisions & Open Questions", and `changes.md` points there.
