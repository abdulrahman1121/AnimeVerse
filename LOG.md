# LOG.md: AnimeVerse Build Journal

> This is my log: what's been built, what's planned but not built yet, and what I learned along the way.
> CLAUDE.md is the handoff file for agents; this file is for me to look back on and reflect.
> Newest entries go at the top of each section.

---

## 🗺️ Planned (not implemented yet)
Ideas, features, and work we've agreed to do but haven't started.

- [ ] Define the AnimeVerse feature scope (MVP vs. stretch goals)
- [ ] Choose a database (discussion pending)
- [ ] Decide how AWS fits in (hosting, storage, DB, CI/CD)
- [ ] Scaffold `backend/` (Spring Boot)
- [ ] Scaffold `frontend/` (React)

## 🚧 In Progress
What's being worked on right now.

- Initial repo setup and first commit; publish the repo to GitHub (`abdulrahman1121/AnimeVerse`)

## ✅ Changelog (done)
What changed, when, and why.

### 2026-10-17
- Initialized a local git repo on `main` with my author identity
- Added `.gitignore` (Java/Spring, Node/React, IDEs, macOS, secrets, AWS/Terraform)
- Added `README.md`, `CLAUDE.md` (agent handoff), and this `LOG.md`

## 🤔 Decisions & Open Questions
Choices made (and why), plus questions still open.

- **Decided:** Backend is Java Spring Boot; frontend is React.
- **Decided:** Commits happen together, after I review the changes and the message.
- **Open:** Which database? Which AWS services? What's the MVP feature set?

## 💭 Reflections & Lessons Learned
What I learned, what surprised me, what I'd do differently.

- _2026-10-17:_ Set up `.gitignore` before the first commit; it's much easier than removing build output or secrets from history later.
