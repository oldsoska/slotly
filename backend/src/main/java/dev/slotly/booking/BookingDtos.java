package dev.slotly.booking;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class BookingDtos {

    private BookingDtos() {
    }

    public record CreateRequest(
            @NotNull Long staffId,
            @NotNull Long serviceId,
            @NotBlank @Size(max = 200) String clientName,
            @NotBlank @Pattern(regexp = "\\+?[0-9]{7,15}") String clientPhone,
            @NotNull @Future Instant startsAt
    ) {
    }

    public record Response(Long id, Long staffId, Long serviceId, String clientName, String clientPhone,
                           Instant startsAt, Instant endsAt, BookingStatus status) {
        static Response from(Booking b) {
            return new Response(b.getId(), b.getStaffId(), b.getServiceId(), b.getClientName(),
                    b.getClientPhone(), b.getStartsAt(), b.getEndsAt(), b.getStatus());
        }
    }
}
