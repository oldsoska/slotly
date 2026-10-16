package dev.slotly.booking;

import dev.slotly.catalog.ServiceOffering;
import dev.slotly.catalog.ServiceOfferingRepository;
import dev.slotly.catalog.Staff;
import dev.slotly.catalog.StaffRepository;
import dev.slotly.common.ConflictException;
import dev.slotly.common.Constraints;
import dev.slotly.common.NotFoundException;
import dev.slotly.schedule.ScheduleService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class BookingService {

    private final StaffRepository staff;
    private final ServiceOfferingRepository services;
    private final BookingRepository bookings;
    private final ScheduleService schedule;
    private final Clock clock;

    public BookingService(StaffRepository staff, ServiceOfferingRepository services,
                          BookingRepository bookings, ScheduleService schedule, Clock clock) {
        this.staff = staff;
        this.services = services;
        this.bookings = bookings;
        this.schedule = schedule;
        this.clock = clock;
    }

    /**
     * Double-booking is prevented by the {@code booking_no_overlap} exclusion constraint,
     * not by a "check then insert" in Java, so concurrent requests for the same slot are safe.
     */
    @Transactional
    public Booking create(Long tenantId, BookingDtos.CreateRequest request) {
        Staff master = staff.findByIdAndTenantId(request.staffId(), tenantId)
                .filter(Staff::isActive)
                .orElseThrow(() -> new NotFoundException("Staff", request.staffId()));
        ServiceOffering offering = services.findByIdAndTenantId(request.serviceId(), tenantId)
                .filter(ServiceOffering::isActive)
                .orElseThrow(() -> new NotFoundException("Service", request.serviceId()));

        Instant start = request.startsAt();
        Instant end = start.plus(offering.getDuration());
        schedule.requireBookable(tenantId, master.getId(), start, end);

        var booking = new Booking(tenantId, master.getId(), offering.getId(),
                request.clientName(), request.clientPhone(), start, end);
        try {
            return bookings.saveAndFlush(booking);
        } catch (DataIntegrityViolationException e) {
            if (Constraints.isViolated(e, "booking_no_overlap")) {
                throw new ConflictException("This time slot is already booked");
            }
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public List<Booking> list(Long tenantId, Instant from, Instant to) {
        return bookings.findInRange(tenantId, from, to);
    }

    @Transactional
    public Booking cancel(Long tenantId, Long bookingId) {
        Booking booking = bookings.findByIdAndTenantId(bookingId, tenantId)
                .orElseThrow(() -> new NotFoundException("Booking", bookingId));
        try {
            booking.cancel();
        } catch (IllegalStateException e) {
            throw new ConflictException(e.getMessage());
        }
        return booking;
    }

    @Transactional(readOnly = true)
    public Booking byCancelToken(UUID token) {
        return bookings.findByCancelToken(token).orElseThrow(() -> new NotFoundException("Booking", token));
    }

    /** Cancellation by the client; unlike staff, a client cannot cancel once the visit has started. */
    @Transactional
    public Booking cancelByToken(UUID token) {
        Booking booking = byCancelToken(token);
        if (booking.getStatus() == BookingStatus.CONFIRMED && !booking.getStartsAt().isAfter(clock.instant())) {
            throw new ConflictException("A booking cannot be cancelled after it has started");
        }
        return cancel(booking.getTenantId(), booking.getId());
    }
}
