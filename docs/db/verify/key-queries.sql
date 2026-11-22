-- =====================================================================
-- key-queries.sql — the key repository queries of doc 08 §5, VERIFICATION ONLY
-- Each block starts with "-- @Qnn title" and lists the values bound for
-- its named parameters in "-- params:". run-verification.py binds them as
-- literals so the same text runs in the mysql client and in H2.
-- In the application these are JdbcClient statements with named parameters
-- (never string concatenation of values, NFR-04).
-- "now" is 2026-11-22 12:00:00 everywhere.
-- =====================================================================

-- @Q01 My Complaints, page 1 (FR-20, BR-64) and its total
-- params: userId=4, limit=10, offset=0
SELECT c.complaint_no, c.title, cat.name AS category, c.status, c.created_at
FROM complaint c
JOIN category cat ON cat.id = c.category_id
WHERE c.owner_id = :userId
ORDER BY c.created_at DESC, c.id DESC
LIMIT :limit OFFSET :offset;
SELECT COUNT(*) AS total FROM complaint c WHERE c.owner_id = :userId;

-- @Q02 My Complaints filtered by status (FR-20)
-- params: userId=4, status='RESOLVED', limit=10, offset=0
SELECT c.complaint_no, c.title, c.status, c.resolved_at
FROM complaint c
WHERE c.owner_id = :userId AND c.status = :status
ORDER BY c.created_at DESC, c.id DESC
LIMIT :limit OFFSET :offset;

-- @Q03 Watching list: active watcher rows only (FR-22, BR-51)
-- params: userId=5, limit=10, offset=0
SELECT c.complaint_no, c.title, cat.name AS category, c.status, c.created_at
FROM complaint_watcher w
JOIN complaint c ON c.id = w.complaint_id
JOIN category cat ON cat.id = c.category_id
WHERE w.user_id = :userId AND w.active = TRUE
ORDER BY c.created_at DESC, c.id DESC
LIMIT :limit OFFSET :offset;

-- @Q04 Access facts for ComplaintAccessPolicy.canView (BR-60): Jayesh on GRV-2026-000001
-- params: complaintId=1, userId=5
SELECT
  (SELECT COUNT(*) FROM complaint_watcher w
    WHERE w.complaint_id = :complaintId AND w.user_id = :userId AND w.active = TRUE) AS is_active_watcher,
  (SELECT COUNT(*) FROM complaint d
    WHERE d.duplicate_of_id = :complaintId AND d.owner_id = :userId) AS owns_linked_duplicate;

-- @Q05 Access facts: Sneha on GRV-2026-000005 (inactive watcher, no duplicate) -> not visible
-- params: complaintId=4, userId=6
SELECT
  (SELECT COUNT(*) FROM complaint_watcher w
    WHERE w.complaint_id = :complaintId AND w.user_id = :userId AND w.active = TRUE) AS is_active_watcher,
  (SELECT COUNT(*) FROM complaint d
    WHERE d.duplicate_of_id = :complaintId AND d.owner_id = :userId) AS owns_linked_duplicate;

-- @Q06 Department Queue: overdue first, then oldest first (FR-40, BR-65)
-- params: departmentId=2, now='2026-11-22 12:00:00', limit=10, offset=0
SELECT c.complaint_no, c.status, c.created_at, cat.sla_days,
       TIMESTAMPADD(DAY, cat.sla_days, c.created_at) AS due_at,
       CASE WHEN c.status IN ('SUBMITTED', 'UNDER_REVIEW', 'ASSIGNED', 'IN_PROGRESS', 'REOPENED')
             AND TIMESTAMPADD(DAY, cat.sla_days, c.created_at) < :now
            THEN 1 ELSE 0 END AS overdue
FROM complaint c
JOIN category cat ON cat.id = c.category_id
WHERE c.department_id = :departmentId
  AND c.status IN ('ASSIGNED', 'IN_PROGRESS', 'REOPENED', 'RESOLVED')
ORDER BY overdue DESC, c.created_at ASC, c.id ASC
LIMIT :limit OFFSET :offset;

-- @Q07 All Complaints with filters and search (FR-30)
-- params: fromDate='2026-11-01 00:00:00', toDateExcl='2026-11-23 00:00:00', q='GARBAGE', qLike='%garbage%', limit=10, offset=0
SELECT c.complaint_no, c.title, c.status, cat.name AS category, a.name AS area, d.name AS department, c.created_at
FROM complaint c
JOIN category cat ON cat.id = c.category_id
JOIN area a ON a.id = c.area_id
LEFT JOIN department d ON d.id = c.department_id
WHERE c.created_at >= :fromDate AND c.created_at < :toDateExcl
  AND (c.complaint_no = :q OR LOWER(c.title) LIKE :qLike)
ORDER BY c.created_at DESC, c.id DESC
LIMIT :limit OFFSET :offset;

-- @Q08 Dashboard: count per status (FR-60)
-- params:
SELECT c.status, COUNT(*) AS cnt
FROM complaint c
GROUP BY c.status
ORDER BY c.status;

-- @Q09 Dashboard: overdue count, all and for one department (FR-60, FR-63, BR-65)
-- params: now='2026-11-22 12:00:00', departmentId=2
SELECT COUNT(*) AS overdue_all
FROM complaint c JOIN category cat ON cat.id = c.category_id
WHERE c.status IN ('SUBMITTED', 'UNDER_REVIEW', 'ASSIGNED', 'IN_PROGRESS', 'REOPENED')
  AND TIMESTAMPADD(DAY, cat.sla_days, c.created_at) < :now;
SELECT COUNT(*) AS overdue_dept
FROM complaint c JOIN category cat ON cat.id = c.category_id
WHERE c.department_id = :departmentId
  AND c.status IN ('SUBMITTED', 'UNDER_REVIEW', 'ASSIGNED', 'IN_PROGRESS', 'REOPENED')
  AND TIMESTAMPADD(DAY, cat.sla_days, c.created_at) < :now;

-- @Q10 Dashboard: open / closed by category (FR-61)
-- params:
SELECT cat.name AS category,
       SUM(CASE WHEN c.status IN ('SUBMITTED', 'UNDER_REVIEW', 'ASSIGNED', 'IN_PROGRESS', 'REOPENED', 'RESOLVED') THEN 1 ELSE 0 END) AS open_cnt,
       SUM(CASE WHEN c.status IN ('CLOSED', 'REJECTED', 'DUPLICATE', 'WITHDRAWN') THEN 1 ELSE 0 END) AS closed_cnt
FROM category cat
LEFT JOIN complaint c ON c.category_id = cat.id
GROUP BY cat.id, cat.name
ORDER BY cat.name;

-- @Q11 Dashboard: open / closed by department, incl. "not assigned" (FR-61)
-- params:
SELECT d.id AS department_id, COALESCE(d.name, '(Not assigned)') AS department,
       SUM(CASE WHEN c.status IN ('SUBMITTED', 'UNDER_REVIEW', 'ASSIGNED', 'IN_PROGRESS', 'REOPENED', 'RESOLVED') THEN 1 ELSE 0 END) AS open_cnt,
       SUM(CASE WHEN c.status IN ('CLOSED', 'REJECTED', 'DUPLICATE', 'WITHDRAWN') THEN 1 ELSE 0 END) AS closed_cnt
FROM complaint c
LEFT JOIN department d ON d.id = c.department_id
GROUP BY d.id, d.name
ORDER BY department;

-- @Q12 Dashboard: open / closed by area (FR-61)
-- params:
SELECT a.name AS area,
       SUM(CASE WHEN c.status IN ('SUBMITTED', 'UNDER_REVIEW', 'ASSIGNED', 'IN_PROGRESS', 'REOPENED', 'RESOLVED') THEN 1 ELSE 0 END) AS open_cnt,
       SUM(CASE WHEN c.status IN ('CLOSED', 'REJECTED', 'DUPLICATE', 'WITHDRAWN') THEN 1 ELSE 0 END) AS closed_cnt
FROM area a
LEFT JOIN complaint c ON c.area_id = a.id
GROUP BY a.id, a.name
ORDER BY a.name;

-- @Q13 Overdue list, most overdue first (FR-62, BR-65, BR-66)
-- params: now='2026-11-22 12:00:00', limit=50
SELECT c.complaint_no, c.status, d.name AS department, cat.sla_days, c.created_at,
       TIMESTAMPADD(DAY, cat.sla_days, c.created_at) AS due_at
FROM complaint c
JOIN category cat ON cat.id = c.category_id
LEFT JOIN department d ON d.id = c.department_id
WHERE c.status IN ('SUBMITTED', 'UNDER_REVIEW', 'ASSIGNED', 'IN_PROGRESS', 'REOPENED')
  AND TIMESTAMPADD(DAY, cat.sla_days, c.created_at) < :now
ORDER BY due_at ASC, c.id ASC
LIMIT :limit;

-- @Q14 Timeline: events merged with their revisions (FR-21, FR-17)
-- params: complaintId=1
SELECT e.id AS event_id, e.event_type, e.from_status, e.to_status, e.created_at,
       u.full_name AS actor_name, u.role AS actor_role, d.name AS department,
       rc.complaint_no AS related_no, e.remark,
       r.field_name, r.old_value, r.new_value
FROM complaint_event e
LEFT JOIN app_user u ON u.id = e.actor_id
LEFT JOIN department d ON d.id = e.department_id
LEFT JOIN complaint rc ON rc.id = e.related_complaint_id
LEFT JOIN complaint_revision r ON r.event_id = e.id
WHERE e.complaint_id = :complaintId
ORDER BY e.created_at ASC, e.id ASC, r.id ASC;

-- @Q15 Unread notification count for the bell (FR-71)
-- params: userId=4
SELECT COUNT(*) AS unread FROM notification n WHERE n.recipient_id = :userId AND n.is_read = FALSE;

-- @Q16 Notification recipients: owner + active watchers, de-duplicated (BR-67, BR-68)
-- params: complaintId=1
SELECT c.owner_id AS user_id FROM complaint c WHERE c.id = :complaintId
UNION
SELECT w.user_id FROM complaint_watcher w WHERE w.complaint_id = :complaintId AND w.active = TRUE
ORDER BY user_id;

-- @Q17 Withdraw guard: does anything link to this complaint as a duplicate? (BR-47)
-- params: complaintId=1
SELECT COUNT(*) AS linked_duplicates FROM complaint d WHERE d.duplicate_of_id = :complaintId;

-- @Q18 Auto-close selection: RESOLVED and review window over (FR-82, BR-30)
-- params: cutoff='2026-11-15 12:00:00', batch=500
SELECT c.id, c.complaint_no, c.version, c.resolved_at
FROM complaint c
WHERE c.status = 'RESOLVED' AND c.resolved_at < :cutoff
ORDER BY c.resolved_at ASC, c.id ASC
LIMIT :batch;

-- @Q19 Optimistic-lock update: first Resolve with version 3 wins (BR-71)
-- params: id=1, version=3, now='2026-11-22 12:00:00'
UPDATE complaint
SET status = 'RESOLVED', resolved_at = :now, updated_at = :now, version = version + 1
WHERE id = :id AND version = :version AND status = 'IN_PROGRESS';

-- @Q20 Optimistic-lock update: second Resolve with the same stale version 3 updates 0 rows
-- params: id=1, version=3, now='2026-11-22 12:00:01'
UPDATE complaint
SET status = 'RESOLVED', resolved_at = :now, updated_at = :now, version = version + 1
WHERE id = :id AND version = :version AND status = 'IN_PROGRESS';
SELECT complaint_no, status, version, resolved_at FROM complaint WHERE id = :id;

-- @Q21 Auto-close update for one selected row (T09), guarded by version and status
-- params: id=6, version=4, now='2026-11-22 12:00:00'
UPDATE complaint
SET status = 'CLOSED', updated_at = :now, version = version + 1
WHERE id = :id AND version = :version AND status = 'RESOLVED';
SELECT complaint_no, status, version FROM complaint WHERE id = :id;

-- @Q22 Complaint number, existing year row (BR-10, BR-11): 2026 -> 14
-- params: year=2026
-- In the app these four statements run inside the submit transaction (@Transactional).
INSERT INTO complaint_sequence (seq_year, last_number) VALUES (:year, 0)
  ON DUPLICATE KEY UPDATE last_number = last_number;
SELECT last_number FROM complaint_sequence WHERE seq_year = :year FOR UPDATE;
UPDATE complaint_sequence SET last_number = last_number + 1 WHERE seq_year = :year;
SELECT last_number AS issued FROM complaint_sequence WHERE seq_year = :year;

-- @Q23 Complaint number, first complaint of a new year: 2027 -> 1
-- params: year=2027
INSERT INTO complaint_sequence (seq_year, last_number) VALUES (:year, 0)
  ON DUPLICATE KEY UPDATE last_number = last_number;
SELECT last_number FROM complaint_sequence WHERE seq_year = :year FOR UPDATE;
UPDATE complaint_sequence SET last_number = last_number + 1 WHERE seq_year = :year;
SELECT last_number AS issued FROM complaint_sequence WHERE seq_year = :year;

-- @Q24 Mark notifications read: one item, then "mark all" (FR-72)
-- params: userId=4, id=2, now='2026-11-22 12:00:00'
UPDATE notification SET is_read = TRUE, read_at = :now
WHERE id = :id AND recipient_id = :userId AND is_read = FALSE;
UPDATE notification SET is_read = TRUE, read_at = :now
WHERE recipient_id = :userId AND is_read = FALSE;
SELECT COUNT(*) AS unread_after FROM notification n WHERE n.recipient_id = :userId AND n.is_read = FALSE;

-- @Q25 Constraint check: a RESOLVED complaint without resolved_at is refused (ck_complaint_resolved_at)
-- params:
UPDATE complaint SET resolved_at = NULL WHERE id = 3;

-- @Q26 Search with an escaped LIKE pattern: "50%" must match literally, not as a wildcard (FR-30, D25)
-- params: q='50%', qLike='%50!%%'
SELECT COUNT(*) AS matches FROM complaint c WHERE (c.complaint_no = :q OR LOWER(c.title) LIKE :qLike ESCAPE '!');

-- @Q27 Notification list, newest first (FR-72)
-- params: userId=4, limit=10, offset=0
SELECT n.id, n.message, n.is_read, n.created_at, c.complaint_no
FROM notification n LEFT JOIN complaint c ON c.id = n.complaint_id
WHERE n.recipient_id = :userId
ORDER BY n.created_at DESC, n.id DESC
LIMIT :limit OFFSET :offset;

-- @Q28 Watcher: Unwatch, then Watch again reactivates the same row (BR-51, BR-52)
-- params: complaintId=1, userId=5, now='2026-11-22 12:00:00'
UPDATE complaint_watcher SET active = FALSE, updated_at = :now
WHERE complaint_id = :complaintId AND user_id = :userId AND active = TRUE;
UPDATE complaint_watcher SET active = TRUE, since = :now, updated_at = :now
WHERE complaint_id = :complaintId AND user_id = :userId AND active = FALSE;
SELECT complaint_id, user_id, active, since FROM complaint_watcher WHERE complaint_id = :complaintId;

-- @Q29 Guards: active complaint attachments (BR-23) and other active admins (BR-82)
-- params: complaintId=1, userId=1
SELECT COUNT(*) AS active_files FROM attachment
WHERE complaint_id = :complaintId AND purpose = 'COMPLAINT' AND deleted = FALSE;
SELECT COUNT(*) AS other_active_admins FROM app_user WHERE role = 'ADMIN' AND active = TRUE AND id <> :userId;

-- @Q30 OTP: issue, cooldown/hourly facts, latest usable OTP (BR-85)
-- params: userId=6, now='2026-11-22 12:00:00', exp='2026-11-22 12:05:00', oneHourAgo='2026-11-22 11:00:00'
UPDATE mobile_otp SET used = TRUE WHERE user_id = :userId AND used = FALSE;
INSERT INTO mobile_otp (user_id, otp_hash, sent_at, expires_at, attempts, used) VALUES (:userId, 'hash-1', :now, :exp, 0, FALSE);
SELECT MAX(sent_at) AS last_sent, SUM(CASE WHEN sent_at > :oneHourAgo THEN 1 ELSE 0 END) AS sends_last_hour
FROM mobile_otp WHERE user_id = :userId;
SELECT id, otp_hash, expires_at, attempts FROM mobile_otp
WHERE user_id = :userId AND used = FALSE ORDER BY sent_at DESC, id DESC LIMIT 1;

-- @Q31 Constraint check: a second notification for the same event and recipient is refused (BR-68)
-- params:
INSERT INTO notification (recipient_id, complaint_id, event_id, message, is_read, created_at) VALUES (4, 1, 2, 'dup', FALSE, '2026-11-22 12:00:00');
