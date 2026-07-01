CREATE TABLE parcel_checks (
  id INT PRIMARY KEY,
  label VARCHAR(60) NOT NULL,
  known_items INT NOT NULL,
  extra_items INT,
  scan_passed BOOLEAN,
  courier_note VARCHAR(80)
);

INSERT INTO parcel_checks (id, label, known_items, extra_items, scan_passed, courier_note) VALUES
  (1, 'green-tag parcel', 1, 4, TRUE, 'ready'),
  (2, 'unknown-count parcel', 1, NULL, NULL, NULL),
  (3, 'red-tag parcel', 1, 0, FALSE, 'blocked'),
  (4, 'manual-review parcel', 1, NULL, FALSE, NULL);
