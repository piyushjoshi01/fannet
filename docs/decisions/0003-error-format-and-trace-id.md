# 0003 — Error format, trace id and log format

## Problem
Every module returns errors, so they need one shape that does not leak internals. Logs need a per-request id.

## Options
- Error body: (1) RFC 9457 `ProblemDetail`, (2) custom `ApiError` record, (3) Spring default.
- Trace id: (1) servlet filter + SLF4J MDC, (2) Micrometer Tracing, (3) nothing.
- Log format: (1) Spring Boot built-in structured logging, (2) logstash-logback-encoder.

## Choice
`ProblemDetail` with extra fields `code` and `traceId` (validation adds `errors` as field -> message); `TraceIdFilter` (reuses a safe incoming `X-Request-Id` or generates a UUID, echoes it in the response); built-in `logstash` JSON logging in the `prod` profile, plain text with `[traceId]` locally.

## Why
Standard format with no custom class, no new dependency beyond `spring-boot-starter-validation`, and the MDC key `traceId` stays the same if we move to Micrometer Tracing in Phase 5. Expected errors extend `ApiException`; anything else becomes a generic 500 and the stack trace is only logged.
