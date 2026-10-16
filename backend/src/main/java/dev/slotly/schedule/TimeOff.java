package dev.slotly.schedule;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "time_off")
public class TimeOff {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "staff_id", nullable = false)
    private Long staffId;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    private String reason;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected TimeOff() {
    }

    public TimeOff(Long tenantId, Long staffId, Instant startsAt, Instant endsAt, String reason) {
        this.tenantId = tenantId;
        this.staffId = staffId;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.reason = reason;
    }

    public Long getId() {
        return id;
    }

    public Long getStaffId() {
        return staffId;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public String getReason() {
        return reason;
    }
}
