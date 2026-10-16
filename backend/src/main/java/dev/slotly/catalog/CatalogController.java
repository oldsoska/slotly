package dev.slotly.catalog;

import dev.slotly.auth.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Writes are restricted to OWNER in SecurityConfig; any member of the business can read. */
@RestController
@RequestMapping("/api/v1")
public class CatalogController {

    private final StaffRepository staff;
    private final ServiceOfferingRepository services;

    public CatalogController(StaffRepository staff, ServiceOfferingRepository services) {
        this.staff = staff;
        this.services = services;
    }

    @PostMapping("/staff")
    @ResponseStatus(HttpStatus.CREATED)
    public CatalogDtos.StaffResponse createStaff(CurrentUser user,
                                                 @Valid @RequestBody CatalogDtos.CreateStaffRequest request) {
        return CatalogDtos.StaffResponse.from(staff.save(new Staff(user.tenantId(), request.fullName())));
    }

    @GetMapping("/staff")
    public List<CatalogDtos.StaffResponse> listStaff(CurrentUser user) {
        return staff.findByTenantIdOrderByFullName(user.tenantId()).stream()
                .map(CatalogDtos.StaffResponse::from)
                .toList();
    }

    @PostMapping("/services")
    @ResponseStatus(HttpStatus.CREATED)
    public CatalogDtos.ServiceResponse createService(CurrentUser user,
                                                     @Valid @RequestBody CatalogDtos.CreateServiceRequest request) {
        var offering = new ServiceOffering(user.tenantId(), request.name(), request.durationMinutes(), request.price());
        return CatalogDtos.ServiceResponse.from(services.save(offering));
    }

    @GetMapping("/services")
    public List<CatalogDtos.ServiceResponse> listServices(CurrentUser user) {
        return services.findByTenantIdOrderByName(user.tenantId()).stream()
                .map(CatalogDtos.ServiceResponse::from)
                .toList();
    }
}
