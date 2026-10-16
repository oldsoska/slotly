package dev.slotly.schedule;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;

public final class ScheduleDtos {

    private ScheduleDtos() {
    }

    /** Working interval for one day of the week, in the business's local time. */
    public record WorkingDay(@NotNull DayOfWeek dayOfWeek, @NotNull LocalTime start, @NotNull LocalTime end) {
        static WorkingDay from(WorkingHours h) {
            return new WorkingDay(h.getDay(), h.getStartTime(), h.getEndTime());
        }
    }

    public record CreateTimeOffRequest(@NotNull Instant startsAt, @NotNull Instant endsAt, @Size(max = 200) String reason) {
    }

    public record TimeOffResponse(Long id, Long staffId, Instant startsAt, Instant endsAt, String reason) {
        static TimeOffResponse from(TimeOff t) {
            return new TimeOffResponse(t.getId(), t.getStaffId(), t.getStartsAt(), t.getEndsAt(), t.getReason());
        }
    }
}
