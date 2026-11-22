# 00 — Specification Plan

**Project:** Smart Public Grievance and Complaint Management System
**Source:** `docs/Synopsis.pdf` (MCA Sem III mini-project synopsis)
**Approach:** Specification-driven development. We write each spec first, review and freeze it, then build code from it. Every code artifact must trace back to a spec ID.

---

## 1. What the synopsis gives us

| Item | From the synopsis |
|---|---|
| Problem | Citizens can't easily report local civic issues (garbage, water leakage, roads, drainage, streetlights) or follow up on them. |
| Core idea | Web app: citizen submits a complaint (category, title, description, location, photo). The system issues a complaint ID for tracking. Admin assigns it to a department. |
| Actors | Citizen, Administrator. (Department staff is not a login role in the synopsis. See open question Q1.) |
| Modules | M1 Registration/Login · M2 Complaint Registration · M3 Complaint Tracking · M4 Admin Dashboard · M5 Department Management · M6 Reports/Summary · M7 In-app Notifications |
| Statuses | Submitted → Under Review → Assigned → In Progress → Resolved |
| Mandated tech | Java, Servlet/JSP **or Spring Boot**, HTML/CSS/JS/Bootstrap, MySQL, JDBC, Tomcat, Git |
| Out of scope (future) | Google Maps, email/SMS, mobile app, duplicate-complaint detection |

## 2. Spec documents to produce (in order)

Each one builds on the ones before it. We do them one at a time.

| # | Document | Purpose | Key outputs |
|---|---|---|---|
| 01 | **Vision & Scope** | Fix the boundary of the project | Goals, in-scope/out-of-scope, assumptions, constraints |
| 02 | **Glossary & Actors** | Shared vocabulary | Terms (Complaint, Category, Department, Status…), roles and their permissions |
| 03 | **Requirements (SRS)** | What the system must do | `FR-xx` functional requirements per module, `NFR-xx` (security, performance, usability, upload limits) |
| 04 | **Use Cases** | Behaviour per actor | Use-case diagram, `UC-xx` specs (actors, pre/post-conditions, main and alternate flows) |
| 05 | **Domain Model & Business Rules** | Core concepts and rules | Class/domain diagram, complaint **state machine** (allowed transitions and who can make them), `BR-xx` rules (complaint ID format, photo type/size, etc.) |
| 06 | **Flow Diagrams** | How the processes run | Activity diagrams (submit, track, assign, resolve), sequence diagrams, DFD level 0/1 (usually required in MCA reports) |
| 07 | **Architecture & Tech Stack** | How it's built | Layered architecture (Controller → Service → Repository), package structure, ADRs for each key choice |
| 08 | **Database Design** | Persistence | ER diagram, data dictionary, MySQL DDL (`schema.sql` / Flyway migrations), seed data (categories, departments, admin user) |
| 09 | **Web / API Contracts** | Interface between UI and server | For every route: method, URL, role, form fields + validation, success view/redirect, error behaviour. Plus the few JSON endpoints we need (e.g. unread-notification count) |
| 10 | **UI / Screen Spec** | What users see | Screen inventory, navigation map, low-fi wireframes, Thymeleaf layout/fragment plan |
| 11 | **Security Spec** | Access control | Spring Security config, role-to-URL matrix, BCrypt passwords, CSRF, ownership checks, safe file upload |
| 12 | **Validation & Error Handling** | Consistent behaviour | Field rules, error messages, global exception handling, 403/404/500 pages |
| 13 | **Test Spec & Traceability** | Proof it works | Given/When/Then acceptance criteria per UC, test plan (unit, MVC slice, integration), FR → UC → Route → Table → Test matrix |
| 14 | **Build & Deployment** | Running it | Maven build, profiles (dev/prod), `application.properties`, run via `spring-boot:run` or deploy WAR/JAR on Tomcat, file-storage location |

> On "API contracts" with Thymeleaf: the app is server-rendered, so most contracts are **MVC route contracts** (form POST → redirect/view), not REST/JSON. Doc 09 covers both kinds.

## 3. Tech stack

Verified with a test build on 2026-11-22. Full details and reasoning are in **doc 07**.

JDK **25** (LTS) · Spring Boot **4.2.0** (Spring Framework 7.1, Spring Security 7.2, Tomcat 11) · Thymeleaf 3.1.7 + Layout Dialect · Spring JDBC `JdbcClient` · MySQL **8.4 LTS** · Flyway 12.11 · Bootstrap 5.3.8 (WebJars) · JUnit 6 + MockMvc + H2 · Maven · executable JAR.

Note: Spring Boot has no "LTS" label in open source. Each minor release gets about 12 months of OSS support. The JDK is the LTS part.

## 4. Repository layout (target)

```
arya_project/
├── docs/
│   ├── Synopsis.pdf
│   ├── specs/            # 00..14 spec documents (this folder)
│   └── diagrams/         # Mermaid / PlantUML sources + exported PNGs
└── app/                  # Spring Boot project (created after specs 07–09 are frozen)
```

## 5. Working process

1. Draft one spec document.
2. Review it and resolve open questions.
3. Mark it **Frozen**. Changes after that are recorded in a short change log at the bottom of the doc.
4. Go to the next document. Code starts after 08 (DB) and 09 (contracts) are frozen, and is built one module at a time (M1 → M7), each with its tests.

## 6. Decisions log

| ID | Question | Decision (2026-11-22) |
|---|---|---|
| D1 | Department role? | **3 roles:** Citizen, Department Officer, Admin. Officers update the status. The admin assigns complaints and monitors. |
| D2 | Anonymous tracking? | **No.** Only the owner (and watchers, see D4) can view a complaint. Public lookup is a possible future feature. |
| D3 | JPA or JDBC? | **Spring JDBC (`JdbcClient`/`JdbcTemplate`)**. It matches the synopsis's "JDBC" requirement, keeps the SQL explicit and easy to explain, and the schema is small. |
| D4 | Extra statuses? | **Add `REJECTED`, `DUPLICATE`, `CLOSED`.** An officer can mark a complaint as a duplicate of another with a note. The duplicate's owner becomes a **watcher** of the original and receives its updates. |
| D5 | Location? | **Area dropdown + free-text address.** No latitude/longitude for now, but kept easy to add later. |
| D6 | Packaging? | **Executable JAR** with embedded Tomcat. |
| D7 | Versions? | Latest stable versions everywhere, JDK 25 LTS, Spring Boot 4.x (see §3). |
| D8 | Can a citizen reopen a resolved complaint? | **Yes.** The owner can reopen a `RESOLVED` complaint with a reason during the review window. It goes back to the same department as `REOPENED`. |
| D10 | Authorization style? | Roles are enforced on URLs with Spring Security. Access to each complaint (owner, watcher, department, admin) is checked by one central `ComplaintAccessPolicy` in the service layer, and anyone not allowed gets **404**, never 403. |
| D11 | Edit or withdraw after submitting? | **Allowed, with a full audit trail.** The owner can **edit** while the status is `SUBMITTED` or `UNDER_REVIEW`, and **withdraw** until work starts (`SUBMITTED`, `UNDER_REVIEW`, `ASSIGNED`). Every edit is stored as a revision (field, old value, new value, who, when) and shown in the timeline. Withdrawing sets the new final status `WITHDRAWN`. Nothing is ever hard-deleted. |
| D12 | Attachments? | Up to **5 files per complaint**, each **≤ 5 MB**. Allowed types are **JPG, PNG and PDF**, checked by file content, not just the extension. Other types such as Office files or executables are blocked for safety. A removed attachment is soft-deleted and stays in the history. |
| D13 | Cross-department duplicates? | An **Officer** can link duplicates only **within their own department**. Other cases are sent back to the Admin, who can link **across departments**. |
| D14 | Unwatch? | **Yes.** A watcher can **Unwatch**, which stops notifications and removes the complaint from their Watching list. They can still open the original through their own duplicate complaint and **Watch again**. |
| D15 | Areas? | Seven well-known **Pune Municipal Corporation** localities: Shivajinagar, Kothrud, Aundh, Baner, Hadapsar, Viman Nagar, Kasba Peth (see Appendix A). |
| D16 | Citizen identity verification? | **Mobile OTP.** A citizen self-registers and then verifies their mobile with a 6-digit OTP (5-minute expiry, 3 attempts, resend after 60 seconds). Sending goes through an `OtpSender` interface: in the demo the OTP is shown on screen and logged, and a real SMS gateway can replace it later. Unverified citizens can log in but **cannot submit complaints**. Staff (officer and admin) accounts are created by the Admin and count as verified. |
| D9 | How does `RESOLVED` end? | The owner reviews it and either **closes** or **reopens** it. If there is no response within the review window (configurable, default 7 days), a scheduled job **auto-closes** it. |
| D17 | Table and column names? | Singular snake_case tables. No reserved words: the user table is **`app_user`** (`USER` is reserved in H2), the sequence is **`complaint_sequence(seq_year, last_number)`** (`YEAR` reserved in H2, `LAST_VALUE` in MySQL 8), the read flag is `is_read`. |
| D18 | Who sets timestamps? | **The application**, from the injected `Clock` (Asia/Kolkata). Time columns have no DB defaults, and queries take `:now` as a parameter instead of `NOW()`. |
| D19 | How is the complaint number generated safely? | In the submit transaction: `INSERT … ON DUPLICATE KEY UPDATE` to create/lock the year row, then `SELECT … FOR UPDATE`, then `UPDATE`. No year rows are pre-seeded. Tested with 16 parallel clients: no duplicates, gaps or deadlocks. |
| D20 | Extra columns beyond doc 05? | `complaint_event.department_id` (department assigned by T03/T04) and `complaint_event.related_complaint_id` (other side of a duplicate link), so the timeline can show them. `area.description` holds the PMC ward office. |
| D21 | Business rules in the database? | Key invariants are repeated as `CHECK`s (DUPLICATE ⇔ linked, RESOLVED ⇒ resolved_at, department required/forbidden by status, officer ⇔ department, staff verified, event shapes). **Configurable** limits (file size, OTP attempts, max reopens, file count) are checked in code only. |
| D22 | Enforce "one notification per recipient per event"? | Yes: `notification.event_id` + unique key `(event_id, recipient_id)`. |
| D23 | Overdue in SQL? | Filter with `TIMESTAMPADD(DAY, sla_days, created_at) < :now`; return `due_at`. **Days overdue are computed in Java**, because `TIMESTAMPDIFF(DAY)` gives different answers on MySQL and H2. |
| D24 | "Open" vs "closed" on the dashboard (FR-61)? | Open = not final (the five open statuses **plus `RESOLVED`**). Closed = `CLOSED`, `REJECTED`, `DUPLICATE`, `WITHDRAWN`. |
| D25 | Optional list filters? | The repository appends fixed, whitelisted `AND …` fragments only for filters that have a value; values are always bound. Search = exact complaint number or case-insensitive title contains (`LOWER(title) LIKE … ESCAPE '!'`). No full-text index. |
| D26 | Bootstrap admin details? | Created only if **no** `ADMIN` row exists (active or not). New settings `app.admin.name` (default "System Administrator") and `app.admin.mobile` (default 9000000000), because name and mobile are required columns. |
| D27 | How is demo data loaded? | By `DevDataSeeder` (Java, `dev` profile only, one transaction), not Flyway. It skips itself if any complaint exists, uses relative dates from the `Clock`, numbers complaints with the **current** year and sets `complaint_sequence` for that year to 11. |
| D28 | Retention? | Complaints, history, attachments and users are kept forever. A daily `HousekeepingJob` (Could, FR-73) purges notifications older than 90 days and OTP rows older than 30 days. |
| D29 | Character set and case? | Database created with `utf8mb4` / `utf8mb4_0900_ai_ci`. Services check name/email uniqueness case-insensitively so H2 (case-sensitive) behaves like MySQL; the unique keys are the final guard. |

## Status

| Doc | Status |
|---|---|
| 00 Specification Plan | Draft |
| 01 Vision & Scope | Draft |
| 02 Glossary & Actors | Draft |
| 03 Requirements (SRS) | Draft |
| 04 Use Cases | Draft |
| 05 Domain Model & Business Rules | Draft |
| 06 Flow Diagrams | Draft |
| 07 Architecture & Tech Stack | Draft |
| Appendix A Seed & Demo Data | Draft |
| 08 Database Design | Draft |
| 09–14 | Not started |
