package dev.slotly.booking;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    Optional<Booking> findByIdAndTenantId(Long id, Long tenantId);

    @Query("""
            select b from Booking b
            where b.tenantId = :tenantId
              and b.startsAt >= :from
              and b.startsAt < :to
            order by b.startsAt
            """)
    List<Booking> findInRange(Long tenantId, Instant from, Instant to);
}
