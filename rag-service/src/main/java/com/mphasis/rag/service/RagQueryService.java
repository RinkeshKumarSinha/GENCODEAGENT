package com.mphasis.rag.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RagQueryService {

    private static final Logger log = LoggerFactory.getLogger(RagQueryService.class);

    private final VectorStoreService vectorStoreService;
    private final EmbeddingService embeddingService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${groq.api-key}")
    private String groqApiKey;

    @Value("${groq.model:llama-3.3-70b-versatile}")
    private String groqModel;

    public RagQueryService(VectorStoreService vectorStoreService, EmbeddingService embeddingService) {
        this.vectorStoreService = vectorStoreService;
        this.embeddingService = embeddingService;
    }

    public Map<String, Object> query(String userQuery, int topK) {
        float[] queryEmbedding = embeddingService.embed(userQuery);
        List<Map<String, Object>> sources = vectorStoreService.search(queryEmbedding, topK);

        if (sources.isEmpty()) {
            return Map.of(
                    "answer", "No tickets indexed yet. Update a Jira ticket to trigger indexing.",
                    "sources", List.of()
            );
        }

        String prompt = buildPrompt(userQuery, sources);
        String answer = callGroq(prompt);

        List<Map<String, Object>> sourceSummary = sources.stream()
                .map(s -> Map.<String, Object>of(
                        "issueKey",   s.getOrDefault("issue_key", ""),
                        "summary",    s.getOrDefault("summary", ""),
                        "status",     s.getOrDefault("status", ""),
                        "similarity", s.getOrDefault("similarity", 0)
                ))
                .toList();

        return Map.of("answer", answer, "sources", sourceSummary);
    }

    private String buildPrompt(String userQuery, List<Map<String, Object>> tickets) {
        StringBuilder ctx = new StringBuilder();
        ctx.append("You are an AI assistant helping software teams understand their Jira tickets.\n");
        ctx.append("The following Jira tickets were recently updated. Use them to answer the question.\n\n");
        ctx.append("=== TICKET CONTEXT ===\n");

        for (int i = 0; i < tickets.size(); i++) {
            Map<String, Object> t = tickets.get(i);
            ctx.append(String.format("[%d] %s — %s (Status: %s)\n",
                    i + 1,
                    t.getOrDefault("issue_key", ""),
                    t.getOrDefault("summary", ""),
                    t.getOrDefault("status", "")));
            String content = String.valueOf(t.getOrDefault("full_content", ""));
            if (!content.isBlank()) {
                ctx.append("Details: ").append(content, 0, Math.min(content.length(), 600)).append("\n");
            }
            ctx.append("\n");
        }

        ctx.append("=== USER QUESTION ===\n").append(userQuery).append("\n\n");
        ctx.append("Provide a concise, helpful answer based on the ticket context above.");
        return ctx.toString();
    }

    @SuppressWarnings("unchecked")
    private String callGroq(String prompt) {
        try {
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("model", groqModel);
            requestBody.put("max_tokens", 2048);
            requestBody.put("messages", List.of(Map.of("role", "user", "content", prompt)));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + groqApiKey);

            ResponseEntity<Map> response = restTemplate.exchange(
                    "https://api.groq.com/openai/v1/chat/completions",
                    HttpMethod.POST,
                    new HttpEntity<>(objectMapper.writeValueAsString(requestBody), headers),
                    Map.class);

            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.getBody().get("choices");
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            return (String) message.get("content");

        } catch (Exception e) {
            log.error("Groq call failed: {}", e.getMessage());
            return "Error generating answer: " + e.getMessage();
        }
    }
}
