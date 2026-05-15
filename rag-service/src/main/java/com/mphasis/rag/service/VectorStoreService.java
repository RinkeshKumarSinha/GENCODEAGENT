package com.mphasis.rag.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class VectorStoreService {

    private static final Logger log = LoggerFactory.getLogger(VectorStoreService.class);

    private final JdbcTemplate jdbcTemplate;

    public VectorStoreService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void upsert(String issueKey, String summary, String description,
                       String status, String fullContent, float[] embedding) {
        String vectorStr = toVectorString(embedding);
        jdbcTemplate.update("""
                INSERT INTO ticket_embeddings
                    (issue_key, summary, description, status, full_content, embedding, indexed_at)
                VALUES (?, ?, ?, ?, ?, ?::vector, NOW())
                ON CONFLICT (issue_key) DO UPDATE SET
                    summary      = EXCLUDED.summary,
                    description  = EXCLUDED.description,
                    status       = EXCLUDED.status,
                    full_content = EXCLUDED.full_content,
                    embedding    = EXCLUDED.embedding,
                    indexed_at   = NOW()
                """,
                issueKey, summary, description, status, fullContent, vectorStr);

        log.info("Upserted ticket for issue={}", issueKey);
    }

    public List<Map<String, Object>> search(float[] queryEmbedding, int topK) {
        String vectorStr = toVectorString(queryEmbedding);
        return jdbcTemplate.queryForList("""
                SELECT issue_key, summary, description, status, full_content,
                       ROUND(CAST(1 - (embedding <=> ?::vector) AS numeric), 4) AS similarity
                FROM ticket_embeddings
                WHERE embedding IS NOT NULL
                ORDER BY embedding <=> ?::vector
                LIMIT ?
                """,
                vectorStr, vectorStr, topK);
    }

    private String toVectorString(float[] v) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(v[i]);
        }
        sb.append(']');
        return sb.toString();
    }

    public List<Map<String, Object>> getAllTickets() {
        return jdbcTemplate.queryForList("""
                SELECT issue_key, summary, status, indexed_at
                FROM ticket_embeddings
                ORDER BY indexed_at DESC
                """);
    }
}
