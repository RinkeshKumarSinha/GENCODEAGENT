package com.mphasis.codegen.controller;

import com.mphasis.codegen.service.BitbucketService;
import com.mphasis.codegen.service.CodeGenerationService;
import com.mphasis.codegen.service.JiraFetchService;
import com.mphasis.codegen.service.RagContextService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class CodegenController {

    private static final Logger log = LoggerFactory.getLogger(CodegenController.class);

    private final JiraFetchService jiraFetchService;
    private final RagContextService ragContextService;
    private final CodeGenerationService codeGenerationService;
    private final BitbucketService bitbucketService;

    public CodegenController(JiraFetchService jiraFetchService,
                             RagContextService ragContextService,
                             CodeGenerationService codeGenerationService,
                             BitbucketService bitbucketService) {
        this.jiraFetchService      = jiraFetchService;
        this.ragContextService     = ragContextService;
        this.codeGenerationService = codeGenerationService;
        this.bitbucketService      = bitbucketService;
    }

    @PostMapping("/generate")
    public ResponseEntity<Map<String, String>> generate(
            @RequestParam String issueKey,
            @RequestParam(required = false) String summary,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String priority) {

        log.info("Received codegen request for issue={}", issueKey);

        try {
            // Try to fetch full ticket from Jira; fall back to params passed from Kafka event
            Map<String, String> ticket = jiraFetchService.fetchFullTicket(issueKey);

            // If Jira fetch returned empty (API permission issue), use values from Kafka event params
            if (ticket.getOrDefault("summary", "").isBlank() && summary != null) {
                ticket = new HashMap<>(ticket);
                ticket.put("summary",     summary != null     ? summary     : "");
                ticket.put("description", description != null ? description : "");
                ticket.put("priority",    priority != null    ? priority    : "");
                log.info("Jira fetch returned empty for {}, using Kafka event data instead", issueKey);
            }

            // Query RAG service for similar tickets as context
            String query = ticket.getOrDefault("summary", "") + " " + ticket.getOrDefault("description", "");
            List<Map<String, Object>> ragContext = ragContextService.fetchSimilarTickets(query.trim(), 3);

            // Generate code using Groq + RAG context
            String generatedCode = codeGenerationService.generateCode(ticket, ragContext);

            // Push to Bitbucket and create PR
            bitbucketService.createPR(issueKey, ticket.get("summary"), generatedCode);

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "issueKey", issueKey,
                    "message", "Code generated and PR created successfully"
            ));

        } catch (Exception e) {
            log.error("Codegen pipeline failed for issue={}: {}", issueKey, e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "issueKey", issueKey,
                    "message", e.getMessage()
            ));
        }
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "ok", "service", "codegen-service"));
    }
}
