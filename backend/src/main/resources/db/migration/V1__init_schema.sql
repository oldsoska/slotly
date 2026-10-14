-- btree_gist lets an EXCLUDE constraint combine "=" on staff_id with "&&" on a time range
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE tenant (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(200) NOT NULL,
    slug        VARCHAR(100) NOT NULL,
    timezone    VARCHAR(64)  NOT NULL DEFAULT 'Europe/Moscow',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT tenant_slug_uk UNIQUE (slug)
);

CREATE TABLE staff (
    id          BIGSERIAL PRIMARY KEY,
    tenant_id   BIGINT       NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    full_name   VARCHAR(200) NOT NULL,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX staff_tenant_idx ON staff (tenant_id);

CREATE TABLE service_offering (
    id                BIGSERIAL PRIMARY KEY,
    tenant_id         BIGINT        NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    name              VARCHAR(200)  NOT NULL,
    duration_minutes  INTEGER       NOT NULL CHECK (duration_minutes BETWEEN 5 AND 600),
    price             NUMERIC(12,2) NOT NULL CHECK (price >= 0),
    active            BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX service_offering_tenant_idx ON service_offering (tenant_id);

CREATE TABLE booking (
    id            BIGSERIAL PRIMARY KEY,
    tenant_id     BIGINT       NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    staff_id      BIGINT       NOT NULL REFERENCES staff (id),
    service_id    BIGINT       NOT NULL REFERENCES service_offering (id),
    client_name   VARCHAR(200) NOT NULL,
    client_phone  VARCHAR(32)  NOT NULL,
    starts_at     TIMESTAMPTZ  NOT NULL,
    ends_at       TIMESTAMPTZ  NOT NULL,
    status        VARCHAR(16)  NOT NULL DEFAULT 'CONFIRMED'
                  CHECK (status IN ('CONFIRMED', 'CANCELLED', 'COMPLETED')),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT booking_time_valid CHECK (ends_at > starts_at),
    -- The database itself guarantees a staff member is never double-booked,
    -- even under concurrent requests. Cancelled bookings free the slot.
    CONSTRAINT booking_no_overlap EXCLUDE USING gist (
        staff_id WITH =,
        tstzrange(starts_at, ends_at, '[)') WITH &&
    ) WHERE (status <> 'CANCELLED')
);
CREATE INDEX booking_tenant_starts_idx ON booking (tenant_id, starts_at);
