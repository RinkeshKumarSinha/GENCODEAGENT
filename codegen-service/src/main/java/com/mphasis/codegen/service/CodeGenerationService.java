package com.mphasis.codegen.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class CodeGenerationService {

    private static final Logger log = LoggerFactory.getLogger(CodeGenerationService.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${groq.api-key}")
    private String groqApiKey;

    @Value("${groq.model:llama-3.3-70b-versatile}")
    private String groqModel;

    @SuppressWarnings("unchecked")
    public String generateCode(Map<String, String> ticket, List<Map<String, Object>> ragContext) {
        String issueKey    = ticket.getOrDefault("issueKey", "");
        String summary     = ticket.getOrDefault("summary", "");
        String description = ticket.getOrDefault("description", "");
        String priority    = ticket.getOrDefault("priority", "");
        String components  = ticket.getOrDefault("components", "");

        String prompt = buildPrompt(issueKey, summary, description, priority, components, ragContext);

        try {
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("model", groqModel);
            requestBody.put("max_tokens", 4096);
            requestBody.put("messages", List.of(Map.of("role", "user", "content", prompt)));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + groqApiKey);

            ResponseEntity<String> response = restTemplate.exchange(
                    "https://api.groq.com/openai/v1/chat/completions",
                    HttpMethod.POST,
                    new HttpEntity<>(objectMapper.writeValueAsString(requestBody), headers),
                    String.class);

            Map<String, Object> body = objectMapper.readValue(response.getBody(), Map.class);
            List<Map<String, Object>> choices = (List<Map<String, Object>>) body.get("choices");
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            String code = (String) message.get("content");

            code = stripMarkdownFences(code);
            log.info("Generated {} chars of code for {}", code.length(), issueKey);
            return code;

        } catch (Exception e) {
            log.error("Code generation failed for {}: {}", issueKey, e.getMessage());
            return "// Code generation failed for " + issueKey + ": " + e.getMessage();
        }
    }

    private String buildPrompt(String issueKey, String summary, String description,
                               String priority, String components,
                               List<Map<String, Object>> ragContext) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are an expert software developer. Generate complete, production-ready code for the following Jira ticket.\n\n");

        sb.append("=== TICKET ===\n");
        sb.append("Key: ").append(issueKey).append("\n");
        sb.append("Summary: ").append(summary).append("\n");
        sb.append("Priority: ").append(priority).append("\n");
        sb.append("Components: ").append(components).append("\n");
        sb.append("Description: ").append(description).append("\n\n");

        if (!ragContext.isEmpty()) {
            sb.append("=== RELATED TICKETS (for context) ===\n");
            for (int i = 0; i < ragContext.size(); i++) {
                Map<String, Object> t = ragContext.get(i);
                sb.append("[").append(i + 1).append("] ")
                  .append(t.getOrDefault("issueKey", "")).append(" — ")
                  .append(t.getOrDefault("summary", ""))
                  .append(" (Status: ").append(t.getOrDefault("status", "")).append(")\n");
            }
            sb.append("\n");
        }

        sb.append("=== INSTRUCTIONS ===\n");
        sb.append("- Output ONLY the code. No markdown fences. No explanation outside the code.\n");
        sb.append("- The very first line MUST be a comment with the filename, e.g.: // File: src/main/java/com/example/MyFeature.java\n");
        sb.append("- Write clean, well-structured, production-ready code.\n");
        sb.append("- Use the related tickets above for context on patterns and conventions used in this codebase.\n");

        return sb.toString();
    }

    private String stripMarkdownFences(String code) {
        if (code == null) return "";
        code = code.trim();
        if (code.startsWith("```")) {
            int firstNewline = code.indexOf('\n');
            if (firstNewline != -1) code = code.substring(firstNewline + 1);
            if (code.endsWith("```")) code = code.substring(0, code.lastIndexOf("```")).trim();
        }
        return code;
    }
}
