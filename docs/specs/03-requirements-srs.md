# 03 — Software Requirements Specification (SRS)

| | |
|---|---|
| **Status** | Draft |
| **Depends on** | 01 Vision & Scope, 02 Glossary & Actors |

**Priority (MoSCoW):** **M** = Must (required for the demo), **S** = Should (planned, can slip), **C** = Could (only if time allows).
Every requirement has a stable ID. Use cases (doc 04), routes (doc 09) and tests (doc 13) refer back to these IDs.

---

## 1. Functional requirements

### M1 — Registration & Login

| ID | Requirement | Pri |
|---|---|:-:|
| FR-01 | A visitor can register as a **Citizen** with full name, email, mobile number, password and confirm password. | M |
| FR-02 | Email must be unique (case-insensitive). The mobile number is 10 digits. The password has at least 8 characters, with at least one letter and one digit. | M |
| FR-03 | Any active user can log in with email and password. After login, users land on their role's home page: Citizen → My Complaints, Officer → Department Queue, Admin → Dashboard. | M |
| FR-04 | Users can log out. The session expires after 30 minutes of inactivity. | M |
| FR-05 | Deactivated users cannot log in and see the generic "invalid credentials or inactive account" message. | M |
| FR-06 | Any logged-in user can change their own password by entering the current password. | S |
| FR-07 | On first startup, the system creates one Admin account from configuration if no admin exists. | M |
| FR-08 | Self-registration always creates a Citizen account. Officer and Admin accounts are never created through registration. | M |
| FR-09 | After registration, the citizen is asked to **verify their mobile number with a 6-digit OTP**. The OTP is valid for 5 minutes and allows 3 wrong attempts, after which a new OTP is needed. Resend is allowed after 60 seconds, at most 5 sends per hour. Mobile numbers are unique across all users. | M |
| FR-09a | An **unverified** citizen can log in and sees a "Verify your mobile number" banner. Opening New Complaint redirects them to the verification page. | M |
| FR-09b | OTP delivery goes through a pluggable `OtpSender`. The demo implementation shows the OTP on the verification page and writes it to the log. A real SMS implementation is future scope. | M |
| FR-09c | Accounts created by the Admin (officers) and the seeded Admin count as verified. The Admin can mark a citizen verified manually. | S |

### M2 — Complaint Registration

| ID | Requirement | Pri |
|---|---|:-:|
| FR-10 | A Citizen can submit a complaint with: **category** (required, active categories only), **title** (5–100 characters), **description** (20–2000 characters), **area** (required, active areas only), **address** (5–255 characters), and **attachments** (optional, 0–5 files). | M |
| FR-11 | Each attachment must be **JPG, PNG or PDF** and **≤ 5 MB**, with **at most 5 active attachments per complaint**. The type is checked from the file's content (magic bytes), not only its extension. If any file is invalid, the whole submission is rejected with a message for that file. | M |
| FR-12 | On submit, the system generates a unique **complaint number** in the format `GRV-YYYY-NNNNNN` (year + a sequence that restarts each year). | M |
| FR-13 | A new complaint starts as `SUBMITTED` and gets its first history entry: "Complaint submitted". | M |
| FR-14 | After submission, a confirmation page shows the complaint number and a link to the complaint. | M |
| FR-15 | Submission notifies the owner ("Complaint GRV-… registered") and all Admins ("New complaint GRV-…"). | M |
| FR-16 | The **owner** can **edit** the category, title, description, area and address, and can add or remove attachments (FR-11 limits apply), while the status is `SUBMITTED` or `UNDER_REVIEW`. An optional "reason for change" can be entered. | M |
| FR-17 | Every edit creates **revision** records (field, old value, new value, editor, timestamp, reason). The timeline shows them as "Edited: Title changed from '…' to '…'". Removed attachments are soft-deleted, still listed as "removed on …", and stay viewable by the Admin. Revisions are never edited or deleted. | M |
| FR-18 | The **owner** can **withdraw** a complaint while it is `SUBMITTED`, `UNDER_REVIEW` or `ASSIGNED`, with a mandatory reason. The status becomes `WITHDRAWN` (final). A complaint that other citizens' duplicates are linked to cannot be withdrawn (BR-47). | M |
| FR-19 | Edit and Withdraw buttons appear only when allowed. A request outside the allowed statuses is refused, even if someone posts the form directly. | M |

### M3 — Complaint Tracking (Citizen)

| ID | Requirement | Pri |
|---|---|:-:|
| FR-20 | **My Complaints** lists the citizen's own complaints (number, title, category, status badge, created date), newest first, paginated, and can be filtered by status. | M |
| FR-21 | **Complaint detail** shows all fields, the attachments (image thumbnails, PDF links), the assigned department and a **timeline** of status changes with dates and remarks. | M |
| FR-22 | **Watching** lists the complaints the citizen watches, with the same columns as FR-20. | M |
| FR-23 | A citizen can only view complaints they own or watch. Any other complaint returns **404 Not Found**, so the system never reveals that a complaint exists. | M |
| FR-24 | A complaint in `DUPLICATE` status shows a banner: "Linked to GRV-… which is already being handled", with a link to the original. | M |
| FR-25 | While a complaint is `RESOLVED`, the **owner** sees **Close** (optional feedback) and **Reopen** (mandatory reason, 10–500 characters) buttons and the date when the review window ends. | M |
| FR-26 | **Close** sets the status to `CLOSED`. **Reopen** sets it to `REOPENED` and keeps the same department. Both are recorded in the history. | M |
| FR-27 | A complaint can be reopened at most **2 times** (configurable). After that, only Close is offered, together with a note to contact the department. | S |
| FR-28 | Watchers can view a complaint and its timeline, but cannot close or reopen it. | M |
| FR-29 | A watcher can **Unwatch** a complaint, which stops its notifications and removes it from Watching. If they own a duplicate linked to it, they can still open it from there and **Watch again**. | M |

### M4 — Admin Complaint Management

| ID | Requirement | Pri |
|---|---|:-:|
| FR-30 | The Admin sees **all complaints**, filterable by status, category, department, area and date range, searchable by complaint number or title, and paginated. | M |
| FR-31 | The Admin can move a `SUBMITTED` complaint to `UNDER_REVIEW`. | M |
| FR-32 | The Admin can **assign** an `UNDER_REVIEW` complaint to a department. The category's default department is pre-selected. The status becomes `ASSIGNED`. | M |
| FR-33 | The Admin can **reassign** a complaint that is `ASSIGNED`, `IN_PROGRESS` or `REOPENED` to another department, with a mandatory remark. | S |
| FR-34 | The Admin can **reject** a complaint that is not final, with a mandatory reason. | M |
| FR-35 | The Admin can **mark a complaint as a duplicate**. The rules are the same as FR-44. | M |
| FR-36 | The Admin can perform any officer status action on any complaint. | S |

### Officer — Department Queue (part of M4/M5)

| ID | Requirement | Pri |
|---|---|:-:|
| FR-40 | An Officer sees the **department queue**: complaints assigned to their department, filterable by status, with overdue complaints first and then oldest first. | M |
| FR-41 | An Officer can move an `ASSIGNED` or `REOPENED` complaint to `IN_PROGRESS`, with a mandatory remark. | M |
| FR-42 | An Officer can move an `IN_PROGRESS` complaint to `RESOLVED`, with a mandatory resolution remark. This starts the review window. | M |
| FR-43 | An Officer can add a progress remark without changing the status. It appears in the timeline. | S |
| FR-44 | An Officer can mark a complaint in their department as a **duplicate of** another complaint, entered by complaint number, with a mandatory note. Rules: see BR-40 to BR-46 in doc 05. In short, the original must be actively handled (`ASSIGNED`, `IN_PROGRESS` or `REOPENED`), and for an Officer both complaints must be in their department. On success, the duplicate becomes `DUPLICATE` and is linked to the original, the duplicate's owner and its watchers become **watchers of the original**, and they are notified. | M |
| FR-45 | An Officer can **reject** a complaint in their department, with a mandatory reason. | M |
| FR-46 | An Officer can **send a complaint back to the Admin** ("wrong department") with a remark. The status returns to `UNDER_REVIEW` and the department is cleared. | S |
| FR-47 | An Officer can only act on complaints of their own department. Any other complaint returns 404. | M |
| FR-48 | Optionally, an Officer can upload up to 3 "work done" attachments when resolving a complaint (same type and size rules as FR-11). | C |

### M5 — Master Data Management (Admin)

| ID | Requirement | Pri |
|---|---|:-:|
| FR-50 | Create, edit and activate/deactivate **departments** (unique name, description). A department that has complaints cannot be deleted, only deactivated. | M |
| FR-51 | Create, edit and activate/deactivate **categories** (unique name, default department, `sla_days` from 1 to 90). | M |
| FR-52 | Create, edit and activate/deactivate **areas** (unique name). | M |
| FR-53 | Create **officer** accounts (name, email, mobile, department, initial password), change their department, and activate or deactivate them. | M |
| FR-54 | Activate or deactivate **citizen** accounts. | S |
| FR-55 | Reset any user's password to a new value. | S |
| FR-56 | Inactive categories, areas and departments are hidden from new-complaint and assignment forms, but still appear on existing complaints. | M |

### M6 — Reports & Dashboard

| ID | Requirement | Pri |
|---|---|:-:|
| FR-60 | The Admin dashboard shows summary cards: Total, Pending (`SUBMITTED` + `UNDER_REVIEW`), Assigned, In Progress, Reopened, Resolved, Closed, Rejected, Duplicate, Withdrawn, **Overdue**. | M |
| FR-61 | It also shows count tables **by category**, **by department** and **by area**, each split into open and closed. | M |
| FR-62 | An **overdue list** shows each complaint, its department, the days elapsed against its SLA days, and its status. | M |
| FR-63 | The Officer home page shows the same cards limited to their own department. | S |
| FR-64 | The dashboard has simple bar or pie charts (Chart.js). | C |
| FR-65 | The filtered complaint list can be exported as CSV. | C |

### M7 — Notifications (in-app)

| ID | Requirement | Pri |
|---|---|:-:|
| FR-70 | The system creates notifications for these events: | M |
| | • Submitted → owner, all admins | |
| | • Assigned / reassigned → owner and watchers, all officers of the department | |
| | • `IN_PROGRESS`, `RESOLVED`, `REJECTED` → owner and watchers | |
| | • Marked duplicate → owner and watchers of the duplicate | |
| | • Reopened → officers of the department, all admins | |
| | • Closed (by the owner or automatically) → owner; officers of the department | |
| | • Sent back to admin → all admins | |
| | • Edited by owner → all admins | |
| | • Withdrawn → all admins; officers of the department, if assigned | |
| FR-71 | The navbar shows a bell icon with the unread count. | M |
| FR-72 | The notification list shows newest first. Clicking an item marks it read and opens the complaint. There is also "Mark all as read". | M |
| FR-73 | Notifications older than 90 days may be purged. | C |

### Cross-cutting

| ID | Requirement | Pri |
|---|---|:-:|
| FR-80 | **Overdue**: a complaint whose status is not `RESOLVED`, `CLOSED`, `REJECTED`, `DUPLICATE` or `WITHDRAWN`, and where `now > created_at + category.sla_days`. Overdue is calculated when queried, not stored. | M |
| FR-81 | **History**: every status change, reassignment, duplicate link and remark is stored with the from-status, to-status, actor (or SYSTEM), timestamp and remark. History is never edited or deleted. | M |
| FR-82 | **Auto-close job**: a scheduled job (hourly, configurable) closes every `RESOLVED` complaint whose review window has expired. The actor is SYSTEM and the remark is "Auto-closed: no response within N days". It sends the FR-70 notifications. | M |
| FR-83 | Status changes are only allowed according to the state machine in doc 05. Invalid transitions are refused with a clear message. | M |
| FR-84 | Two users acting on the same complaint at once must not corrupt it. The second, conflicting action is refused with "This complaint was updated by someone else, please refresh". | S |

---

## 2. Non-functional requirements

### Security
| ID | Requirement |
|---|---|
| NFR-01 | Passwords are hashed with BCrypt, never stored or logged in plain text. |
| NFR-02 | Access is controlled per role by URL **and** per complaint (owner, watcher, department, admin) in the service layer. |
| NFR-03 | CSRF protection is on for all state-changing forms. Thymeleaf adds the token automatically. |
| NFR-04 | All SQL uses parameterised queries. Strings are never concatenated into SQL. |
| NFR-05 | Uploaded files are stored **outside** the application's static folder with a random UUID filename. They are served only through a controller that applies the same access check as the complaint. |
| NFR-06 | Error pages never show stack traces or SQL. Errors are logged on the server with a reference ID. |
| NFR-07 | Thymeleaf output is escaped by default (`th:text`). Unescaped output (`th:utext`) is never used for user-entered content. |

### Performance & capacity
| ID | Requirement |
|---|---|
| NFR-10 | List and dashboard pages respond in under 2 seconds with 10,000 complaints on the reference laptop (Core i3, 4 GB RAM). |
| NFR-11 | All lists are paginated, with a default page size of 10 and a maximum of 50. |
| NFR-12 | Database indexes exist on the columns used for filtering: status, department, category, area, owner and created_at. |

### Usability
| ID | Requirement |
|---|---|
| NFR-20 | The layout is responsive with Bootstrap 5 and works on mobile screens (≥ 360 px) and desktop. |
| NFR-21 | Each status has its own colour badge, used the same way across all pages. |
| NFR-22 | Validation errors appear next to the field, and the form keeps what the user typed. |
| NFR-23 | Dates are shown as `dd-MMM-yyyy hh:mm a` in the Asia/Kolkata time zone. |

### Reliability & data integrity
| ID | Requirement |
|---|---|
| NFR-30 | A status change, its history row and its notifications are saved in **one transaction**. Either all of them are saved, or none are. |
| NFR-31 | The database schema is versioned with Flyway migrations. The app creates or upgrades the schema on startup. |
| NFR-32 | Foreign keys and unique constraints are enforced in the database, not only in code. |

### Maintainability & portability
| ID | Requirement |
|---|---|
| NFR-40 | Layered design: Controller → Service → Repository (JDBC), with packages by feature (doc 07). |
| NFR-41 | Service-layer rules (state machine, access checks, duplicate linking) are covered by automated tests. |
| NFR-42 | The app runs with `java -jar` on Windows or Linux with only JDK 25 and MySQL installed. Configuration comes from `application.properties` and can be overridden by environment variables. |
| NFR-43 | Logging uses SLF4J/Logback at INFO by default, and every status change is logged. |

---

## 3. Configurable parameters

| Key | Default | Used by |
|---|---|---|
| `app.complaint.review-window-days` | 7 | FR-25, FR-82 |
| `app.complaint.max-reopens` | 2 | FR-27 |
| `app.complaint.auto-close-cron` | `0 0 * * * *` (hourly) | FR-82 |
| `app.upload.max-file-size` | 5MB | FR-11 |
| `app.upload.max-files-per-complaint` | 5 | FR-11 |
| `app.upload.allowed-types` | `image/jpeg, image/png, application/pdf` | FR-11 |
| `spring.servlet.multipart.max-request-size` | 26MB | FR-11 (5 × 5 MB + form) |
| `app.upload.dir` | `./uploads` | NFR-05 |
| `app.admin.email` / `app.admin.password` | set at deployment time | FR-07 |
| `app.admin.name` / `app.admin.mobile` | System Administrator / 9000000000 | FR-07 (D26) |
| `server.servlet.session.timeout` | 30m | FR-04 |
| `app.otp.length` / `app.otp.ttl` | 6 / 5m | FR-09 |
| `app.otp.max-attempts` / `app.otp.resend-cooldown` / `app.otp.max-sends-per-hour` | 3 / 60s / 5 | FR-09 |
| `app.otp.sender` | `demo` (show on screen + log) | FR-09b |

## 4. Seed data (initial)

- **Departments:** Sanitation, Water Supply, Road Maintenance, Drainage, Electrical.
- **Categories → default department (SLA days):** Garbage → Sanitation (3), Water Leakage → Water Supply (2), Road Damage → Road Maintenance (10), Drainage → Drainage (5), Streetlight → Electrical (3), Other → *none, admin decides* (7).
- **Areas:** Shivajinagar, Kothrud, Aundh, Baner, Hadapsar, Viman Nagar, Kasba Peth (Pune, PMC).
- **Users:** one Admin from configuration, plus demo officers, citizens and sample complaints in the `dev` profile only. The full list is in **Appendix A**.

## Change log
| Date | Change |
|---|---|
| 2026-11-22 | Initial draft |
| 2026-11-22 | FR-09 to FR-09c mobile OTP verification (D16); Pune areas (D15) |
| 2026-11-22 | FR-29 Unwatch; duplicate and withdraw rules point to doc 05 (D13, D14) |
| 2026-11-22 | Multiple attachments (JPG/PNG/PDF, ≤ 5 MB, max 5); edit and withdraw with revisions (FR-16 to FR-19) |
| 2026-11-22 | Added `app.admin.name` / `app.admin.mobile` settings (doc 08, D26) |
