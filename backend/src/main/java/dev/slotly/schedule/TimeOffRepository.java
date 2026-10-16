package dev.slotly.schedule;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TimeOffRepository extends JpaRepository<TimeOff, Long> {

    List<TimeOff> findByStaffIdOrderByStartsAt(Long staffId);

    Optional<TimeOff> findByIdAndStaffIdAndTenantId(Long id, Long staffId, Long tenantId);

    /** Half-open intervals [start, end) overlap when each one starts before the other ends. */
    @Query("""
            select count(t) > 0 from TimeOff t
            where t.staffId = :staffId
              and t.startsAt < :end
              and t.endsAt > :start
            """)
    boolean overlaps(Long staffId, Instant start, Instant end);
}
