-- =====================================================================
-- Smart Grievance: databases and users for a STUDENT LAPTOP (doc 14 §6.1)
-- Run once as the MySQL root user:
--   mysql -u root -p < docs/deploy/db/01-dev-and-test.sql
-- The passwords below are for a developer laptop only. They also appear in
-- application-dev.properties / application-mysqltest.properties.
-- Never use them on a server (use 02-prod.sql there).
-- =====================================================================

-- utf8mb4 + the MySQL 8 default collation (D29): Devanagari names, emoji, case-insensitive compare
CREATE DATABASE IF NOT EXISTS grievance_dev
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS grievance_test
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- dev: the app (incl. Flyway) owns its database; nothing else
CREATE USER IF NOT EXISTS 'grievance_dev'@'localhost' IDENTIFIED BY 'Dev@Grievance1';
GRANT ALL PRIVILEGES ON grievance_dev.* TO 'grievance_dev'@'localhost';

-- test (-Pmysql): TestDatabase.reset() needs DELETE and ALTER TABLE (identity restart, doc 13 §2.4)
CREATE USER IF NOT EXISTS 'grievance_test'@'localhost' IDENTIFIED BY 'Test@Grievance1';
GRANT ALL PRIVILEGES ON grievance_test.* TO 'grievance_test'@'localhost';

-- Some clients connect over TCP as 127.0.0.1 instead of "localhost": allow both.
CREATE USER IF NOT EXISTS 'grievance_dev'@'127.0.0.1' IDENTIFIED BY 'Dev@Grievance1';
GRANT ALL PRIVILEGES ON grievance_dev.* TO 'grievance_dev'@'127.0.0.1';
CREATE USER IF NOT EXISTS 'grievance_test'@'127.0.0.1' IDENTIFIED BY 'Test@Grievance1';
GRANT ALL PRIVILEGES ON grievance_test.* TO 'grievance_test'@'127.0.0.1';
