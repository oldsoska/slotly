package dev.slotly.tenant;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenants")
public class TenantController {

    private final TenantService service;

    public TenantController(TenantService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TenantDtos.Response create(@Valid @RequestBody TenantDtos.CreateRequest request) {
        return TenantDtos.Response.from(service.create(request));
    }

    @GetMapping("/{id}")
    public TenantDtos.Response get(@PathVariable Long id) {
        return TenantDtos.Response.from(service.get(id));
    }
}
