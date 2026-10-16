package dev.slotly.auth;

import dev.slotly.catalog.StaffRepository;
import dev.slotly.common.ConflictException;
import dev.slotly.common.Constraints;
import dev.slotly.common.NotFoundException;
import dev.slotly.common.UnauthorizedException;
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
    /** Compared against when the email is unknown, so both failure paths take the same time. */
    private final String dummyHash;

    public AuthService(AppUserRepository users, StaffRepository staff, TenantService tenants,
                       PasswordEncoder passwordEncoder, TokenService tokens) {
        this.users = users;
        this.staff = staff;
        this.tenants = tenants;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.dummyHash = passwordEncoder.encode("timing-equaliser");
    }

    @Transactional
    public AuthDtos.TokenResponse register(AuthDtos.RegisterRequest request) {
        String email = normalize(request.email());
        requireEmailFree(email);
        Tenant tenant = tenants.create(request.businessName(), request.slug(), request.timezone());
        AppUser owner = save(AppUser.owner(tenant.getId(), email, passwordEncoder.encode(request.password())));
        return tokens.issue(owner);
    }

    @Transactional(readOnly = true)
    public AuthDtos.TokenResponse login(AuthDtos.LoginRequest request) {
        AppUser user = users.findByEmail(normalize(request.email())).orElse(null);
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
