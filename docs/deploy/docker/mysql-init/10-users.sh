#!/bin/bash
# OPTIONAL (doc 14 §8.3): runs once, when the db-data volume is empty.
# Creates the Flyway owner and the DML-only application user of doc 14 §6.1 (D114).
# The official mysql image has already created the database "grievance" (MYSQL_DATABASE).
set -euo pipefail

mysql --protocol=socket -uroot -p"${MYSQL_ROOT_PASSWORD}" <<SQL
CREATE USER IF NOT EXISTS 'grievance_owner'@'%' IDENTIFIED BY '${OWNER_DB_PASSWORD}';
GRANT CREATE, ALTER, DROP, INDEX, REFERENCES, SELECT, INSERT, UPDATE, DELETE
  ON grievance.* TO 'grievance_owner'@'%';
CREATE USER IF NOT EXISTS 'grievance_app'@'%' IDENTIFIED BY '${APP_DB_PASSWORD}';
GRANT SELECT, INSERT, UPDATE, DELETE ON grievance.* TO 'grievance_app'@'%';
SQL
