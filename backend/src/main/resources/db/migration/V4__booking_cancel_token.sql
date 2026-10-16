-- Clients book without an account, so a booking carries its own unguessable token:
-- whoever holds the confirmation link can view or cancel that one booking.
ALTER TABLE booking ADD COLUMN cancel_token UUID;
UPDATE booking SET cancel_token = gen_random_uuid() WHERE cancel_token IS NULL;
ALTER TABLE booking ALTER COLUMN cancel_token SET NOT NULL;
ALTER TABLE booking ADD CONSTRAINT booking_cancel_token_uk UNIQUE (cancel_token);
