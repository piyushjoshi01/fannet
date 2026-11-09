# 0002 — Schema and index changes with Mongock

## Problem
We need repeatable, reviewable index and schema changes that are safe with several instances starting at once.

## Options
1. Mongock versioned change units (lock, history, data migrations).
2. `@Indexed` + `auto-index-creation` (adds only; cannot alter, drop or backfill; scattered; discouraged for production).
3. A single `IndexInitializer` bean (centralised and idempotent, but no history, lock or data migrations).

## Choice
Mongock 5.5.1 (`mongock-springboot-v3` + `mongodb-springdata-v4-driver`), community edition, `transactional: false` (index creation is not allowed in transactions).

## Why
Every query needs a supporting index, unique indexes enforce business rules, and later phases add TTL and vector indexes plus backfills. Versioned, once-only changes with an audit trail fit that. Each model task adds its own change unit in `com.fannet.shared.migration`.
