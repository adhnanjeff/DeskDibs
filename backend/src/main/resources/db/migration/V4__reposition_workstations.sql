-- Reposition the 18 workstation tables into the real floor's two bands.
--
-- Only pos_x / pos_y / rotation change. Labels, capacities and every seat row are
-- left exactly as V2 seeded them, so the anti-double-booking guarantees — which
-- key off seat *labels* (e.g. R5-A1), never positions — stay valid and the
-- 150-thread concurrency tests keep proving what they proved before.
--
-- Coordinates are LOGICAL GRID indices, resolved into pixels by the front-end:
--   pos_y = band   (0 = upper zone, 1 = lower zone)
--   pos_x = column within the band (upper 0..9, lower 0..7)
-- The floor image shows ~10 clusters across the top and ~8 across the bottom;
-- the front-end owns the pixel layout and the collaboration-area gaps. Keeping
-- the office shape in data (not a React component) is the V2 seed's stated rule.

UPDATE desk_table t
SET pos_x = v.col, pos_y = v.band, rotation = 0
FROM (VALUES
    ('R1', 0, 0), ('R2', 1, 0), ('R3', 2, 0), ('R4', 3, 0), ('R5', 4, 0),
    ('R6', 5, 0), ('R7', 6, 0), ('R8', 7, 0), ('R9', 8, 0), ('R10', 9, 0),
    ('L1', 0, 1), ('L2', 1, 1), ('L3', 2, 1), ('L4', 3, 1),
    ('L5', 4, 1), ('L6', 5, 1), ('L7', 6, 1), ('L8', 7, 1)
) AS v (label, col, band)
WHERE t.label = v.label;

-- Fail the migration loudly if the bands did not come out as expected.
DO $$
DECLARE
    upper_count int;
    lower_count int;
    seats_total int;
BEGIN
    SELECT count(*) FILTER (WHERE pos_y = 0),
           count(*) FILTER (WHERE pos_y = 1)
      INTO upper_count, lower_count
      FROM desk_table;

    SELECT count(*) INTO seats_total FROM seat;

    IF upper_count <> 10 THEN
        RAISE EXCEPTION 'V4: expected 10 upper-band tables, found %', upper_count;
    END IF;
    IF lower_count <> 8 THEN
        RAISE EXCEPTION 'V4: expected 8 lower-band tables, found %', lower_count;
    END IF;
    IF seats_total <> 110 THEN
        RAISE EXCEPTION 'V4: seat count changed to % (expected 110)', seats_total;
    END IF;
END $$;
