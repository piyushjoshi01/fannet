# Progress

**Current phase:** Phase 1 — Foundation (in progress)
**Next task:** 1.2 Docker Compose (MongoDB Atlas local, Redis, Kafka, MinIO, `.env.example`)

Architecture and decisions: `docs/architecture.md`. Tasks marked **[Learn]** get a Learn block before the code and Check-your-understanding questions after it. Each task ends with passing tests and a one-line commit message.

## Done

- [x] §12 analysis written to `docs/architecture.md`
- [x] Phase 1 task list written
- [x] 1.1 Backend scaffolded (Maven, Spring Boot 3.5.16, Java 21, 14 module packages, health test). Build with JDK 21: `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home mvn test` in `backend/`

## Phase 1 — Foundation

Backend scaffold and infrastructure
- [x] 1.1 Scaffold the Spring Boot backend (Java 21, modules as empty packages, health endpoint test)
- [ ] 1.2 Docker Compose: MongoDB Atlas local (replica set), Redis, Kafka, MinIO, plus `.env.example`
- [ ] 1.3 Mongo connection, Mongock set-up and the first migration, plus the Testcontainers base test class (decision record: Mongock vs startup indexes)
- [ ] 1.4 Global exception handling, validation, structured logging with trace id

Accounts and auth
- [ ] 1.5 `Account` model (HUMAN/AGENT, embedded `humanAuth` / `agentConfig`), repository, unique indexes (decision record: Account abstraction)
- [ ] 1.6 Register and login, Argon2/BCrypt, age gate
- [ ] 1.7 JWT access and refresh tokens with rotation, Spring Security config, roles USER/ADMIN/SYSTEM, agents cannot log in
- [ ] 1.8 Agent seed: 3 agents from configuration (one `@footballagent` plus two clubs)

Social core
- [ ] 1.9 Follow and unfollow (unique index, counters with atomic `$inc`)
- [ ] 1.10 Onboarding: pick interests, auto-follow matching agents
- [ ] 1.11 Posts: create a text post, own-content rules, AI flag and `sources[]` fields in the model
- [ ] 1.12 Chronological feed with cursor pagination behind `FeedRankingStrategy`, with `explain()` on the main query
- [ ] 1.13 Likes and comments (unique like, atomic counters)

LLM foundation
- [ ] 1.14 **[Learn]** LLM basics: tokens, context window, temperature, cost and latency (reading plus a small exercise, no app code)
- [ ] 1.15 **[Learn]** Call an LLM API raw with `RestClient` (a throwaway spike, with timeout)
- [ ] 1.16 **[Learn]** Spring AI `ChatClient` call, and compare it with the raw call
- [ ] 1.17 **[Learn]** `LLMClient` interface with `MockLLMClient` and the real provider implementation, a prompt template loader (`resources/prompts/`), and tests that use only the mock

Frontend
- [ ] 1.18 Scaffold the frontend (Vite, React, TS, router, TanStack Query, Vitest and RTL, PWA plugin)
- [ ] 1.19 Frontend auth: register/login screens, token handling, a protected route
- [ ] 1.20 Frontend onboarding and the feed (follow, like, comment, create post), with AI badge on agent accounts

Wrap-up
- [ ] 1.21 Fill in the Commands section of `CLAUDE.md`, and run the end-to-end flow (register → onboarding → feed → post) manually

**Phase 1 exit criteria:** steps 1–4 of the beta flow (§2 of the master prompt) work end to end, `LLMClient` works with both the mock and the real provider, and all tests pass without network access.

## Later phases (summary, see architecture.md §19)

- Phase 2 — First agent post
- Phase 3 — Pipeline quality
- Phase 4 — Live rooms
- Phase 5 — Hardening
