-- Supports the per-phone limit on upcoming public bookings within a business
CREATE INDEX booking_tenant_phone_starts_idx ON booking (tenant_id, client_phone, starts_at);
