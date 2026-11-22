-- Timed on the 10k load (MySQL). Same SQL as key-queries.sql with literals.
-- QA My Complaints
SELECT c.complaint_no, c.title, cat.name, c.status, c.created_at FROM complaint c JOIN category cat ON cat.id = c.category_id
WHERE c.owner_id = 106 ORDER BY c.created_at DESC, c.id DESC LIMIT 10 OFFSET 0;
-- QB All Complaints, status filter, page 20
SELECT c.complaint_no, c.title, c.status, cat.name, a.name, d.name, c.created_at FROM complaint c
JOIN category cat ON cat.id = c.category_id JOIN area a ON a.id = c.area_id LEFT JOIN department d ON d.id = c.department_id
WHERE c.status = 'ASSIGNED' ORDER BY c.created_at DESC, c.id DESC LIMIT 10 OFFSET 190;
-- QC All Complaints, title search (full scan, accepted)
SELECT COUNT(*) FROM complaint c WHERE (c.complaint_no = 'LOAD TEST COMPLAINT 99' OR LOWER(c.title) LIKE '%complaint 99%');
-- QD Department queue
SELECT c.complaint_no, c.status, c.created_at, CASE WHEN c.status IN ('SUBMITTED','UNDER_REVIEW','ASSIGNED','IN_PROGRESS','REOPENED')
 AND TIMESTAMPADD(DAY, cat.sla_days, c.created_at) < '2026-11-22 12:00:00' THEN 1 ELSE 0 END AS overdue
FROM complaint c JOIN category cat ON cat.id = c.category_id
WHERE c.department_id = 2 AND c.status IN ('ASSIGNED','IN_PROGRESS','REOPENED','RESOLVED')
ORDER BY overdue DESC, c.created_at ASC, c.id ASC LIMIT 10 OFFSET 0;
-- QE Dashboard status counts
SELECT c.status, COUNT(*) FROM complaint c GROUP BY c.status;
-- QF Dashboard overdue count
SELECT COUNT(*) FROM complaint c JOIN category cat ON cat.id = c.category_id
WHERE c.status IN ('SUBMITTED','UNDER_REVIEW','ASSIGNED','IN_PROGRESS','REOPENED') AND TIMESTAMPADD(DAY, cat.sla_days, c.created_at) < '2026-11-22 12:00:00';
-- QG Dashboard by category
SELECT cat.name, SUM(CASE WHEN c.status IN ('SUBMITTED','UNDER_REVIEW','ASSIGNED','IN_PROGRESS','REOPENED','RESOLVED') THEN 1 ELSE 0 END),
 SUM(CASE WHEN c.status IN ('CLOSED','REJECTED','DUPLICATE','WITHDRAWN') THEN 1 ELSE 0 END) FROM category cat LEFT JOIN complaint c ON c.category_id = cat.id GROUP BY cat.id, cat.name;
-- QH Auto-close selection
SELECT c.id, c.version FROM complaint c WHERE c.status = 'RESOLVED' AND c.resolved_at < '2026-11-15 12:00:00' ORDER BY c.resolved_at, c.id LIMIT 500;
-- QI Unread count
SELECT COUNT(*) FROM notification n WHERE n.recipient_id = 106 AND n.is_read = FALSE;
-- QJ Timeline
SELECT e.id, e.event_type, r.field_name FROM complaint_event e LEFT JOIN complaint_revision r ON r.event_id = e.id WHERE e.complaint_id = 5000 ORDER BY e.created_at, e.id, r.id;
