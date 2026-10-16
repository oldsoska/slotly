package dev.slotly.availability;

import java.time.Instant;

public final class AvailabilityDtos {

    private AvailabilityDtos() {
    }

    public record Slot(Long staffId, String staffName, Instant startsAt, Instant endsAt) {
    }
}
