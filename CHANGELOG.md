# Changelog

## [Unreleased]

## [1.0.1] — 26-Nov-2026

### Added

- `build-and-run-windows.bat` (repository root): on Windows with only Java 25 it checks Java (offers a winget install), gets Maven through the Maven Wrapper (no Maven install), builds `dist\smart-grievance.jar` and starts it with the local profile; exercised by the Windows CI job.
- Local run without a database server: the `local` profile runs the JAR on an embedded H2 file database with the demo data, keeping data, uploads and logs in `local-data/` (`run-local.sh` / `run-local.bat`, IntelliJ run configurations in `.run/`).
- H2 web console on the local run at `http://localhost:8080/h2-console` (database login admin / admin, this computer only) and the guide `RUN-LOCAL.md`: prerequisites, download, run command, console, troubleshooting.
- Release workflow: pushing a `v*` tag builds and tests that commit and publishes the JAR on the GitHub Releases page.
- Independent validation: an end-to-end walkthrough of all 30 use cases, the error pages and every screen at 360 and 1280 px against the demo data on MySQL, with its report and screenshots in `docs/validation/`.

### Security

- Production refuses to start on the embedded H2 database or with the H2 console enabled (P-8), or with the local profile (P-1).

### Fixed

- The complaint timeline now lists every edited field with its old and new value; the edit entry was empty before (FR-17).
- An encoded slash in the URL now gets the application's own 400 page instead of the server's plain one.
- Admin lists (officers, citizens, departments, categories, areas) and the dashboard's overdue list stack as labelled cards on phones, so every column and action button stays on screen.

## [1.0.0] — 23-Nov-2026 (M9)

### Added

- Production start-up checks: the prod profile refuses to start with a missing or default administrator password, an empty database password, combined dev and prod profiles, or weak BCrypt settings, and warns about insecure cookies, the demo OTP sender and example.com admin addresses.
- Release quality checks: MySQL runs of the query tests, parallel numbering and OTP tests, a 10,000-complaint performance and index check, browser checks for accessibility, layout, CSP, focus and large uploads with JavaScript on and off, and scans for coverage gates and test-case IDs.
- Admin dashboard with status cards, open and closed counts by department, category and area, the overdue list, and a department chart; every number links to the matching complaint list.
- CSV export of the filtered complaint list for administrators, capped at the configured row limit.
- In-app notifications, unread counts, and scheduled housekeeping.
- Staff can link duplicate complaints, and citizens can manage updates for complaints they follow.
- Complaint workflow actions, officer queues, and administrative complaint filters.
- Complaint tracking lists, role-scoped detail and history, and private attachment downloads.
- Verified citizens can submit, edit, and withdraw complaints with content-validated attachments and a unique complaint number.
- Administration for departments, categories, areas, officers, and citizens, including account activation, password reset, and manual mobile verification.
- Citizen registration, role-based sign-in and sign-out, and password changes.
- Demo OTP issuance, verification, and resend flows.
- Initial administrator setup and development accounts for citizen, officer, and administrator login.

### Fixed

- Outline buttons and the notification list now meet WCAG 2.1 AA: darker outline button colours on the page background, and notification buttons include their visible text in the accessible name.

### Security

- Logs never contain passwords, OTPs or session ids; emails and mobile numbers are masked in security events.
- CSV export escapes cells that spreadsheets could run as formulas and leaves out email addresses and mobile numbers.
- Per-account and per-network login limits, BCrypt password storage, HMAC-protected OTPs, and session refresh when account state changes.

## [0.0.1] — 23-Nov-2026 (M0)

### Added

- Spring Boot MVC foundation with shared Thymeleaf layouts, a login placeholder, and reusable error pages.
- Maven Wrapper, CI on Linux and Windows, and browser checks at mobile and desktop widths.

### Security

- Baseline request authorization, security headers, and safe error responses.

### Migrations

- Flyway V1 creates the MySQL schema. V2 loads reference data.

### Configuration

- Development, production, H2 testing, and MySQL testing profiles.
