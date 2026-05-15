CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS ticket_embeddings (
    id            BIGSERIAL PRIMARY KEY,
    issue_key     VARCHAR(50)  NOT NULL,
    summary       TEXT,
    description   TEXT,
    status        VARCHAR(100),
    full_content  TEXT,
    embedding     vector(384),
    indexed_at    TIMESTAMP DEFAULT NOW(),
    CONSTRAINT uq_issue_key UNIQUE (issue_key)
);

CREATE INDEX IF NOT EXISTS idx_ticket_embedding ON ticket_embeddings USING ivfflat (embedding vector_cosine_ops) WITH (lists = 10);
