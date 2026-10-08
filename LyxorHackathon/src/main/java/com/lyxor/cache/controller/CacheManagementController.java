package com.lyxor.cache.controller;

import com.lyxor.cache.service.DistributedCacheManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/v1/cache")
public class CacheManagementController {

    private final DistributedCacheManager cacheManager;

    public CacheManagementController() {
        this.cacheManager = new DistributedCacheManager();
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(Map.of(
                "hits", cacheManager.getStatistics().getHits(),
                "misses", cacheManager.getStatistics().getMisses(),
                "hitRate", cacheManager.getStatistics().getHitRate()
        ));
    }

    @DeleteMapping("/evict-pattern")
    public ResponseEntity<String> evictByPattern(@RequestParam String pattern) {
        if (pattern == null || pattern.isBlank()) {
            return ResponseEntity.badRequest().body("Pattern must not be empty");
        }

        Pattern regex = Pattern.compile(pattern);
        return ResponseEntity.ok("Evicted entries matching: " + regex.pattern());
    }
}
