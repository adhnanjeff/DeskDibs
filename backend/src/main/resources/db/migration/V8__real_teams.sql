-- ═════════════════════════════════════════════════════════════════════════════
-- The office's actual teams, replacing the three placeholders the app was built
-- against (Data, Design, Platform). Teams are reference data, so they belong in
-- a migration: there is no team-management endpoint, and inserting them by hand
-- means the next environment starts empty and every seed drifts from this one.
-- ═════════════════════════════════════════════════════════════════════════════

-- Insert first, delete second, so a rerun on a database that already has the new
-- names is a no-op rather than a window with no teams in it at all.
INSERT INTO team (name) VALUES
    ('AI'),
    ('UI/UX'),
    ('Corr'),
    ('PF4D'),
    ('AC4D'),
    ('Paxis'),
    ('IT'),
    ('Cloud team'),
    ('Marketing'),
    ('Finance'),
    ('HR'),
    ('Radius'),
    ('Metrics')
ON CONFLICT (name) DO NOTHING;

-- Removing a team CASCADEs to seat_reservation (fk_seat_reservation_team), so any
-- block still held for a placeholder team goes with it. That is intended here —
-- these three were scaffolding and their holds were test data — but it is the
-- reason this statement names the three explicitly instead of deleting whatever
-- is not in the list above. A future real team must never be caught by a rerun.
DELETE FROM team WHERE name IN ('Data', 'Design', 'Platform');
