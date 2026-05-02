package com.candleservice.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimiterService {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterService.class);

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public boolean isAllowed(String apiKey) {
        Bucket bucket = buckets.computeIfAbsent(apiKey, this::newBucket);
        boolean allowed = bucket.tryConsume(1);
        if (!allowed) {
            log.warn("Rate limit exceeded for key={}", maskKey(apiKey));
        }
        return allowed;
    }

    private Bucket newBucket(String apiKey) {
        Bandwidth limit = Bandwidth.classic(
                100, Refill.greedy(100, Duration.ofSeconds(1))
        );
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }

    private String maskKey(String key) {
        if (key == null || key.length() < 4) return "****";
        return "****" + key.substring(key.length() - 4);
    }
}