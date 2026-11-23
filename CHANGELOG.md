# Changelog

## [Unreleased]

### Added

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

### Security

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
