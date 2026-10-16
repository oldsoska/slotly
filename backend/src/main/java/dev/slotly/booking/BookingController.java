package dev.slotly.booking;

import dev.slotly.auth.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/bookings")
public class BookingController {

    private final BookingService service;

    public BookingController(BookingService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BookingDtos.Response create(CurrentUser user, @Valid @RequestBody BookingDtos.CreateRequest request) {
        return BookingDtos.Response.from(service.create(user.tenantId(), request));
    }

    @GetMapping
    public List<BookingDtos.Response> list(CurrentUser user,
                                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return service.list(user.tenantId(), from, to).stream()
                .map(BookingDtos.Response::from)
                .toList();
    }

    @PostMapping("/{bookingId}/cancel")
    public BookingDtos.Response cancel(CurrentUser user, @PathVariable Long bookingId) {
        return BookingDtos.Response.from(service.cancel(user.tenantId(), bookingId));
    }
}
