package com.mphasis.codegen.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
public class RagContextService {

    private static final Logger log = LoggerFactory.getLogger(RagContextService.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${rag.service-url:http://localhost:8081}")
    private String ragServiceUrl;

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> fetchSimilarTickets(String query, int topK) {
        try {
            String url = ragServiceUrl + "/api/query";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            String body = objectMapper.writeValueAsString(Map.of("query", query, "topK", topK));

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);

            Map<String, Object> parsed = objectMapper.readValue(response.getBody(), Map.class);
            List<Map<String, Object>> sources = (List<Map<String, Object>>) parsed.get("sources");

            log.info("Fetched {} similar tickets from RAG service for query: {}", sources.size(), query);
            return sources;

        } catch (Exception e) {
            log.warn("Failed to fetch RAG context: {}", e.getMessage());
            return List.of();
        }
    }
}
