package dev.slotly.auth;

/**
 * The authenticated caller, taken from the verified JWT. Controllers declare it as a parameter;
 * the tenant always comes from here, never from the request, so one business cannot address another's data.
 */
public record CurrentUser(Long userId, Long tenantId, Role role) {
}
