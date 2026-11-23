# Smart Public Grievance and Complaint Management System

A Spring Boot MVC application for citizens to submit and track public grievances and for staff to assign and resolve them. The application uses Thymeleaf, Spring Security, Spring JDBC, and MySQL.

## Run M0 locally

Install JDK 25 and MySQL 8.4. From the repository root, provision the local development and test databases using the scripts in the build specification.

```sh
mysql -u root -p < docs/deploy/db/01-dev-and-test.sql
cd app
./mvnw clean verify
java -jar target/smart-grievance.jar --spring.profiles.active=dev
```

Use `mvnw.cmd` on Windows. Open `http://localhost:8080/` to see the login placeholder. Registration, verification, and login are part of M1.

Run `./mvnw verify -Pmysql` against the local MySQL test database. For Playwright, install Node.js and run `npm ci` followed by `npx playwright install chromium` in `app/e2e`. Then run `./mvnw verify -Pe2e -De2e.skip.npm.install=true` from `app`.

## Implementation sequence

Work through M0–M9 in the order defined in the build and deployment specification. Verify each milestone against its linked requirements and test cases before moving to the next.

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
