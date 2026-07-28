-- Two more ways a booking can stop holding its seat. Both are administrative — something
-- happened *to* the booking rather than something its owner did — which is why neither reuses
-- CANCELLED. A person looking at "cancelled" in their history should be able to read it as
-- "I gave that up", and these two are precisely the cases where they did not.
--
--   RELEASED_USER_DEACTIVATED  the person left the company; their future desks go back to the pool
--   RELEASED_SEAT_REMOVED      the desk left the floor plan, so the booking cannot stand
--
-- Neither value is ACTIVE, so both drop straight out of uq_seat_active_per_date and
-- uq_user_active_per_date exactly as CANCELLED and RELEASED_NO_SHOW already do. The invariant
-- is untouched by this migration: it is stated as `WHERE status = 'ACTIVE'`, so adding
-- non-ACTIVE values cannot widen what the partial indexes admit.

-- RELEASED_USER_DEACTIVATED is 25 characters; the original column was sized varchar(24) for the
-- three statuses that existed at the time. Widen before the constraint can permit the value.
ALTER TABLE booking ALTER COLUMN status TYPE varchar(32);

ALTER TABLE booking DROP CONSTRAINT ck_booking_status;

ALTER TABLE booking ADD CONSTRAINT ck_booking_status
    CHECK (status IN ('ACTIVE',
                      'CANCELLED',
                      'RELEASED_NO_SHOW',
                      'RELEASED_USER_DEACTIVATED',
                      'RELEASED_SEAT_REMOVED'));
