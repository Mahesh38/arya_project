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
| 13 | **Test Spec & Traceability** | Proof it works | Given/When/Then acceptance criteria per UC, test plan (unit, MVC slice, integration), FR → UC → Route → Table → Test matrix; the full catalogue is the appendix `13a-test-catalogue.md` |
| 14 | **Build & Deployment** | Running it | Maven build, profiles (dev/prod), `application.properties`, run via `spring-boot:run` or deploy WAR/JAR on Tomcat, file-storage location |

> On "API contracts" with Thymeleaf: the app is server-rendered, so most contracts are **MVC route contracts** (form POST → redirect/view), not REST/JSON. Doc 09 covers both kinds.

## 3. Tech stack

Verified with a test build on 2026-11-22. Full details and reasoning are in **doc 07**.

JDK **25** (LTS) · Spring Boot **4.2.0** (Spring Framework 7.1, Spring Security 7.2, Tomcat 11) · Thymeleaf 3.1.7 + Layout Dialect · Spring JDBC `JdbcClient` · MySQL **8.4 LTS** · Flyway 12.11 · Bootstrap 5.3.8, Bootstrap Icons 1.13.2, Chart.js 4.6.1 (WebJars) · Actuator (health only) · JUnit 6 + MockMvc + H2 · Playwright 1.65 · Maven 3.9.16 (wrapper) · executable JAR. Build and deployment: **doc 14**.

Note: Spring Boot has no "LTS" label in open source. Each minor release gets about 12 months of OSS support. The JDK is the LTS part.

## 4. Repository layout (target)

```
arya_project/
├── docs/
│   ├── Synopsis.pdf
│   ├── specs/            # 00..14 spec documents (this folder), drafts, verify/ check scripts
│   ├── db/               # migration/ (V1, V2) and verify/ (doc 08)
│   ├── build/            # pom.xml, properties, logback and config-class drafts (doc 14)
│   ├── deploy/           # SQL, run/backup scripts, systemd, Nginx, Docker (doc 14)
│   ├── ci/  e2e/         # CI workflow + Dependabot drafts, test report; Playwright drafts (doc 14)
│   └── diagrams/         # Mermaid / PlantUML sources + exported PNGs
└── app/                  # Spring Boot project (created in milestone M0, doc 14 §3, §11)
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
| D30 | URL scheme? (doc 09) | Role prefixes `/citizen`, `/officer`, `/admin`; shared `/complaints/{no}`, `/attachments/{id}`, `/notifications`, `/account`; `/verify-mobile` is CITIZEN-only. Complaints are always addressed by `complaintNo` (`{no:GRV-\d{4}-\d{6}}`, no match → 404); attachments, notifications, master data and users by numeric id, always access-checked. |
| D31 | How are actions with input shown? | Each action with input has its own small **GET form page** (one shared template `complaint/action-form`) and a **POST**. Actions without input (start review, unwatch, watch again) are POST buttons on the detail page. No modals are required, so everything works without JavaScript. |
| D32 | Workflow URLs for officer and admin? | `/{officer\|admin}/complaints/{no}/{action}`, both calling the same service method. The admin also gets start / remark / resolve (FR-36). Citizen close and reopen go to a new `CitizenWorkflowController`; watch / unwatch live under `/citizen/complaints/{no}/…`. |
| D33 | Paging and sorting parameters? | `page` (1-based, default 1), `size` (default 10, clamped 1..50). No `sort` parameter: each list has a fixed order. Unconvertible filter values are ignored, not errors; a page past the end shows the last page. |
| D34 | O1: Department Queue default filter? | `ASSIGNED`, `IN_PROGRESS`, `REOPENED` **and `RESOLVED`** (group `ACTIVE`), overdue first then oldest first. RESOLVED rows show "Awaiting citizen until …" and no buttons. Filter also offers each status and `ALL`. |
| D35 | Status groups in filters? | The `status` parameter takes a status or a group: `PENDING`, `ACTIVE`, `NOT_FINAL` (D24 "open"), `FINAL`, `ALL`. The repository uses `c.status IN (:statuses)`. All Complaints also gets `overdue=true` and `departmentId=NONE`, so every dashboard card and count can link to a filtered list. |
| D36 | Where is the 5 MB file limit enforced? | In `FileStorageService` (from `app.upload.max-file-size`), so the error is a normal field error. `spring.servlet.multipart.max-file-size` is raised to the request limit (26 MB); only requests above 26 MB hit the global handler (redirect + flash). |
| D37 | JSON endpoints? | Exactly one: `GET /notifications/unread-count` → `{"unread": n}`, 401 JSON when not logged in. Fetched on `pageshow` (back-forward cache) and `visibilitychange`, **never polled**, so it cannot defeat the 30-minute idle timeout. Chart data is inlined in the dashboard page. |
| D38 | Opening a notification? | `POST /notifications/{id}/open` marks it read and redirects to the complaint; GET requests never change data (except D41). |
| D39 | Allowed actions? | One pure function `TransitionRules.decide(action, actor, facts, now)` drives both the buttons (`allowedActions` → `Set<Action>`) and the POST check (`require`). The web helper `ActionLinks` turns the set into buttons; templates use `c.can('…')` and never test status or role for actions. Explainable refusals (reopen limit, BR-47, review period over) are shown as notices. |
| D40 | Error responses? | Validation and uniqueness → 200 same view with field errors; business rule, wrong status, concurrent update → 302 back (detail page or list) + `flashError`; not visible / unknown → 404; wrong role → 403; JSON → 401. |
| D41 | When is an OTP issued? | After registration, on Resend, and on `GET /verify-mobile` only when the user has no usable OTP and the limits allow. The demo code is shown only in the response that issued it (model or `demoOtp` flash) and in the log. |
| D42 | Deactivating a department used as a default? | Refused while it is the default department of an **active** category ("Change that category first"), so no category points to an inactive default (UC-40). |
| D43 | CSV export (FR-65, Could)? | Same filters as All Complaints, UTF-8 with BOM, at most 5,000 rows (`app.export.max-rows`); cells starting with `=`, `+`, `-` or `@` are prefixed with `'` (CSV injection). |
| D44 | Attachment response? | Stored content type, `Content-Disposition: inline` (or `attachment` with `?download=true`) with ASCII `filename` and UTF-8 `filename*`, `nosniff`, Spring Security's no-cache headers, streamed from disk. Deleted files: admins only. |
| D45 | Admin accounts? | No UI to create or manage admins; the bootstrap admin is the only one (FR-07) and uses Change password. BR-82 stays enforced in the service. |
| D46 | After registration? | The new citizen is logged in automatically and sent to `/verify-mobile` (UC-01 step 5). |
| D47 | Status badge colours (K9, doc 10)? | One CSS class per status (`status-in-progress` …) with **icon + label**, never colour alone. Pending = pale (grey, light blue), active = strong (blue Assigned, yellow In Progress, orange Reopened, green Resolved), final = dark or muted (grey Closed, red Rejected, purple Duplicate, dashed outline Withdrawn). Group chips for `PENDING`/`ACTIVE`/`NOT_FINAL`/`FINAL`/`ALL`. All pairs ≥ 4.5:1 contrast (checked). Labels, classes and icons live in `ComplaintStatus`. |
| D48 | O2 / K6: officer name or role label in the timeline? | **Citizens see the department, never a staff name:** officer actions show "{Department} Dept." (department in charge at that moment, derived by `TimelineService`), admin actions "Grievance Cell", the owner's own actions "You", and for watchers the owner is "The complainant". Officers and admins see full names. Protects staff and the owner's privacy; accountability stays with the department, and `actor_id` is still stored and shown to staff. |
| D49 | JavaScript? | Every core flow works **without JavaScript**. `app.js` only adds confirm prompts on the three POST-only buttons, submit-disable ("Saving…"), file pre-check, character counters, bell refresh and (dashboard) Chart.js. A `no-js` class and `.no-js` CSS rules keep the navbar usable without JS (the original `<noscript>` style was replaced by D64). HTML validation uses `required`/`maxlength` only, so server messages are the ones shown. |
| D50 | Date formatting? | One `@fmt` bean (`DisplayFormat`) with **`Locale.ENGLISH`**: `dd-MMM-yyyy hh:mm a` and `dd-MMM-yyyy`, IST (`en-IN` prints "pm" in lower case on JDK 25). Templates never format dates themselves. |
| D51 | Complaint lists on phones? | Stacked list below `md`, table from `md` up, from the same fragment. **No action buttons in lists**: all actions are on the detail page, so the allowed-actions rules stay in one place. |
| D52 | Template set? | 28 screen templates (doc 10 §2). No separate `complaint/duplicate-confirm` (it is step 2 of `complaint/action-form`); master-data forms use singular names (`admin/department-form` …); new `error/4xx` for other 4xx errors (400, 405). |
| D53 | Navigation? | Navbar per role (citizen: My Complaints, Watching, New Complaint; officer: Department Queue; admin: Dashboard, All Complaints, Master data ▾, Users ▾), bell always visible next to the toggler and linking to the list (no dropdown preview), user menu with Change password and a POST Log out, breadcrumbs on pages two levels deep. New global model attribute `activeMenu` from `GlobalModelAdvice`. The verify banner is shown by the layout on every citizen page while unverified. |
| D54 | Action button styles? | `ActionLink.style` ∈ `primary` (forward step), `success` (Close, Resolve), `secondary` (Edit, Send back, Unwatch, Watch, Mark duplicate), `danger` (Withdraw, Reject). Danger buttons are outlined on the detail page and solid only on their own confirmation page. |
| D55 | Duplicate links in the timeline? | On the original, staff see "GRV-B linked as a duplicate" with a link; citizens see "Another complaint about the same problem was linked to this one" without the number. Long description edits are shown in a `<details>` element (works without JS). |
| D56 | Login brute-force protection? (doc 11) | In-memory `LoginAttemptService`: 5 failures per email in 15 min (also for unknown emails) or 20 per IP lock that key for 15 min (`/login?locked`); never permanent; the admin's password reset clears it; registration limited to 10 per IP per hour. One instance assumed. |
| D57 | Order of login checks? | Account-status checks (inactive) run **after** the password check, so unknown, wrong-password and inactive accounts cost the same and give the same `/login?error`. |
| D58 | Sessions? | 30-min idle timeout, cookie-only tracking, `HttpOnly`, `SameSite=Lax`, `Secure` in prod (`COOKIE_SECURE`, default true), new session id on login, registration and own password change; no concurrent-session limit; **no remember-me**. |
| D59 | Keeping the session's user current (K3, BR-83)? | Every `UPDATE app_user` increments `version`. `UserStateFilter` reads the row on each authenticated request: inactive → session ended (`/login?inactive`); password changed elsewhere → `/login?expired`; other change → principal refreshed. |
| D60 | Auto-login after registration (K2)? | `AuthSessionService.login`: change session id + new CSRF token (`ChangeSessionId` + `CsrfAuthenticationStrategy`) and save the `SecurityContext` through the shared `SecurityContextRepository`. `refresh` re-reads the user (K3, after OTP verification). |
| D61 | Entry points (K4)? | `GET /notifications/unread-count` → 401 JSON (registered first); everything else → `/login`, or `/login?expired` when the browser sent a session id that is no longer valid. The request cache saves only GET pages (not R-77, `/error`). |
| D62 | CSRF failures (K5)? | Expired session → `302 /login?expired`; visitor without a session → `302 /login`; logged in with a stale token → 403 page with "This form has expired" text. |
| D63 | Over-size uploads (K1)? | Tomcat 11 answers 413 when `CsrfFilter` reads `_csrf` from an over-size body, so `UploadSizeGuardFilter` (before Spring Security) redirects multipart POSTs above 26 MB on the three upload routes to their form page with `flashError`; `server.tomcat.max-swallow-size=64MB` (2 MB gives a connection reset in Chromium). Other routes: 413 → `error/4xx`. |
| D64 | Security headers? | Strict CSP without `'unsafe-inline'` (`default-src 'self'; img-src 'self' data:; object-src 'none'; base-uri 'none'; form-action 'self'; frame-ancestors 'none'`), `Referrer-Policy: same-origin`, `Permissions-Policy`, COOP/CORP same-origin, `X-Frame-Options: DENY`, HSTS only on HTTPS. So: no inline scripts or styles; `early.js` replaces the inline no-js script, `.no-js` CSS replaces `<noscript><style>`, chart data goes in a `data-chart` attribute. |
| D65 | Method security? | **No `@PreAuthorize`.** Roles by URL prefix, objects by `ComplaintAccessPolicy`; `anyRequest().authenticated()` (unknown URL → 404) plus a route-inventory test that fails if a handler path is outside the known prefixes. |
| D66 | OTP storage and attempts? | HMAC-SHA256 with a random 32-byte key created at each start (never stored), constant-time compare; an attempt is **reserved** with an atomic `UPDATE … attempts < max` before comparing, so parallel guesses cannot exceed 3. Demo sender in prod only logs a warning. |
| D67 | Password hashing and policy? | BCrypt strength 12 (`app.security.bcrypt-strength`, tests 4); FR-02 plus at most 72 UTF-8 bytes; (Should) a small common-password list and no email name or mobile inside the password; (Should) re-hash on login when the strength rises. |
| D68 | Upload and abuse limits? | New **BR-88**: at most 10 complaints per citizen per rolling 24 h (`app.complaint.max-per-day`). Only JPG/PNG/PDF by magic bytes (SVG never), nothing decoded server-side, canonical-path check on every file access; antivirus is future (`FileScanner`). |
| D69 | K7 / U2 (staff information)? | K7 confirmed: an officer learns only that a number exists in another department, never its details (no preview). U2: staff see the duplicate's number always, as a link only when they can open it (`TimelineEntry.relatedLinkable`). |
| D70 | U1 (no staff names for citizens)? | Citizen view models have no staff-name fields; `actorLabel` per viewer; notification texts built by `NotificationMessages` from department names only; scan test SEC-49 over all citizen pages and stored notification texts. |
| D71 | U6 (email after failed login)? | Kept: the failure handler stores the lower-cased email in the session, the login page shows it once. Never the password. |
| D72 | U11 (timeout while typing)? | `app.js` warns 5 minutes before the 30-minute timeout with a "Stay logged in" button (one GET to R-77). No drafts in browser storage (shared computers). Without JS the text is lost; the login page explains. |
| D73 | Production safety checks? | `ProdSafetyCheck` refuses to start in `prod` with `dev`+`prod` profiles, an empty DB password, a default/weak bootstrap admin password while no admin exists, or BCrypt strength < 10; warns for an insecure cookie, the demo OTP sender and an `@example.com` admin email. |
| D74 | Security logging? | Logger `SECURITY` (own file, kept 1 year) with fixed `key=value` events (login, lockout, 403, CSRF, policy 404, OTP, uploads, admin actions) and an hourly summary of the counts; emails and mobiles masked; never passwords, OTP codes (except the one demo line) or session ids. |
| D75 | CSV injection list? | D43 extended: also prefix cells starting with tab or carriage return (OWASP list). |
| D76 | Privacy notices (DPDP Act 2023)? | Short notices on the registration page and the new-complaint form (no new route or field); officers never see citizens' email or mobile; EXIF data not removed yet (future). |
| D77 | K8 / U4: where do message texts live? (doc 12) | In `messages.properties` through Spring's `MessageSource`, English only, `FixedLocaleResolver(Locale.ENGLISH)`, `fallback-to-system-locale=false`. Every text produced by Java (form errors, refusals, flashes, login alerts, notices, confirm prompts, notification templates, error pages) is a key; static template copy (labels, headings, help, buttons, banners, empty states) stays literal in the templates; status labels stay in `ComplaintStatus` (D47). Draft: `docs/specs/messages.properties.draft` (182 keys). |
| D78 | Message key convention? | `<kind>.<area>.<detail>` with kinds `validation` (shown on a form), `error` (refusal), `flash`, `login`, `notice`, `confirm`, `notify`, `page.error`, `form`, plus Spring's `typeMismatch*`. Apostrophes as ’ (U+2019); Bean Validation texts use only `{min}`/`{max}`, others only `{0}`…; checked by `verify/check-12-messages.py`. |
| D79 | Input normalisation? | One `FormBindingAdvice` (`@InitBinder`) for every binder: trim, empty → null, white space collapsed in single-line fields, CR LF → LF in `description`/`remark`/`changeReason`, email lower-cased, mobile `+91`/`0`/spaces removed, complaint numbers upper-cased, OTP spaces removed; passwords untouched. |
| D80 | Validation layers? | Browser hints → binding/normalisation → Bean Validation on forms → service checks (DB/state) → DB constraints. Services do not re-run Bean Validation. Service input problems = `FormRejectedException` (same view, field/global error), refusals = `ActionRefusedException` (redirect + flash). No validation groups. Order in services: access (404) → rules → input checks → versioned write → constraints. |
| D81 | Custom validators? | `@ValidMobile`, `@StrongPassword` (first failing rule only: length, 72 UTF-8 bytes, letter+digit, common list), `@FieldsMatch`, `@FieldsDiffer`; "no email name or mobile in the password" is a service check (`PasswordPolicy`). Every constraint has an explicit message key (scan test). |
| D82 | Exception hierarchy? | `AppException(code, messageKey, args)` → `NotFoundException`, `ActionRefusedException` (`InvalidTransition`, `BusinessRule(rule)`, `ConcurrentUpdate`) with a `Subject` that the web layer turns into a local redirect, `FormRejectedException(List<FormError>)`, `StorageException`. Exception messages hold codes and keys only. |
| D83 | `GlobalExceptionHandler` mapping? | Table in doc 12 §5.2: refusals 302 + `flashError`; Spring MVC client errors 400/405/406/415 → `error/4xx`; `NoResourceFound` → 404; anything else 500 with reference (JSON for R-77, chosen by path). Numeric path ids declared `{id:\d{1,18}}`, so malformed ids give 404. Filter objects need a `BindingResult`. Security exceptions are re-thrown. |
| D84 | Database constraint violations? | Only the five unique keys a race can hit (email, mobile, three master-data names) are translated into field errors by constraint name. Every other violation, incl. `uq_notification_event_recipient` (D22), is a bug: rollback and 500. BR-88 may be exceeded by one under exact concurrency (accepted). |
| D85 | U3: reference id and error page routing? | `ERR-XXXX-XXXX` (8 Crockford base-32 characters, 40 random bits), created once per failed request, logged at ERROR with the stack trace and MDC `ref`, shown on `error/500`, in R-77's JSON and in the storage-failure message. `AppErrorViewResolver` renders `/error` (firewall 400, security 403 incl. CSRF variant, Tomcat 413, filter exceptions) with the same model; error pages contain no form and create no session. |
| D86 | Error properties in Spring Boot 4? | `spring.web.error.*` (include-stacktrace/message/binding-errors never, include-exception false, **include-path never**, whitelabel off); the old `server.error.*` keys are ignored by Boot 4 (verified). Docs 07 §6 and 11 §9.3 corrected. |
| D87 | U5: field ids? | `th:field` default ids (`x`), `x-help`, `x-error`, summary `#error-summary` with links `#x` in visual order (field list passed by the page), hidden-field errors unlinked; file input `id="files"`, one message per file; forms with files get a read-only `getFiles()` and `files` is a disallowed binder field. Page title prefixed "Error:". |
| D88 | Flash conventions? | Three flash attributes (success, info, error) with the text resolved at redirect time by `Flash`; warning style only for in-page banners; exception handlers and the upload filter write to the output flash map; validation errors are never flashed. |
| D89 | Notification texts? | `notify.*` templates rendered once with `Locale.ENGLISH` by `NotificationMessages`; remark excerpts ≤ 120 and title excerpts ≤ 100 characters, so every template fits `VARCHAR(300)` (worst case 257, checked by script). |
| D90 | Doc 11 message wording (K-S1)? | Final texts for locked, inactive, expired (also covers password change and lost typing), form expired (403 CSRF variant), registration limit, BR-88, password too long / common / personal, session warning and privacy notices: doc 12 §8.5. |
| D91 | Logging of errors? | ERROR only for unexpected failures (with reference and stack trace); refusals one INFO `REFUSED` line; client errors DEBUG; never rejected values, `BindingResult`s, passwords, OTPs or translated constraint messages (they contain PII). |
| D92 | Test levels and where they run? (doc 13) | Unit, Repo (`@JdbcTest` + Flyway on H2), Flow and Web (`@SpringBootTest` + MockMvc on the **full context with real services**; no `@WebMvcTest` slices), HTTP (`RANDOM_PORT`) and Scan tests run in `mvn test` with JDK 25 only. `-Pmysql` (failsafe `*MySqlIT`, MySQL 8.4) runs concurrency, MySQL-only and NFR-10 tests; `-Pe2e` runs the browser tests. Added test tools: jsoup 1.24.1, JaCoCo 0.8.14. |
| D93 | Test IDs and naming? | `TC-<group>-<n>` (`SM`, `BR`, `UC`, `RT`, `SEC`, `VE`, `SC`, `Q`, `NF`, `UT`, `SCAN`), all listed in `13a-test-catalogue.md`; every `@DisplayName` starts with its ID; class suffix by level (`…Test`, `…RepositoryTest`, `…FlowTest`, `…WebTest`/`…RouteTest`/`…AcceptanceTest`, `…HttpTest`, `…ScanTest`, `…MySqlIT`); JUnit tags `unit`, `repo`, `flow`, `web`, `http`, `scan`, `mysql`, `slow`. |
| D94 | Time in tests? | One `@Primary` `TestClock` (fixed, movable) starting **23-Nov-2026 10:00 IST**, reset before each test; scheduled jobs disabled with cron `-` and called directly; no `sleep`. |
| D95 | Isolation between tests? | No test-transaction rollback (it would hide `REQUIRES_NEW`, rollback file clean-up and identity counters): `TestDatabase.reset()` before each test deletes all rows in FK order, restarts identity counters, re-inserts V2, empties the test upload dir and the throttling maps. JUnit parallel execution off (the in-memory H2 is shared by every cached context). Doc 08 `sample-data.sql` is reused unchanged as the query fixture. |
| D96 | Security test helpers? | `@WithAppUser` and `appUser()` load the real `app_user` row as `CurrentUser`; `@WithMockUser` is not used (`UserStateFilter` would end its session); `Sessions.requestedSession()` is mandatory for reused MockMvc sessions (doc 11 §12 note). |
| D97 | State-machine oracle? | An expected matrix (15 actions × 10 statuses, relations owner / watcher / duplicate owner / department officer / admin; 58 allowed cells) written from doc 05 §3 in 13a §A.2 and stored as `transition-matrix.csv`; a 720-case test runs `TransitionRules.decide` against it, plus one Flow test per transition row and actor; `check-13-traceability.py` compares the matrix with doc 05. |
| D98 | K11: route tests? | `routes.csv` (doc 09 §3 as data) drives four generated sweeps: wrong role → 403 (SEC-16), not visible / unknown → identical 404, anonymous → login (R-77: 401), and the route inventory compared both ways (SEC-19). Each route row also has its own hand-written test (happy path + its specific errors). |
| D99 | U7 / U8: browser tests? | Node `@playwright/test` + `@axe-core/playwright` in `app/e2e/`, against the JAR started by `-Pe2e` (profiles `dev,e2e`, H2 from the test classpath). Projects `js-on` and `js-off`, viewports 360 and 1280 px; axe-core only with JS on (it never finishes with JS disabled, found in the prototype); zero violations for the WCAG 2.1 A/AA tags. |
| D100 | Test fixtures? | `TestData` builders insert consistent rows in any state; `DemoWorld` builds Appendix A with the same code as the `dev` profile: the seeding logic moves to `seed.DemoData` (no profile), which `DevDataSeeder` calls. |
| D101 | T-3: message checks in the build? | `MessageCatalogueScanTest` and `ConstraintMessageScanTest` (JUnit) run in `mvn test`; the documentation scripts `check-09/10/12/13` run on CI, so students need no Python to build. |
| D102 | Coverage targets? | JaCoCo: `TransitionRules` and `ComplaintAccessPolicy` 100 % branch; packages `workflow`, `access` 90 % line / 85 % branch from the start; services and `common` 80 % / 70 % and the bundle 75 % / 60 %, enforced after M7. |
| D103 | When is testing done? | Defect severities S1–S4, a GitHub-issue template with related IDs and a regression test for every fixed S1/S2; exit criteria of doc 13 §7.4 (verify green, every Must test passing, `-Pmysql` and `-Pe2e` run within 7 days, coverage met, no open S1/S2, the check scripts pass, demo checklist ticked). |
| D104 | Notifications in S1 and S4 (found while writing tests)? | The T01 confirmation to the owner (FR-15) is the one deliberate exception to BR-68 ("the actor is never notified"). S1: Mahesh receives **5** notifications (registration + 4 status changes). S4: the watcher is notified of the new In Progress and Resolved steps, **not** of reopen or close, which go to staff (FR-70). Doc 05 corrected. |
| D105 | Concurrency tests? | Only on MySQL (H2 locks differently): SEC-37 with 10 threads, the number generator (8 × 50 and 16 × 25), S10 with two threads, and BR-88 asserted as a soft limit (≤ limit + 1, doc 12 T-6). On H2 the same cases are sequential stale-version tests. |
| D106 | Auto-close during the demo? | The `dev` profile runs the auto-close job every 5 minutes (`0 */5 * * * *`, set in doc 14), so GRV-…-000011 is visibly auto-closed at viva time; `prod` stays hourly. |
| D107 | What must a student install to build? (doc 14) | Only **JDK 25** (plus MySQL to run the app, Node only for `-Pe2e`). Maven comes through the committed **Maven Wrapper 3.3.4 → Maven 3.9.16**; a system Maven ≥ 3.9 also works. |
| D108 | Coordinates, version and JAR name? | `com.smartgrievance:smart-grievance`, version `0.1.0-SNAPSHOT` in the pom only; `finalName` = `smart-grievance`, so the file is always `target/smart-grievance.jar`; the version is in `META-INF/build-info.properties` (`build-info` goal) and in the Git tag. |
| D109 | Versions Spring Boot does not manage? | Pinned exactly and checked on Maven Central / npm on 2026-11-23: Bootstrap 5.3.8, Bootstrap Icons **1.13.2**, Chart.js **4.6.1**, jsoup 1.24.1, JaCoCo 0.8.14, exec-maven-plugin 3.6.2, dependency-check-maven 13.0.0, Playwright 1.65.1, axe-core/playwright 4.14.0, Node 26 LTS (≥ 22.12). |
| D110 | U9: WebJar or `static/vendor`? | **WebJars** for all three libraries (`org.webjars:bootstrap`, `org.webjars.npm:bootstrap-icons`, `org.webjars.npm:chart.js`), version-free URLs through `webjars-locator-lite`; nothing is copied into `static/vendor/`. Dependabot can update them; no files to keep in sync. |
| D111 | O5: MySQL connection options? | `connectionTimeZone=%2B05:30&forceConnectionTimeZoneToSession=true&characterEncoding=UTF-8`. The zone name `Asia/Kolkata` fails (`ERROR 1298`) on MySQL servers without time-zone tables, which is the default for the Ubuntu and Windows installers (verified); India has no daylight saving, so `+05:30` is equivalent. |
| D112 | A clear message when `prod` lacks its settings? | `config.ProdEnvironmentCheck`, an `EnvironmentPostProcessor` registered in `META-INF/spring.factories`, stops a `prod` start **before any bean** when `DB_URL`, `DB_USERNAME` or `DB_PASSWORD` is missing, or when `dev` and `prod` are combined (P-1). Without it the start fails with "'url' must start with jdbc" (verified). `ProdSafetyCheck` (D73) still does P-1…P-7. |
| D113 | Health check / Actuator? | `spring-boot-starter-actuator` with **only `/actuator/health`** exposed, no details or components, probes off; allowed for requests from 127.0.0.1/::1 or an ADMIN (an `AuthorizationManager` before `anyRequest()`); Nginx also blocks `/actuator/` from outside. Replaces doc 11 §9.3 "not a dependency" under the condition that rule set. Not part of `routes.csv` (own handler mapping). |
| D114 | Database accounts? | Least privilege: `grievance_owner` (DDL) used only by Flyway (`spring.flyway.user/password` = `DB_MIGRATION_*`), `grievance_app` with `SELECT, INSERT, UPDATE, DELETE` for the pool, `grievance_backup` read-only for `mysqldump`; on laptops `grievance_dev` / `grievance_test` own only their database. Verified: the app user cannot `CREATE TABLE`. |
| D115 | O4: housekeeping schedule? | `app.housekeeping.cron=0 30 2 * * *` (daily 02:30 IST), `-` in tests (doc 13 B-2); new settings `app.housekeeping.notification-retention-days` (90), `otp-retention-days` (30), `batch-size` (1000). Every `@Scheduled` cron is declared with `zone = "${app.timezone}"`. |
| D116 | Log files? | `logback-spring.xml`: time stamps always in IST (independent of the server zone), pattern with `%X{ref:-}`; `app.log` daily + 20 MB parts, 90 days, 2 GB cap; `security.log` (logger `SECURITY` only) daily, **366 days**, 1 GB cap; console threshold WARN in `prod` (journal), INFO in Docker; tests log to the console only. No logrotate. |
| D117 | Caching of static files? | `Cache-Control: no-cache` (always revalidate, 304 via `Last-Modified`) for static files and WebJars; no long `max-age` or content-hash URLs, so an upgrade is never hidden by a stale browser cache. |
| D118 | K-S5: dependency vulnerabilities? | GitHub **Dependabot** (Maven, npm, Actions; weekly, grouped; Spring Boot minor/major as planned upgrades) plus an optional `-Psecurity-scan` profile with OWASP Dependency-Check 13.0.0 (fail at CVSS ≥ 7, `NVD_API_KEY`) before each release; not in every build (large NVD download). |
| D119 | Maven test wiring (doc 13 B-1)? | surefire: `excludedGroups=mysql,e2e`, excludes `*IT`, parallel off, `-Duser.timezone=UTC` (proves nothing depends on the JVM zone); failsafe `*MySqlIT` in `-Pmysql` with the Spring profile `mysqltest` (`TEST_MYSQL_*`); `-Pe2e`: `spring-boot:start` with **`testClasspath=all`** (Boot 4.2 deprecated `useTestClasspath`, which did not add the test resources: the app silently used MySQL, verified), `-Dspring.devtools.restart.enabled=false`, port 8090, JMX 9011, then `npm ci` and `npx playwright test`. |
| D120 | When does the full coverage gate start? | Two JaCoCo `check` executions: `check-core` (D102 "from the start" rules) always; `check-later` (services, `common`, bundle) skipped while `coverage.later.skip=true`, switched to `false` at the end of milestone **M7** (doc 13 §6.1 "after M7"). |
| D121 | HTTPS and reverse proxy? | Nginx with Let's Encrypt (certbot) in front; the app listens on 127.0.0.1:8080; `FORWARD_HEADERS_STRATEGY=native` **only behind the proxy**, trusting `X-Forwarded-*` from 127.0.0.1/::1 only; `client_max_body_size 64m` (= swallow size), `proxy_request_buffering off`; security headers come from the app only. Verified: HSTS and `Secure` cookie through the proxy, a forged `X-Forwarded-For` is not trusted. |
| D122 | Deployment form? | Main: the executable JAR as a **systemd** service on one Linux VM. Optional: Docker Compose (app + `mysql:8.4`) with a runtime-only image built from the Maven JAR (non-root, `HEALTHCHECK`). The viva runs on a laptop with the `dev` profile. |
| D123 | Folders and permissions (K-S4)? | Service user `grievance`; `/var/lib/smart-grievance/uploads` and `/var/log/smart-grievance` mode 750, files 640 (`UMask=0027`); JAR read-only under `/opt/smart-grievance`; env file `/etc/smart-grievance/env` mode 640; systemd `ProtectSystem=strict` with `ReadWritePaths` for the two folders. |
| D124 | Versioning and releases? | Semantic versioning from 0.1.0; one MINOR release per milestone (`0.<n>.0`), **`1.0.0` for the viva**; annotated tags `vX.Y.Z`; `CHANGELOG.md` in *Keep a Changelog* format; release checklist in doc 14 §10.3. |
| D125 | In which order is the app built? | Ten milestones **M0–M9** (skeleton/CI, auth+OTP, master data, submit/edit/withdraw+files, tracking, workflow, duplicates/watchers, notifications, dashboard/reports, hardening/e2e/viva); every one of the 446 test cases, 70 FRs and 99 routes belongs to exactly one milestone, checked by `verify/check-14-milestones.py`. `TransitionRules` and `ComplaintAccessPolicy` are written complete in M3; `DemoData` grows per milestone and is complete in M6. |
| D126 | JVM options, port, context path? | `-Xms128m` (laptop) / `256m` (server), `-Xmx512m`, `-XX:+ExitOnOutOfMemoryError` (server, Docker), `-XX:MaxRAMPercentage=75` in Docker; `-Duser.timezone` not needed (D18, D116); port 8080 (`SERVER_PORT`), context path `/`. |
| D127 | O5: demo sample files? | Two small JPEGs for GRV-…-000003 in `src/main/resources/seed/files/` (made by the students, ≤ 100 KB, EXIF removed); `DemoData` copies them to `app.upload.dir` with the BR-24 name inside its transaction and deletes them on rollback. |
| D129 | Admin tables on phones? (validation finding V-3) | Below 576 px the admin tables (officers, citizens, departments, categories, areas, dashboard overdue list) stack each row as a labelled card (`.table-stack` + `data-label`), so every column and every action button stays on screen; extends D51. TC-NF-04 and the walkthrough now fail when a table scrolls sideways or a link or button is off screen. |
| D128 | Copies of spec files in the app? | `db/migration/*.sql`, `messages.properties` and `common-passwords.txt` (new draft, 34 entries) are byte-identical copies of the docs; a CI step `diff`s them, so a change is always made in the spec first. |

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
| 09 Web & API Contracts | Draft |
| 10 UI / Screen Spec | Draft |
| 11 Security Spec | Draft |
| 12 Validation & Error Handling | Draft |
| 13 Test Spec & Traceability (+ 13a Test Catalogue) | Draft |
| 14 Build & Deployment | Draft |
