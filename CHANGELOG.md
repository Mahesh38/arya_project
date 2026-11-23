# Changelog

## [Unreleased]

### Added

- Verified citizens can submit, edit, and withdraw complaints with content-validated attachments and a unique complaint number.
- Administration for departments, categories, areas, officers, and citizens, including account activation, password reset, and manual mobile verification.
- Citizen registration, role-based sign-in and sign-out, and password changes.
- Demo OTP issuance, verification, and resend flows.
- Initial administrator setup and development accounts for citizen, officer, and administrator login.

### Security

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
