# 0005 — Password hashing and age gate

## Problem
Registration needs a password hash that can evolve, and a minimum-age rule the brief leaves open.

## Options
- Hashing: Argon2id only / BCrypt only / `DelegatingPasswordEncoder` (prefixed hashes).
- Age: fixed 13 / fixed 16 / configurable.

## Choice
- `DelegatingPasswordEncoder`, default `{argon2}` (Argon2id), `{bcrypt}` still verifiable.
- `fannet.auth.min-age`, default 16, checked server-side against a `Clock` bean. Only the date of birth is stored.

## Why
- The prefix lets us change algorithm or parameters later without forcing password resets.
- 16 is the stricter EU default and avoids parental-consent logic; it is one property to change per deployment.
- Passwords are capped at 72 chars because BCrypt truncates there.
- Login returns one generic 401 and verifies against a dummy hash for unknown emails, so neither message nor timing leaks which emails exist.
- Duplicate handle/email is decided by the unique indexes (409), not check-then-insert.
