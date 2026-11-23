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

## Implementation sequence

Work through M0–M9 in the order defined in the build and deployment specification. Verify each milestone against its linked requirements and test cases before moving to the next. Verify shared infrastructure behavior in M0 and exercise each exact route in the milestone that implements it.

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
