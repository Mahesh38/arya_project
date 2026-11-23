-- load-10k.sql — MySQL only. Adds 10,000 complaints, 60,000 events and
-- 30,000 notifications to the sample database to check NFR-10 / index use.
SET SESSION cte_max_recursion_depth = 100000;
INSERT INTO app_user (full_name, email, mobile, password_hash, role, department_id, active, mobile_verified, created_at, updated_at)
WITH RECURSIVE n(i) AS (SELECT 1 UNION ALL SELECT i + 1 FROM n WHERE i < 2000)
SELECT CONCAT('Citizen ', i), CONCAT('citizen', i, '@example.com'), CONCAT('97', LPAD(i, 8, '0')), '$2a$10$hash',
       'CITIZEN', NULL, TRUE, TRUE, '2026-01-01 09:00:00', '2026-01-01 09:00:00' FROM n;
INSERT INTO complaint (complaint_no, owner_id, category_id, area_id, department_id, duplicate_of_id, title, description, address,
                       status, reopen_count, created_at, updated_at, resolved_at, version)
WITH RECURSIVE n(i) AS (SELECT 1 UNION ALL SELECT i + 1 FROM n WHERE i < 10000),
s AS (SELECT i, ELT(1 + (i % 9), 'SUBMITTED','UNDER_REVIEW','ASSIGNED','IN_PROGRESS','RESOLVED','REOPENED','CLOSED','REJECTED','WITHDRAWN') AS st,
             TIMESTAMPADD(MINUTE, -(i * 47), '2026-11-22 12:00:00') AS ts FROM n)
SELECT CONCAT('GRV-2025-', LPAD(i, 6, '0')), 6 + (i % 2000), 1 + (i % 6), 1 + (i % 7),
       CASE WHEN st IN ('SUBMITTED','UNDER_REVIEW','WITHDRAWN') THEN NULL ELSE 1 + (i % 5) END, NULL,
       CONCAT('Load test complaint ', i), 'Generated description for the load test row', 'Some address, Pune',
       st, 0, ts, ts, CASE WHEN st = 'RESOLVED' THEN TIMESTAMPADD(DAY, 1, ts) END, 0 FROM s;
INSERT INTO complaint_event (complaint_id, event_type, from_status, to_status, actor_id, remark, created_at)
WITH RECURSIVE k(j) AS (SELECT 1 UNION ALL SELECT j + 1 FROM k WHERE j < 6)
SELECT c.id, 'REMARK', NULL, NULL, 1, 'Load test remark', TIMESTAMPADD(MINUTE, k.j, c.created_at)
FROM complaint c CROSS JOIN k WHERE c.complaint_no LIKE 'GRV-2025-%';
INSERT INTO notification (recipient_id, complaint_id, event_id, message, is_read, created_at, read_at)
SELECT c.owner_id, c.id, NULL, 'Load test notification', (c.id % 3 = 0), c.created_at, CASE WHEN c.id % 3 = 0 THEN c.created_at END
FROM complaint c CROSS JOIN (SELECT 1 AS x UNION ALL SELECT 2 UNION ALL SELECT 3) t WHERE c.complaint_no LIKE 'GRV-2025-%';
ANALYZE TABLE app_user, complaint, complaint_event, notification, complaint_watcher;
