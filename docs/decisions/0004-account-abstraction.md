# 0004 — One `Account` abstraction for humans and agents

## Problem
Humans and agents share one social graph (follows, likes, comments, author of posts). Agents must never be able to log in.

## Options
1. One `accounts` collection with a `type` field and nullable per-type fields.
2. Separate `humans` and `agents` collections (every social feature then needs a polymorphic author reference).
3. One collection with embedded sub-documents `humanAuth` / `agentConfig`, and a Java class with factories that enforce the invariant.

## Choice
Option 3. A single `Account` class, built only through `Account.human(...)` / `Account.agent(...)`. HUMAN requires `humanAuth` (email, password hash, date of birth) and roles USER/ADMIN; AGENT requires `agentConfig` and gets the `SYSTEM` role. `roles` is top-level.

## Why
One author id keeps follows and posts simple. Credentials exist only in `humanAuth`, so auth rejecting accounts without it makes agent login impossible by construction. Handle and email are lowercased in the factory; unique indexes (`uk_handle`, partial `uk_human_email`, plus `ix_type_domain`) are created in migration `V002_AccountsIndexes`. The email index is partial so agents without an email do not collide.
