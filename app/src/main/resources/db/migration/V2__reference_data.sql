-- =====================================================================
-- V2__reference_data.sql — reference data from Appendix A §1–3
-- Spec: docs/specs/08-database-design.md §4
--
-- Loaded in every profile (dev, test, prod). Ids are NOT given explicitly:
-- they come from AUTO_INCREMENT, and categories find their department by name.
-- This keeps the identity counters correct on both MySQL and H2.
-- The creation timestamp is a fixed literal so the script is deterministic.
-- Users, demo complaints and the complaint_sequence rows are NOT loaded here
-- (AdminBootstrap, DevDataSeeder and ComplaintNumberGenerator do that).
-- =====================================================================

-- Departments (Appendix A §2)
INSERT INTO department (name, description, active, created_at, updated_at) VALUES
    ('Sanitation',       'Garbage collection and street cleaning',        TRUE, '2026-11-22 00:00:00', '2026-11-22 00:00:00'),
    ('Water Supply',     'Water pipelines, leakages and supply timing',   TRUE, '2026-11-22 00:00:00', '2026-11-22 00:00:00'),
    ('Road Maintenance', 'Potholes, damaged roads and footpaths',         TRUE, '2026-11-22 00:00:00', '2026-11-22 00:00:00'),
    ('Drainage',         'Sewer lines, blocked drains and chambers',      TRUE, '2026-11-22 00:00:00', '2026-11-22 00:00:00'),
    ('Electrical',       'Streetlights and public electrical fittings',   TRUE, '2026-11-22 00:00:00', '2026-11-22 00:00:00');

-- Categories with default department and SLA days (Appendix A §3)
INSERT INTO category (name, default_department_id, sla_days, active, created_at, updated_at)
    SELECT 'Garbage', id, 3, TRUE, '2026-11-22 00:00:00', '2026-11-22 00:00:00' FROM department WHERE name = 'Sanitation';
INSERT INTO category (name, default_department_id, sla_days, active, created_at, updated_at)
    SELECT 'Water Leakage', id, 2, TRUE, '2026-11-22 00:00:00', '2026-11-22 00:00:00' FROM department WHERE name = 'Water Supply';
INSERT INTO category (name, default_department_id, sla_days, active, created_at, updated_at)
    SELECT 'Road Damage', id, 10, TRUE, '2026-11-22 00:00:00', '2026-11-22 00:00:00' FROM department WHERE name = 'Road Maintenance';
INSERT INTO category (name, default_department_id, sla_days, active, created_at, updated_at)
    SELECT 'Drainage', id, 5, TRUE, '2026-11-22 00:00:00', '2026-11-22 00:00:00' FROM department WHERE name = 'Drainage';
INSERT INTO category (name, default_department_id, sla_days, active, created_at, updated_at)
    SELECT 'Streetlight', id, 3, TRUE, '2026-11-22 00:00:00', '2026-11-22 00:00:00' FROM department WHERE name = 'Electrical';
INSERT INTO category (name, default_department_id, sla_days, active, created_at, updated_at) VALUES
    ('Other', NULL, 7, TRUE, '2026-11-22 00:00:00', '2026-11-22 00:00:00');

-- Areas: Pune Municipal Corporation localities (Appendix A §1, D15).
-- The description holds the PMC ward office.
INSERT INTO area (name, description, active, created_at, updated_at) VALUES
    ('Shivajinagar', 'PMC ward office: Shivajinagar–Ghole Road',   TRUE, '2026-11-22 00:00:00', '2026-11-22 00:00:00'),
    ('Kothrud',      'PMC ward office: Kothrud–Bavdhan',           TRUE, '2026-11-22 00:00:00', '2026-11-22 00:00:00'),
    ('Aundh',        'PMC ward office: Aundh–Baner',               TRUE, '2026-11-22 00:00:00', '2026-11-22 00:00:00'),
    ('Baner',        'PMC ward office: Aundh–Baner',               TRUE, '2026-11-22 00:00:00', '2026-11-22 00:00:00'),
    ('Hadapsar',     'PMC ward office: Hadapsar–Mundhwa',          TRUE, '2026-11-22 00:00:00', '2026-11-22 00:00:00'),
    ('Viman Nagar',  'PMC ward office: Nagar Road–Wadgaonsheri',   TRUE, '2026-11-22 00:00:00', '2026-11-22 00:00:00'),
    ('Kasba Peth',   'PMC ward office: Kasba–Vishrambaug Wada',    TRUE, '2026-11-22 00:00:00', '2026-11-22 00:00:00');
