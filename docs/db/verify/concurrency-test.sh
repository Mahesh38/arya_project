#!/usr/bin/env bash
# MySQL-only: N parallel clients each take M complaint numbers for a year that has
# no complaint_sequence row yet (the hardest case: the first insert races).
# Expect N*M distinct numbers 1..N*M, no gaps, no duplicates, no deadlock errors.
set -u
DB=${DB:-grievance_verify}; N=${N:-8}; M=${M:-50}; YEAR=${YEAR:-2030}
mysql "$DB" -e "DELETE FROM complaint_sequence WHERE seq_year=$YEAR;
  DROP TABLE IF EXISTS seq_issued; CREATE TABLE seq_issued (n INT NOT NULL, client INT NOT NULL);"
one_client() {
  local c=$1 sql=""
  for _ in $(seq 1 "$M"); do
    sql+="START TRANSACTION;
INSERT INTO complaint_sequence (seq_year, last_number) VALUES ($YEAR, 0) ON DUPLICATE KEY UPDATE last_number = last_number;
SELECT last_number + 1 INTO @n FROM complaint_sequence WHERE seq_year = $YEAR FOR UPDATE;
UPDATE complaint_sequence SET last_number = @n WHERE seq_year = $YEAR;
INSERT INTO seq_issued VALUES (@n, $c);
COMMIT;
"
  done
  mysql "$DB" <<<"$sql" 2>&1
}
for c in $(seq 1 "$N"); do one_client "$c" > "/tmp/seqtest.$c.log" & done
wait
cat /tmp/seqtest.*.log; rm -f /tmp/seqtest.*.log
mysql -t "$DB" -e "SELECT COUNT(*) AS issued, COUNT(DISTINCT n) AS distinct_numbers, MIN(n) AS min_n, MAX(n) AS max_n,
  (SELECT last_number FROM complaint_sequence WHERE seq_year=$YEAR) AS last_number FROM seq_issued; DROP TABLE seq_issued;"
