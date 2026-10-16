package dev.slotly.storefront;

import dev.slotly.booking.BookingStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** What anonymous clients see. Deliberately narrower than the staff-facing DTOs. */
public final class StorefrontDtos {

    private StorefrontDtos() {
    }

    public record Business(String name, String slug, String timezone, List<Service> services, List<Master> staff) {
    }

    public record Service(Long id, String name, int durationMinutes, BigDecimal price) {
    }

    public record Master(Long id, String fullName) {
    }

    /** Returned once after booking and on the confirmation link; the token is the client's only key. */
    public record Confirmation(UUID cancelToken, String businessName, String businessSlug, String timezone,
                               String serviceName, String staffName, Instant startsAt, Instant endsAt,
                               BookingStatus status) {
    }
}
