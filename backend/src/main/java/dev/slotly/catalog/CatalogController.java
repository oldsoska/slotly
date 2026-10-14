package dev.slotly.catalog;

import dev.slotly.tenant.TenantService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}")
public class CatalogController {

    private final TenantService tenants;
    private final StaffRepository staff;
    private final ServiceOfferingRepository services;

    public CatalogController(TenantService tenants, StaffRepository staff, ServiceOfferingRepository services) {
        this.tenants = tenants;
        this.staff = staff;
        this.services = services;
    }

    @PostMapping("/staff")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public CatalogDtos.StaffResponse createStaff(@PathVariable Long tenantId,
                                                 @Valid @RequestBody CatalogDtos.CreateStaffRequest request) {
        tenants.requireExists(tenantId);
        return CatalogDtos.StaffResponse.from(staff.save(new Staff(tenantId, request.fullName())));
    }

    @GetMapping("/staff")
    public List<CatalogDtos.StaffResponse> listStaff(@PathVariable Long tenantId) {
        tenants.requireExists(tenantId);
        return staff.findByTenantIdOrderByFullName(tenantId).stream()
                .map(CatalogDtos.StaffResponse::from)
                .toList();
    }

    @PostMapping("/services")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public CatalogDtos.ServiceResponse createService(@PathVariable Long tenantId,
                                                     @Valid @RequestBody CatalogDtos.CreateServiceRequest request) {
        tenants.requireExists(tenantId);
        var offering = new ServiceOffering(tenantId, request.name(), request.durationMinutes(), request.price());
        return CatalogDtos.ServiceResponse.from(services.save(offering));
    }

    @GetMapping("/services")
    public List<CatalogDtos.ServiceResponse> listServices(@PathVariable Long tenantId) {
        tenants.requireExists(tenantId);
        return services.findByTenantIdOrderByName(tenantId).stream()
                .map(CatalogDtos.ServiceResponse::from)
                .toList();
    }
}
