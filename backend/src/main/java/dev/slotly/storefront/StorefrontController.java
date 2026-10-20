package dev.slotly.storefront;

import dev.slotly.availability.AvailabilityDtos;
import dev.slotly.booking.BookingDtos;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.servlet.http.HttpServletRequest;
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

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Open to anyone (see SecurityConfig): this is what a business shares with its clients. */
@RestController
@RequestMapping("/api/v1/public")
@SecurityRequirements
public class StorefrontController {

    private final StorefrontService service;

    public StorefrontController(StorefrontService service) {
        this.service = service;
    }

    @GetMapping("/businesses/{slug}")
    public StorefrontDtos.Business business(@PathVariable String slug, HttpServletRequest http) {
        return service.business(slug, http.getRemoteAddr());
    }

    @GetMapping("/businesses/{slug}/availability")
    public List<AvailabilityDtos.Slot> freeSlots(@PathVariable String slug,
                                                 @RequestParam Long serviceId,
                                                 @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                                 @RequestParam(required = false) Long staffId,
                                                 HttpServletRequest http) {
        return service.freeSlots(slug, serviceId, date, staffId, http.getRemoteAddr());
    }

    @PostMapping("/businesses/{slug}/bookings")
    @ResponseStatus(HttpStatus.CREATED)
    public StorefrontDtos.Confirmation book(@PathVariable String slug,
                                            @Valid @RequestBody BookingDtos.CreateRequest request,
                                            HttpServletRequest http) {
        return service.book(slug, request, http.getRemoteAddr());
    }

    @GetMapping("/bookings/{token}")
    public StorefrontDtos.Confirmation booking(@PathVariable UUID token, HttpServletRequest http) {
        return service.booking(token, http.getRemoteAddr());
    }

    @PostMapping("/bookings/{token}/cancel")
    public StorefrontDtos.Confirmation cancel(@PathVariable UUID token, HttpServletRequest http) {
        return service.cancel(token, http.getRemoteAddr());
    }
}
