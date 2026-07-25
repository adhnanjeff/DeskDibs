-- The finalized upper-band 3-seater is R10 (right end), not R1 (left end). V5
-- shrank R1 during an earlier layout pass; this corrects it forward: R1 goes
-- back to a full 6-seater and R10 becomes the 3-seater. Net seat count stays 102.

-- Re-add the three seats V5 dropped from R1 (A3 on side A, B2 and B3 on side B).
INSERT INTO seat (table_id, label, side, seat_index, accessible, status)
SELECT t.id, v.label, v.side, v.idx, false, 'ACTIVE'
FROM desk_table t
CROSS JOIN (VALUES ('R1-A3', 'A', 3), ('R1-B2', 'B', 2), ('R1-B3', 'B', 3)) AS v (label, side, idx)
WHERE t.label = 'R1'
ON CONFLICT (label) DO NOTHING;

UPDATE desk_table SET capacity = 6 WHERE label = 'R1';

-- Shrink R10 to a 3-seater, keeping A1, A2 and B1.
DELETE FROM seat s
USING desk_table t
WHERE s.table_id = t.id
  AND t.label = 'R10' AND s.label IN ('R10-A3', 'R10-B2', 'R10-B3');

UPDATE desk_table SET capacity = 3 WHERE label = 'R10';

DO $$
DECLARE
    seats_total int;
    r1_cap int;
    r10_cap int;
BEGIN
    SELECT count(*) INTO seats_total FROM seat;
    SELECT capacity INTO r1_cap FROM desk_table WHERE label = 'R1';
    SELECT capacity INTO r10_cap FROM desk_table WHERE label = 'R10';

    IF seats_total <> 102 THEN
        RAISE EXCEPTION 'V6: expected 102 seats, found %', seats_total;
    END IF;
    IF r1_cap <> 6 OR r10_cap <> 3 THEN
        RAISE EXCEPTION 'V6: R1 should be 6 and R10 should be 3, got % / %', r1_cap, r10_cap;
    END IF;
END $$;
