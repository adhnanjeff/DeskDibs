-- Interim 110-seat layout. Layout is data, not code: when the real floor plan
-- arrives these rows change (or an admin drags tables), never a React component.
--
--   Left Wing  : L1-L7 capacity 6 (42) + L8 capacity 8   =  50 seats
--   Right Wing : R1-R10 capacity 6                       =  60 seats
--                                                  Total = 110 seats
--
-- Seat labels read `R3-A2` = Right wing, table 3, side A, position 2.
--
-- pos_x / pos_y are logical coordinates. Left Wing is laid out 2 columns x 4 rows,
-- Right Wing 2 columns x 5 rows, with the Right Wing pushed out to x=800 to leave a
-- central aisle between the two wings. Tables fill row-major (L1 L2 / L3 L4 / ...).
--
-- Every statement is idempotent (ON CONFLICT DO NOTHING) and the DO block at the
-- bottom fails the migration loudly if the counts ever come out wrong.

INSERT INTO floor (name, display_order)
VALUES ('Main Floor', 1)
ON CONFLICT (name) DO NOTHING;

INSERT INTO zone (floor_id, name, display_order)
SELECT f.id, z.name, z.display_order
FROM floor f
CROSS JOIN (VALUES ('Left Wing'::varchar, 1), ('Right Wing'::varchar, 2)) AS z (name, display_order)
WHERE f.name = 'Main Floor'
ON CONFLICT (floor_id, name) DO NOTHING;

-- ─── Tables ──────────────────────────────────────────────────────────────────

WITH wing (zone_name, prefix, table_count, columns, origin_x, origin_y, col_pitch, row_pitch) AS (
    VALUES ('Left Wing',  'L',  8, 2,  80, 80, 260, 180),
           ('Right Wing', 'R', 10, 2, 800, 80, 260, 180)
),
planned AS (
    SELECT w.zone_name,
           w.prefix || n                                             AS label,
           -- L8 is the one 8-seater; everything else seats 6.
           CASE WHEN w.prefix = 'L' AND n = 8 THEN 8 ELSE 6 END      AS capacity,
           w.origin_x + ((n - 1) % w.columns) * w.col_pitch          AS pos_x,
           w.origin_y + ((n - 1) / w.columns) * w.row_pitch          AS pos_y
    FROM wing w
    CROSS JOIN LATERAL generate_series(1, w.table_count) AS n
)
INSERT INTO desk_table (zone_id, label, capacity, pos_x, pos_y, rotation)
SELECT z.id, p.label, p.capacity, p.pos_x, p.pos_y, 0
FROM planned p
JOIN zone z  ON z.name = p.zone_name
JOIN floor f ON f.id = z.floor_id AND f.name = 'Main Floor'
ON CONFLICT (label) DO NOTHING;

-- ─── Seats ───────────────────────────────────────────────────────────────────
-- capacity/2 seats per side, sides A and B, index 1..capacity/2.

INSERT INTO seat (table_id, label, side, seat_index, accessible, status)
SELECT t.id,
       t.label || '-' || s.side || i.seat_index,
       s.side,
       i.seat_index,
       false,
       'ACTIVE'
FROM desk_table t
JOIN zone z  ON z.id = t.zone_id
JOIN floor f ON f.id = z.floor_id AND f.name = 'Main Floor'
CROSS JOIN (VALUES ('A'::varchar), ('B'::varchar)) AS s (side)
CROSS JOIN LATERAL generate_series(1, t.capacity / 2) AS i (seat_index)
ON CONFLICT (label) DO NOTHING;

-- ─── The seed is worthless if it is off by one ───────────────────────────────

DO $$
DECLARE
    tables_seeded int;
    seats_total   int;
    seats_left    int;
    seats_right   int;
BEGIN
    SELECT count(*)
      INTO tables_seeded
      FROM desk_table t
      JOIN zone z  ON z.id = t.zone_id
      JOIN floor f ON f.id = z.floor_id AND f.name = 'Main Floor';

    SELECT count(*),
           count(*) FILTER (WHERE z.name = 'Left Wing'),
           count(*) FILTER (WHERE z.name = 'Right Wing')
      INTO seats_total, seats_left, seats_right
      FROM seat s
      JOIN desk_table t ON t.id = s.table_id
      JOIN zone z       ON z.id = t.zone_id
      JOIN floor f      ON f.id = z.floor_id AND f.name = 'Main Floor';

    IF tables_seeded <> 18 THEN
        RAISE EXCEPTION 'Layout seed produced % tables, expected 18', tables_seeded;
    END IF;
    IF seats_left <> 50 THEN
        RAISE EXCEPTION 'Layout seed produced % Left Wing seats, expected 50', seats_left;
    END IF;
    IF seats_right <> 60 THEN
        RAISE EXCEPTION 'Layout seed produced % Right Wing seats, expected 60', seats_right;
    END IF;
    IF seats_total <> 110 THEN
        RAISE EXCEPTION 'Layout seed produced % seats, expected 110', seats_total;
    END IF;
END $$;
