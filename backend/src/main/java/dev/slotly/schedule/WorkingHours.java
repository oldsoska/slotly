package dev.slotly.schedule;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.DayOfWeek;
import java.time.LocalTime;

@Entity
@Table(name = "working_hours")
public class WorkingHours {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "staff_id", nullable = false)
    private Long staffId;

    /** ISO-8601 day number, 1 = Monday, matching {@link DayOfWeek#getValue()}. */
    @Column(name = "day_of_week", nullable = false)
    private int dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    protected WorkingHours() {
    }

    public WorkingHours(Long tenantId, Long staffId, DayOfWeek day, LocalTime startTime, LocalTime endTime) {
        this.tenantId = tenantId;
        this.staffId = staffId;
        this.dayOfWeek = day.getValue();
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public DayOfWeek getDay() {
        return DayOfWeek.of(dayOfWeek);
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }
}
