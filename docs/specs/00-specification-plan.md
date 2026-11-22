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

## 3. Proposed tech stack (to confirm in doc 07)

| Layer | Choice | Note |
|---|---|---|
| Language | Java 21 (LTS) | |
| Framework | Spring Boot 3.x (Web MVC) | Allowed by the synopsis |
| View | Thymeleaf + Layout Dialect, Bootstrap 5 | One project, no separate frontend |
| Security | Spring Security 6 (form login, roles) | |
| Persistence | Spring Data JPA (Hibernate) on JDBC | Alternative: `JdbcTemplate` if the guide wants plain JDBC. See Q3 |
| DB | MySQL 8; H2 for tests | |
| Migrations | Flyway | Schema is versioned with the code |
| Validation | Jakarta Bean Validation | |
| Build | Maven | |
| Server | Embedded Tomcat (`java -jar`), or WAR on external Tomcat | Both satisfy "Apache Tomcat" |
| Tests | JUnit 5, Spring Boot Test, MockMvc | |

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

## 6. Open questions (needed before doc 01/02)

- **Q1 — Department role:** Do department officers log in to update status (3 roles: Citizen, Dept Officer, Admin), or does only the Admin update status (2 roles, as in the synopsis)?
- **Q2 — Anonymous tracking:** Can anyone track a complaint with just its ID (public page), or only a logged-in owner?
- **Q3 — JDBC requirement:** Is Spring Data JPA acceptable to the guide, or must the data layer visibly use JDBC (`JdbcTemplate`)?
- **Q4 — Extra statuses:** Add `Rejected` (invalid/duplicate) and `Closed` (citizen confirms) beyond the five in the synopsis?
- **Q5 — Location:** Free-text address plus ward/area dropdown, or also optional latitude/longitude fields (to make the future Google Maps step easier)?
- **Q6 — Packaging:** Executable JAR with embedded Tomcat (simplest) or WAR for an external Tomcat?

## Status

| Doc | Status |
|---|---|
| 00 Specification Plan | Draft |
| 01–14 | Not started |
