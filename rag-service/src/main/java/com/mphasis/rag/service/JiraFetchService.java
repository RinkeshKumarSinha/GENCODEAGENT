package com.mphasis.rag.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class JiraFetchService {

    private static final Logger log = LoggerFactory.getLogger(JiraFetchService.class);

    @Value("${jira.base-url}")
    private String jiraBaseUrl;

    @Value("${jira.email}")
    private String jiraEmail;

    @Value("${jira.api-token}")
    private String jiraApiToken;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @SuppressWarnings("unchecked")
    public Map<String, String> fetchFullTicket(String issueKey) {
        String url = jiraBaseUrl + "/rest/api/3/issue/" + issueKey
                + "?fields=summary,description,status,assignee,reporter,labels,components,priority,comment";

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Basic " + encodeCredentials());
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);

            Map<String, Object> body = response.getBody();
            if (body == null) return emptyTicket(issueKey);

            Map<String, Object> fields = (Map<String, Object>) body.get("fields");
            if (fields == null) return emptyTicket(issueKey);

            String summary     = safe(fields.get("summary"));
            String description = extractAdfText(fields.get("description"));
            String status      = extractName(fields.get("status"));
            String priority    = extractName(fields.get("priority"));
            String assignee    = extractDisplayName(fields.get("assignee"));
            String reporter    = extractDisplayName(fields.get("reporter"));
            String labels      = extractLabels(fields.get("labels"));
            String components  = extractComponents(fields.get("components"));
            String comments    = extractComments(fields.get("comment"));

            log.info("Fetched ticket {}: summary='{}' status='{}'", issueKey, summary, status);

            Map<String, String> result = new HashMap<>();
            result.put("issueKey",    issueKey);
            result.put("summary",     summary);
            result.put("description", description);
            result.put("status",      status);
            result.put("priority",    priority);
            result.put("assignee",    assignee);
            result.put("reporter",    reporter);
            result.put("labels",      labels);
            result.put("components",  components);
            result.put("comments",    comments);
            return result;

        } catch (Exception e) {
            log.error("Failed to fetch Jira ticket {}: {}", issueKey, e.getMessage());
            return emptyTicket(issueKey);
        }
    }

    @SuppressWarnings("unchecked")
    private String extractAdfText(Object node) {
        if (node == null) return "";
        if (node instanceof String s) return s;
        if (node instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) node;
            if ("text".equals(map.get("type"))) {
                return safe(map.get("text"));
            }
            Object content = map.get("content");
            if (content instanceof List) {
                return joinAdfList((List<Object>) content);
            }
        }
        if (node instanceof List) {
            return joinAdfList((List<Object>) node);
        }
        return "";
    }

    private String joinAdfList(List<Object> list) {
        StringBuilder sb = new StringBuilder();
        for (Object item : list) {
            String text = extractAdfText(item);
            if (!text.isBlank()) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(text);
            }
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private String extractName(Object obj) {
        if (obj instanceof Map) return safe(((Map<String, Object>) obj).get("name"));
        return "";
    }

    @SuppressWarnings("unchecked")
    private String extractDisplayName(Object obj) {
        if (obj instanceof Map) return safe(((Map<String, Object>) obj).get("displayName"));
        return "";
    }

    @SuppressWarnings("unchecked")
    private String extractLabels(Object obj) {
        if (!(obj instanceof List)) return "";
        List<Object> list = (List<Object>) obj;
        StringBuilder sb = new StringBuilder();
        for (Object item : list) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(item);
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private String extractComponents(Object obj) {
        if (!(obj instanceof List)) return "";
        List<Object> list = (List<Object>) obj;
        StringBuilder sb = new StringBuilder();
        for (Object item : list) {
            if (item instanceof Map) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(safe(((Map<String, Object>) item).get("name")));
            }
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private String extractComments(Object obj) {
        if (!(obj instanceof Map)) return "";
        Object commentsList = ((Map<String, Object>) obj).get("comments");
        if (!(commentsList instanceof List)) return "";
        List<Object> comments = (List<Object>) commentsList;
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (Object c : comments) {
            if (count >= 5) break;
            if (c instanceof Map) {
                Map<String, Object> comment = (Map<String, Object>) c;
                String author = extractDisplayName(comment.get("author"));
                String body   = extractAdfText(comment.get("body"));
                if (sb.length() > 0) sb.append(" | ");
                sb.append(author).append(": ").append(body);
                count++;
            }
        }
        return sb.toString();
    }

    private String safe(Object obj) {
        return obj == null ? "" : String.valueOf(obj);
    }

    private Map<String, String> emptyTicket(String issueKey) {
        Map<String, String> m = new HashMap<>();
        m.put("issueKey", issueKey);
        for (String k : new String[]{"summary","description","status","priority",
                                     "assignee","reporter","labels","components","comments"}) {
            m.put(k, "");
        }
        return m;
    }

    private String encodeCredentials() {
        return Base64.getEncoder().encodeToString((jiraEmail + ":" + jiraApiToken).getBytes());
    }
}
