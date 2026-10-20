package dev.slotly.auth;

import dev.slotly.catalog.StaffRepository;
import dev.slotly.common.ConflictException;
import dev.slotly.common.Constraints;
import dev.slotly.common.NotFoundException;
import dev.slotly.common.UnauthorizedException;
import dev.slotly.ratelimit.RateLimitProperties;
import dev.slotly.ratelimit.RateLimiter;
import dev.slotly.tenant.Tenant;
import dev.slotly.tenant.TenantService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AuthService {

    private static final String BAD_CREDENTIALS = "Invalid email or password";

    private final AppUserRepository users;
    private final StaffRepository staff;
    private final TenantService tenants;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;
    private final RateLimiter rateLimiter;
    private final RateLimitProperties limits;
    /** Compared against when the email is unknown, so both failure paths take the same time. */
    private final String dummyHash;

    public AuthService(AppUserRepository users, StaffRepository staff, TenantService tenants,
                       PasswordEncoder passwordEncoder, TokenService tokens,
                       RateLimiter rateLimiter, RateLimitProperties limits) {
        this.users = users;
        this.staff = staff;
        this.tenants = tenants;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.rateLimiter = rateLimiter;
        this.limits = limits;
        this.dummyHash = passwordEncoder.encode("timing-equaliser");
    }

    @Transactional
    public AuthDtos.TokenResponse register(AuthDtos.RegisterRequest request, String clientIp) {
        rateLimiter.consume("register-ip:" + clientIp, limits.registerPerIp());
        String email = normalize(request.email());
        requireEmailFree(email);
        Tenant tenant = tenants.create(request.businessName(), request.slug(), request.timezone());
        AppUser owner = save(AppUser.owner(tenant.getId(), email, passwordEncoder.encode(request.password())));
        return tokens.issue(owner);
    }

    /**
     * Limited per client IP (one machine guessing many accounts) and per email (many machines guessing
     * one account). Checked before BCrypt, so rejected attempts cost no hashing.
     */
    @Transactional(readOnly = true)
    public AuthDtos.TokenResponse login(AuthDtos.LoginRequest request, String clientIp) {
        String email = normalize(request.email());
        rateLimiter.consume("login-ip:" + clientIp, limits.loginPerIp());
        rateLimiter.consume("login-email:" + email, limits.loginPerEmail());
        AppUser user = users.findByEmail(email).orElse(null);
        String hash = user != null ? user.getPasswordHash() : dummyHash;
        if (!passwordEncoder.matches(request.password(), hash) || user == null) {
            throw new UnauthorizedException(BAD_CREDENTIALS);
        }
        return tokens.issue(user);
    }

    @Transactional
    public AppUser createStaffAccount(CurrentUser owner, Long staffId, AuthDtos.CreateAccountRequest request) {
        staff.findByIdAndTenantId(staffId, owner.tenantId())
                .orElseThrow(() -> new NotFoundException("Staff", staffId));
        if (users.existsByStaffId(staffId)) {
            throw new ConflictException("Staff " + staffId + " already has an account");
        }
        String email = normalize(request.email());
        requireEmailFree(email);
        return save(AppUser.staff(owner.tenantId(), staffId, email, passwordEncoder.encode(request.password())));
    }

    @Transactional(readOnly = true)
    public AppUser get(CurrentUser user) {
        return users.findById(user.userId()).orElseThrow(() -> new UnauthorizedException("Account no longer exists"));
    }

    private void requireEmailFree(String email) {
        if (users.existsByEmail(email)) {
            throw new ConflictException("Email is already registered");
        }
    }

    private AppUser save(AppUser user) {
        try {
            return users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            if (Constraints.isViolated(e, "app_user_email_uk")) {
                throw new ConflictException("Email is already registered");
            }
            if (Constraints.isViolated(e, "app_user_staff_uk")) {
                throw new ConflictException("Staff already has an account");
            }
            throw e;
        }
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
