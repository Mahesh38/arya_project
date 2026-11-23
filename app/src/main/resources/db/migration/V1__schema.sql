-- =====================================================================
-- V1__schema.sql — Smart Public Grievance and Complaint Management System
-- Spec: docs/specs/08-database-design.md
--
-- Portable SQL: runs unchanged on MySQL 8.x (8.0.16+, target 8.4 LTS)
-- and on H2 2.x in MySQL mode (ADR-13). Rules followed:
--   * no ENUM, no ON UPDATE CURRENT_TIMESTAMP, no table options
--   * statuses and roles are VARCHAR + CHECK
--   * no reserved words as identifiers (user, year, value, last_value, read)
--   * timestamps are DATETIME in Asia/Kolkata, always supplied by the app
--     from the injected Clock (ADR-11, BR-74), so they have no DB default
--   * no CHECK on AUTO_INCREMENT columns or on FK columns with actions
-- The database itself is created with utf8mb4 (doc 14), so tables inherit it.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Master data
-- ---------------------------------------------------------------------

CREATE TABLE department (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    name         VARCHAR(100) NOT NULL,
    description  VARCHAR(255),
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   DATETIME     NOT NULL,
    updated_at   DATETIME     NOT NULL,
    CONSTRAINT pk_department PRIMARY KEY (id),
    CONSTRAINT uq_department_name UNIQUE (name),
    CONSTRAINT ck_department_name CHECK (CHAR_LENGTH(TRIM(name)) >= 2)
);

CREATE TABLE category (
    id                     BIGINT       NOT NULL AUTO_INCREMENT,
    name                   VARCHAR(100) NOT NULL,
    default_department_id  BIGINT,
    sla_days               INT          NOT NULL,
    active                 BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at             DATETIME     NOT NULL,
    updated_at             DATETIME     NOT NULL,
    CONSTRAINT pk_category PRIMARY KEY (id),
    CONSTRAINT uq_category_name UNIQUE (name),
    CONSTRAINT fk_category_default_department FOREIGN KEY (default_department_id) REFERENCES department (id),
    CONSTRAINT ck_category_name CHECK (CHAR_LENGTH(TRIM(name)) >= 2),
    CONSTRAINT ck_category_sla_days CHECK (sla_days BETWEEN 1 AND 90)
);

CREATE TABLE area (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    name         VARCHAR(100) NOT NULL,
    description  VARCHAR(255),
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   DATETIME     NOT NULL,
    updated_at   DATETIME     NOT NULL,
    CONSTRAINT pk_area PRIMARY KEY (id),
    CONSTRAINT uq_area_name UNIQUE (name),
    CONSTRAINT ck_area_name CHECK (CHAR_LENGTH(TRIM(name)) >= 2)
);

-- ---------------------------------------------------------------------
-- Users and OTP
-- ---------------------------------------------------------------------

CREATE TABLE app_user (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    full_name        VARCHAR(100) NOT NULL,
    email            VARCHAR(254) NOT NULL,
    mobile           VARCHAR(10)  NOT NULL,
    password_hash    VARCHAR(100) NOT NULL,
    role             VARCHAR(10)  NOT NULL,
    department_id    BIGINT,
    active           BOOLEAN      NOT NULL DEFAULT TRUE,
    mobile_verified  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at       DATETIME     NOT NULL,
    updated_at       DATETIME     NOT NULL,
    version          INT          NOT NULL DEFAULT 0,
    CONSTRAINT pk_app_user PRIMARY KEY (id),
    CONSTRAINT uq_app_user_email UNIQUE (email),
    CONSTRAINT uq_app_user_mobile UNIQUE (mobile),
    CONSTRAINT fk_app_user_department FOREIGN KEY (department_id) REFERENCES department (id),
    CONSTRAINT ck_app_user_role CHECK (role IN ('CITIZEN', 'OFFICER', 'ADMIN')),
    CONSTRAINT ck_app_user_email_lower CHECK (email = LOWER(email)),
    CONSTRAINT ck_app_user_mobile CHECK (CHAR_LENGTH(mobile) = 10),
    CONSTRAINT ck_app_user_officer_dept CHECK (
        (role = 'OFFICER' AND department_id IS NOT NULL)
        OR (role <> 'OFFICER' AND department_id IS NULL)),
    CONSTRAINT ck_app_user_staff_verified CHECK (role = 'CITIZEN' OR mobile_verified = TRUE),
    CONSTRAINT ck_app_user_version CHECK (version >= 0)
);

CREATE INDEX ix_app_user_role_active ON app_user (role, active);
CREATE INDEX ix_app_user_dept_role_active ON app_user (department_id, role, active);

CREATE TABLE mobile_otp (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL,
    otp_hash    VARCHAR(100) NOT NULL,
    sent_at     DATETIME     NOT NULL,
    expires_at  DATETIME     NOT NULL,
    attempts    INT          NOT NULL DEFAULT 0,
    used        BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_mobile_otp PRIMARY KEY (id),
    CONSTRAINT fk_mobile_otp_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT ck_mobile_otp_attempts CHECK (attempts >= 0),
    CONSTRAINT ck_mobile_otp_expiry CHECK (expires_at > sent_at)
);

CREATE INDEX ix_mobile_otp_user_sent ON mobile_otp (user_id, sent_at);

-- ---------------------------------------------------------------------
-- Complaint number sequence (BR-10, BR-11)
-- One row per year; locked with SELECT ... FOR UPDATE in the submit transaction.
-- ---------------------------------------------------------------------

CREATE TABLE complaint_sequence (
    seq_year     INT NOT NULL,
    last_number  INT NOT NULL DEFAULT 0,
    CONSTRAINT pk_complaint_sequence PRIMARY KEY (seq_year),
    CONSTRAINT ck_complaint_sequence_year CHECK (seq_year BETWEEN 2000 AND 9999),
    CONSTRAINT ck_complaint_sequence_number CHECK (last_number BETWEEN 0 AND 999999)
);

-- ---------------------------------------------------------------------
-- Complaints
-- ---------------------------------------------------------------------

CREATE TABLE complaint (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    complaint_no     VARCHAR(20)   NOT NULL,
    owner_id         BIGINT        NOT NULL,
    category_id      BIGINT        NOT NULL,
    area_id          BIGINT        NOT NULL,
    department_id    BIGINT,
    duplicate_of_id  BIGINT,
    title            VARCHAR(100)  NOT NULL,
    description      VARCHAR(2000) NOT NULL,
    address          VARCHAR(255)  NOT NULL,
    status           VARCHAR(20)   NOT NULL,
    reopen_count     INT           NOT NULL DEFAULT 0,
    created_at       DATETIME      NOT NULL,
    updated_at       DATETIME      NOT NULL,
    resolved_at      DATETIME,
    version          INT           NOT NULL DEFAULT 0,
    CONSTRAINT pk_complaint PRIMARY KEY (id),
    CONSTRAINT uq_complaint_no UNIQUE (complaint_no),
    CONSTRAINT fk_complaint_owner FOREIGN KEY (owner_id) REFERENCES app_user (id),
    CONSTRAINT fk_complaint_category FOREIGN KEY (category_id) REFERENCES category (id),
    CONSTRAINT fk_complaint_area FOREIGN KEY (area_id) REFERENCES area (id),
    CONSTRAINT fk_complaint_department FOREIGN KEY (department_id) REFERENCES department (id),
    CONSTRAINT fk_complaint_duplicate_of FOREIGN KEY (duplicate_of_id) REFERENCES complaint (id),
    CONSTRAINT ck_complaint_status CHECK (status IN (
        'SUBMITTED', 'UNDER_REVIEW', 'ASSIGNED', 'IN_PROGRESS', 'RESOLVED',
        'REOPENED', 'CLOSED', 'REJECTED', 'DUPLICATE', 'WITHDRAWN')),
    CONSTRAINT ck_complaint_no_format CHECK (CHAR_LENGTH(complaint_no) = 15 AND complaint_no LIKE 'GRV-____-______'),
    CONSTRAINT ck_complaint_title CHECK (CHAR_LENGTH(title) >= 5),
    CONSTRAINT ck_complaint_description CHECK (CHAR_LENGTH(description) >= 20),
    CONSTRAINT ck_complaint_address CHECK (CHAR_LENGTH(address) >= 5),
    CONSTRAINT ck_complaint_reopen_count CHECK (reopen_count >= 0),
    CONSTRAINT ck_complaint_version CHECK (version >= 0),
    -- DUPLICATE <=> linked to an original (BR-44)
    CONSTRAINT ck_complaint_duplicate CHECK (
        (status = 'DUPLICATE' AND duplicate_of_id IS NOT NULL)
        OR (status <> 'DUPLICATE' AND duplicate_of_id IS NULL)),
    -- RESOLVED always has a resolution time (BR-30, BR-33)
    CONSTRAINT ck_complaint_resolved_at CHECK (status <> 'RESOLVED' OR resolved_at IS NOT NULL),
    -- Work statuses need a department; pre-assignment statuses must not have one (T03, T10)
    CONSTRAINT ck_complaint_dept_required CHECK (
        status NOT IN ('ASSIGNED', 'IN_PROGRESS', 'RESOLVED', 'REOPENED') OR department_id IS NOT NULL),
    CONSTRAINT ck_complaint_dept_forbidden CHECK (
        status NOT IN ('SUBMITTED', 'UNDER_REVIEW') OR department_id IS NULL),
    CONSTRAINT ck_complaint_updated CHECK (updated_at >= created_at)
);

-- My Complaints (FR-20): owner, newest first
CREATE INDEX ix_complaint_owner_created ON complaint (owner_id, created_at);
-- All Complaints status filter, dashboard counts, auto-close (FR-30, FR-60, FR-82)
CREATE INDEX ix_complaint_status_created ON complaint (status, created_at);
CREATE INDEX ix_complaint_status_resolved ON complaint (status, resolved_at);
-- Department Queue and officer dashboard (FR-40, FR-63)
CREATE INDEX ix_complaint_dept_status_created ON complaint (department_id, status, created_at);
-- Category / area filters and group-by (FR-30, FR-61)
CREATE INDEX ix_complaint_category_status ON complaint (category_id, status);
CREATE INDEX ix_complaint_area_status ON complaint (area_id, status);
-- Date-range filter and default "newest first" sort (FR-30)
CREATE INDEX ix_complaint_created ON complaint (created_at);
-- Duplicates of an original (BR-47, BR-60)
CREATE INDEX ix_complaint_duplicate_of ON complaint (duplicate_of_id);

-- ---------------------------------------------------------------------
-- History (append-only, BR-72)
-- ---------------------------------------------------------------------

CREATE TABLE complaint_event (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    complaint_id          BIGINT       NOT NULL,
    event_type            VARCHAR(20)  NOT NULL,
    from_status           VARCHAR(20),
    to_status             VARCHAR(20),
    actor_id              BIGINT,
    department_id         BIGINT,
    related_complaint_id  BIGINT,
    remark                VARCHAR(500),
    created_at            DATETIME     NOT NULL,
    CONSTRAINT pk_complaint_event PRIMARY KEY (id),
    CONSTRAINT fk_event_complaint FOREIGN KEY (complaint_id) REFERENCES complaint (id),
    CONSTRAINT fk_event_actor FOREIGN KEY (actor_id) REFERENCES app_user (id),
    CONSTRAINT fk_event_department FOREIGN KEY (department_id) REFERENCES department (id),
    CONSTRAINT fk_event_related FOREIGN KEY (related_complaint_id) REFERENCES complaint (id),
    CONSTRAINT ck_event_type CHECK (event_type IN (
        'CREATED', 'STATUS_CHANGED', 'REASSIGNED', 'REMARK', 'EDITED',
        'DUPLICATE_LINKED', 'DUPLICATE_RECEIVED', 'AUTO_CLOSED')),
    CONSTRAINT ck_event_from_status CHECK (from_status IS NULL OR from_status IN (
        'SUBMITTED', 'UNDER_REVIEW', 'ASSIGNED', 'IN_PROGRESS', 'RESOLVED',
        'REOPENED', 'CLOSED', 'REJECTED', 'DUPLICATE', 'WITHDRAWN')),
    CONSTRAINT ck_event_to_status CHECK (to_status IS NULL OR to_status IN (
        'SUBMITTED', 'UNDER_REVIEW', 'ASSIGNED', 'IN_PROGRESS', 'RESOLVED',
        'REOPENED', 'CLOSED', 'REJECTED', 'DUPLICATE', 'WITHDRAWN')),
    -- Status-changing events carry both ends; others carry neither (CREATED: to only)
    CONSTRAINT ck_event_status_pair CHECK (
        (event_type = 'CREATED' AND from_status IS NULL AND to_status = 'SUBMITTED')
        OR (event_type IN ('STATUS_CHANGED', 'REASSIGNED', 'DUPLICATE_LINKED', 'AUTO_CLOSED')
            AND from_status IS NOT NULL AND to_status IS NOT NULL)
        OR (event_type IN ('REMARK', 'EDITED', 'DUPLICATE_RECEIVED')
            AND from_status IS NULL AND to_status IS NULL)),
    -- Only the system auto-closes; every other event has a human actor
    CONSTRAINT ck_event_actor CHECK (
        (event_type = 'AUTO_CLOSED' AND actor_id IS NULL)
        OR (event_type <> 'AUTO_CLOSED' AND actor_id IS NOT NULL)),
    CONSTRAINT ck_event_related CHECK (
        event_type NOT IN ('DUPLICATE_LINKED', 'DUPLICATE_RECEIVED') OR related_complaint_id IS NOT NULL)
);

-- Timeline of one complaint in order (FR-21)
CREATE INDEX ix_event_complaint_created ON complaint_event (complaint_id, created_at, id);
CREATE INDEX ix_event_actor ON complaint_event (actor_id);

CREATE TABLE complaint_revision (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    event_id    BIGINT        NOT NULL,
    field_name  VARCHAR(30)   NOT NULL,
    old_value   VARCHAR(2000),
    new_value   VARCHAR(2000),
    CONSTRAINT pk_complaint_revision PRIMARY KEY (id),
    CONSTRAINT fk_revision_event FOREIGN KEY (event_id) REFERENCES complaint_event (id),
    CONSTRAINT ck_revision_field CHECK (field_name IN (
        'category', 'title', 'description', 'area', 'address', 'attachment')),
    CONSTRAINT ck_revision_changed CHECK (old_value IS NOT NULL OR new_value IS NOT NULL)
);

CREATE INDEX ix_revision_event ON complaint_revision (event_id);

-- ---------------------------------------------------------------------
-- Attachments (metadata only; files live on disk, ADR-06)
-- ---------------------------------------------------------------------

CREATE TABLE attachment (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    complaint_id   BIGINT       NOT NULL,
    purpose        VARCHAR(12)  NOT NULL,
    original_name  VARCHAR(255) NOT NULL,
    stored_name    VARCHAR(100) NOT NULL,
    content_type   VARCHAR(50)  NOT NULL,
    size_bytes     BIGINT       NOT NULL,
    uploaded_by    BIGINT       NOT NULL,
    uploaded_at    DATETIME     NOT NULL,
    deleted        BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_by     BIGINT,
    deleted_at     DATETIME,
    CONSTRAINT pk_attachment PRIMARY KEY (id),
    CONSTRAINT uq_attachment_stored_name UNIQUE (stored_name),
    CONSTRAINT fk_attachment_complaint FOREIGN KEY (complaint_id) REFERENCES complaint (id),
    CONSTRAINT fk_attachment_uploaded_by FOREIGN KEY (uploaded_by) REFERENCES app_user (id),
    CONSTRAINT fk_attachment_deleted_by FOREIGN KEY (deleted_by) REFERENCES app_user (id),
    CONSTRAINT ck_attachment_purpose CHECK (purpose IN ('COMPLAINT', 'RESOLUTION')),
    CONSTRAINT ck_attachment_type CHECK (content_type IN ('image/jpeg', 'image/png', 'application/pdf')),
    CONSTRAINT ck_attachment_size CHECK (size_bytes > 0),
    CONSTRAINT ck_attachment_deleted CHECK (
        (deleted = FALSE AND deleted_at IS NULL AND deleted_by IS NULL)
        OR (deleted = TRUE AND deleted_at IS NOT NULL AND deleted_by IS NOT NULL))
);

-- Active attachments of a complaint by purpose (FR-21, BR-23 count)
CREATE INDEX ix_attachment_complaint ON attachment (complaint_id, purpose, deleted);

-- ---------------------------------------------------------------------
-- Watchers (D4, D14, BR-44, BR-50..52)
-- ---------------------------------------------------------------------

CREATE TABLE complaint_watcher (
    complaint_id  BIGINT   NOT NULL,
    user_id       BIGINT   NOT NULL,
    active        BOOLEAN  NOT NULL DEFAULT TRUE,
    since         DATETIME NOT NULL,
    updated_at    DATETIME NOT NULL,
    CONSTRAINT pk_complaint_watcher PRIMARY KEY (complaint_id, user_id),
    CONSTRAINT fk_watcher_complaint FOREIGN KEY (complaint_id) REFERENCES complaint (id),
    CONSTRAINT fk_watcher_user FOREIGN KEY (user_id) REFERENCES app_user (id)
);

-- Watching list of a citizen (FR-22) and canView check (BR-60)
CREATE INDEX ix_watcher_user_active ON complaint_watcher (user_id, active, complaint_id);

-- ---------------------------------------------------------------------
-- Notifications (M7)
-- ---------------------------------------------------------------------

CREATE TABLE notification (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    recipient_id  BIGINT       NOT NULL,
    complaint_id  BIGINT,
    event_id      BIGINT,
    message       VARCHAR(300) NOT NULL,
    is_read       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at    DATETIME     NOT NULL,
    read_at       DATETIME,
    CONSTRAINT pk_notification PRIMARY KEY (id),
    CONSTRAINT fk_notification_recipient FOREIGN KEY (recipient_id) REFERENCES app_user (id),
    CONSTRAINT fk_notification_complaint FOREIGN KEY (complaint_id) REFERENCES complaint (id),
    CONSTRAINT fk_notification_event FOREIGN KEY (event_id) REFERENCES complaint_event (id),
    -- At most one notification per recipient per event (BR-68)
    CONSTRAINT uq_notification_event_recipient UNIQUE (event_id, recipient_id),
    CONSTRAINT ck_notification_read CHECK (
        (is_read = FALSE AND read_at IS NULL) OR (is_read = TRUE AND read_at IS NOT NULL))
);

-- Bell count (FR-71) and list newest first (FR-72)
CREATE INDEX ix_notification_recipient_read ON notification (recipient_id, is_read);
CREATE INDEX ix_notification_recipient_created ON notification (recipient_id, created_at);
-- Purge of old rows (FR-73)
CREATE INDEX ix_notification_created ON notification (created_at);
