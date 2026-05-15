package com.mphasis.webhook.service;

import com.mphasis.webhook.model.KafkaJiraEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * Temporary test consumer — verifies events land in Kafka.
 * Disable by removing @Service once pipeline is confirmed working.
 */
@Service
public class KafkaTestConsumerService {

    private static final Logger log = LoggerFactory.getLogger(KafkaTestConsumerService.class);

    @KafkaListener(
            topics = "${app.kafka.topic}",
            groupId = "webhook-test-consumer"
    )
    public void consume(ConsumerRecord<String, KafkaJiraEvent> record) {
        KafkaJiraEvent event = record.value();
        log.info("=== TEST CONSUMER RECEIVED ===");
        log.info("  Topic     : {}", record.topic());
        log.info("  Partition : {} | Offset: {}", record.partition(), record.offset());
        log.info("  Key       : {}", record.key());
        log.info("  Issue Key : {}", event.getIssueKey());
        log.info("  Event     : {}", event.getEventType());
        log.info("  Summary   : {}", event.getSummary());
        log.info("  Status    : {}", event.getStatus());
        if (event.getChangedFields() != null && !event.getChangedFields().isEmpty()) {
            log.info("  Changes:");
            event.getChangedFields().forEach(cf ->
                    log.info("    {} : '{}' -> '{}'", cf.getField(), cf.getFromValue(), cf.getToValue()));
        }
        log.info("==============================");
    }
}
