# Fan Network — project guide for Claude

AI-hosted fan network: topic agents publish verified news, host live rooms during big events, and turn the best fan takes into recaps.

The full product and architecture brief is in @docs/master-prompt.md. Read it at the start of every session. Its §12 ("What I want from you first") applied only to the first session; after that, follow the current phase in `docs/progress.md`.

## Where we are

- Current phase, finished tasks and next task: `docs/progress.md`. Read it at the start of every session and update it at the end of every task.
- Architecture output: `docs/architecture.md`
- Decisions: one short file per decision in `docs/decisions/` (problem, options, choice, why)

## How we work

- One task at a time. Explain the problem, give 2–3 options, recommend one, then implement after I agree.
- Never generate a whole module or many files in one go. Small, reviewable changes.
- Every task ends with passing tests and a one-line commit message I can use.
- I know Java, Spring Boot and React well: keep explanations of application code short.
- I am learning LLM engineering and agent pipelines: for any AI/agent concept, add a **Learn** block before the code and **Check your understanding** questions after it (see master prompt §11). Give me the exercise and wait for my attempt before showing the full solution.

## Stack (do not change without a decision record)

- Backend: Java 21, Spring Boot, Spring Security, Spring Data MongoDB, Spring AI
- Frontend: React, TypeScript, Vite, PWA
- Data: MongoDB replica set (Atlas local image in Docker), Redis, Kafka, MinIO
- Tests: JUnit 5, Mockito, Testcontainers; Vitest + React Testing Library

## Rules

- Modular monolith; a module never reads another module's collections directly.
- Every LLM call goes through our `LLMClient` interface; tests use the mock. No real LLM or network calls in tests.
- Secrets only in environment variables or `.env` (git-ignored). Never commit keys.
- Prompts are versioned template files under `src/main/resources/prompts/`.

## Commands

(Fill in once the project is scaffolded.)
- Start infrastructure: `docker compose up -d`
- Backend: 
- Frontend: 
- All tests: