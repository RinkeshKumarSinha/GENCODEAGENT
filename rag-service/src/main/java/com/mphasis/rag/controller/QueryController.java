package com.mphasis.rag.controller;

import com.mphasis.rag.service.RagQueryService;
import com.mphasis.rag.service.VectorStoreService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class QueryController {

    private final RagQueryService ragQueryService;
    private final VectorStoreService vectorStoreService;

    public QueryController(RagQueryService ragQueryService, VectorStoreService vectorStoreService) {
        this.ragQueryService = ragQueryService;
        this.vectorStoreService = vectorStoreService;
    }

    @PostMapping("/query")
    public ResponseEntity<Map<String, Object>> query(@RequestBody Map<String, Object> request) {
        String userQuery = (String) request.get("query");
        if (userQuery == null || userQuery.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "query is required"));
        }
        int topK = request.containsKey("topK") ? (int) request.get("topK") : 5;
        Map<String, Object> result = ragQueryService.query(userQuery, topK);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/tickets")
    public ResponseEntity<List<Map<String, Object>>> getTickets() {
        return ResponseEntity.ok(vectorStoreService.getAllTickets());
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "ok", "service", "rag-service"));
    }
}
