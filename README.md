# Chat with my docs

Upload your own documents, then chat with an AI that answers **only from them** and shows where each answer came from.
It's a small RAG (Retrieval-Augmented Generation) proof of concept:

- **Ingestion:** uploaded files (PDF, DOCX, TXT, MD) are read, split into ~500-token chunks, embedded with Gemini and stored in PostgreSQL + pgvector.
- **Retrieval:** a question is embedded, and the most similar chunks of *your* documents are found. Other users' documents are never searched.
- **Generation:** Gemini answers from those chunks, streams the answer word by word, and cites sources as `[1]`, `[2]`, … with clickable chips.

**Stack:** Java 21 · Spring Boot 4 · Spring AI 2 · Vaadin 25 + Hilla (React 19) · PostgreSQL 17 + pgvector · Flyway · Docker Compose · Gemini (`gemini-3.8-flash`, `gemini-embedding-001`).

## Prerequisites

- JDK 21
- Docker Desktop (running)
- A Gemini API key from [Google AI Studio](https://aistudio.google.com/). The free tier is enough for a demo.
  Google may use free-tier content to improve its products, so only upload non-confidential documents.

Node.js is not needed: the Vaadin Maven plugin downloads it.

## Setup

1. Create a `.env` file in the project root (it's git-ignored):

   ```properties
   GEMINI_API_KEY=your-key-here
   ```

   A normal `GEMINI_API_KEY` environment variable works too.

2. That's it. Spring Boot starts the PostgreSQL container from `compose.yaml` automatically, and Flyway creates the tables.

## Run

```bash
./mvnw spring-boot:run
```

Open <http://localhost:8080> and log in with a demo user (created only in the `dev` profile, which is the default):

| User | Password |
|---|---|
| `demo` | `demo` |
| `alice` | `alice` |

The first start takes a few minutes while the frontend is built. Uploaded files are stored in `./data/uploads`.

## Test

```bash
./mvnw test      # unit + integration tests (Testcontainers), no Gemini calls
./mvnw verify    # the same, plus the production build
```

Tests use fake chat and embedding models, so they work offline and use no API quota. Tests that call the real Gemini API are tagged `external` and skipped by default:

```bash
./mvnw test -Dtest='GeminiSmokeTests,VectorStoreSmokeTests' -Dexternal=true
```

## Demo script

1. Log in as `demo`.
2. **Documents** → upload a PDF with a few pages of distinct facts (for example an HR policy). The badge goes *Queued → Processing → Ready* and shows the chunk count.
3. **Chat → New chat** → leave *Documents* empty (= all documents) → *Start chat*.
4. Ask *"How many days of annual leave do interns get?"*. The answer streams in and ends with a chip like `[1] hr-policies.pdf · p.1`.
5. Hover the chip to see the passage; click it to open the PDF at that page.
6. Ask a follow-up: *"and for full-time employees?"*. It's understood from the conversation.
7. Ask something off-topic: *"What is the capital of France?"* → *"I couldn't find that in your documents."*
8. Log in as `alice` in another browser: she sees none of demo's documents or chats.
9. Back as `demo`, **Documents** → delete the PDF. Its file and all its chunks are removed.

## Configuration

Set in `src/main/resources/application.properties` (or override with environment variables, e.g. `APP_RAG_TOP_K=8`):

| Property | Default | Meaning |
|---|---|---|
| `app.storage-dir` | `./data/uploads` | Where uploaded files are stored |
| `app.max-upload-size` | `20MB` | Largest accepted file |
| `app.ingestion.chunk-size` | `500` | Target chunk size in tokens |
| `app.ingestion.batch-size` | `20` | Chunks embedded per Gemini request |
| `app.ingestion.pause-between-batches` | `20s` | Pause between batches (free-tier token limit) |
| `app.rag.top-k` | `5` | Chunks retrieved per question |
| `app.rag.similarity-threshold` | `0.55` | Minimum cosine similarity (measured: relevant ≈ 0.62–0.71, off-topic ≤ 0.47) |
| `app.rag.history-messages` | `6` | Earlier chat messages sent with each question |
| `app.rag.max-question-length` | `2000` | Longest accepted question, in characters |

Each answer logs its latency and token usage, e.g. `Answer: 3614 ms total, first text after 900 ms | tokens: prompt=1450, completion=64, total=1514`.

## Project layout

```
src/main/java/com/company/chatdocs/
  config/      SecurityConfig, AiConfig, AsyncConfig, AppProperties
  entity/      JPA entities (AppUser, Document, ChatSession, ChatMessage, …)
  repository/  Spring Data repositories
  service/     IngestionService, RetrievalService, GenerationService, ChatService, DocumentService, …
  dto/         Records sent to the browser
  controller/  File download (GET /api/documents/{id}/file)
  exception/, event/, seed/
src/main/frontend/        React views (file-based routes) and components
src/main/resources/db/migration/   Flyway migrations (V1–V6)
src/main/resources/prompts/        Prompt templates
```

## Troubleshooting

- **"Gemini's free-tier quota is used up"**: the free tier allows roughly 1,000 embedding requests per day and limited chat requests per minute. Wait a minute (or until the next day) and use **Reprocess** on failed documents.
- **`gemini-2.5-flash` returns 404**: it's closed to new API keys; this project uses `gemini-3.8-flash`.
- **A document is stuck or failed**: failed documents show the reason and a **Reprocess** button. Documents interrupted by a restart are marked failed on the next start.
- **curl in Git Bash and Hilla uploads**: run with `MSYS_NO_PATHCONV=1`, otherwise the multipart field name `/file` is rewritten into a Windows path.
