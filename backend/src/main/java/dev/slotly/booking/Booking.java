package dev.slotly.booking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "booking")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "staff_id", nullable = false)
    private Long staffId;

    @Column(name = "service_id", nullable = false)
    private Long serviceId;

    @Column(name = "client_name", nullable = false)
    private String clientName;

    @Column(name = "client_phone", nullable = false)
    private String clientPhone;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status = BookingStatus.CONFIRMED;

    /** Secret handed to the client in the confirmation link; lets them view or cancel without an account. */
    @Column(name = "cancel_token", nullable = false, unique = true, updatable = false)
    private UUID cancelToken = UUID.randomUUID();

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected Booking() {
    }

    public Booking(Long tenantId, Long staffId, Long serviceId,
                   String clientName, String clientPhone, Instant startsAt, Instant endsAt) {
        this.tenantId = tenantId;
        this.staffId = staffId;
        this.serviceId = serviceId;
        this.clientName = clientName;
        this.clientPhone = clientPhone;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
    }

    public void cancel() {
        if (status == BookingStatus.COMPLETED) {
            throw new IllegalStateException("A completed booking cannot be cancelled");
        }
        status = BookingStatus.CANCELLED;
    }

    public Long getId() {
        return id;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public Long getStaffId() {
        return staffId;
    }

    public Long getServiceId() {
        return serviceId;
    }

    public String getClientName() {
        return clientName;
    }

    public String getClientPhone() {
        return clientPhone;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public UUID getCancelToken() {
        return cancelToken;
    }

    public BookingStatus getStatus() {
        return status;
    }
}
