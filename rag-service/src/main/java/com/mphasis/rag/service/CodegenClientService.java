package com.mphasis.rag.service;

import com.mphasis.rag.model.KafkaJiraEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class CodegenClientService {

    private static final Logger log = LoggerFactory.getLogger(CodegenClientService.class);

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${codegen.service-url:http://localhost:8082}")
    private String codegenServiceUrl;

    public void triggerGeneration(KafkaJiraEvent event) {
        try {
            String url = UriComponentsBuilder.fromHttpUrl(codegenServiceUrl + "/api/generate")
                    .queryParam("issueKey",    event.getIssueKey())
                    .queryParam("summary",     event.getSummary() != null ? event.getSummary() : "")
                    .queryParam("description", "")
                    .queryParam("priority",    event.getPriority() != null ? event.getPriority() : "")
                    .toUriString();

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, HttpEntity.EMPTY, String.class);
            log.info("Codegen triggered for issue={}, response={}", event.getIssueKey(), response.getStatusCode());
        } catch (Exception e) {
            log.error("Failed to trigger codegen for issue={}: {}", event.getIssueKey(), e.getMessage());
        }
    }
}
