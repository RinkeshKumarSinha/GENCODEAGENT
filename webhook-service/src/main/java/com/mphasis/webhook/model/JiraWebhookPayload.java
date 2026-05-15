package com.mphasis.webhook.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class JiraWebhookPayload {

    @JsonProperty("webhookEvent")
    private String webhookEvent;

    @JsonProperty("issue_event_type_name")
    private String issueEventTypeName;

    @JsonProperty("timestamp")
    private long timestamp;

    @JsonProperty("issue")
    private JiraIssue issue;

    @JsonProperty("changelog")
    private JiraChangelog changelog;

    public String getWebhookEvent() { return webhookEvent; }
    public String getIssueEventTypeName() { return issueEventTypeName; }
    public long getTimestamp() { return timestamp; }
    public JiraIssue getIssue() { return issue; }
    public JiraChangelog getChangelog() { return changelog; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JiraIssue {
        @JsonProperty("id")   private String id;
        @JsonProperty("key")  private String key;
        @JsonProperty("fields") private JiraFields fields;

        public String getId() { return id; }
        public String getKey() { return key; }
        public JiraFields getFields() { return fields; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JiraFields {
        @JsonProperty("summary")     private String summary;
        @JsonProperty("status")      private JiraStatus status;
        @JsonProperty("assignee")    private JiraUser assignee;
        @JsonProperty("reporter")    private JiraUser reporter;
        @JsonProperty("labels")      private List<String> labels;
        @JsonProperty("priority")    private JiraPriority priority;
        @JsonProperty("description") private Object description;

        public String getSummary() { return summary; }
        public JiraStatus getStatus() { return status; }
        public JiraUser getAssignee() { return assignee; }
        public JiraUser getReporter() { return reporter; }
        public List<String> getLabels() { return labels; }
        public JiraPriority getPriority() { return priority; }
        public Object getDescription() { return description; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JiraStatus {
        @JsonProperty("name") private String name;
        public String getName() { return name; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JiraUser {
        @JsonProperty("displayName")   private String displayName;
        @JsonProperty("emailAddress")  private String emailAddress;
        public String getDisplayName() { return displayName; }
        public String getEmailAddress() { return emailAddress; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JiraPriority {
        @JsonProperty("name") private String name;
        public String getName() { return name; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JiraChangelog {
        @JsonProperty("items") private List<ChangeItem> items;
        public List<ChangeItem> getItems() { return items; }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class ChangeItem {
            @JsonProperty("field")        private String field;
            @JsonProperty("fromString")   private String fromString;
            @JsonProperty("toString")     private String toString;

            public String getField() { return field; }
            public String getFromString() { return fromString; }
            public String getToString() { return toString; }
        }
    }
}
