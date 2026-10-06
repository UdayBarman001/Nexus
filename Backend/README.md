# Nexus — Enterprise RAG Knowledge Platform (Backend)

Spring Boot backend for a Retrieval-Augmented Generation system: upload documents, they get
chunked and embedded into pgvector, and users chat with an LLM (via Ollama) that answers
grounded in that knowledge base.

## Stack
- Java 21, Spring Boot 3.4.5, Spring AI 1.0
- PostgreSQL + pgvector (vector store)
- Ollama (local LLM + embedding model)
- MinIO (S3-compatible object storage for original files)
- JWT auth (stateless), Spring Security, method-level `@PreAuthorize`
- Flyway migrations, Apache Tika (text extraction), Springdoc/Swagger, Actuator

## Running locally

1. Copy `.env.example` to `.env` and fill in real secrets (especially `JWT_SECRET` —
   generate one with `openssl rand -base64 32`).
2. `docker compose up -d` — brings up Postgres, Ollama, MinIO, and the app itself.
3. On first run, pull the models into the Ollama container:
   ```
   docker exec -it nexus-ollama ollama pull llama3.2
   docker exec -it nexus-ollama ollama pull nomic-embed-text
   ```
4. API docs: http://localhost:8080/swagger-ui.html
5. Health check: http://localhost:8080/actuator/health

### Running the app outside Docker (Postgres/Ollama/MinIO still in Docker)
```
docker compose up -d postgres ollama minio
export JWT_SECRET=$(openssl rand -base64 32)
./mvnw spring-boot:run
```

## API overview

| Endpoint | Auth | Description |
|---|---|---|
| `POST /api/auth/register` | public | Create an account |
| `POST /api/auth/login` | public | Get a JWT |
| `GET /api/auth/me` | JWT | Current user profile |
| `POST /api/documents/upload` | JWT | Upload a file; extraction + embedding run async |
| `GET /api/documents` | JWT | List your documents (paginated) |
| `GET /api/documents/{id}` | JWT | Get status/metadata |
| `GET /api/documents/{id}/download` | JWT | Download original file |
| `DELETE /api/documents/{id}` | JWT | Delete file + metadata + embedded chunks |
| `POST /api/chat/ask` | JWT | Ask a question; omit `sessionId` to start a new chat |
| `GET /api/chat/sessions` | JWT | List your chat sessions |
| `GET /api/chat/sessions/{id}/messages` | JWT | Full history of one session |

Admins (`role: ADMIN`) can see/manage every user's documents; regular users only their own.

## Design notes / what's implemented

- **Async ingestion**: uploads return `202 Accepted` immediately with status `PENDING`;
  a background pool does text extraction (Tika) → chunking → embedding, updating status to
  `PROCESSING` → `EMBEDDED`/`FAILED`.
- **Grounded, session-aware chat**: each `/ask` call persists both turns to `chat_messages`
  and feeds a rolling window of recent history back into the prompt so follow-up questions work.
- **Ownership + RBAC**: documents and chat sessions are scoped to their creator; `ADMIN` can
  see everything.
- **Uniform errors**: every 4xx/5xx returns the same JSON shape (`GlobalExceptionHandler`).
- **Secrets**: nothing sensitive is hardcoded — `JWT_SECRET`, DB/MinIO credentials all come
  from environment variables (`.env` locally, real secret store in production).

## Known limitations / next steps

- **This code has not been run through a build in this environment** (no Maven Central
  access here) — it was written and manually reviewed for correctness, but run
  `./mvnw clean verify` yourself before deploying, and send me any compiler errors.
- `NexusApplicationTests` (`@SpringBootTest` context load) needs live Postgres/pgvector,
  Ollama, and MinIO to pass — wire up Testcontainers for real CI.
- No refresh-token flow yet — JWTs are long-lived (`jwt.expiration-ms`, default 24h).
- Rate limiting is not implemented — add it (e.g. Bucket4j) before exposing this publicly.
- `TokenTextSplitter` chunks by token count without sliding-window overlap; revisit if
  retrieval quality needs it.
