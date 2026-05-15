# Integration Guide — MPhasis

This document explains how each external system is integrated, what credentials are needed, where to get them, and how the integration works internally.

---

## 1. Jira Cloud

### Purpose
- **webhook-service** receives real-time ticket events via webhook
- **rag-service** and **codegen-service** fetch full ticket details via REST API

### How it works
Jira sends an HTTP POST to your ngrok URL whenever a ticket is created or updated. The payload contains the issue key, status, changelog, and fields. The services also make outbound REST API calls to fetch richer ticket data (description in ADF format, comments, components, etc.).

### Credentials needed

| Key | Where to get it |
|---|---|
| `jira.base-url` | Your Jira Cloud URL: `https://YOUR_ORG.atlassian.net` |
| `jira.email` | Your Atlassian account email |
| `jira.api-token` | https://id.atlassian.net → Security → API tokens → Create |

### Jira Webhook Setup
1. Go to your Jira project → **Project settings** → **Webhooks**
2. Click **Create a WebHook**
3. URL: `https://YOUR_NGROK_URL/webhook/jira`
4. Events: tick **Issue → created** and **Issue → updated**
5. Save

### API used
```
GET /rest/api/3/issue/{issueKey}?fields=summary,description,status,
    assignee,reporter,labels,components,priority,comment
Authorization: Basic base64(email:api-token)
```

### Jira Workflow — CODEGEN status
Add a custom status called `CODEGEN` to your Jira workflow:
1. Jira project → **Project settings** → **Workflows** → Edit
2. Add status: `CODEGEN`
3. Add a transition from any status → `CODEGEN`
4. Publish the workflow

When a ticket is moved to `CODEGEN`, the full automation pipeline fires.

---

## 2. Apache Kafka

### Purpose
Decouples webhook-service from rag-service. webhook-service publishes events; rag-service consumes them independently.

### How it works
Kafka runs in Docker. webhook-service uses Spring Kafka's `KafkaTemplate` to publish. rag-service uses `@KafkaListener` to consume. Each ticket's events always go to the same partition (partitioned by issueKey), guaranteeing ordering.

### Configuration

```yaml
# Shared across services
spring.kafka.bootstrap-servers: localhost:9092
app.kafka.topic: jira.issue.events
```

### Topic details
```
Topic:      jira.issue.events
Partitions: 3
Replication factor: 1
Partition key: issueKey (e.g. SCRUM-1)
```

### Kafka UI
Browse topics and messages at: http://localhost:8090

### Consumer groups
| Group ID | Service | Reads |
|---|---|---|
| `rag-consumer` | rag-service | All events, filters `jira:issue_updated` |

---

## 3. PostgreSQL + pgvector

### Purpose
Stores ticket embeddings as 384-dimensional vectors. Enables cosine similarity search to find semantically related tickets.

### How it works
rag-service uses Spring JDBC (`JdbcTemplate`) to insert and query. The `?::vector` cast converts a string like `[0.1, 0.2, ...]` into a pgvector type. The `<=>` operator computes cosine distance.

### Connection
```yaml
spring.datasource.url: jdbc:postgresql://localhost:5432/ragdb
spring.datasource.username: postgres
spring.datasource.password: postgres
```

### Schema
```sql
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE ticket_embeddings (
    id           BIGSERIAL PRIMARY KEY,
    issue_key    VARCHAR(50) NOT NULL,
    summary      TEXT,
    description  TEXT,
    status       VARCHAR(100),
    full_content TEXT,
    embedding    vector(384),
    indexed_at   TIMESTAMP DEFAULT NOW(),
    CONSTRAINT uq_issue_key UNIQUE (issue_key)
);

CREATE INDEX idx_ticket_embedding
    ON ticket_embeddings USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 10);
```

### Key query — similarity search
```sql
SELECT issue_key, summary, status,
       ROUND(CAST(1 - (embedding <=> ?::vector) AS numeric), 4) AS similarity
FROM ticket_embeddings
WHERE embedding IS NOT NULL
ORDER BY embedding <=> ?::vector
LIMIT ?
```

---

## 4. HuggingFace Inference API

### Purpose
Converts ticket text (and user queries) into 384-dimensional float vectors for semantic similarity comparison.

### How it works
rag-service calls the HuggingFace router API with the ticket's full content. The API returns a list of floats that represent the semantic meaning of the text. These are stored in pgvector and used to find similar tickets.

### Credentials needed

| Key | Where to get it |
|---|---|
| `huggingface.api-token` | https://huggingface.co → Settings → Access Tokens → New token (Fine-grained, enable "Make calls to Inference API") |

### API call
```
POST https://router.huggingface.co/hf-inference/models/BAAI/bge-small-en-v1.5
Authorization: Bearer hf_YOUR_TOKEN
Content-Type: application/json
Accept: application/json

{"inputs": "ticket content here", "options": {"wait_for_model": true}}
```

### Response format
```json
[[0.023, -0.14, 0.087, ...]]   ← outer list = batch, inner list = 384 floats
```

### Model
`BAAI/bge-small-en-v1.5` — 384-dimensional English embedding model, free tier.

---

## 5. Groq API (LLM)

### Purpose
Used by both rag-service and codegen-service:
- **rag-service**: answers natural language questions about indexed tickets
- **codegen-service**: generates complete, production-ready code from ticket descriptions

### How it works
Both services call Groq's OpenAI-compatible chat completions endpoint. The prompt includes ticket context (from RAG retrieval) and instructions. The response is the LLM's generated text.

### Credentials needed

| Key | Where to get it |
|---|---|
| `groq.api-key` | https://console.groq.com → API Keys → Create API Key |

### API call
```
POST https://api.groq.com/openai/v1/chat/completions
Authorization: Bearer gsk_YOUR_KEY
Content-Type: application/json

{
  "model": "llama-3.3-70b-versatile",
  "max_tokens": 2048,
  "messages": [{"role": "user", "content": "YOUR PROMPT"}]
}
```

### Response parsing
```java
choices[0].message.content  // the generated text
```

### Model
`llama-3.3-70b-versatile` — 70B parameter LLaMA model, free tier on Groq.

---

## 6. Bitbucket Cloud

### Purpose
codegen-service creates a feature branch, commits the generated code file, and opens a Pull Request automatically.

### How it works
Two Bitbucket REST API calls are made:
1. `POST /repositories/{workspace}/{repo}/src` (multipart form) — creates the file and branch in one call
2. `POST /repositories/{workspace}/{repo}/pullrequests` (JSON) — opens PR from feature branch → master

### Credentials needed

| Key | Where to get it |
|---|---|
| `bitbucket.workspace` | Workspace slug from Bitbucket URL: `https://bitbucket.org/{workspace}` |
| `bitbucket.email` | Your Atlassian account email (same as Jira) |
| `bitbucket.app-password` | https://bitbucket.org/account/settings/api-tokens → Create API token with `write:repository:bitbucket` and `write:pullrequest:bitbucket` scopes |

### Required token scopes
- `write:repository:bitbucket` — push files and create branches
- `write:pullrequest:bitbucket` — open pull requests
- `read:repository:bitbucket` — read repo info
- `read:pullrequest:bitbucket` — read PR info

### Authentication
Basic Auth with email:token (not username:token):
```
Authorization: Basic base64(email:api_token)
```

### Branch naming
```
feature/{issueKey}-{sanitized-summary}
e.g. feature/SCRUM-7-make-add-method-in-python
```

### File path
The LLM is instructed to include `// File: path/to/File.java` as the first line.
If present, that path is used. Otherwise defaults to `generated/{issueKey}.java`.

### Project → repo mapping
```yaml
bitbucket.project-repo-map: "{SCRUM: 'NammaHelpApiV1', PROJ: 'another-repo'}"
```
The project key is extracted from the issueKey prefix: `SCRUM-7` → `SCRUM`.

---

## 7. ngrok

### Purpose
Exposes your local `webhook-service` (port 8080) to the public internet so Jira Cloud can send webhook payloads to it.

### Setup
```bash
# Install
brew install ngrok   # or download from ngrok.com

# Authenticate (one time)
ngrok config add-authtoken YOUR_NGROK_TOKEN   # from dashboard.ngrok.com

# Run
ngrok http 8080
```

### Use the forwarding URL
```
Forwarding: https://abc123.ngrok.io -> http://localhost:8080
```

Set Jira webhook URL to: `https://abc123.ngrok.io/webhook/jira`

Note: the ngrok URL changes every time you restart ngrok (free tier). Update the Jira webhook URL each session.

---

## Integration Sequence Diagram

```
Developer        Jira Cloud       ngrok        webhook-service      Kafka
   │                │               │                │                │
   │─update ticket─▶│               │                │                │
   │                │──POST webhook─▶│                │                │
   │                │               │──POST /webhook/jira──▶│          │
   │                │               │                │──publish event──▶│
   │                │               │                │                │

                                rag-service        HuggingFace      pgvector
                                    │                   │               │
                         ◀──consume─┤                   │               │
                                    │──embed text───────▶│               │
                                    │◀──384-dim vector───│               │
                                    │──upsert embedding──────────────────▶│
                                    │                                   │

                    (if status = CODEGEN)

rag-service      codegen-service     Groq           Bitbucket
    │                  │               │                │
    │──POST /generate──▶│               │                │
    │                  │──POST /query──▶rag-service      │
    │                  │◀──RAG context──│                │
    │                  │──generate code─▶│               │
    │                  │◀──generated────│                │
    │                  │──push file + create branch──────▶│
    │                  │──open PR────────────────────────▶│
    │                  │                                  │
    Developer gets PR notification ◀──────────────────────│
```

---

## Ports Reference

| Port | Service | URL |
|---|---|---|
| 8080 | webhook-service | http://localhost:8080/webhook/jira |
| 8081 | rag-service | http://localhost:8081 |
| 8082 | codegen-service | http://localhost:8082/api/generate |
| 8090 | Kafka UI | http://localhost:8090 |
| 5432 | PostgreSQL | jdbc:postgresql://localhost:5432/ragdb |
| 9092 | Kafka broker | localhost:9092 |
| 2181 | Zookeeper | localhost:2181 |
