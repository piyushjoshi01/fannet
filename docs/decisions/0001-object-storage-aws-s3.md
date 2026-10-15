# 0001 — Object storage: real AWS S3 instead of local MinIO

**Problem:** The plan used MinIO in Docker Compose for local S3. The official community image is no longer pullable (removed from Docker Hub, `quay.io` returns 401).

**Options:**
1. Find another MinIO source (mirror or community build).
2. Switch to another S3-compatible server (RustFS, SeaweedFS, Garage).
3. Use real AWS S3 in all environments, through the AWS SDK.

**Choice:** 3. Credentials (`AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`), `AWS_REGION` and `S3_BUCKET` live in `.env`.

**Why:** Nothing to maintain or pin locally, and dev matches the beta/prod target. Cost is negligible for beta (no media uploads in scope).

**Consequences:** Needs a dedicated dev bucket and an IAM user limited to it. Tests must not hit S3 (mock the storage service). Compose now has only MongoDB, Redis and Kafka.
