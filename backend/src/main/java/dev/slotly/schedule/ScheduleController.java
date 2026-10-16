package dev.slotly.schedule;

import dev.slotly.auth.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Changes are restricted to OWNER in SecurityConfig; any member of the business can read. */
@RestController
@RequestMapping("/api/v1/staff/{staffId}")
public class ScheduleController {

    private final ScheduleService service;

    public ScheduleController(ScheduleService service) {
        this.service = service;
    }

    @PutMapping("/working-hours")
    public List<ScheduleDtos.WorkingDay> replaceWeek(CurrentUser user, @PathVariable Long staffId,
                                                     @RequestBody List<ScheduleDtos.@Valid WorkingDay> days) {
        return service.replaceWeek(user.tenantId(), staffId, days);
    }

    @GetMapping("/working-hours")
    public List<ScheduleDtos.WorkingDay> week(CurrentUser user, @PathVariable Long staffId) {
        return service.week(user.tenantId(), staffId);
    }

    @PostMapping("/time-off")
    @ResponseStatus(HttpStatus.CREATED)
    public ScheduleDtos.TimeOffResponse addTimeOff(CurrentUser user, @PathVariable Long staffId,
                                                   @Valid @RequestBody ScheduleDtos.CreateTimeOffRequest request) {
        return ScheduleDtos.TimeOffResponse.from(service.addTimeOff(user.tenantId(), staffId, request));
    }

    @GetMapping("/time-off")
    public List<ScheduleDtos.TimeOffResponse> listTimeOff(CurrentUser user, @PathVariable Long staffId) {
        return service.listTimeOff(user.tenantId(), staffId).stream()
                .map(ScheduleDtos.TimeOffResponse::from)
                .toList();
    }

    @DeleteMapping("/time-off/{timeOffId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTimeOff(CurrentUser user, @PathVariable Long staffId, @PathVariable Long timeOffId) {
        service.deleteTimeOff(user.tenantId(), staffId, timeOffId);
    }
}
