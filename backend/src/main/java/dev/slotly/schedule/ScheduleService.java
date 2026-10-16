package dev.slotly.schedule;

import dev.slotly.catalog.Staff;
import dev.slotly.catalog.StaffRepository;
import dev.slotly.common.BadRequestException;
import dev.slotly.common.ConflictException;
import dev.slotly.common.NotFoundException;
import dev.slotly.tenant.TenantService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class ScheduleService {

    private final StaffRepository staff;
    private final WorkingHoursRepository workingHours;
    private final TimeOffRepository timeOff;
    private final TenantService tenants;

    public ScheduleService(StaffRepository staff, WorkingHoursRepository workingHours,
                           TimeOffRepository timeOff, TenantService tenants) {
        this.staff = staff;
        this.workingHours = workingHours;
        this.timeOff = timeOff;
        this.tenants = tenants;
    }

    /** A staff member's working interval on one calendar day, as absolute instants. */
    public record Window(Instant start, Instant end) {
    }

    /** Replaces the whole week: days missing from {@code days} become days off. */
    @Transactional
    public List<ScheduleDtos.WorkingDay> replaceWeek(Long tenantId, Long staffId, List<ScheduleDtos.WorkingDay> days) {
        requireStaff(tenantId, staffId);
        Set<DayOfWeek> seen = EnumSet.noneOf(DayOfWeek.class);
        for (ScheduleDtos.WorkingDay day : days) {
            if (!seen.add(day.dayOfWeek())) {
                throw new BadRequestException(day.dayOfWeek() + " is listed more than once");
            }
            if (!day.end().isAfter(day.start())) {
                throw new BadRequestException(day.dayOfWeek() + ": end must be after start");
            }
        }
        workingHours.deleteAllForStaff(staffId);
        workingHours.saveAll(days.stream()
                .map(d -> new WorkingHours(tenantId, staffId, d.dayOfWeek(), d.start(), d.end()))
                .toList());
        return week(tenantId, staffId);
    }

    @Transactional(readOnly = true)
    public List<ScheduleDtos.WorkingDay> week(Long tenantId, Long staffId) {
        requireStaff(tenantId, staffId);
        return workingHours.findByStaffIdOrderByDayOfWeek(staffId).stream()
                .map(ScheduleDtos.WorkingDay::from)
                .toList();
    }

    @Transactional
    public TimeOff addTimeOff(Long tenantId, Long staffId, ScheduleDtos.CreateTimeOffRequest request) {
        requireStaff(tenantId, staffId);
        if (!request.endsAt().isAfter(request.startsAt())) {
            throw new BadRequestException("endsAt must be after startsAt");
        }
        return timeOff.save(new TimeOff(tenantId, staffId, request.startsAt(), request.endsAt(), request.reason()));
    }

    @Transactional(readOnly = true)
    public List<TimeOff> listTimeOff(Long tenantId, Long staffId) {
        requireStaff(tenantId, staffId);
        return timeOff.findByStaffIdOrderByStartsAt(staffId);
    }

    @Transactional
    public void deleteTimeOff(Long tenantId, Long staffId, Long timeOffId) {
        TimeOff entry = timeOff.findByIdAndStaffIdAndTenantId(timeOffId, staffId, tenantId)
                .orElseThrow(() -> new NotFoundException("Time off", timeOffId));
        timeOff.delete(entry);
    }

    /** Working hours on {@code date} in the business's timezone, or empty on a day off. */
    @Transactional(readOnly = true)
    public Optional<Window> workingWindow(Long staffId, LocalDate date, ZoneId zone) {
        return workingHours.findByStaffIdAndDayOfWeek(staffId, date.getDayOfWeek().getValue())
                .map(h -> new Window(
                        ZonedDateTime.of(date, h.getStartTime(), zone).toInstant(),
                        ZonedDateTime.of(date, h.getEndTime(), zone).toInstant()));
    }

    /** Throws unless [start, end) lies inside working hours and does not touch any time off. */
    @Transactional(readOnly = true)
    public void requireBookable(Long tenantId, Long staffId, Instant start, Instant end) {
        ZoneId zone = tenants.zone(tenantId);
        LocalDate date = start.atZone(zone).toLocalDate();
        Window window = workingWindow(staffId, date, zone)
                .orElseThrow(() -> new ConflictException("Staff " + staffId + " does not work on " + date));
        if (start.isBefore(window.start()) || end.isAfter(window.end())) {
            throw new ConflictException("Booking is outside working hours");
        }
        if (timeOff.overlaps(staffId, start, end)) {
            throw new ConflictException("Staff " + staffId + " is unavailable at this time");
        }
    }

    private Staff requireStaff(Long tenantId, Long staffId) {
        return staff.findByIdAndTenantId(staffId, tenantId)
                .orElseThrow(() -> new NotFoundException("Staff", staffId));
    }
}
