package com.mphasis.codegen.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class BitbucketService {

    private static final Logger log = LoggerFactory.getLogger(BitbucketService.class);

    private static final String BB_API = "https://api.bitbucket.org/2.0";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${bitbucket.workspace}")
    private String workspace;

    @Value("${bitbucket.email}")
    private String email;

    @Value("${bitbucket.app-password}")
    private String appPassword;

    @Value("${bitbucket.destination-branch:main}")
    private String destinationBranch;

    // Map of Jira project key → Bitbucket repo slug, injected from YAML map
    @Value("#{${bitbucket.project-repo-map}}")
    private Map<String, String> projectRepoMap;

    public void createPR(String issueKey, String summary, String generatedCode) {
        String projectKey = issueKey.contains("-")
                ? issueKey.substring(0, issueKey.indexOf('-'))
                : issueKey;

        String repo = projectRepoMap.get(projectKey);
        if (repo == null) {
            log.warn("No Bitbucket repo mapped for project key '{}' (issue {}). Skipping PR.", projectKey, issueKey);
            return;
        }

        String branchName = buildBranchName(issueKey, summary);
        String filePath   = resolveFilePath(issueKey, generatedCode);
        String commitMsg  = "feat(" + issueKey + "): auto-generated code from Jira ticket";
        String prTitle    = "[" + issueKey + "] " + summary;
        String prDesc     = "Auto-generated code for Jira ticket **" + issueKey + "**.\n\n"
                          + "Summary: " + summary + "\n\n"
                          + "Please review the generated implementation and merge when ready.";

        try {
            // Step 1: Push file to new branch (Bitbucket auto-creates the branch)
            pushFileToBranch(repo, branchName, filePath, generatedCode, commitMsg);
            log.info("Pushed generated code to branch '{}' in repo '{}'", branchName, repo);

            // Step 2: Open PR
            String prUrl = openPullRequest(repo, branchName, prTitle, prDesc);
            log.info("PR created: {}", prUrl);

        } catch (Exception e) {
            log.error("Failed to create PR for {}: {}", issueKey, e.getMessage(), e);
        }
    }

    private void pushFileToBranch(String repo, String branch, String filePath,
                                  String content, String commitMessage) throws Exception {
        String url = BB_API + "/repositories/" + workspace + "/" + repo + "/src";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.set("Authorization", basicAuth());

        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("message", commitMessage);
        form.add("branch", branch);
        form.add(filePath, content);

        restTemplate.exchange(url, HttpMethod.POST,
                new HttpEntity<>(form, headers), String.class);
    }

    @SuppressWarnings("unchecked")
    private String openPullRequest(String repo, String sourceBranch,
                                   String title, String description) throws Exception {
        String url = BB_API + "/repositories/" + workspace + "/" + repo + "/pullrequests";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", basicAuth());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", title);
        body.put("description", description);
        body.put("source", Map.of("branch", Map.of("name", sourceBranch)));
        body.put("destination", Map.of("branch", Map.of("name", destinationBranch)));
        body.put("close_source_branch", true);

        ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.POST,
                new HttpEntity<>(objectMapper.writeValueAsString(body), headers),
                String.class);

        Map<String, Object> parsed = objectMapper.readValue(response.getBody(), Map.class);
        Map<String, Object> links  = (Map<String, Object>) parsed.get("links");
        Map<String, Object> html   = (Map<String, Object>) links.get("html");
        return (String) html.get("href");
    }

    private String buildBranchName(String issueKey, String summary) {
        String sanitized = summary == null ? "" : summary
                .toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
        if (sanitized.length() > 40) sanitized = sanitized.substring(0, 40).replaceAll("-$", "");
        return "feature/" + issueKey + (sanitized.isEmpty() ? "" : "-" + sanitized);
    }

    private String resolveFilePath(String issueKey, String code) {
        if (code != null && code.startsWith("// File:")) {
            String firstLine = code.lines().findFirst().orElse("");
            String path = firstLine.replace("// File:", "").trim();
            if (!path.isEmpty()) return path;
        }
        return "generated/" + issueKey + ".java";
    }

    private String basicAuth() {
        return "Basic " + Base64.getEncoder().encodeToString((email + ":" + appPassword).getBytes());
    }
}
