-- Weekly schedule: one working interval per staff member per day, in the tenant's local time.
-- A day without a row is a day off.
CREATE TABLE working_hours (
    id           BIGSERIAL PRIMARY KEY,
    tenant_id    BIGINT  NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    staff_id     BIGINT  NOT NULL REFERENCES staff (id) ON DELETE CASCADE,
    day_of_week  INTEGER NOT NULL CHECK (day_of_week BETWEEN 1 AND 7), -- ISO-8601: 1 = Monday
    start_time   TIME    NOT NULL,
    end_time     TIME    NOT NULL,
    CONSTRAINT working_hours_time_valid CHECK (end_time > start_time),
    CONSTRAINT working_hours_staff_day_uk UNIQUE (staff_id, day_of_week)
);

-- One-off unavailability: vacation, sick leave, a lunch break on a given day
CREATE TABLE time_off (
    id          BIGSERIAL PRIMARY KEY,
    tenant_id   BIGINT       NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    staff_id    BIGINT       NOT NULL REFERENCES staff (id) ON DELETE CASCADE,
    starts_at   TIMESTAMPTZ  NOT NULL,
    ends_at     TIMESTAMPTZ  NOT NULL,
    reason      VARCHAR(200),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT time_off_time_valid CHECK (ends_at > starts_at)
);
CREATE INDEX time_off_staff_starts_idx ON time_off (staff_id, starts_at);
