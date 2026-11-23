# Smart Public Grievance and Complaint Management System

A Spring Boot MVC application for citizens to submit and track public grievances and for staff to assign and resolve them. The application uses Thymeleaf, Spring Security, Spring JDBC, and MySQL.

## Run locally

Install JDK 25 and MySQL 8.4. From the repository root, provision the local development and test databases using the scripts in the build specification.

```sh
mysql -u root -p < docs/deploy/db/01-dev-and-test.sql
cd app
./mvnw clean verify
java -jar target/smart-grievance.jar --spring.profiles.active=dev
```

Use `mvnw.cmd` on Windows. Open `http://localhost:8080/` to register or sign in. The development profile seeds the accounts listed in [Appendix A](docs/specs/appendix-a-seed-and-demo-data.md#4-users). The mobile verification page displays the demo OTP when it issues a new code. Administrators use the Master data and Users menus to manage departments, categories, areas, officers, and citizens.

Run `./mvnw verify -Pmysql` against the local MySQL test database. For Playwright, install Node.js and run `npm ci` followed by `npx playwright install chromium` in `app/e2e`. Then run `./mvnw verify -Pe2e -De2e.skip.npm.install=true` from `app`.

## Demo logins (dev profile)

| Role | Email | Password | Try |
|---|---|---|---|
| Admin | `admin@example.com` | `Admin@1234` | Dashboard, All Complaints with CSV export, assign `GRV-…-000008` |
| Officer (Water Supply) | `anita.deshmukh@example.com` | `Demo@1234` | Department queue, resolve `GRV-…-000001` |
| Citizen | `mahesh@example.com` | `Demo@1234` | Close or reopen `GRV-…-000004` |
| Citizen | `jayesh@example.com` | `Demo@1234` | Watching `GRV-…-000001`, unwatch and watch again |
| Citizen (unverified) | `rahul@example.com` | `Demo@1234` | Mobile verification with the on-screen demo OTP |

The full viva script is the demo checklist in [doc 13 §5](docs/specs/13-test-spec-and-traceability.md).

## Production start

`java -jar app/target/smart-grievance.jar --spring.profiles.active=prod` reads its settings from environment variables (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `ADMIN_EMAIL`, `ADMIN_PASSWORD`, …; see [`docs/deploy/systemd/env.example`](docs/deploy/systemd/env.example)). The start is refused with a one-line reason when a database variable is missing, the dev and prod profiles are combined, the first administrator would get the default or a weak password, or BCrypt is weaker than 10 rounds. Insecure but tolerable settings (cookie without `Secure`, demo OTP sender, an `example.com` admin email) are logged as `STARTUP_CHECK` warnings. Deployment with Nginx, systemd or Docker Compose is described in [doc 14 §8](docs/specs/14-build-and-deployment.md).

## Tests

| Command (from `app`) | Runs |
|---|---|
| `./mvnw clean verify` | Unit, repository, web, HTTP and scan tests on H2, plus the JaCoCo coverage gates |
| `./mvnw verify -Pmysql` | The MySQL suite: query statements on MySQL, concurrency, unique keys, and the 10,000-complaint performance and index check (writes `target/perf-report.txt`) |
| `./mvnw verify -Pe2e` | Playwright in Chromium with JavaScript on and off at 360 and 1280 px: flows, axe-core accessibility, CSP, responsive layout |

`python3 docs/ci/test-report.py app/target > test-report.md` prints the one-page test report for the viva (doc 14 §11.5).

## Implementation sequence

The application was built in ten milestones, each verified against its requirements and test cases before the next one started:

1. M0 — Spring Boot MVC foundation, build, and CI
2. M1 — Registration, login, accounts, and OTP
3. M2 — Master data and staff accounts
4. M3 — Complaint submission, editing, withdrawal, and attachments
5. M4 — Complaint tracking, timeline, and downloads
6. M5 — Assignment, resolution, citizen review, and automatic closure
7. M6 — Duplicate complaints and watchers
8. M7 — Notifications
9. M8 — Dashboards and reports
10. M9 — Hardening, end-to-end checks, and release

[Build and deployment specification](docs/specs/14-build-and-deployment.md#11-implementation-milestone-plan-the-roadmap) · [Test catalogue](docs/specs/13a-test-catalogue.md)
