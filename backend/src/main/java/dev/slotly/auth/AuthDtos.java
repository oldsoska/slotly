package dev.slotly.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    /** Signs up a new business together with its owner account. */
    public record RegisterRequest(
            @NotBlank @Size(max = 200) String businessName,
            @NotBlank @Size(max = 100) @Pattern(regexp = "[a-z0-9-]+") String slug,
            @Size(max = 64) String timezone,
            @NotBlank @Email @Size(max = 254) String email,
            // BCrypt only uses the first 72 bytes of a password
            @NotBlank @Size(min = 8, max = 72) String password
    ) {
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {
    }

    public record CreateAccountRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 8, max = 72) String password
    ) {
    }

    public record TokenResponse(String accessToken, String tokenType, long expiresIn) {
    }

    public record AccountResponse(Long userId, Long tenantId, String email, Role role, Long staffId) {
        static AccountResponse from(AppUser u) {
            return new AccountResponse(u.getId(), u.getTenantId(), u.getEmail(), u.getRole(), u.getStaffId());
        }
    }
}
