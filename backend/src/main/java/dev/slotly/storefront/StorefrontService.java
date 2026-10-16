package dev.slotly.storefront;

import dev.slotly.availability.AvailabilityDtos;
import dev.slotly.availability.AvailabilityService;
import dev.slotly.booking.Booking;
import dev.slotly.booking.BookingDtos;
import dev.slotly.booking.BookingService;
import dev.slotly.catalog.ServiceOffering;
import dev.slotly.catalog.ServiceOfferingRepository;
import dev.slotly.catalog.Staff;
import dev.slotly.catalog.StaffRepository;
import dev.slotly.tenant.Tenant;
import dev.slotly.tenant.TenantService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Public, unauthenticated booking flow. The business is resolved from its slug and every call is
 * delegated to the same services staff use, so the schedule and overlap rules apply unchanged.
 */
@Service
public class StorefrontService {

    private final TenantService tenants;
    private final StaffRepository staff;
    private final ServiceOfferingRepository services;
    private final AvailabilityService availability;
    private final BookingService bookings;

    public StorefrontService(TenantService tenants, StaffRepository staff, ServiceOfferingRepository services,
                             AvailabilityService availability, BookingService bookings) {
        this.tenants = tenants;
        this.staff = staff;
        this.services = services;
        this.availability = availability;
        this.bookings = bookings;
    }

    @Transactional(readOnly = true)
    public StorefrontDtos.Business business(String slug) {
        Tenant tenant = tenants.getBySlug(slug);
        List<StorefrontDtos.Service> offered = services.findByTenantIdOrderByName(tenant.getId()).stream()
                .filter(ServiceOffering::isActive)
                .map(s -> new StorefrontDtos.Service(s.getId(), s.getName(), s.getDurationMinutes(), s.getPrice()))
                .toList();
        List<StorefrontDtos.Master> masters = staff.findByTenantIdOrderByFullName(tenant.getId()).stream()
                .filter(Staff::isActive)
                .map(s -> new StorefrontDtos.Master(s.getId(), s.getFullName()))
                .toList();
        return new StorefrontDtos.Business(tenant.getName(), tenant.getSlug(), tenant.getTimezone(), offered, masters);
    }

    @Transactional(readOnly = true)
    public List<AvailabilityDtos.Slot> freeSlots(String slug, Long serviceId, LocalDate date, Long staffId) {
        return availability.freeSlots(tenants.getBySlug(slug).getId(), serviceId, date, staffId);
    }

    @Transactional
    public StorefrontDtos.Confirmation book(String slug, BookingDtos.CreateRequest request) {
        Tenant tenant = tenants.getBySlug(slug);
        return confirmation(tenant, bookings.create(tenant.getId(), request));
    }

    @Transactional(readOnly = true)
    public StorefrontDtos.Confirmation booking(UUID token) {
        Booking booking = bookings.byCancelToken(token);
        return confirmation(tenants.get(booking.getTenantId()), booking);
    }

    @Transactional
    public StorefrontDtos.Confirmation cancel(UUID token) {
        Booking booking = bookings.cancelByToken(token);
        return confirmation(tenants.get(booking.getTenantId()), booking);
    }

    private StorefrontDtos.Confirmation confirmation(Tenant tenant, Booking b) {
        String serviceName = services.findById(b.getServiceId()).map(ServiceOffering::getName).orElse(null);
        String staffName = staff.findById(b.getStaffId()).map(Staff::getFullName).orElse(null);
        return new StorefrontDtos.Confirmation(b.getCancelToken(), tenant.getName(), tenant.getSlug(),
                tenant.getTimezone(), serviceName, staffName, b.getStartsAt(), b.getEndsAt(), b.getStatus());
    }
}
