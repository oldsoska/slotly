package dev.slotly.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.slotly.common.TooManyRequestsException;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Token-bucket limiter keyed by an arbitrary string ("login-ip:203.0.113.7", "public-booking:42").
 * Buckets are kept in a bounded Caffeine cache, so a flood of distinct keys cannot exhaust memory;
 * an idle bucket is dropped only after it would have refilled anyway.
 */
@Component
public class RateLimiter {

    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterAccess(Duration.ofHours(2))
            .build();

    /** Takes one token from the bucket for {@code key}, or throws with how long to wait. */
    public void consume(String key, RateLimitProperties.Limit limit) {
        Bucket bucket = buckets.get(key + '|' + limit.requests() + '/' + limit.per(), k -> newBucket(limit));
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            long seconds = Duration.ofNanos(probe.getNanosToWaitForRefill()).toSeconds() + 1;
            throw new TooManyRequestsException(seconds);
        }
    }

    private static Bucket newBucket(RateLimitProperties.Limit limit) {
        return Bucket.builder()
                .addLimit(l -> l.capacity(limit.requests()).refillGreedy(limit.requests(), limit.per()))
                .build();
    }
}
