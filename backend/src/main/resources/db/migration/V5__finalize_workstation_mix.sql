-- Finalize the workstation mix to the office's real 102 seats.
--
-- Two end clusters shrink from their interim sizes to 3 seats each, matching the
-- finalized floor plan:
--   R1  (leftmost of the upper band) : 6 -> 3
--   L8  (rightmost of the lower band): 8 -> 3
-- Each keeps seats A1, A2 and B1 (2 on side A, 1 on side B). That drops exactly
-- 8 seats (110 -> 102) and, deliberately, preserves every seat label the test
-- suite pins (R1-A1, R1-A2, R1-B1).
--
-- Positions were set by V4; only capacity and the surplus seats change here. No
-- booking rows exist at migration time on a fresh database, so the seat deletes
-- have nothing to cascade.

-- The interim schema assumed every desk seats an even number (equal A/B sides).
-- The finalized floor has two 3-seaters, so relax the invariant to "positive".
-- A 3-seater keeps 2 seats on side A and 1 on side B.
ALTER TABLE desk_table DROP CONSTRAINT ck_desk_table_capacity;
ALTER TABLE desk_table ADD CONSTRAINT ck_desk_table_capacity CHECK (capacity > 0);

DELETE FROM seat s
USING desk_table t
WHERE s.table_id = t.id
  AND ( (t.label = 'R1' AND s.label IN ('R1-A3', 'R1-B2', 'R1-B3'))
     OR (t.label = 'L8' AND s.label IN ('L8-A3', 'L8-A4', 'L8-B2', 'L8-B3', 'L8-B4')) );

UPDATE desk_table SET capacity = 3 WHERE label IN ('R1', 'L8');

DO $$
DECLARE
    seats_total int;
    r1_cap int;
    l8_cap int;
BEGIN
    SELECT count(*) INTO seats_total FROM seat;
    SELECT capacity INTO r1_cap FROM desk_table WHERE label = 'R1';
    SELECT capacity INTO l8_cap FROM desk_table WHERE label = 'L8';

    IF seats_total <> 102 THEN
        RAISE EXCEPTION 'V5: expected 102 seats, found %', seats_total;
    END IF;
    IF r1_cap <> 3 OR l8_cap <> 3 THEN
        RAISE EXCEPTION 'V5: R1/L8 capacity should be 3, got % / %', r1_cap, l8_cap;
    END IF;
END $$;
