# MPhasis — AI-Driven Software Development Automation

An end-to-end system that automates the software development lifecycle by listening to Jira ticket events, enriching them with AI, storing semantic embeddings for intelligent search, and automatically generating code and raising Pull Requests on Bitbucket.

---

## Architecture Overview

```
Jira Cloud
    │
    │  Webhook (HTTP POST)
    ▼
┌─────────────────────┐
│   webhook-service   │  Port 8080
│   (Spring Boot)     │──── ngrok tunnel (exposes localhost to Jira)
└────────┬────────────┘
         │  Publishes KafkaJiraEvent
         ▼
┌─────────────────────┐
│    Apache Kafka     │  Topic: jira.issue.events (3 partitions)
│  (Docker)           │  Partition key: issueKey (ordering per ticket)
└────────┬────────────┘
         │  Consumes events
         ▼
┌─────────────────────┐          ┌──────────────────────┐
│    rag-service      │  8081    │   HuggingFace API    │
│   (Spring Boot)     │─────────▶│  BAAI/bge-small-en  │
│                     │          │  384-dim embeddings  │
│  - Indexes tickets  │          └──────────────────────┘
│  - Vector search    │
│  - RAG query UI     │          ┌──────────────────────┐
│  - Triggers codegen │          │  PostgreSQL+pgvector │
└────────┬────────────┘─────────▶│  ticket_embeddings   │
         │  status=CODEGEN        │  vector(384) + IVFFlat│
         │  HTTP POST             └──────────────────────┘
         ▼
┌─────────────────────┐          ┌──────────────────────┐
│  codegen-service    │  8082    │    Groq API          │
│   (Spring Boot)     │─────────▶│  llama-3.3-70b       │
│                     │          │  Code generation     │
│  - Queries RAG      │          └──────────────────────┘
│  - Generates code   │
│  - Creates Bitbucket│          ┌──────────────────────┐
│    branch + PR      │─────────▶│  Bitbucket Cloud     │
└─────────────────────┘          │  Branch + PR via API │
                                 └──────────────────────┘
```

---

## Services

### 1. webhook-service (Port 8080)
**Role:** Jira event gateway — receives webhook calls from Jira Cloud and publishes slim events to Kafka.

**Flow:**
- Jira calls `POST /webhook/jira` when any ticket is created or updated
- Extracts key fields: issueKey, status, summary, priority, assignee, changed fields
- Publishes a `KafkaJiraEvent` to the `jira.issue.events` topic
- Uses issueKey as Kafka partition key (ensures ordering per ticket)

**Key files:**
- `JiraWebhookController` — validates token, filters events, builds Kafka message
- `KafkaProducerService` — sends to Kafka with idempotent producer config
- `KafkaJiraEvent` — lean event model (no Lombok, plain Java)

---

### 2. rag-service (Port 8081)
**Role:** Intelligence layer — indexes Jira tickets as vector embeddings for semantic search, answers natural language questions, and triggers code generation.

**Flow:**
1. Consumes `jira:issue_updated` events from Kafka
2. Fetches full ticket details from Jira REST API (summary, description, comments, priority, etc.)
3. Sends full content to HuggingFace to generate a 384-dimensional vector embedding
4. Upserts into PostgreSQL pgvector table (cosine similarity index)
5. If ticket status = `CODEGEN` → calls codegen-service via HTTP
6. Exposes a web UI at `http://localhost:8081` for asking questions about tickets

**RAG Query flow:**
- User submits a question in the UI
- Question is embedded via HuggingFace
- Top-K similar tickets retrieved from pgvector using cosine distance (`<=>`)
- Ticket context + question sent to Groq LLM
- Answer returned with source tickets and similarity scores

**Key files:**
- `JiraEventConsumer` — Kafka listener, orchestrates indexing pipeline
- `JiraFetchService` — Jira REST API client, ADF description parser
- `EmbeddingService` — calls HuggingFace inference API
- `VectorStoreService` — pgvector upsert and cosine similarity search
- `RagQueryService` — builds prompt, calls Groq, returns answer + sources
- `CodegenClientService` — HTTP client to trigger codegen-service
- `QueryController` — REST API (`POST /api/query`, `GET /api/tickets`)
- `static/index.html` — dark-themed query UI

---

### 3. codegen-service (Port 8082)
**Role:** Autonomous code generation — when triggered by rag-service, fetches the ticket, uses RAG context from indexed tickets, generates production-ready code with an LLM, and raises a PR on Bitbucket.

**Flow:**
1. Receives `POST /api/generate?issueKey=SCRUM-7` from rag-service
2. Fetches full ticket details from Jira REST API
3. Calls rag-service `/api/query` to get top-3 semantically similar tickets as context
4. Builds a detailed prompt: ticket details + similar tickets + coding instructions
5. Calls Groq (llama-3.3-70b) to generate complete, production-ready code
6. Pushes generated code file to a new branch on Bitbucket (`feature/SCRUM-7-...`)
7. Opens a Pull Request targeting the `master` branch

**Key files:**
- `CodegenController` — REST endpoint, orchestrates the pipeline
- `JiraFetchService` — same as rag-service, fetches ticket details
- `RagContextService` — queries rag-service for similar tickets
- `CodeGenerationService` — builds prompt, calls Groq, strips markdown fences
- `BitbucketService` — Bitbucket Cloud REST API: file commit + PR creation

---

## Technology Stack

| Category | Technology | Purpose |
|---|---|---|
| **Framework** | Spring Boot 3.2.0 | All three microservices |
| **Language** | Java 21 (runs on Java 26) | Business logic |
| **Messaging** | Apache Kafka 3.6 | Event streaming between services |
| **Database** | PostgreSQL 16 + pgvector | Vector storage and similarity search |
| **Embeddings** | HuggingFace `BAAI/bge-small-en-v1.5` | 384-dim text embeddings (free API) |
| **LLM** | Groq `llama-3.3-70b-versatile` | RAG answers + code generation (free API) |
| **Ticket System** | Jira Cloud REST API v3 | Webhook receiver + full ticket fetch |
| **Code Hosting** | Bitbucket Cloud REST API v2 | Branch creation + PR creation |
| **Tunnel** | ngrok | Expose local webhook endpoint to Jira Cloud |
| **Containers** | Docker Compose | Kafka, Zookeeper, Kafka UI, PostgreSQL |
| **Vector Index** | IVFFlat (pgvector) | Approximate cosine similarity search |

---

## Infrastructure (Docker Compose)

```
Service          Port    Purpose
─────────────────────────────────────────
Zookeeper        2181    Kafka coordination
Kafka            9092    Message broker
Kafka UI         8090    Visual topic browser
PostgreSQL       5432    Vector database (ragdb)
```

---

## Data Flow — Full End to End

```
1. Developer updates Jira ticket status to CODEGEN
        │
2. Jira fires webhook → webhook-service publishes KafkaJiraEvent
        │
3. rag-service consumes event
   ├── Fetches full ticket from Jira API
   ├── Generates 384-dim embedding via HuggingFace
   ├── Upserts into pgvector (cosine index)
   └── Detects status=CODEGEN → HTTP POST to codegen-service
        │
4. codegen-service receives request
   ├── Fetches ticket from Jira
   ├── Queries rag-service for top-3 similar tickets (RAG context)
   ├── Sends enriched prompt to Groq → gets generated code
   ├── Pushes code file to new Bitbucket branch
   └── Opens Pull Request on Bitbucket
        │
5. Reviewer gets PR notification → reviews → merges
```

---

## Prerequisites

Before starting, make sure you have:

- Docker Desktop (running)
- Java 21+ installed (`java -version`)
- Maven 3.8+ installed (`mvn -version`)
- ngrok installed and authenticated (`ngrok config add-authtoken YOUR_TOKEN`)
- All API keys configured in each service's `application.yml` (see Configuration section below)

---

## Startup Sequence

Services must be started in this exact order. Open a separate terminal for each.

### Terminal 1 — Infrastructure (Docker)

```bash
cd /Users/apple/Desktop/Hackathon/MPhasis
docker-compose up -d
```

Verify everything is up:
```bash
docker ps
```
You should see: `zookeeper`, `kafka`, `kafka-ui`, `postgres` all running.

Check Kafka UI at: http://localhost:8090

---

### Terminal 2 — webhook-service

```bash
cd /Users/apple/Desktop/Hackathon/MPhasis/webhook-service
mvn package -DskipTests -q
java -jar target/webhook-service-1.0.0-SNAPSHOT.jar
```

**Verify:** You should see `Started WebhookServiceApplication` in the logs.
Service runs on: http://localhost:8080

---

### Terminal 3 — ngrok (Jira webhook tunnel)

```bash
ngrok http 8080
```

Copy the `Forwarding` URL (e.g. `https://abc123.ngrok.io`) and configure it in Jira:
- Jira project → **Project settings** → **Webhooks** → Create webhook
- URL: `https://abc123.ngrok.io/webhook/jira`
- Events: Issue → created, updated

---

### Terminal 4 — rag-service

```bash
cd /Users/apple/Desktop/Hackathon/MPhasis/rag-service
mvn package -DskipTests -q
java -jar target/rag-service-1.0.0-SNAPSHOT.jar
```

**Verify:** You should see `Started RagServiceApplication` and Kafka consumer partition assignment.
Service runs on: http://localhost:8081
Query UI at: http://localhost:8081

---

### Terminal 5 — codegen-service

```bash
cd /Users/apple/Desktop/Hackathon/MPhasis/codegen-service
mvn package -DskipTests -q
java -jar target/codegen-service-1.0.0-SNAPSHOT.jar
```

**Verify:** You should see `Started CodegenServiceApplication`.
Service runs on: http://localhost:8082

---

## Testing the Pipeline

### Test 1 — Webhook is working
Update any Jira ticket. Check webhook-service logs for:
```
Published event: SCRUM-X eventType=jira:issue_updated
```

### Test 2 — RAG indexing is working
Check rag-service logs for:
```
Embedded 312 chars -> 384 dimensions
Indexed issue=SCRUM-X status='...' summary='...'
```
Then open http://localhost:8081 and ask a question about the ticket.

### Test 3 — Full CODEGEN pipeline
1. Change a Jira ticket status to **CODEGEN**
2. rag-service logs should show:
   ```
   Status is CODEGEN — triggering codegen-service for issue=SCRUM-X
   ```
3. codegen-service logs should show:
   ```
   Received codegen request for issue=SCRUM-X
   Generated XXXX chars of code for SCRUM-X
   Pushed generated code to branch 'feature/SCRUM-X-...'
   PR created: https://bitbucket.org/...
   ```
4. Check Bitbucket for the new branch and PR.

---

## Quick Restart Commands

If you need to restart a service after a code change:

```bash
# Kill by port
lsof -ti :8080 | xargs kill -9   # webhook-service
lsof -ti :8081 | xargs kill -9   # rag-service
lsof -ti :8082 | xargs kill -9   # codegen-service

# Rebuild and restart (run from each service directory)
mvn package -DskipTests -q && java -jar target/*.jar
```

---

## Configuration

Each service has its own `src/main/resources/application.yml`.

### webhook-service
```yaml
server.port: 8080
app.kafka.topic: jira.issue.events
```

### rag-service
```yaml
server.port: 8081
jira.base-url: https://YOUR_ORG.atlassian.net
jira.email: your-email@gmail.com
jira.api-token: YOUR_ATLASSIAN_API_TOKEN      # id.atlassian.net → Security → API tokens
huggingface.api-token: hf_YOUR_TOKEN          # huggingface.co → Settings → Access Tokens
huggingface.model: BAAI/bge-small-en-v1.5
groq.api-key: gsk_YOUR_KEY                    # console.groq.com → API Keys
groq.model: llama-3.3-70b-versatile
codegen.service-url: http://localhost:8082
```

### codegen-service
```yaml
server.port: 8082
jira.base-url: https://YOUR_ORG.atlassian.net
jira.email: your-email@gmail.com
jira.api-token: YOUR_ATLASSIAN_API_TOKEN
groq.api-key: gsk_YOUR_KEY
rag.service-url: http://localhost:8081
bitbucket.workspace: your-workspace-slug
bitbucket.email: your-email@gmail.com
bitbucket.app-password: YOUR_BITBUCKET_API_TOKEN   # bitbucket.org → Account settings → API tokens
bitbucket.destination-branch: master
bitbucket.project-repo-map: "{SCRUM: 'your-repo-slug'}"
```

---

## API Endpoints

### rag-service
| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/query` | Ask a question about indexed tickets |
| `GET` | `/api/tickets` | List all indexed tickets |
| `GET` | `/api/health` | Health check |

### codegen-service
| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/generate?issueKey=SCRUM-1` | Trigger code generation for a ticket |
| `GET` | `/api/health` | Health check |
