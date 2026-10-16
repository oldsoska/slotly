package dev.slotly.schedule;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface WorkingHoursRepository extends JpaRepository<WorkingHours, Long> {

    List<WorkingHours> findByStaffIdOrderByDayOfWeek(Long staffId);

    Optional<WorkingHours> findByStaffIdAndDayOfWeek(Long staffId, int dayOfWeek);

    /**
     * Bulk delete that runs immediately. A derived deleteBy... only queues removals until flush, while IDENTITY
     * inserts execute on persist, so re-inserting the same (staff_id, day_of_week) would hit the unique key first.
     */
    @Modifying
    @Query("delete from WorkingHours w where w.staffId = :staffId")
    void deleteAllForStaff(Long staffId);
}
