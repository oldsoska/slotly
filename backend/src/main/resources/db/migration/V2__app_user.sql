CREATE TABLE app_user (
    id             BIGSERIAL PRIMARY KEY,
    tenant_id      BIGINT       NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    email          VARCHAR(254) NOT NULL,
    password_hash  VARCHAR(100) NOT NULL,
    role           VARCHAR(16)  NOT NULL CHECK (role IN ('OWNER', 'STAFF')),
    staff_id       BIGINT       REFERENCES staff (id) ON DELETE CASCADE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- Login is global, so an email identifies exactly one account across all tenants
    CONSTRAINT app_user_email_uk UNIQUE (email),
    CONSTRAINT app_user_email_lower CHECK (email = lower(email)),
    CONSTRAINT app_user_staff_uk UNIQUE (staff_id),
    CONSTRAINT app_user_staff_linked CHECK (role = 'OWNER' OR staff_id IS NOT NULL)
);
CREATE INDEX app_user_tenant_idx ON app_user (tenant_id);
