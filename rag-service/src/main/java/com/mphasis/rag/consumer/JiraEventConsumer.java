package com.mphasis.rag.consumer;

import com.mphasis.rag.model.KafkaJiraEvent;
import com.mphasis.rag.service.CodegenClientService;
import com.mphasis.rag.service.EmbeddingService;
import com.mphasis.rag.service.JiraFetchService;
import com.mphasis.rag.service.VectorStoreService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class JiraEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(JiraEventConsumer.class);

    private final JiraFetchService jiraFetchService;
    private final VectorStoreService vectorStoreService;
    private final EmbeddingService embeddingService;
    private final CodegenClientService codegenClientService;

    public JiraEventConsumer(JiraFetchService jiraFetchService,
                             VectorStoreService vectorStoreService,
                             EmbeddingService embeddingService,
                             CodegenClientService codegenClientService) {
        this.jiraFetchService = jiraFetchService;
        this.vectorStoreService = vectorStoreService;
        this.embeddingService = embeddingService;
        this.codegenClientService = codegenClientService;
    }

    @KafkaListener(topics = "${app.kafka.topic}", groupId = "rag-consumer")
    public void consume(ConsumerRecord<String, KafkaJiraEvent> record) {
        KafkaJiraEvent event = record.value();

        if (!"jira:issue_updated".equals(event.getEventType())) {
            log.debug("Skipping non-update event: {} for {}", event.getEventType(), event.getIssueKey());
            return;
        }

        log.info("Processing update for issue={}", event.getIssueKey());

        try {
            Map<String, String> ticket = jiraFetchService.fetchFullTicket(event.getIssueKey());
            String fullContent = buildContent(ticket);
            float[] embedding = embeddingService.embed(fullContent);

            vectorStoreService.upsert(
                    ticket.get("issueKey"),
                    ticket.get("summary"),
                    ticket.get("description"),
                    ticket.get("status"),
                    fullContent,
                    embedding
            );

            log.info("Indexed issue={} status='{}' summary='{}'",
                    ticket.get("issueKey"), ticket.get("status"), ticket.get("summary"));

            // Use event status (from Kafka) as it's reliable even when Jira API fetch fails
            if ("CODEGEN".equalsIgnoreCase(event.getStatus())) {
                log.info("Status is CODEGEN — triggering codegen-service for issue={}", event.getIssueKey());
                codegenClientService.triggerGeneration(event);
            }

        } catch (Exception e) {
            log.error("Failed to index issue={}: {}", event.getIssueKey(), e.getMessage(), e);
        }
    }

    private String buildContent(Map<String, String> t) {
        return String.join(" | ",
                "Issue: " + t.getOrDefault("issueKey", ""),
                "Summary: " + t.getOrDefault("summary", ""),
                "Status: " + t.getOrDefault("status", ""),
                "Priority: " + t.getOrDefault("priority", ""),
                "Assignee: " + t.getOrDefault("assignee", ""),
                "Reporter: " + t.getOrDefault("reporter", ""),
                "Labels: " + t.getOrDefault("labels", ""),
                "Description: " + t.getOrDefault("description", ""),
                "Comments: " + t.getOrDefault("comments", "")
        );
    }
}
