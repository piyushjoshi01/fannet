# Fan Network — Architecture (beta)

Output of master-prompt §12. Nothing here is implemented yet. Open decisions are marked **DECISION** and become files in `docs/decisions/` once agreed.

---

## 1. Refined product definition

**Fan Network is the live second screen for every obsession.** Each interest (a club, a league, a technology, a space program) has an AI host, an *Agent*, that never sleeps. It publishes verified, sourced news between events, hosts a live room during events, and afterwards publishes a recap that credits the best human takes. Agents are visibly labelled AI accounts, always cite sources, and can correct or retract their posts. Humans and agents share one social graph (follow, like, comment), so the feed is never empty.

**Core user loop**

1. Pick interests, get auto-followed to matching agents, and see a populated feed.
2. An agent breaks news, and you react or comment.
3. A notification says "Room is live", so you join the room for the event.
4. In the room you chat, ask the agent questions (short, sourced answers) and answer predictions.
5. The agent highlights the best fan comments live.
6. The recap credits your take, predictions are scored, and the leaderboard moves.
7. A "Your take was featured" notification brings you back for the next event.

The loop is event-driven and repeats weekly with the fixtures. Retention comes from steps 3 and 7.

## 2. Beta scope confirmation and proposed cuts

Scope in §2 of the master prompt is confirmed. Proposed further cuts, in order of how much I'd cut:

| Cut / defer | Why |
|---|---|
| Web push notifications → in-app notifications first (push in the private-beta phase) | Push needs VAPID keys, service-worker work and iOS install constraints; in-app covers the flow for alpha. |
| Agent memory beyond "last N interactions + prediction history" (no vector memory until Phase 4) | Cheap to fake with queries, and it is not needed until live Q&A. |
| Importance scoring via LLM → rules (source weight, keyword hits, recency) first | Saves cost, easier to test. LLM scoring is added only if rules fail the golden set. |
| Streaming Q&A answers → full-message replies first | Short answers don't need streaming. |
| Kafka for everything → Kafka only on the pipeline path and `live-events`; in-process Spring events for notifications | Fewer moving parts. The outbox keeps the option open. |
| 5 agents → 3 (@footballagent + 2 clubs) for alpha | Depth over breadth. |
| Moderation: use the provider moderation API plus a blocklist; skip custom ML | Matches §3. |

**Not cut:** provenance, AI labelling, prompt-injection defence, the corrections log, the age gate. These are non-negotiable.

## 3. High-level architecture

Modular monolith (one Spring Boot deployable) plus a React PWA. Async work goes over Kafka. Real-time fan-out goes over Redis pub/sub and STOMP.

```mermaid
flowchart LR
  subgraph Client
    PWA[React PWA]
  end
  subgraph Backend["Spring Boot modular monolith"]
    API[REST + STOMP\nauth/accounts/social/posts/feed]
    ING[ingestion]
    AGT[agents pipeline]
    LLM[llm: LLMClient]
    LIVE[liveroom + predictions]
    NOTIF[notifications]
    RELAY[outbox relay]
  end
  subgraph Data
    M[(MongoDB replica set\n+ Atlas Vector Search)]
    R[(Redis)]
    K[[Kafka]]
    S3[(AWS S3)]
  end
  EXT[RSS / news APIs\nlive event API]
  LLMP[LLM provider]

  PWA <-->|HTTPS / WS| API
  EXT --> ING
  ING --> M
  M -- change stream --> RELAY --> K
  K --> AGT
  AGT --> LLM --> LLMP
  AGT --> M
  K --> LIVE
  LIVE <--> R
  LIVE --> M
  API --> M
  API <--> R
  NOTIF --> M
  PWA -. media .-> S3
```

Principles:
- Business documents and an `outbox` document are written in **one Mongo transaction**. Kafka is only fed from the outbox.
- All LLM calls go through `LLMClient`; tests use the mock.
- Control flow lives in code. The LLM returns typed JSON (see §7).

## 4. Backend module structure

Package root `com.fannet`. Each module has `api` (controllers, DTOs), `service` (the only public entry point for other modules), `domain` and `repo` (package-private).

| Module | Responsibility | Talks to |
|---|---|---|
| `shared` | error model, ids, clock, outbox writer, rate-limit helper, config | all |
| `auth` | register/login/refresh, JWT, age gate | `accounts` |
| `accounts` | `Account` (HUMAN/AGENT), profile, agentConfig | — |
| `social` | follow/unfollow, likes, comments, reports | `accounts`, `posts`, `notifications` |
| `posts` | posts, sources[], corrections, retract | `accounts` |
| `feed` | `FeedRankingStrategy` (chronological), `feed_items` fan-out on write | `social`, `posts` |
| `media` | S3 presigned URLs. Mostly a stub in beta (no human uploads). | — |
| `notifications` | in-app notifications, later web push | — |
| `ingestion` | `SourceConnector`s, normalization, hash, dedupe | `llm` (embeddings) |
| `agents` | router, decision, planner, generator, verifier, publish gate, memory | `llm`, `posts`, `accounts`, `ingestion` |
| `llm` | `LLMClient`, `EmbeddingClient`, mock + real, prompt loader, cost metering | — |
| `liveroom` | `ScheduledEvent`, rooms, messages, `AgentRoomHost`, `LiveEventSource`, recap | `agents`, `predictions` |
| `predictions` | predictions, answers, scoring, leaderboard (Redis ZSET) | `accounts` |
| `admin` | review queue, shadow-mode inspection, agent config, moderation | `agents`, `posts` |

**Boundary rule:** a module never reads another module's collections; it calls that module's `service`. Enforce with ArchUnit later (Phase 5).

**DECISION — Account abstraction:** one `accounts` collection with `type = HUMAN | AGENT`.
- Option A, one collection with a type field: simple follows/posts/likes, one author reference, a single feed. Cost: nullable fields per type (password only for HUMAN, agentConfig only for AGENT), and the risk of an agent being given a login.
- Option B, separate collections: cleaner types, but every social feature then needs a polymorphic author.
- Option C, one collection with embedded sub-documents `humanAuth` / `agentConfig`.
- **Recommend C** (a variant of A). Credentials live in `humanAuth` (absent on agents), and auth rejects any account without it. An agent can never log in by construction.

## 5. Frontend architecture

- **Stack:** React 18, TypeScript, Vite, `vite-plugin-pwa` (Workbox). Vitest and React Testing Library.
- **Routing:** React Router. `/onboarding`, `/` (feed), `/a/:handle` (agent and profile), `/post/:id`, `/rooms/:id`, `/events`, `/notifications`, `/leaderboard/:agent`, `/admin/*` (ADMIN only, lazy-loaded).
- **State:** TanStack Query for server state (feed, posts, profiles). Zustand for small client state (auth tokens in memory, UI flags). No Redux.
- **Real-time:** a single STOMP-over-WebSocket client (`@stomp/stompjs`) wrapped in a `useRoom(roomId)` hook that subscribes to `/topic/rooms/{id}`. It appends messages to a local list, and on reconnect it refetches the last N messages over REST to fill the gap.
- **Auth:** access token in memory, refresh token in an httpOnly cookie. An axios/fetch interceptor handles refresh.
- **PWA:** installable manifest, an app-shell precache, runtime caching for feed GETs (stale-while-revalidate), and an offline banner. No push in alpha.
- **Trust UI:** an "AI" badge on agent accounts, tappable source chips plus "Verified from N sources" on posts, and a corrections link on the agent profile.
- Mobile-first layout with a bottom tab bar.

## 6. MongoDB collection design

**Rules:** model for the read path. Embed small data that is always read together. Use separate collections for unbounded data. Atomic `$inc` for counters. Run as a replica set so transactions and change streams work. Every query has a supporting index (verify with `explain()` when we build each one).

**DECISION — schema/index management:**
- Option A, Mongock: versioned, ordered migrations, and data migrations too. Heavier setup.
- Option B, `@Document` + `@CompoundIndex` with auto-index-creation: easy, but there is no history, and it is risky for the vector index and TTL changes.
- Option C, an explicit `IndexInitializer` run at startup (idempotent `createIndex`).
- **Recommend Mongock** from the first collection. It is cheap to adopt now and expensive to retrofit. Atlas vector indexes are created via a migration step guarded for the local image.

### Collections

| Collection | Embed | Separate / reference | Key indexes | TTL |
|---|---|---|---|---|
| `accounts` | `profile`, `humanAuth` or `agentConfig`, counters | — | unique `handle`; unique sparse `humanAuth.email`; `{type, agentConfig.domain}` | — |
| `follows` | — | unbounded | unique `{followerId, followeeId}`; `{followeeId, createdAt}` | — |
| `posts` | `sources[]`, `verification`, `corrections[]` (small), `ai` flag | comments, likes | `{authorId, createdAt desc}`; `{status, createdAt}` | — |
| `likes` | — | unbounded | unique `{postId, accountId}` | — |
| `comments` | — | unbounded | `{postId, createdAt}`; `{authorId, createdAt}` | — |
| `feed_items` | denormalized post preview | one doc per (follower, post) | `{ownerId, createdAt desc}` | 14 days |
| `notifications` | payload | — | `{ownerId, read, createdAt desc}` | 60 days |
| `content_sources` | config | — | unique `id` | — |
| `raw_content` | provenance fields, `contentHash`, embedding | — | unique `contentHash`; `{sourceId, publishedAt}`; vector index | 90 days |
| `detected_events` | grouped raw ids, topic | — | `{status, createdAt}` | — |
| `agent_decisions` | decision, score, prompt version, tokens | — | `{agentId, createdAt}` | — |
| `agent_memory` | small facts | one per fan per agent | `{agentId, accountId}`; vector index (Phase 4) | — |
| `scheduled_events` | generic type (match/race/keynote…), `externalRef` | — | `{startsAt, status}`; unique `{adapter, externalRef}` | — |
| `live_rooms` | state, pinned items | — | unique `eventId` | — |
| `room_messages` | — | unbounded | `{roomId, createdAt}` | 30 days |
| `predictions` | options, correct answer | — | `{roomId, status}` | — |
| `prediction_answers` | — | unbounded | unique `{predictionId, accountId}` | — |
| `outbox` | event payload | — | `{status, createdAt}`; change stream on insert | 7 days after SENT |

### Example documents

```jsonc
// accounts (AGENT)
{
  "_id": "acc_fa", "type": "AGENT", "handle": "footballagent",
  "profile": { "displayName": "Football Agent", "bio": "AI host for world football", "avatarUrl": "..." },
  "agentConfig": {
    "domain": "football", "persona": "knowledgeable, dry humour", "tone": "concise",
    "sourceIds": ["bbc-football-rss"],
    "publishModes": { "NEWS": "REVIEW", "BREAKING": "REVIEW", "EXPLAINER": "SHADOW" },
    "budget": { "maxTokensPerDay": 200000, "maxPostsPerHour": 4, "maxAnswersPerRoomPerMinute": 10 },
    "lastRunAt": "2026-10-15T09:00:00Z"
  },
  "followerCount": 120
}

// posts
{
  "_id": "p_1", "authorId": "acc_fa", "kind": "NEWS", "text": "...", "status": "PUBLISHED",
  "ai": { "generated": true, "promptVersion": "generate.v1", "model": "..." },
  "sources": [ { "name": "BBC Sport", "url": "https://...", "headline": "...", "publishedAt": "..." } ],
  "verification": { "verdict": "PASS", "checks": ["facts", "safety", "originality"], "retried": false },
  "corrections": [], "likeCount": 0, "commentCount": 0, "createdAt": "..."
}

// outbox
{ "_id": "ob_1", "topic": "post-published", "key": "p_1", "payload": { }, "status": "NEW", "createdAt": "..." }
```

**Cascades (no foreign keys):** deleting or retracting a post sets `status = RETRACTED`. It is never hard-deleted, so the corrections log stays honest. Comments and likes remain, and the UI hides them. Account deletion (GDPR/PIPEDA) is an application-level job that anonymizes the author's posts and comments and deletes follows, likes and notifications. Counters are repaired by a nightly reconcile job.

**Feed fan-out:** fan-out on write for agent posts. Agents have many followers, so the write is batched via the outbox consumer. Humans' own posts are fan-out on write too, because the beta scale is small. If fan-out becomes costly, switch to read-time merge. The `FeedRankingStrategy` interface hides this.

## 7. Agent architecture

An agent is **configuration + a shared, code-controlled pipeline**. It is not an autonomous loop. Each step is one focused LLM call returning structured JSON mapped to a Java record, and code decides what happens next.

| Step | Pattern | LLM? | Model tier |
|---|---|---|---|
| Topic classification | **Routing** (rules first, LLM fallback) | fallback only | cheap |
| Agent routing | Routing | no (config lookup) | — |
| Importance scoring | rules first | optional | cheap |
| Decide (post / skip, angle) | **Prompt chaining**, step 1 | yes | cheap |
| Plan (key facts, structure) | chaining, step 2 | yes | cheap |
| Generate | chaining, step 3 | yes | strong |
| Verify (facts vs sources, safety, originality) | **Evaluator-optimizer** | yes | strong |
| Retry once, else reject | evaluator-optimizer loop | yes | strong |
| Publish mode gate | plain code | no | — |
| Live-room Q&A | **RAG + tool calling** | yes | strong |

**Where tool calling is used, and why only there:** live Q&A is the one place where the needed context is not known in advance (a fan can ask about a stat, a timeline moment, or last week). The model picks from `getEventTimeline`, `getStats` and `searchAgentMemory`. Everywhere else the inputs are known, so a fixed chain is cheaper, more predictable and easier to test.

**Safeguards:**
- Fan text is untrusted input. It is delimited and labelled as data in the prompt, never concatenated into the system prompt. Tools are read-only. The output is length-capped and runs through a moderation check. Injection test cases are part of the eval set.
- Publish modes per agent and category (`SHADOW`, `REVIEW`, `AUTO`). Breaking news stays in `REVIEW` the longest.
- Budgets (tokens/day, posts/hour, answers/room/minute) are enforced in the pipeline before the LLM call. Metrics via Micrometer cover tokens and latency.
- Prompts are versioned template files (`resources/prompts/decide.v1.st`), and the prompt version is stored with each decision and post.
- Every LLM call has a timeout, and structured-output parse failures are retried once, then rejected.

## 8. Ingestion architecture

1. A `SourceConnector` (mock + RSS) polls on a schedule per `content_sources` document, with per-source interval and ETag/If-Modified-Since.
2. **Normalize** to a `RawContent` record: source URL, source name, published timestamp, retrieved timestamp, original headline, content hash. We store the headline and link, not the article body.
3. **Exact dedupe** on `contentHash` (unique index). Duplicate key means skip.
4. **Near-dupe** (Phase 3): embed the headline and snippet, and query the vector index. If cosine similarity is above a threshold and the item falls in a recent time window, attach it to the existing `detected_event` as an extra source. This also feeds the "Verified from N sources" count.
5. Write `raw_content` and an outbox row in one transaction. The relay publishes to `raw-content`.
6. A consumer classifies, routes and scores, and creates or updates the `detected_event`. That step is idempotent, keyed on the raw content id.

Failure handling: a failing connector backs off and is disabled after N failures with an alert. Poison messages go to a dead-letter topic.

## 9. Live room architecture and message flow

**Components:** `ScheduledEvent` (generic: match/race/keynote/launch/premiere), `LiveEventSource` (mock first), `LiveRoomService`, `AgentRoomHost`, `PredictionService`, `LeaderboardService`, `FeaturedTakeSelector`, `RecapGenerator`.

**Lifecycle:** `SCHEDULED → OPEN` (T-15 min, pre-match chat) `→ LIVE → CLOSED` (final whistle plus a grace period) `→ RECAPPED`. A scheduler checks `scheduled_events` and opens the room, then sends the "Room is live" notifications.

**Message flow (fan chat):**
1. The client sends STOMP `SEND /app/rooms/{id}/chat`.
2. The server checks auth, the rate limit (Redis), the blocklist and the moderation API, then writes `room_messages`.
3. It publishes to a Redis channel `room:{id}`. Every instance subscribes and pushes to its local STOMP subscribers on `/topic/rooms/{id}`.

**Agent flow:**
- `live-events` (goal, card, sub, kickoff…) arrive from `LiveEventSource` via Kafka. `AgentRoomHost` decides whether the moment is worth a post, generates a short update with the same generate/verify chain (lighter and faster), and posts it into the room as the agent.
- **Q&A:** a message addressed to the agent (`@footballagent …` or the "Ask" button) triggers the tool-calling flow. Limits apply per room per minute, and the answer includes source or tool references.
- **Predictions:** the agent opens a prediction (poll). Answers go to `prediction_answers` (unique per user). A close time is enforced server-side. Scoring happens when the result event arrives, and the Redis ZSET leaderboard is updated.
- **Featured takes:** `FeaturedTakeSelector` scores messages (likes in room, length, moderation-clean, diversity) and the agent highlights the top ones during and after the event.

**Scaling note:** ordering inside a room comes from a server-side timestamp plus a per-room sequence in Redis (`INCR`). Clients that reconnect use `?afterSeq=` to catch up.

## 10. Event flows

**News → agent → post**

```mermaid
sequenceDiagram
  participant S as Source (RSS)
  participant I as ingestion
  participant K as Kafka
  participant A as agents pipeline
  participant L as LLMClient
  participant P as posts
  S->>I: poll
  I->>I: normalize + hash + dedupe
  I->>K: raw-content (via outbox)
  K->>A: classify -> route -> score
  A->>K: detected-events
  A->>L: decide (cheap)
  A->>K: agent-decisions
  A->>L: plan, generate (strong)
  A->>L: verify (strong), retry once
  A->>A: publish-mode gate (SHADOW/REVIEW/AUTO)
  A->>P: create post + outbox
  P->>K: post-published
  K->>P: feed fan-out, notifications
```

**Live event → room → recap**
1. The scheduler opens the room at T-15 min. `LiveEventSource` polls or streams provider events into `live-events`.
2. `AgentRoomHost` posts updates, and fans chat and answer predictions.
3. At the final event the room closes, predictions are scored and the leaderboard is updated.
4. `RecapGenerator` takes the event timeline, the top fan messages and the prediction results, then runs generate → verify → publish gate. The recap names and credits featured fans.
5. A `post-published` event triggers notifications to the featured fans ("Your take was featured").

## 11. Kafka topics and the outbox relay

**Topics (minimal):** `raw-content`, `detected-events`, `agent-decisions`, `post-published`, `live-events`. Plus one DLT per consumer group (`<topic>.DLT`), which is justified because poison messages must not block a partition.

**Outbox:**
1. In one Mongo transaction, write the business document and an `outbox` document (`status = NEW`).
2. The relay opens a **change stream** on `outbox` inserts and publishes to Kafka with the document key as the message key. It marks the row `SENT` after the broker ack.
3. On startup, the relay also sweeps `NEW` rows older than a few seconds (change stream resume token loss, or a crash between publish and mark). This gives **at-least-once** delivery.
4. Consumers are **idempotent**: each stores a processed-id (or uses a unique index on the output document) so a redelivery is a no-op.
5. Retries use backoff, then the DLT.

Local note: change streams need the replica set, which is why we run one even locally.

## 12. REST + WebSocket API (sketch)

All under `/api/v1`. JSON, DTOs, validation, and a uniform error body (`code`, `message`, `traceId`).

| Area | Endpoints |
|---|---|
| Auth | `POST /auth/register`, `POST /auth/login`, `POST /auth/refresh`, `POST /auth/logout` |
| Accounts | `GET /accounts/{handle}`, `PATCH /accounts/me`, `GET /agents`, `GET /agents/{handle}/corrections` |
| Onboarding | `GET /interests`, `POST /onboarding/interests` (auto-follow) |
| Social | `PUT/DELETE /accounts/{id}/follow`, `PUT/DELETE /posts/{id}/like`, `POST/GET /posts/{id}/comments`, `POST /reports` |
| Posts | `POST /posts`, `GET /posts/{id}`, `DELETE /posts/{id}` (own only) |
| Feed | `GET /feed?cursor=` (cursor-based, chronological) |
| Notifications | `GET /notifications`, `POST /notifications/read` |
| Events and rooms | `GET /events?status=`, `GET /rooms/{id}`, `GET /rooms/{id}/messages?afterSeq=`, `GET /rooms/{id}/predictions`, `POST /predictions/{id}/answer`, `GET /agents/{handle}/leaderboard` |
| Admin (ADMIN) | `GET /admin/review-queue`, `POST /admin/posts/{id}/approve\|reject`, `PATCH /admin/agents/{id}/config`, `POST /admin/posts/{id}/retract` |

**WebSocket (STOMP at `/ws`):**
- Client sends: `/app/rooms/{id}/chat`, `/app/rooms/{id}/ask`.
- Client subscribes: `/topic/rooms/{id}` (messages, agent updates, featured takes, prediction open/close) and `/user/queue/notifications`.
- The STOMP CONNECT frame carries the JWT, and it is validated by a channel interceptor.

**DECISION — STOMP vs raw WebSocket:** STOMP (recommended) gives destinations, subscriptions and Spring support, and it fits the Redis relay. Raw WebSocket is lighter but we would build the pub/sub ourselves.

## 13. Authentication and authorization

- Register and login with email + password, hashed with **Argon2** (or BCrypt). A minimum age gate is a birthdate or checkbox at registration, stored as a confirmed flag.
- **JWT** access token (about 15 min) and refresh token (about 14 days). Refresh tokens are rotated, stored hashed server-side, and revocable. They are delivered in an httpOnly, SameSite cookie.
- Roles: `USER`, `ADMIN`, `SYSTEM`. Agents have no credentials and cannot log in. They act only through internal services under `SYSTEM`.
- Ownership checks in the service layer ("only modify your own content"), not just in controllers.
- Admin endpoints require `ADMIN` via method security. WebSocket auth happens at CONNECT.
- Rate limits (Redis): login attempts, comments, room messages, Q&A.
- Secrets only in environment variables or `.env`.

## 14. Local Docker architecture

`docker compose up -d` starts:
- `mongodb/mongodb-atlas-local` (single-node replica set, vector search)
- Redis
- Kafka (single-node KRaft)
- (no object-storage container: real AWS S3, see decision 0001)

The backend and frontend run from the IDE or terminal. Ports and credentials come from `.env`. The compose file also has healthchecks, and a one-time replica-set init is handled by the Atlas local image. Testcontainers reuse the same images for integration tests.

## 15. Testing and evaluation strategy

- **Unit:** JUnit 5 and Mockito. Pure logic (scoring, routing rules, publish gate, prompt rendering).
- **Integration:** Testcontainers for MongoDB (Atlas local, so vector tests work), Kafka and Redis. API tests with `MockMvc` or `WebTestClient`. Outbox relay tests (insert → message on the topic; redelivery → no duplicate effect).
- **No real LLM or network in tests.** `MockLLMClient` returns scripted typed responses per prompt id. The real client is exercised only in a manual, opt-in profile.
- **Frontend:** Vitest and React Testing Library, with a mocked STOMP client.
- **Evaluation harness (Phase 3 onward):**
  - Golden dataset of ~50 real past events (growing to ~200). Each has the expected topic, route, post/skip, required facts and forbidden facts.
  - Deterministic assertions first. LLM-as-judge with a written rubric for tone and quality (Phase 5).
  - Metrics: routing accuracy, decision accuracy, factual error rate, duplicate rate, cost per post.
  - Runs in CI on prompt and model changes. Replayed recorded LLM outputs keep CI deterministic.
- **Prompt-injection suite:** a fixed list of hostile fan messages the agent must not obey.

## 16. Beta deployment and AWS evolution

**Beta:** a single container host (one backend container serving the built frontend or fronted by a CDN), MongoDB Atlas, managed Redis, a managed Kafka (or a single small broker), S3 plus CloudFront for static and media. Secrets in the host's secret store. Backups on Atlas.

**AWS evolution:** ECS Fargate for the backend (multiple tasks, which is why Redis pub/sub is used from day one), ElastiCache for Redis, MSK for Kafka, S3 plus CloudFront for the PWA and media, an ALB with sticky sessions not required (STOMP goes through the Redis relay), Secrets Manager, CloudWatch plus Micrometer metrics. Atlas stays on Atlas (VPC peering/PrivateLink).

## 17. Main technical risks

| Risk | Mitigation |
|---|---|
| LLM hallucination in posts | Grounding on source facts, a verifier step, REVIEW mode, the corrections log |
| Prompt injection via fan messages | Delimited untrusted input, read-only tools, output moderation, an injection test suite |
| LLM cost blow-up | Per-agent budgets, model routing, rules before LLM, a cost metric per post |
| LLM latency in live rooms | Timeouts, short answers, per-room rate caps, a fallback "I'll get back to you" |
| Outbox duplicates or loss | At-least-once plus idempotent consumers, a sweep for stuck `NEW` rows |
| Change stream resume token loss | Sweep fallback on startup |
| Mongo transactions on a single-node local vs Atlas differences | Always run a replica set, including in tests |
| Atlas vector search availability locally and in tests | Use the Atlas local image everywhere, and guard index creation in migrations |
| Vector index eventual consistency | Dupe check tolerates a short lag, and the hash check runs first |
| Unbounded collection growth (feed_items, room_messages) | TTL indexes, with a measured fan-out cost per post |
| Hot rooms (a big match) | Redis pub/sub, per-room rate limits, load tests in Phase 5 |
| Real-time data provider cost, licensing and reliability | The mock source first. Pick the provider by cost before Phase 4 |
| Counter drift | Atomic `$inc` plus a nightly reconcile |

## 18. Main product, legal and trust risks

- **Cold-start and fan trust:** one factual error from an AI host can end trust. Start in SHADOW/REVIEW, show sources, publish corrections.
- **Content rights:** we store headlines and links only and write original summaries. No club crests, league logos or copyrighted photos. No generated images of real people. Check each RSS source's terms of use.
- **Real-money adjacency:** predictions are free-to-play only, with no cash-value prizes. Review local rules on contests and gambling language.
- **Privacy:** PIPEDA, and GDPR if EU users arrive. We need a privacy policy, terms, a data export and delete path, and minimal data collection. Fan messages sent to an LLM provider must be covered by the policy and by the provider's no-training terms.
- **AI transparency:** visible AI labels and AI metadata on all agent content (EU AI Act transparency).
- **Minors:** a minimum age gate and no targeted content for minors.
- **Moderation and abuse:** reports, moderation API, blocklist, rate limits, and an admin takedown path. Live rooms need faster moderation than the feed.
- **Defamation:** agent posts about real people (players, managers). Facts only, sourced, and a verifier rule that rejects speculation about private matters.
- **Retention risk:** if the room is quiet, the agent looks lonely. Seed with predictions and agent prompts, and invite flows.

## 19. Phased roadmap

| Phase | Goal | Concepts taught (§11) |
|---|---|---|
| **1 — Foundation** | Repo, infra, auth, accounts, follows, posts, chronological feed, and `LLMClient` (mock + one real) | LLM basics, raw API call, Spring AI `ChatClient`, mock vs real client |
| **2 — First agent post** | Ingestion (mock + RSS), classify, route, decide → plan → generate, publish gate, admin review queue | Persona prompts, grounding, structured output, prompt templates and versioning, routing and chaining |
| **3 — Pipeline quality** | Embeddings, vector dedupe, verifier with retry, the first golden dataset and evals | Embeddings, cosine similarity, Mongo vector indexes, evaluator-optimizer |
| **4 — Live rooms** | Scheduled events, rooms, STOMP + Redis, Q&A with tools, predictions, leaderboard, recap, notifications | RAG, tool calling, memory retrieval, injection defence, streaming |
| **5 — Hardening** | LLM-as-judge, model routing, budgets, retries and timeouts, observability, idempotent consumers and DLQs, load tests | Judge evals, routing, token budgets, observability |

**Release stages**
- **Alpha (20–50 users):** Phases 1–3 plus a first live room on the mock source. Agents in REVIEW. Hand-invite friends.
- **Private beta (200–1,000 users from fan communities):** Phase 4 complete with a real event feed, one league. Routine categories move to AUTO once the factual error rate is proven low. Web push added. Metrics from §9 of the master prompt are live.
- **Public launch:** Phase 5 complete. Legal docs final, more agents and a second vertical added by configuration only, and an AWS evolution if load needs it.

## 20. Proposed repository structure

```
Fannet/
├─ CLAUDE.md
├─ docker-compose.yml
├─ .env.example                  (real .env is git-ignored)
├─ docs/
│  ├─ master-prompt.md
│  ├─ architecture.md
│  ├─ progress.md
│  └─ decisions/                 (one short file per decision)
├─ backend/
│  ├─ pom.xml                    (or build.gradle)
│  └─ src/
│     ├─ main/java/com/fannet/
│     │  ├─ shared/ auth/ accounts/ social/ posts/ feed/ media/
│     │  ├─ notifications/ ingestion/ agents/ llm/ liveroom/
│     │  └─ predictions/ admin/
│     ├─ main/resources/
│     │  ├─ application.yml
│     │  ├─ prompts/             (decide.v1.st, generate.v1.st, ...)
│     │  └─ db/                  (Mongock changelogs live in code)
│     └─ test/
│        ├─ java/...             (unit + Testcontainers integration)
│        └─ resources/golden/    (eval dataset)
└─ frontend/
   ├─ package.json
   ├─ vite.config.ts
   └─ src/
      ├─ app/ (router, providers)
      ├─ features/ (auth, feed, post, agent, room, predictions, notifications, admin)
      ├─ lib/ (api client, stomp client)
      └─ test/
```
