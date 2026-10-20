package dev.slotly.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("slotly.rate-limit")
public record RateLimitProperties(
        Limit registerPerIp,
        Limit loginPerIp,
        Limit loginPerEmail,
        Limit publicReadPerIp,
        Limit publicBookingPerIp,
        Limit publicBookingPerBusiness,
        Limit publicCancelPerIp,
        int upcomingBookingsPerPhone
) {

    /** At most {@code requests} per {@code per}, refilled gradually rather than all at once. */
    public record Limit(int requests, Duration per) {

        public Limit {
            if (requests <= 0 || per == null || per.isZero() || per.isNegative()) {
                throw new IllegalArgumentException("A rate limit needs positive requests and period");
            }
        }
    }
}
