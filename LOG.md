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
- [ ] Write the V1 database schema as a real migration (design agreed; see Decisions)
- [ ] Build the AniList sync job (pulls data into my Postgres DB on a schedule)
- [ ] Decide how AWS fits in (hosting, storage, DB, CI/CD)
- [ ] Scaffold `frontend/` (React)

## 🚧 In Progress
What's being worked on right now.

- Backend scaffold + local Postgres (branch `chore/scaffold-backend`)

## ✅ Changelog (done)
What changed, when, and why.

### 2026-10-24
- Generated the Spring Boot backend in `backend/` (Maven, Spring Boot 4.1.2, Java 25 LTS) with Web MVC, Data JPA, PostgreSQL driver, Flyway and Validation. Removed unused `static/`, `templates/` and `HELP.md`.
- Merged PR #1 (schema decisions docs) with squash merge, my first pull request.

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

- _2026-10-24:_ Release suffixes matter: `-SNAPSHOT`, `-M1` and `-RC1` are unreleased builds. LTS Java versions (21, 25…) are what companies run. Spring Boot's `<parent>` picks compatible library versions, so my dependencies list none.
- _2026-10-23:_ Many-to-many relationships (a show has many genres, a genre has many shows) need a third "join" table holding one row per link. A **join** in SQL follows those foreign keys from one table to the next.
- _2026-10-18:_ Don't store made-up values to fill a gap. `NULL` means "unknown" in SQL; keep the data honest and let the UI decide how to display it (the "NEW" badge). Remember `NULLS LAST` when sorting.
- _2026-10-18:_ At ~150–300 anime per year, any database is fast. The real question is which data model fits my queries. Mine are filter-and-join, which points to SQL.
- _2026-10-17:_ Set up `.gitignore` before the first commit; it's much easier than removing build output or secrets from history later.
