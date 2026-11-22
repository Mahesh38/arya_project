-- =====================================================================
-- sample-data.sql — VERIFICATION ONLY (not a migration, not demo data)
-- A few rows used to run the key queries of doc 08 §5 on MySQL and H2.
-- "Now" for every query in key-queries.sql is 2026-11-22 12:00:00.
-- Ids are known because this script runs on a fresh V1+V2 database:
--   departments 1 Sanitation, 2 Water Supply, 3 Road Maintenance, 4 Drainage, 5 Electrical
--   categories  1 Garbage(3d), 2 Water Leakage(2d), 3 Road Damage(10d), 4 Drainage(5d), 5 Streetlight(3d), 6 Other(7d)
--   areas       1 Shivajinagar, 2 Kothrud, 3 Aundh, 4 Baner, 5 Hadapsar, 6 Viman Nagar, 7 Kasba Peth
-- =====================================================================

INSERT INTO app_user (full_name, email, mobile, password_hash, role, department_id, active, mobile_verified, created_at, updated_at) VALUES
 ('System Administrator', 'admin@example.com',          '9000000000', '$2a$10$hash', 'ADMIN',   NULL, TRUE, TRUE,  '2026-11-01 09:00:00', '2026-11-01 09:00:00'), -- 1
 ('Anita Deshmukh',       'anita.deshmukh@example.com', '9000000201', '$2a$10$hash', 'OFFICER', 2,    TRUE, TRUE,  '2026-11-01 09:00:00', '2026-11-01 09:00:00'), -- 2
 ('Suresh Patil',         'suresh.patil@example.com',   '9000000101', '$2a$10$hash', 'OFFICER', 1,    TRUE, TRUE,  '2026-11-01 09:00:00', '2026-11-01 09:00:00'), -- 3
 ('Mahesh Joshi',         'mahesh@example.com',         '9800000001', '$2a$10$hash', 'CITIZEN', NULL, TRUE, TRUE,  '2026-11-01 09:00:00', '2026-11-01 09:00:00'), -- 4
 ('Jayesh Gaikwad',       'jayesh@example.com',         '9800000002', '$2a$10$hash', 'CITIZEN', NULL, TRUE, TRUE,  '2026-11-01 09:00:00', '2026-11-01 09:00:00'), -- 5
 ('Sneha Kale',           'sneha@example.com',          '9800000003', '$2a$10$hash', 'CITIZEN', NULL, TRUE, TRUE,  '2026-11-01 09:00:00', '2026-11-01 09:00:00'); -- 6

INSERT INTO complaint (complaint_no, owner_id, category_id, area_id, department_id, duplicate_of_id, title, description, address, status, reopen_count, created_at, updated_at, resolved_at, version) VALUES
 ('GRV-2026-000001', 4, 2, 2, 2,    NULL, 'Pipeline leaking on Karve Road', 'Water pipeline leaking near Kothrud Depot for a day', 'Karve Road, near Kothrud Depot', 'IN_PROGRESS', 0, '2026-11-21 10:00:00', '2026-11-22 09:00:00', NULL, 3),                    -- 1
 ('GRV-2026-000003', 6, 1, 5, NULL, NULL, 'Garbage not collected',          'Garbage not collected for 4 days near Magarpatta gate', 'Near Magarpatta City gate',      'SUBMITTED',   0, '2026-11-18 08:00:00', '2026-11-18 08:00:00', NULL, 0),                    -- 2
 ('GRV-2026-000004', 4, 5, 3, 5,    NULL, 'Streetlight not working',        'Streetlight on ITI Road has not worked for a week',   'ITI Road',                        'RESOLVED',    0, '2026-11-15 19:00:00', '2026-11-20 11:00:00', '2026-11-20 11:00:00', 4),   -- 3
 ('GRV-2026-000005', 5, 3, 1, 3,    NULL, 'Large pothole on FC Road',       'Large pothole on FC Road causing accidents at night', 'FC Road, near Fergusson College', 'ASSIGNED',    0, '2026-11-07 10:00:00', '2026-11-08 10:00:00', NULL, 2),                    -- 4
 ('GRV-2026-000007', 5, 1, 6, 1,    NULL, 'Garbage piled up again',         'Garbage collected once but piled up again near mall', 'Near Phoenix Marketcity',         'REOPENED',    1, '2026-11-15 09:00:00', '2026-11-21 09:00:00', NULL, 6),                    -- 5
 ('GRV-2026-000011', 4, 5, 6, 5,    NULL, 'Streetlight flickering',         'Streetlight flickering all night near the main gate', 'Near Phoenix Marketcity',         'RESOLVED',    0, '2026-11-10 20:00:00', '2026-11-14 10:00:00', '2026-11-14 10:00:00', 4);   -- 6
INSERT INTO complaint (complaint_no, owner_id, category_id, area_id, department_id, duplicate_of_id, title, description, address, status, reopen_count, created_at, updated_at, resolved_at, version) VALUES
 ('GRV-2026-000002', 5, 2, 2, 2,    1,    'Water leak near depot',          'Same leak on Karve Road wasting a lot of water',      'Karve Road',                      'DUPLICATE',   0, '2026-11-21 12:00:00', '2026-11-22 09:30:00', NULL, 3);                    -- 7

-- Two more Water Supply complaints so the queue ordering can be checked:
-- 000012 is older but within its 10-day SLA; 000013 is newer but overdue (2-day SLA).
INSERT INTO complaint (complaint_no, owner_id, category_id, area_id, department_id, duplicate_of_id, title, description, address, status, reopen_count, created_at, updated_at, resolved_at, version) VALUES
 ('GRV-2026-000012', 6, 3, 2, 2,    NULL, 'Road dug up 50% for pipe work',      'Road dug up for pipe work and never refilled properly', 'Paud Road, Kothrud',              'ASSIGNED',    0, '2026-11-16 10:00:00', '2026-11-16 12:00:00', NULL, 2),  -- 8
 ('GRV-2026-000013', 6, 2, 3, 2,    NULL, 'No water since morning',         'No water supply since morning in the whole lane here', 'ITI Road, Aundh',                 'ASSIGNED',    0, '2026-11-19 07:00:00', '2026-11-19 09:00:00', NULL, 2);  -- 9

INSERT INTO complaint_sequence (seq_year, last_number) VALUES (2026, 13);

-- Timeline of complaint 1 (plus an edit with two revisions while UNDER_REVIEW)
INSERT INTO complaint_event (complaint_id, event_type, from_status, to_status, actor_id, department_id, related_complaint_id, remark, created_at) VALUES
 (1, 'CREATED',            NULL,           'SUBMITTED',    4, NULL, NULL, NULL,                          '2026-11-21 10:00:00'), -- 1
 (1, 'STATUS_CHANGED',     'SUBMITTED',    'UNDER_REVIEW', 1, NULL, NULL, NULL,                          '2026-11-21 10:30:00'), -- 2
 (1, 'EDITED',             NULL,           NULL,           4, NULL, NULL, 'Added the exact spot',        '2026-11-21 10:45:00'), -- 3
 (1, 'STATUS_CHANGED',     'UNDER_REVIEW', 'ASSIGNED',     1, 2,    NULL, NULL,                          '2026-11-21 11:00:00'), -- 4
 (1, 'STATUS_CHANGED',     'ASSIGNED',     'IN_PROGRESS',  2, NULL, NULL, 'Team sent to the site',       '2026-11-22 09:00:00'), -- 5
 (7, 'DUPLICATE_LINKED',   'ASSIGNED',     'DUPLICATE',    2, NULL, 1,    'Same leak as GRV-2026-000001','2026-11-22 09:30:00'), -- 6
 (1, 'DUPLICATE_RECEIVED', NULL,           NULL,           2, NULL, 7,    'GRV-2026-000002 linked as duplicate', '2026-11-22 09:30:00'); -- 7
INSERT INTO complaint_revision (event_id, field_name, old_value, new_value) VALUES
 (3, 'title',   'Pipeline leaking',  'Pipeline leaking on Karve Road'),
 (3, 'address', 'Karve Road',        'Karve Road, near Kothrud Depot');

-- Jayesh watches 000001 (active, from the duplicate); Sneha used to watch 000005 (inactive)
INSERT INTO complaint_watcher (complaint_id, user_id, active, since, updated_at) VALUES
 (1, 5, TRUE,  '2026-11-22 09:30:00', '2026-11-22 09:30:00'),
 (4, 6, FALSE, '2026-11-08 10:00:00', '2026-11-09 10:00:00');

INSERT INTO notification (recipient_id, complaint_id, event_id, message, is_read, created_at, read_at) VALUES
 (4, 1, 1, 'GRV-2026-000001 registered',             TRUE,  '2026-11-21 10:00:00', '2026-11-21 10:05:00'),
 (4, 1, 2, 'GRV-2026-000001 is now Under Review',    FALSE, '2026-11-21 10:30:00', NULL),
 (4, 1, 4, 'GRV-2026-000001 is now Assigned',        FALSE, '2026-11-21 11:00:00', NULL),
 (4, 1, 5, 'GRV-2026-000001 is now In Progress',     FALSE, '2026-11-22 09:00:00', NULL),
 (5, 7, 6, 'GRV-2026-000002 linked to GRV-2026-000001', FALSE, '2026-11-22 09:30:00', NULL),
 (1, 1, 1, 'New complaint GRV-2026-000001',          FALSE, '2026-11-21 10:00:00', NULL);
