package dev.slotly.tenant;

import dev.slotly.auth.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenant")
public class TenantController {

    private final TenantService service;

    public TenantController(TenantService service) {
        this.service = service;
    }

    /** The business the caller belongs to. New businesses are created via /api/v1/auth/register. */
    @GetMapping
    public TenantDtos.Response current(CurrentUser user) {
        return TenantDtos.Response.from(service.get(user.tenantId()));
    }
}
