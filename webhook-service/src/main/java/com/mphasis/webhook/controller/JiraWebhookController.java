package com.mphasis.webhook.controller;

import com.mphasis.webhook.model.JiraWebhookPayload;
import com.mphasis.webhook.model.KafkaJiraEvent;
import com.mphasis.webhook.service.KafkaProducerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/webhook")
public class JiraWebhookController {

    private static final Logger log = LoggerFactory.getLogger(JiraWebhookController.class);

    private final KafkaProducerService kafkaProducerService;

    @Value("${app.webhook.secret:}")
    private String webhookSecret;

    public JiraWebhookController(KafkaProducerService kafkaProducerService) {
        this.kafkaProducerService = kafkaProducerService;
    }

    @PostMapping("/jira")
    public ResponseEntity<Map<String, String>> handleJiraWebhook(
            @RequestParam(value = "token", required = false) String token,
            @RequestHeader(value = "X-Webhook-Token", required = false) String headerToken,
            @RequestBody JiraWebhookPayload payload) {

        if (!isValidToken(token, headerToken)) {
            log.warn("Rejected webhook — invalid token");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid webhook token"));
        }

        String event = payload.getWebhookEvent();
        if (event == null || (!event.equals("jira:issue_created") && !event.equals("jira:issue_updated"))) {
            log.debug("Ignoring unsupported event type: {}", event);
            return ResponseEntity.ok(Map.of("status", "ignored", "event", String.valueOf(event)));
        }

        if (payload.getIssue() == null || payload.getIssue().getKey() == null) {
            log.warn("Webhook payload missing issue key, ignoring");
            return ResponseEntity.badRequest().body(Map.of("error", "Missing issue key"));
        }

        KafkaJiraEvent kafkaEvent = buildKafkaEvent(payload);
        kafkaProducerService.publishEvent(kafkaEvent);

        log.info("Accepted webhook: event={} issue={}", event, payload.getIssue().getKey());
        return ResponseEntity.ok(Map.of(
                "status", "accepted",
                "issueKey", payload.getIssue().getKey(),
                "event", event
        ));
    }

    private boolean isValidToken(String queryToken, String headerToken) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            return true;
        }
        return webhookSecret.equals(queryToken) || webhookSecret.equals(headerToken);
    }

    private KafkaJiraEvent buildKafkaEvent(JiraWebhookPayload payload) {
        JiraWebhookPayload.JiraIssue issue = payload.getIssue();
        JiraWebhookPayload.JiraFields fields = issue.getFields();

        List<KafkaJiraEvent.ChangedField> changedFields = new ArrayList<>();
        if (payload.getChangelog() != null && payload.getChangelog().getItems() != null) {
            for (JiraWebhookPayload.JiraChangelog.ChangeItem item : payload.getChangelog().getItems()) {
                changedFields.add(new KafkaJiraEvent.ChangedField(
                        item.getField(), item.getFromString(), item.getToString()));
            }
        }

        KafkaJiraEvent event = new KafkaJiraEvent();
        event.setIssueKey(issue.getKey());
        event.setIssueId(issue.getId());
        event.setEventType(payload.getWebhookEvent());
        event.setIssueEventTypeName(payload.getIssueEventTypeName());
        event.setTimestamp(payload.getTimestamp());
        event.setChangedFields(changedFields);

        if (fields != null) {
            event.setSummary(fields.getSummary());
            event.setStatus(fields.getStatus() != null ? fields.getStatus().getName() : null);
            event.setPriority(fields.getPriority() != null ? fields.getPriority().getName() : null);
            event.setAssignee(fields.getAssignee() != null ? fields.getAssignee().getDisplayName() : null);
            event.setReporter(fields.getReporter() != null ? fields.getReporter().getDisplayName() : null);
            event.setLabels(fields.getLabels() != null ? fields.getLabels() : Collections.emptyList());
        }

        return event;
    }
}
