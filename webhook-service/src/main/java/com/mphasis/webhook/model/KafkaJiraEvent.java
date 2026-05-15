package com.mphasis.webhook.model;

import java.util.List;

public class KafkaJiraEvent {

    private String issueKey;
    private String issueId;
    private String eventType;
    private String issueEventTypeName;
    private long timestamp;
    private String summary;
    private String status;
    private String priority;
    private String assignee;
    private String reporter;
    private List<String> labels;
    private List<ChangedField> changedFields;

    public KafkaJiraEvent() {}

    public String getIssueKey() { return issueKey; }
    public void setIssueKey(String issueKey) { this.issueKey = issueKey; }
    public String getIssueId() { return issueId; }
    public void setIssueId(String issueId) { this.issueId = issueId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getIssueEventTypeName() { return issueEventTypeName; }
    public void setIssueEventTypeName(String issueEventTypeName) { this.issueEventTypeName = issueEventTypeName; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public String getAssignee() { return assignee; }
    public void setAssignee(String assignee) { this.assignee = assignee; }
    public String getReporter() { return reporter; }
    public void setReporter(String reporter) { this.reporter = reporter; }
    public List<String> getLabels() { return labels; }
    public void setLabels(List<String> labels) { this.labels = labels; }
    public List<ChangedField> getChangedFields() { return changedFields; }
    public void setChangedFields(List<ChangedField> changedFields) { this.changedFields = changedFields; }

    public static class ChangedField {
        private String field;
        private String fromValue;
        private String toValue;

        public ChangedField() {}
        public ChangedField(String field, String fromValue, String toValue) {
            this.field = field;
            this.fromValue = fromValue;
            this.toValue = toValue;
        }

        public String getField() { return field; }
        public void setField(String field) { this.field = field; }
        public String getFromValue() { return fromValue; }
        public void setFromValue(String fromValue) { this.fromValue = fromValue; }
        public String getToValue() { return toValue; }
        public void setToValue(String toValue) { this.toValue = toValue; }
    }
}
