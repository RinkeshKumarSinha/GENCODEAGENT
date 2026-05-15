# INTENTION.md — Jira → Kafka → RAG + Bedrock → GitHub PR Automation

## Overview

This project automates the full software development lifecycle by listening to Jira ticket events and using AI (AWS Bedrock + Claude) to generate code, create GitHub PRs, and keep Jira in sync — all without manual developer intervention for boilerplate or scaffolding work.

---

## Goals

- Automatically detect Jira ticket activity (create / update / status change)
- Enrich and embed ticket data for semantic retrieval (RAG)
- Use AWS Bedrock Agents with Claude to generate production-ready code per ticket
- Automatically open GitHub Pull Requests with the generated code
- Post feedback (PR link, status) back to the originating Jira ticket
- Support continuous updates: when a ticket changes, the workflow re-triggers and updates the PR

---

## Architecture Layers

### 1. Jira (Event Source)
- **Role:** Source of truth for all work items
- **Mechanism:** Jira Webhooks configured for `jira:issue_created` and `jira:issue_updated` events
- **Payload:** Sends issue key, event type, changed fields, and metadata to the Spring Boot receiver

### 2. Spring Boot + Maven (Webhook Receiver)
- **Role:** Lightweight HTTP endpoint that receives and validates Jira webhook payloads
- **Tech:** Spring Boot (Maven), REST Controller
- **Responsibility:**
  - Validate incoming webhook (shared secret / HMAC)
  - Extract issue key and event type
  - Immediately publish a slim event to Kafka (keep receiver thin)
- **Endpoint:** `POST /webhook/jira`

### 3. Apache Kafka (Event Bus)
- **Role:** Decouples webhook ingestion from downstream processing
- **Topic:** `jira.issue.events`
- **Partition Key:** Jira issue key (ensures ordered processing per ticket)
- **Consumer Group:** Allows horizontal scaling of consumers

### 4. Kafka Consumer Service (Ticket Enrichment)
- **Role:** Consumes events and fetches full ticket details from Jira REST API
- **Why:** Webhook payloads are partial; full context is needed for code generation
- **Jira API Call:** `GET /rest/api/3/issue/{issueKey}?fields=summary,description,acceptanceCriteria,status,labels,components`
- **Idempotency:** Deduplicate using `issue.updated` timestamp to handle duplicate webhook deliveries

### 5. Embedding Service (Semantic Indexing)
- **Role:** Converts ticket content into vector embeddings for RAG retrieval
- **Model:** Amazon Bedrock — Titan Embeddings v2 or Cohere Embed
- **Input:** Concatenated `summary + description + acceptance criteria`
- **Storage:** pgvector (Postgres) or Amazon OpenSearch Serverless
- **Index Key:** Jira issue key (supports upsert on re-trigger)

### 6. AWS Bedrock Agent + RAG (Code Generation)
- **Role:** Core AI engine — retrieves relevant context and generates code
- **RAG Flow:**
  1. Query vector DB with current ticket embedding → retrieve similar past tickets / implementations
  2. Assemble prompt: ticket details + RAG context + code style guidelines
  3. Call Claude (via Bedrock `InvokeModel` or Bedrock Agents with Knowledge Base)
- **Output:** Production-ready code files (controllers, services, tests, etc.) per ticket
- **Prompt Strategy:** Include ticket type (story / bug / task) to tailor generation style

### 7. GitHub API (PR Automation)
- **Role:** Commits generated code and opens a Pull Request
- **Flow:**
  1. Create branch: `feature/{ISSUE-KEY}-{short-slug}`
  2. Commit generated files to the branch
  3. Open PR with title `[{ISSUE-KEY}] {ticket summary}` and body containing ticket description
  4. Label PR as `ai-generated` for mandatory human review
- **Merge Policy:** Never auto-merge — always require human approval

### 8. Feedback Loop (Jira Update)
- **Role:** Keeps Jira in sync with GitHub activity
- **Triggers:** PR opened, PR reviewed, PR merged
- **Actions:**
  - Post comment on Jira ticket with PR link
  - Optionally transition ticket status (e.g. `In Progress → In Review`)
- **API:** `POST /rest/api/3/issue/{key}/comment`

---

## Data Flow (End-to-End)

```
Jira Ticket Event
      │
      ▼
Spring Boot Webhook Receiver  ──►  Kafka Topic: jira.issue.events
                                          │
                                          ▼
                               Kafka Consumer Service
                                          │
                                          ▼
                               Jira REST API  (/issue/{key})
                                          │
                                          ▼
                               Embedding Service (Bedrock Titan/Cohere)
                                          │
                              ┌───────────┴───────────┐
                              ▼                       ▼
                         Vector DB              AWS Bedrock Agent
                       (pgvector /            RAG retrieval + Claude
                       OpenSearch)             code generation
                              └───────────┬───────────┘
                                          │
                                          ▼
                               GitHub API
                          (branch → commit → PR)
                                          │
                                          ▼
                          Jira comment + status update
```

---

## Tech Stack

| Layer              | Technology                                      |
|--------------------|-------------------------------------------------|
| Backend Framework  | Spring Boot 3.x + Maven                         |
| Event Bus          | Apache Kafka                                    |
| Ticket Source      | Jira REST API v3                                |
| Embedding Model    | Amazon Bedrock — Titan Embeddings v2 / Cohere   |
| Vector Store       | pgvector (Postgres) or Amazon OpenSearch        |
| AI Agent           | AWS Bedrock Agents + Claude (claude-sonnet-4-6) |
| Code Repository    | GitHub REST API v3                              |
| Cloud              | AWS (Bedrock, MSK or self-hosted Kafka)         |

---

## Key Design Decisions

- **Thin webhook receiver:** The Spring Boot endpoint only validates and publishes to Kafka. No business logic at ingestion time — ensures low latency and high reliability.
- **Full ticket fetch on consume:** Enrichment happens in the consumer, not the receiver, so Kafka acts as a reliable buffer even if Jira is temporarily slow.
- **Upsert embeddings:** Every ticket update re-embeds and upserts — the vector DB always reflects the latest ticket state.
- **RAG over pure prompting:** Retrieving similar past tickets and implementations gives Claude real project-specific context, dramatically improving code relevance.
- **Human-in-the-loop on PRs:** All AI-generated PRs require human review. The `ai-generated` label ensures visibility and prevents accidental auto-merges.
- **Idempotent processing:** Deduplication by `issue.updated` timestamp prevents duplicate embeddings or duplicate PRs from repeated webhook deliveries.

---

## Guardrails & Risks

| Risk                              | Mitigation                                              |
|-----------------------------------|---------------------------------------------------------|
| Duplicate webhook delivery        | Deduplicate by `issue.updated` timestamp in consumer    |
| Large tickets exceeding LLM limit | Chunk description + summarize comments separately       |
| Bad AI-generated code merged      | Mandatory PR review; `ai-generated` label; CI gates     |
| Jira API rate limits              | Exponential backoff + caching of recently fetched keys  |
| Kafka consumer lag                | Monitor consumer group lag; scale consumer instances    |
| Sensitive data in embeddings      | Sanitize PII from ticket text before embedding          |

---

## Future Enhancements

- **Test generation:** Automatically generate unit and integration tests alongside feature code
- **Multi-repo support:** Route PR creation to the correct repo based on Jira project / component
- **Feedback-driven fine-tuning:** Use PR review comments to improve future prompts
- **Slack notifications:** Notify the assignee when a PR is auto-created for their ticket
- **Dry-run mode:** Preview generated code in a Jira comment before committing to GitHub
