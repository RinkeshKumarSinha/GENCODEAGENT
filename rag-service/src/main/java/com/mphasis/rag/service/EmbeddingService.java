package com.mphasis.rag.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${huggingface.api-token}")
    private String hfToken;

    @Value("${huggingface.model:BAAI/bge-small-en-v1.5}")
    private String model;

    @SuppressWarnings("unchecked")
    public float[] embed(String text) {
        try {
            String truncated = text.length() > 512 ? text.substring(0, 512) : text;

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
            headers.set("Authorization", "Bearer " + hfToken);

            String body = objectMapper.writeValueAsString(
                    java.util.Map.of(
                            "inputs", truncated,
                            "options", java.util.Map.of("wait_for_model", true)
                    )
            );

            String url = "https://router.huggingface.co/hf-inference/models/" + model;

            // Use String response to avoid Spring choking on HuggingFace's multi-value Content-Type header
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    String.class);

            List<?> outer = objectMapper.readValue(response.getBody(), List.class);
            List<Double> embedding;
            if (!outer.isEmpty() && outer.get(0) instanceof List) {
                embedding = (List<Double>) outer.get(0);
            } else {
                embedding = (List<Double>) outer;
            }

            float[] result = new float[embedding.size()];
            for (int i = 0; i < embedding.size(); i++) {
                result[i] = ((Number) embedding.get(i)).floatValue();
            }

            log.debug("Embedded {} chars -> {} dimensions", text.length(), result.length);
            return result;

        } catch (Exception e) {
            log.error("Embedding failed: {}", e.getMessage());
            throw new RuntimeException("Embedding failed", e);
        }
    }
}
