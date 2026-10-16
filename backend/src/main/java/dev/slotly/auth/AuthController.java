package dev.slotly.auth;

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
@RequestMapping("/api/v1")
public class AuthController {

    private final AuthService service;

    public AuthController(AuthService service) {
        this.service = service;
    }

    @PostMapping("/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthDtos.TokenResponse register(@Valid @RequestBody AuthDtos.RegisterRequest request) {
        return service.register(request);
    }

    @PostMapping("/auth/login")
    public AuthDtos.TokenResponse login(@Valid @RequestBody AuthDtos.LoginRequest request) {
        return service.login(request);
    }

    @GetMapping("/me")
    public AuthDtos.AccountResponse me(CurrentUser user) {
        return AuthDtos.AccountResponse.from(service.get(user));
    }

    /** Owner gives a staff member a login of their own. */
    @PostMapping("/staff/{staffId}/account")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthDtos.AccountResponse createStaffAccount(CurrentUser owner, @PathVariable Long staffId,
                                                       @Valid @RequestBody AuthDtos.CreateAccountRequest request) {
        return AuthDtos.AccountResponse.from(service.createStaffAccount(owner, staffId, request));
    }
}
