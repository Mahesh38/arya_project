# 07 — Architecture & Tech Stack

| | |
|---|---|
| **Status** | Draft |
| **Depends on** | 00 Plan (D1–D16), 05 Domain Model, 06 Flow Diagrams |

---

## 1. Architecture at a glance

A **single Spring Boot application** (a modular monolith) that renders HTML on the server with Thymeleaf and runs as one executable JAR with embedded Tomcat. There is no separate front-end project, no REST client and no message broker.

```mermaid
flowchart LR
    B[Browser<br/>HTML + Bootstrap 5]
    subgraph JAR[smart-grievance.jar  ·  JDK 25]
        T[Embedded Tomcat 11]
        SEC[Spring Security<br/>filter chain<br/>login · roles · CSRF]
        C[Controllers<br/>Spring MVC]
        V[Thymeleaf<br/>templates + layouts]
        S[Services<br/>@Transactional<br/>business rules]
        AP[ComplaintAccessPolicy<br/>TransitionRules]
        R[Repositories<br/>JdbcClient + SQL]
        FS[FileStorageService]
        OTP[OtpSender<br/>Demo / SMS later]
        J[AutoCloseJob<br/>@Scheduled]
        FW[Flyway]
    end
    DB[(MySQL 8.4 LTS)]
    DISK[(uploads/ folder)]

    B -- HTTP --> T --> SEC --> C
    C --> S
    S --> AP
    S --> R --> DB
    S --> FS --> DISK
    S --> OTP
    C --> V --> B
    J --> S
    FW -- migrations on startup --> DB
```

### 1.1 Layer rules

| Layer | May call | Must not |
|---|---|---|
| **Controller** (`*Controller`) | Services, form/view classes | Call repositories, contain business rules, or open transactions |
| **Service** (`*Service`) | Repositories, other services, `ComplaintAccessPolicy`, `TransitionRules`, `FileStorageService`, `NotificationService` | Know about HTTP or the `Model` |
| **Repository** (`*Repository`) | `JdbcClient` only | Contain business logic. Every SQL statement lives here. |
| **Template** | Model attributes (view records, forms) | Call services or contain logic beyond display conditions |

Transactions start in the service layer (`@Transactional`), so each user action is exactly one transaction (BR-70).

---

## 2. Tech stack (verified)

Verified on **2026-11-22** by building and running a test project (§9). Spring Boot's dependency management (BOM) pins all versions except Bootstrap, so we **do not** override them.

| Concern | Technology | Version |
|---|---|---|
| JDK | OpenJDK (LTS) | **25** (tested on 25.0.5) |
| Framework | Spring Boot | **4.2.0** |
| | Spring Framework | 7.1.0 (managed) |
| Web | Spring MVC (`spring-boot-starter-webmvc`), embedded Tomcat | Tomcat 11.0.27 (managed) |
| Views | Thymeleaf | 3.1.7 (managed) |
| | Thymeleaf Layout Dialect | 4.0.1 (managed) |
| | `thymeleaf-extras-springsecurity6`, which also works with Spring Security 7. No `…7` artifact has been published. | 3.1.7 (managed) |
| Security | Spring Security | 7.2.0 (managed) |
| Data access | Spring JDBC: `JdbcClient` (`spring-boot-starter-jdbc`), HikariCP pool | managed |
| Database | MySQL **8.4 LTS** in production. The test used MySQL 8.0, and the SQL works on both. | Connector/J 26.10.0 (managed) |
| Migrations | Flyway (`spring-boot-starter-flyway` + `flyway-mysql`) | 12.11.0 (managed) |
| Validation | Jakarta Bean Validation / Hibernate Validator | 9.1.6 (managed) |
| CSS / UI | Bootstrap via **WebJars** + `webjars-locator-lite` | Bootstrap **5.3.8** |
| Icons | Bootstrap Icons (WebJar or copied into `static/`) | 1.x |
| Charts (FR-64, Could) | Chart.js, copied into `static/vendor/` | 4.x |
| Tests | JUnit Jupiter, Spring Boot Test, MockMvc, Spring Security Test, H2 (MySQL mode) | JUnit 6.1.3, H2 2.5.252 (managed) |
| Build | Maven + Maven Wrapper (`mvnw`) | Maven 3.9.x |

Note: Spring Boot 4 split the starters into modules. The web starter is now `spring-boot-starter-webmvc`, Flyway needs `spring-boot-starter-flyway`, and the test helpers come from `spring-boot-starter-webmvc-test` / `spring-boot-starter-security-test`.

### 2.1 Maven coordinates

| | |
|---|---|
| groupId | `com.smartgrievance` |
| artifactId | `smart-grievance` |
| Base package | `com.smartgrievance` |
| Packaging | `jar` (D6) |
| Final artifact | `target/smart-grievance.jar` |

### 2.2 Dependencies (pom.xml)

```text
compile : spring-boot-starter-webmvc, spring-boot-starter-thymeleaf, spring-boot-starter-security,
          spring-boot-starter-validation, spring-boot-starter-jdbc, spring-boot-starter-flyway,
          flyway-mysql, thymeleaf-extras-springsecurity6, thymeleaf-layout-dialect,
          org.webjars:bootstrap:5.3.8, webjars-locator-lite
runtime : com.mysql:mysql-connector-j
dev     : spring-boot-devtools (optional, auto-restart)
test    : spring-boot-starter-webmvc-test, spring-boot-starter-security-test,
          spring-boot-starter-jdbc-test, com.h2database:h2
plugin  : spring-boot-maven-plugin
```

---

## 3. Package structure (by feature)

```text
com.smartgrievance
├── SmartGrievanceApplication.java
├── config/            SecurityConfig, WebConfig, AppProperties (@ConfigurationProperties "app"),
│                      ClockConfig (Clock bean, Asia/Kolkata), SchedulingConfig
├── common/            exceptions (NotFound, InvalidTransition, ConcurrentUpdate, BusinessRule),
│                      GlobalExceptionHandler (@ControllerAdvice), GlobalModelAdvice (unread count,
│                      current user), PageResult<T>, Flash helper, TextUtils
├── user/              User, Role, UserRepository, CurrentUser (principal), AppUserDetailsService
├── auth/              RegistrationController/Service/Form, LoginController,
│                      RoleBasedSuccessHandler, PasswordController/Service
├── otp/               OtpService, OtpRepository, OtpSender (interface), DemoOtpSender,
│                      VerificationController, VerifiedCitizenInterceptor (FR-09a)
├── masterdata/        Department, Category, Area + repositories, MasterDataService,
│                      admin controllers for departments, categories and areas
├── staff/             OfficerAdminController, CitizenAdminController, UserAdminService
├── complaint/         Complaint, ComplaintStatus, ComplaintRepository, ComplaintNumberGenerator,
│                      ComplaintService (submit, edit, withdraw), ComplaintQueryService (lists,
│                      filters), CitizenComplaintController, ComplaintDetailController, forms/views
├── access/            ComplaintAccessPolicy (BR-60..64)
├── workflow/          Transition (enum T01..T13), TransitionRules, ComplaintWorkflowService,
│                      DuplicateService, AdminWorkflowController, OfficerWorkflowController,
│                      AutoCloseJob
├── history/           ComplaintEvent, EventType, ComplaintRevision, EventRepository,
│                      RevisionRepository, TimelineService (merges events + revisions)
├── watch/             WatcherRepository, WatchService, WatchController
├── attachment/        Attachment, AttachmentRepository, FileStorageService, FileTypeDetector
│                      (magic bytes), AttachmentController
├── notification/      Notification, NotificationRepository, NotificationService
│                      (recipient rules BR-67..69), NotificationController
├── dashboard/         DashboardRepository (aggregate SQL), DashboardService,
│                      AdminDashboardController, OfficerSummary
└── seed/              AdminBootstrap (FR-07, all profiles), DevDataSeeder (@Profile("dev"))
```

**Rule:** a feature package may use another feature's **service** or **domain records**, but never its repository directly. The one exception is `access` and `dashboard`, which only read.

### 3.1 Domain classes
- Domain objects are **Java records** (e.g. `record Complaint(Long id, String complaintNo, …, int version)`), mapped from SQL rows with `RowMapper`s or `JdbcClient` `.query(Complaint.class)`.
- Forms are separate mutable classes with Bean Validation annotations (`ComplaintForm`, `RegistrationForm`) so Thymeleaf can bind them. Forms are never saved directly.
- Views use small records built for one screen (`ComplaintListItem`, `TimelineEntry`, `DashboardCounts`).

---

## 4. Architecture Decision Records (ADRs)

| ADR | Decision | Why | Consequence |
|---|---|---|---|
| **ADR-01** | Modular monolith, server-rendered (Spring MVC + Thymeleaf) | One project and one deployment (C2). Easy to run on a lab PC. No CORS or JWT complexity. | Pages reload on each action. A little JavaScript is used only for small things such as previews and confirm dialogs. |
| **ADR-02** | **Spring JDBC (`JdbcClient`)**, not JPA | Matches the synopsis's "JDBC" requirement. SQL is explicit and easy to explain in the viva. Locking (`FOR UPDATE`, version checks) is clear. Small schema. (D3) | We write the row mapping and SQL ourselves. Every SQL statement is kept in repositories. |
| **ADR-03** | **Flyway** for schema and reference data | The schema is versioned with the code, and a fresh database is created automatically (NFR-31). | Migrations are never edited after release. Changes go into a new `V<n>__*.sql`. |
| **ADR-04** | Hand-written state machine: a `Transition` enum plus a `TransitionRules` class implementing the table in doc 05 §3 | Exactly 13 rules. A library such as Spring Statemachine would be heavier than the problem. Easy to unit-test row by row. | Every status change **must** go through `ComplaintWorkflowService` → `TransitionRules`. |
| **ADR-05** | `ComplaintAccessPolicy` in the service layer, answering **404** (D10) | URL roles alone can't express "owner or watcher or department". One class makes the rule auditable, and lists reuse the same conditions in SQL (BR-64). | Every service method that loads a complaint for a user calls the policy. |
| **ADR-06** | Files stored on **local disk** outside `static/`, with metadata in the DB, served by a controller | Simple, no cloud dependency, and access-checked (NFR-05). | The `uploads/` folder must be backed up together with the DB. |
| **ADR-07** | Our own small `FileTypeDetector` (magic bytes for JPG, PNG and PDF) instead of Apache Tika | Only 3 types, and it avoids a large dependency. | Adding more types means adding their signatures. |
| **ADR-08** | `OtpSender` interface with `DemoOtpSender` (on screen + log), selected by `app.otp.sender` (D16) | SMS is out of scope, but the design is ready for it. | Production use needs an SMS gateway implementation. |
| **ADR-09** | **Optimistic locking** with a `version` column (BR-71) | Prevents lost updates with no long-lived database locks. | Forms carry a hidden `version` field. A conflict shows "please refresh". |
| **ADR-10** | Notifications are written **synchronously in the same transaction** as the action | Simple and consistent (NFR-30). In-app notifications are just rows. | If email or SMS is added later, send it after commit (`@TransactionalEventListener`). |
| **ADR-11** | Inject a `java.time.Clock` (zone Asia/Kolkata) and never call `now()` without it | Time rules (review window, overdue, OTP expiry, auto-close) become testable by fixing the clock. | Tests use `Clock.fixed(...)`. |
| **ADR-12** | Front-end libraries come from **WebJars** or are copied into `static/`, **not from a CDN** | Works offline, which suits college labs and viva demos. | The JAR grows slightly. |
| **ADR-13** | Tests run on **H2 in MySQL mode**. Migrations use portable SQL. | Needs no Docker or MySQL on a student laptop to run `mvn test`. | No MySQL-only syntax (`ENUM`, `ON UPDATE CURRENT_TIMESTAMP`, etc.). Statuses are `VARCHAR` + `CHECK`. Final checks are run manually on MySQL. |

---

## 5. Security architecture (summary; details in doc 11)

| Concern | Approach |
|---|---|
| Authentication | Form login (`/login`). Username = email, lower-cased. BCrypt `PasswordEncoder`. `UserDetails.enabled = active`. |
| Landing page | `RoleBasedSuccessHandler`: CITIZEN → `/citizen/complaints`, OFFICER → `/officer/queue`, ADMIN → `/admin/dashboard`. |
| URL authorization | `/citizen/**` → CITIZEN, `/officer/**` → OFFICER, `/admin/**` → ADMIN. `/complaints/**`, `/attachments/**`, `/notifications/**` and `/account/**` require any logged-in user. `/`, `/login`, `/register`, `/webjars/**`, `/css/**`, `/js/**` and `/images/**` are public. |
| Object authorization | `ComplaintAccessPolicy` → 404 (ADR-05). |
| Verified citizen | `VerifiedCitizenInterceptor` sends unverified citizens from `/citizen/complaints/new` to `/verify-mobile` (FR-09a). |
| CSRF | Enabled (default). Thymeleaf `th:action` adds the token automatically. |
| Session | 30-minute timeout, session fixation protection (default), logout invalidates the session. |
| Headers | Spring Security defaults (X-Frame-Options DENY, nosniff, cache-control). |

---

## 6. Configuration & profiles

| File | Purpose |
|---|---|
| `application.properties` | Shared defaults: `app.*` values from doc 03 §3, multipart limits, Thymeleaf settings, session timeout, `spring.profiles.default=dev` |
| `application-dev.properties` | Local MySQL `grievance_dev`, Thymeleaf cache off, `app.otp.sender=demo`, DevDataSeeder enabled, DEBUG logging for `com.smartgrievance` |
| `application-prod.properties` | DB URL, user and password **from environment variables**, Thymeleaf cache on, admin password from env (required), INFO logging |
| `src/test/resources/application-test.properties` | H2 `jdbc:h2:mem:test;MODE=MySQL;DATABASE_TO_LOWER=TRUE`, fixed clock where needed |

Sketch of `application.properties` (final values come in doc 14):

```properties
spring.application.name=smart-grievance
spring.profiles.default=dev
server.servlet.session.timeout=30m
spring.servlet.multipart.max-file-size=5MB
spring.servlet.multipart.max-request-size=26MB
spring.thymeleaf.cache=true

app.timezone=Asia/Kolkata
app.complaint.review-window-days=7
app.complaint.max-reopens=2
app.complaint.auto-close-cron=0 0 * * * *
app.upload.dir=./uploads
app.upload.max-file-size=5MB
app.upload.max-files-per-complaint=5
app.upload.allowed-types=image/jpeg,image/png,application/pdf
app.otp.sender=demo
app.otp.length=6
app.otp.ttl=5m
app.otp.max-attempts=3
app.otp.resend-cooldown=60s
app.otp.max-sends-per-hour=5
app.admin.email=admin@example.com
app.admin.password=${ADMIN_PASSWORD:Admin@1234}
```

These properties are bound to a type-safe `AppProperties` record and validated at startup (`@Validated`), so a bad value stops the app with a clear message.

---

## 7. View layer (Thymeleaf) plan

```text
src/main/resources/
├── templates/
│   ├── layouts/
│   │   ├── main.html          navbar (role menus via sec:authorize), bell + unread count,
│   │   │                      flash messages, footer; pages use layout:decorate
│   │   └── public.html        minimal layout for login / register / verify
│   ├── fragments/
│   │   ├── status-badge.html  one colour per status (NFR-21)
│   │   ├── pagination.html
│   │   ├── timeline.html      merged events + revisions
│   │   ├── attachments.html   thumbnails / PDF links
│   │   ├── complaint-actions.html  buttons shown according to allowed actions
│   │   └── form-errors.html
│   ├── auth/       login, register, verify-mobile, change-password
│   ├── citizen/    my-complaints, watching, complaint-form, submitted
│   ├── complaint/  detail (shared by all roles), duplicate-confirm
│   ├── officer/    queue
│   ├── admin/      dashboard, complaints, departments, categories, areas, officers, citizens
│   ├── notification/ list
│   └── error/      404, 403, 500 (no stack traces)
└── static/
    ├── css/app.css
    ├── js/app.js              file-count/size pre-check, confirm dialogs (server re-validates)
    └── images/logo.svg
```

- **Allowed actions** are computed once in the service (`Set<Action> allowedActions(user, complaint)`) and passed to the view, so templates never repeat business rules.
- The unread count and current user are added to every page model by `GlobalModelAdvice`, using one indexed `COUNT(*)` query.

---

## 8. Cross-cutting concerns

| Concern | Approach |
|---|---|
| Errors | `GlobalExceptionHandler`. `NotFoundException` → 404 page. `InvalidTransition`/`BusinessRule`/`ConcurrentUpdate` → redirect back with a flash error. Anything unexpected → 500 page with a reference ID, and the full stack trace is logged. |
| Logging | SLF4J/Logback. Every transition is logged at INFO (`complaintNo`, from, to, actor). OTP codes are logged **only** by `DemoOtpSender`. |
| Time | `Clock` bean (ADR-11). `LocalDateTime` values in Asia/Kolkata are stored in `DATETIME` columns without conversion (BR-74). |
| Scheduling | `@EnableScheduling`. `AutoCloseJob` uses the `app.complaint.auto-close-cron` setting. One application instance is assumed. |
| Pagination | `PageResult<T>(items, page, size, total)` with `LIMIT ? OFFSET ?`. Size is capped at 50 (NFR-11). |
| Validation | Bean Validation on forms, plus service-level checks for rules that need the DB (uniqueness, status, counts). |

---

## 9. Verification run (2026-11-22)

A throwaway test project with the exact dependency set from §2.2 was built and run here:

| Check | Result |
|---|---|
| `mvn verify` on **JDK 25.0.5** with Spring Boot **4.2.0** | ✅ Build and tests passed |
| MockMvc test: Thymeleaf page reads data through `JdbcClient` from a Flyway-migrated H2 database, with `sec:authentication` showing the user | ✅ |
| Anonymous request → redirected to `/login`; WebJar Bootstrap CSS served | ✅ |
| `java -jar` against **MySQL 8.0** | ✅ Started in about 3.4 s on Tomcat. Flyway applied the migration; `GET /` → 302 `/login`; `/webjars/bootstrap/css/bootstrap.min.css` → 200 |

## Change log
| Date | Change |
|---|---|
| 2026-11-22 | Initial draft; versions verified with a test build |
