-- =====================================================================
-- Smart Grievance: production database and least-privilege users (doc 14 §6.1, D114)
-- Run once as the MySQL root user on the server. Replace the three passwords
-- first (long random values, e.g. `openssl rand -base64 24`) and keep them in
-- /etc/smart-grievance/env (doc 14 §5.6), never in Git.
--   mysql -u root -p < 02-prod.sql
-- Accounts:
--   grievance_owner   used ONLY by Flyway at start-up (DB_MIGRATION_*): creates and alters tables
--   grievance_app     the application's connection pool (DB_*): reads and writes rows, no DDL
--   grievance_backup  mysqldump only (doc 14 §6.4): read-only
-- Host 'localhost' = the app runs on the same machine as MySQL (doc 14 §8.2).
-- For Docker Compose the host part is '%' (the compose file creates its own users, §8.3).
-- =====================================================================

CREATE DATABASE IF NOT EXISTS grievance
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS 'grievance_owner'@'localhost' IDENTIFIED BY 'CHANGE-ME-owner-password';
GRANT CREATE, ALTER, DROP, INDEX, REFERENCES, SELECT, INSERT, UPDATE, DELETE
  ON grievance.* TO 'grievance_owner'@'localhost';

-- DML only. DELETE is needed by HousekeepingJob (notification, mobile_otp; D28).
-- SELECT ... FOR UPDATE needs SELECT + UPDATE (complaint number generator, D19).
CREATE USER IF NOT EXISTS 'grievance_app'@'localhost' IDENTIFIED BY 'CHANGE-ME-app-password';
GRANT SELECT, INSERT, UPDATE, DELETE ON grievance.* TO 'grievance_app'@'localhost';

-- --single-transaction needs no LOCK TABLES on InnoDB; --no-tablespaces avoids the PROCESS privilege
CREATE USER IF NOT EXISTS 'grievance_backup'@'localhost' IDENTIFIED BY 'CHANGE-ME-backup-password';
GRANT SELECT, SHOW VIEW, TRIGGER, LOCK TABLES ON grievance.* TO 'grievance_backup'@'localhost';
