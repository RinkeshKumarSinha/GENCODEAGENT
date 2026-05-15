package com.mphasis.webhook.service;

import com.mphasis.webhook.model.KafkaJiraEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class KafkaProducerService {

    private static final Logger log = LoggerFactory.getLogger(KafkaProducerService.class);

    private final KafkaTemplate<String, KafkaJiraEvent> kafkaTemplate;

    @Value("${app.kafka.topic}")
    private String topic;

    public KafkaProducerService(KafkaTemplate<String, KafkaJiraEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishEvent(KafkaJiraEvent event) {
        CompletableFuture<SendResult<String, KafkaJiraEvent>> future =
                kafkaTemplate.send(topic, event.getIssueKey(), event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish event for issue={} event={}: {}",
                        event.getIssueKey(), event.getEventType(), ex.getMessage());
            } else {
                log.info("Published event: issue={} event={} partition={} offset={}",
                        event.getIssueKey(),
                        event.getEventType(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }
}
