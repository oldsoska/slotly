package dev.slotly.availability;

import dev.slotly.auth.CurrentUser;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/availability")
public class AvailabilityController {

    private final AvailabilityService service;

    public AvailabilityController(AvailabilityService service) {
        this.service = service;
    }

    @GetMapping
    public List<AvailabilityDtos.Slot> freeSlots(CurrentUser user,
                                                 @RequestParam Long serviceId,
                                                 @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                                 @RequestParam(required = false) Long staffId) {
        return service.freeSlots(user.tenantId(), serviceId, date, staffId);
    }
}
