package dev.slotly.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StaffRepository extends JpaRepository<Staff, Long> {

    List<Staff> findByTenantIdOrderByFullName(Long tenantId);

    Optional<Staff> findByIdAndTenantId(Long id, Long tenantId);
}
