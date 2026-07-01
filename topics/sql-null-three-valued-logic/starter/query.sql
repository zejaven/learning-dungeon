-- The table below simulates parcel checks at a small post office.
-- Some values are deliberately unknown (NULL).
--
-- Try the missions by changing this SELECT:
-- 1) compute known_items + extra_items;
-- 2) inspect scan_passed OR TRUE and scan_passed OR FALSE;
-- 3) filter with WHERE and with IS NULL.
SELECT id, label, known_items, extra_items, scan_passed, courier_note
FROM parcel_checks
ORDER BY id;
