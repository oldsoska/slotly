package dev.slotly.availability;

import dev.slotly.catalog.ServiceOffering;
import dev.slotly.catalog.ServiceOfferingRepository;
import dev.slotly.catalog.Staff;
import dev.slotly.catalog.StaffRepository;
import dev.slotly.common.NotFoundException;
import dev.slotly.schedule.ScheduleService;
import dev.slotly.tenant.TenantService;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Service
public class AvailabilityService {

    /** Candidate start times are offered on a 15-minute grid from the start of the working day. */
    static final int SLOT_STEP_MINUTES = 15;

    /*
     * generate_series builds every candidate start inside the working window; NOT EXISTS drops those that
     * overlap a live booking or time off. The booking check uses the same tstzrange && predicate as the
     * booking_no_overlap constraint, so it is served by that constraint's GiST index.
     */
    private static final String FREE_SLOTS_SQL = """
            SELECT s.slot_start
            FROM generate_series(
                     CAST(:windowStart AS timestamptz),
                     CAST(:windowEnd AS timestamptz) - :duration * INTERVAL '1 minute',
                     :step * INTERVAL '1 minute') AS s(slot_start)
            WHERE s.slot_start >= CAST(:notBefore AS timestamptz)
              AND NOT EXISTS (
                    SELECT 1 FROM booking b
                    WHERE b.staff_id = :staffId
                      AND b.status <> 'CANCELLED'
                      AND tstzrange(b.starts_at, b.ends_at, '[)')
                          && tstzrange(s.slot_start, s.slot_start + :duration * INTERVAL '1 minute', '[)'))
              AND NOT EXISTS (
                    SELECT 1 FROM time_off t
                    WHERE t.staff_id = :staffId
                      AND t.starts_at < s.slot_start + :duration * INTERVAL '1 minute'
                      AND t.ends_at > s.slot_start)
            ORDER BY s.slot_start
            """;

    private final JdbcClient jdbc;
    private final StaffRepository staff;
    private final ServiceOfferingRepository services;
    private final ScheduleService schedule;
    private final TenantService tenants;
    private final Clock clock;

    public AvailabilityService(JdbcClient jdbc, StaffRepository staff, ServiceOfferingRepository services,
                               ScheduleService schedule, TenantService tenants, Clock clock) {
        this.jdbc = jdbc;
        this.staff = staff;
        this.services = services;
        this.schedule = schedule;
        this.tenants = tenants;
        this.clock = clock;
    }

    /**
     * Free start times for {@code serviceId} on {@code date} (the business's local date), for one staff member
     * or, when {@code staffId} is null, for every active one.
     */
    @Transactional(readOnly = true)
    public List<AvailabilityDtos.Slot> freeSlots(Long tenantId, Long serviceId, LocalDate date, Long staffId) {
        ServiceOffering offering = services.findByIdAndTenantId(serviceId, tenantId)
                .filter(ServiceOffering::isActive)
                .orElseThrow(() -> new NotFoundException("Service", serviceId));
        ZoneId zone = tenants.zone(tenantId);
        Instant now = clock.instant();

        List<AvailabilityDtos.Slot> slots = new ArrayList<>();
        for (Staff master : candidates(tenantId, staffId)) {
            schedule.workingWindow(master.getId(), date, zone).ifPresent(window -> {
                for (Instant start : query(master.getId(), window, offering.getDurationMinutes(), now)) {
                    slots.add(new AvailabilityDtos.Slot(master.getId(), master.getFullName(),
                            start, start.plus(offering.getDuration())));
                }
            });
        }
        return slots;
    }

    private List<Staff> candidates(Long tenantId, Long staffId) {
        if (staffId == null) {
            return staff.findByTenantIdOrderByFullName(tenantId).stream().filter(Staff::isActive).toList();
        }
        return List.of(staff.findByIdAndTenantId(staffId, tenantId)
                .filter(Staff::isActive)
                .orElseThrow(() -> new NotFoundException("Staff", staffId)));
    }

    private List<Instant> query(Long staffId, ScheduleService.Window window, int durationMinutes, Instant notBefore) {
        return jdbc.sql(FREE_SLOTS_SQL)
                .param("windowStart", utc(window.start()))
                .param("windowEnd", utc(window.end()))
                .param("notBefore", utc(notBefore))
                .param("duration", durationMinutes)
                .param("step", SLOT_STEP_MINUTES)
                .param("staffId", staffId)
                .query((rs, rowNum) -> rs.getObject("slot_start", OffsetDateTime.class).toInstant())
                .list();
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
