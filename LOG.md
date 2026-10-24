# LOG.md: AnimeVerse Build Journal

> This is my log: what's been built, what's planned but not built yet, and what I learned along the way.
> CLAUDE.md is the handoff file for agents; this file is for me to look back on and reflect.
> Newest entries go at the top of each section.

---

## 🗺️ Planned (not implemented yet)
Ideas, features, and work we've agreed to do but haven't started.

- [ ] **V1: This Year in Anime.** Show every anime from the 2026 calendar year (Jan 1, 2026 to ongoing). Sort: airing now → finished this year (most recently ended first). Upcoming shows go in their own section under an "Upcoming" header. Every card is clearly labeled with genres, season, episodes, and a rating. No personal filtering in V1.
- [ ] **V2: Keep Up.** Sign in with Google, pick the anime I want to follow, and get an email whenever a new episode airs.
- [ ] Later / stretch: phone sign-up and SMS notifications (postponed)
- [ ] Design the V1 database schema
- [ ] Build the AniList sync job (pulls data into my Postgres DB on a schedule)
- [ ] Decide how AWS fits in (hosting, storage, DB, CI/CD)
- [ ] Scaffold `backend/` (Spring Boot)
- [ ] Scaffold `frontend/` (React)

## 🚧 In Progress
What's being worked on right now.

- Planning V1 and V2 scope and architecture

## ✅ Changelog (done)
What changed, when, and why.

### 2026-10-17
- Made the first commit and published the repo to GitHub (`abdulrahman1121/AnimeVerse`)
- Initialized a local git repo on `main` with my author identity
- Added `.gitignore` (Java/Spring, Node/React, IDEs, macOS, secrets, AWS/Terraform)
- Added `README.md`, `CLAUDE.md` (agent handoff), and this `LOG.md`

## 🤔 Decisions & Open Questions
Choices made (and why), plus questions still open.

- **Decided (Oct 18):** Data comes from the **AniList GraphQL API**. It has rich metadata plus the exact airing time of each episode, which V2 needs.
- **Decided (Oct 18):** A **scheduled sync job** copies AniList data into my own database, and the app reads only from my DB. This makes pages fast, keeps me under AniList's rate limits, and the site still works if AniList goes down.
- **Decided (Oct 18):** **PostgreSQL** (on AWS RDS). My data is relational (anime↔genres, users↔subscriptions), it's the standard Spring Boot + AWS pairing, and it's a new combo for me to learn.
- **Decided (Oct 18):** V2 starts with **Google sign-in + email**. SMS is postponed because texting costs money per message and needs US carrier registration.
- **Decided (Oct 17):** Backend is Java Spring Boot; frontend is React.
- **Decided (Oct 17):** Commits happen together, after I review the changes and the message.
- **Decided (Oct 18):** Shows that started before 2026 but are **still airing count**. People are watching them right now.
- **Decided (Oct 18):** Cards show the **broadcast season** (e.g. "Spring 2026") plus **aired/total episodes** (e.g. "Ep 8 / 12"). Franchise season numbers ("Season 2") come later.
- **Decided (Oct 18):** Rating uses AniList's **averageScore**. Unrated shows store `NULL` in the DB and the card shows a **NEW** badge. I first wanted to show 100%, but that would rank unrated shows above the best-reviewed ones and mix fake data in with real scores.
- **Open:** AWS hosting for the backend and frontend.

## 💭 Reflections & Lessons Learned
What I learned, what surprised me, what I'd do differently.

- _2026-10-18:_ Don't store made-up values to fill a gap. `NULL` means "unknown" in SQL; keep the data honest and let the UI decide how to display it (the "NEW" badge). Remember `NULLS LAST` when sorting.
- _2026-10-18:_ At ~150–300 anime per year, any database is fast. The real question is which data model fits my queries. Mine are filter-and-join, which points to SQL.
- _2026-10-17:_ Set up `.gitignore` before the first commit; it's much easier than removing build output or secrets from history later.
