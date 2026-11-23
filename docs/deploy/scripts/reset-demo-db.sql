-- Reset the DEMO database (doc 14 §8.1): run as root, then start the app with the dev profile.
--   mysql -u root -p < docs/deploy/scripts/reset-demo-db.sql
-- Flyway recreates the schema, AdminBootstrap the admin, DevDataSeeder the Appendix A data
-- (with dates relative to "now"). Also delete the app's uploads/ folder (demo files are re-copied).
-- NEVER run this against a production database.
DROP DATABASE IF EXISTS grievance_dev;
CREATE DATABASE grievance_dev CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
GRANT ALL PRIVILEGES ON grievance_dev.* TO 'grievance_dev'@'localhost';
GRANT ALL PRIVILEGES ON grievance_dev.* TO 'grievance_dev'@'127.0.0.1';
