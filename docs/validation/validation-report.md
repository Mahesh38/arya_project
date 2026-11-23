# Validation report — Smart Grievance v1.0.0

| | |
|---|---|
| Date | 23-Nov-2026 |
| Code | `main` at `5f64d48` (merge of #10) plus the two fixes below |
| Environment | JDK 25.0.5, MySQL 8.0, Chromium (Playwright 1.65), Linux |
| Result | **All automated suites green; independent walkthrough 94/94 checks; 3 defects found and fixed, then re-validated** |

## 1. How the project was validated

| Layer | What ran | Result |
|---|---|---|
| Automated, H2 | `./mvnw clean verify`: unit, repository, web, HTTP and scan tests + JaCoCo gates | **1,403 passed**, 0 failed, coverage gates met (after all fixes) |
| Automated, MySQL | `./mvnw verify -Pmysql`: every query test class on MySQL, concurrency, unique keys, 10,000-complaint performance and `EXPLAIN` | **117 passed**, 0 failed; every page under 100 ms median at 10k complaints |
| Automated, browser | `./mvnw verify -Pe2e`: Playwright, JavaScript on and off, 360 and 1280 px, axe-core, CSP, off-screen controls | **81 passed**, 15 skipped (JS-only or no-JS-only checks), 0 failed (after all fixes) |
| Independent walkthrough | [`walkthrough.mjs`](walkthrough.mjs) against the packaged JAR with the `dev` profile on MySQL and the Appendix A demo data, exactly as at the viva: use case by use case, error by error, screen by screen | **94/94 passed** ([raw results](walkthrough-results.json)) |

The walkthrough is independent of the automated suites: it drives the real browser through the UI the way a citizen, officer and administrator would, on MySQL rather than H2, and checks the visible result of every step.

## 2. Use cases (main and alternate flows)

| UC | Flow checked | Result | Evidence |
|---|---|:-:|---|
| UC-01 | Register as citizen (main + duplicate email + weak password) | ✅ | registered priya.1795425819486@example.com; field errors for taken email and short password |
| UC-04 | Verify mobile with OTP (wrong code, resend cooldown, correct code) | ✅ | wrong code counted down; resend disabled and refused by the server inside the cooldown; correct code verified |
| UC-02 | Log in / log out (role landing, wrong password, inactive, lockout, logout) | ✅ | three roles land on their homes; generic error for wrong password and inactive officer; lockout after 5 failures |
| UC-03 | Change own password (wrong current, mismatch, success, old password rejected) | ✅ | errors shown for wrong current / mismatch; new password works, old refused |
| UC-10 | Submit complaint (validation, wrong file type, too many files, success with photo + PDF) | ✅ | GRV-2026-000012 filed with 2 attachments after required, length, type and count errors |
| UC-11 | Edit complaint (no changes, change title, revision in timeline) | ✅ | no-op edit refused politely; title change recorded as revision |
| UC-12 | Withdraw complaint (reason required, success; refused after work started) | ✅ | GRV-2026-000013 withdrawn with reason; GRV-2026-000001 (In Progress) refused |
| UC-13 | My Complaints / Watching lists (filter, search, watching) | ✅ | own complaints only, status filter, number search, Jayesh watches 000001 |
| UC-14 | Complaint detail & timeline (owner, watcher, officer, admin, duplicate banner, staff names hidden) | ✅ | each role sees the right actions; citizen timeline shows department, not officer name |
| UC-15 | Review resolution: reopen with reason, then close; window and limits enforced | ✅ | GRV-2026-000004: reopen (reason required) → officer start + resolve → close; reopen count shown |
| UC-16 | View / download attachment (owner yes, outsider 404, download name) | ✅ | inline with nosniff, ?download=true as attachment, outsider 404 (/attachments/3) |
| UC-20 | Review new complaint (admin, confirm prompt) | ✅ | GRV-2026-000012 moved to Under Review after a confirm prompt |
| UC-21 | Assign (default department pre-selected) and reassign (remark required) | ✅ | GRV-2026-000012 → Water Supply (default); GRV-2026-000005 reassigned to Drainage with remark |
| UC-23 | Mark duplicate (unknown no., self, other department, success; watcher added) | ✅ | GRV-2026-000012 linked to GRV-2026-000001 after unknown/self/other-department refusals; Sneha now watches GRV-2026-000001 |
| UC-17 | Unwatch and watch again | ✅ | unwatch removes it from Watching; original still reachable from the duplicate; watch again works |
| UC-30 | Department queue (scope, overdue first, filters) | ✅ | Water Supply and Sanitation queues show only their own complaints |
| UC-31 | Start work on a reopened complaint (remark required) | ✅ |  |
| UC-32 | Add progress remark | ✅ |  |
| UC-33 | Resolve (with work-done file) and optimistic-lock conflict on a stale form | ✅ | first resolve wins; second (stale version) gets the "updated by someone else" message |
| UC-34 | Send complaint back to the admin (wrong department) | ✅ | GRV-2026-000005 back to Under Review; Drainage officer loses access (404) |
| UC-22 | Reject (reason required) (reason required; officer rejection is covered by TC-UC-22b/c) | ✅ | GRV-2026-000008 rejected with a reason; owner sees Rejected |
| UC-40/41/42 | Manage departments, categories, areas (create, duplicate name, deactivate, guard) | ✅ | Parks 19486 + category created; duplicate name refused; deactivating a default department refused |
| UC-43 | Manage officer accounts (create, duplicate email, login as new officer) | ✅ | officer.1795425819486@example.com created after a duplicate-email error and can log in to the Water Supply queue |
| UC-44 | Manage citizen accounts (deactivate → login refused, reactivate, reset password) | ✅ | deactivated citizen cannot log in; reactivated; admin reset password works |
| UC-50 | Admin dashboard and reports (cards link to matching lists, CSV export) | ✅ | Total 13 = CSV rows; Pending card 2 opens matching list; chart rendered |
| UC-51 | Officer summary cards | ✅ | Assigned 0 · In Progress 1 · Reopened 0 · Awaiting citizen 0 · Closed 0 · Rejected 0 · Duplicate 2 · Withdrawn 0 · Overdue 0 |
| UC-60 | Notifications (bell count, open marks read, mark all read) | ✅ | Notifications, 10 unread → opened one → all read |
| UC-70 | Auto-close expired resolved complaints (dev job runs every 5 minutes) | ✅ | GRV-2026-000011 (resolved 8 days ago) closed by the scheduler with an auto-close entry |

## 3. Errors and refusals

| Check | Case | Result | Evidence |
|---|---|:-:|---|
| E-404a | Unknown complaint number → 404 page | ✅ |  |
| E-404b | Another citizen's complaint → 404 (not 403: existence is not revealed) | ✅ |  |
| E-404c | Officer opens another department's complaint → 404 | ✅ |  |
| E-404d | Unknown URL and missing static file → 404 without a session cookie | ✅ |  |
| E-403a | Wrong role (citizen opens admin and officer pages) → 403 page | ✅ |  |
| E-403b | Form without CSRF token while logged in → 403 "form expired" | ✅ |  |
| E-401 | Session expired: stale session cookie → /login?expired; JSON endpoint → 401 | ✅ |  |
| E-405 | GET on a POST-only action → 405 page | ✅ |  |
| E-400 | Request rejected by the firewall (encoded path traversal) → 400 page | ✅ | encoded ../, double slash and encoded / all answer the 400 page |
| E-413 | Upload over 26 MB without JavaScript → back to the form with a size message | ✅ |  |
| E-BR | Business refusals: edit after assignment, reopen of a closed complaint, staff action as citizen | ✅ |  |
| E-500 | Unexpected error page with reference id | ✅ | not triggerable from the UI; verified by the automated test TC-VE-14 (500 page shows ERR-XXXX-XXXX, no stack trace) |

## 4. Screens

Every screen of doc 10 was opened at 1280 px and 360 px. Each check asserts: the page renders, **no sideways scroll**, **no table that has to be scrolled sideways and no link or button off screen** (added after V-3), **no JavaScript error**, **no CSP violation**, and at 1280 px **0 axe-core violations** (WCAG 2.1 A/AA). SCR-92 (500 page) is covered by TC-VE-14 because a running build has no safe way to raise a server error.

| Screen | Page | 1280 px | 360 px | Screenshots |
|---|---|:-:|:-:|---|
| SCR-01 | `/login` | ✅ | ✅ | [1280](screenshots/SCR-01-1280.png) · [360](screenshots/SCR-01-360.png) |
| SCR-02 | `/register` | ✅ | ✅ | [1280](screenshots/SCR-02-1280.png) · [360](screenshots/SCR-02-360.png) |
| SCR-03 | `/verify-mobile` | ✅ | ✅ | [1280](screenshots/SCR-03-1280.png) · [360](screenshots/SCR-03-360.png) |
| SCR-04 | `/account/password` | ✅ | ✅ | [1280](screenshots/SCR-04-1280.png) · [360](screenshots/SCR-04-360.png) |
| SCR-10 | `/citizen/complaints` | ✅ | ✅ | [1280](screenshots/SCR-10-1280.png) · [360](screenshots/SCR-10-360.png) |
| SCR-11 | `/citizen/watching` | ✅ | ✅ | [1280](screenshots/SCR-11-1280.png) · [360](screenshots/SCR-11-360.png) |
| SCR-12 | `/citizen/complaints/new` | ✅ | ✅ | [1280](screenshots/SCR-12-1280.png) · [360](screenshots/SCR-12-360.png) |
| SCR-13 | `/citizen/complaints/GRV-2026-000013/submitted` | ✅ | ✅ | [1280](screenshots/SCR-13-1280.png) · [360](screenshots/SCR-13-360.png) |
| SCR-20 | `/complaints/GRV-2026-000001` | ✅ | ✅ | [1280](screenshots/SCR-20-1280.png) · [360](screenshots/SCR-20-360.png) |
| SCR-21 | `/officer/complaints/GRV-2026-000001/resolve` | ✅ | ✅ | [1280](screenshots/SCR-21-1280.png) · [360](screenshots/SCR-21-360.png) |
| SCR-30 | `/officer/queue` | ✅ | ✅ | [1280](screenshots/SCR-30-1280.png) · [360](screenshots/SCR-30-360.png) |
| SCR-40 | `/admin/dashboard` | ✅ | ✅ | [1280](screenshots/SCR-40-1280.png) · [360](screenshots/SCR-40-360.png) |
| SCR-41 | `/admin/complaints` | ✅ | ✅ | [1280](screenshots/SCR-41-1280.png) · [360](screenshots/SCR-41-360.png) |
| SCR-42 | `/admin/departments` | ✅ | ✅ | [1280](screenshots/SCR-42-1280.png) · [360](screenshots/SCR-42-360.png) |
| SCR-43 | `/admin/departments/new` | ✅ | ✅ | [1280](screenshots/SCR-43-1280.png) · [360](screenshots/SCR-43-360.png) |
| SCR-44 | `/admin/categories` | ✅ | ✅ | [1280](screenshots/SCR-44-1280.png) · [360](screenshots/SCR-44-360.png) |
| SCR-45 | `/admin/categories/new` | ✅ | ✅ | [1280](screenshots/SCR-45-1280.png) · [360](screenshots/SCR-45-360.png) |
| SCR-46 | `/admin/areas` | ✅ | ✅ | [1280](screenshots/SCR-46-1280.png) · [360](screenshots/SCR-46-360.png) |
| SCR-47 | `/admin/areas/new` | ✅ | ✅ | [1280](screenshots/SCR-47-1280.png) · [360](screenshots/SCR-47-360.png) |
| SCR-48 | `/admin/officers` | ✅ | ✅ | [1280](screenshots/SCR-48-1280.png) · [360](screenshots/SCR-48-360.png) |
| SCR-49 | `/admin/officers/new` | ✅ | ✅ | [1280](screenshots/SCR-49-1280.png) · [360](screenshots/SCR-49-360.png) |
| SCR-50 | `/admin/citizens` | ✅ | ✅ | [1280](screenshots/SCR-50-1280.png) · [360](screenshots/SCR-50-360.png) |
| SCR-51 | `/admin/officers/2/password` | ✅ | ✅ | [1280](screenshots/SCR-51-1280.png) · [360](screenshots/SCR-51-360.png) |
| SCR-60 | `/notifications` | ✅ | ✅ | [1280](screenshots/SCR-60-1280.png) · [360](screenshots/SCR-60-360.png) |
| SCR-90 | `/admin/dashboard` | ✅ | ✅ | [1280](screenshots/SCR-90-1280.png) · [360](screenshots/SCR-90-360.png) |
| SCR-91 | `/complaints/GRV-2026-999999` | ✅ | ✅ | [1280](screenshots/SCR-91-1280.png) · [360](screenshots/SCR-91-360.png) |
| SCR-93 | `/notifications/read-all` | ✅ | ✅ | [1280](screenshots/SCR-93-1280.png) · [360](screenshots/SCR-93-360.png) |

## 5. Defects found and fixed

| # | Defect | Severity | Found by | Fix | Regression test |
|---|---|---|---|---|---|
| V-1 | **Edit revisions were not shown in the timeline (FR-17).** The "Complaint edited" entry rendered an empty list, so neither the citizen nor staff could see which field changed or its old and new value; the revisions were stored correctly. Cause: `th:switch` on the same `<li>` as `th:each` in `fragments/timeline.html` left every item empty. | Medium (requirement not visible to users) | UC-11 walkthrough | `th:if` conditions instead of the switch | `TimelineRevisionWebTest` (TC-UC-11a, FR-17): fails before, passes after |
| V-3 | **Admin tables were cut off on phones (NFR-20).** At 360 px the officer, citizen, department, category and area lists showed only the first columns; the status and every action button (Edit, Deactivate, Reset password) were off screen and reachable only by scrolling the table sideways, and long emails were cut. The dashboard's overdue table clipped its "Days / SLA" column. Found by looking at the walkthrough screenshots; the earlier automated check only measured page-level scrolling, which a `.table-responsive` hides. | Medium (admin work on a phone was impractical) | Screenshot review of SCR-40, 44, 48, 50 at 360 px | Below 576 px these tables stack each row as a labelled card and wrap the action buttons (`.table-stack`, `data-label`, D129) | New off-screen check in TC-NF-04 (`quality.spec.ts`, now also sweeping the five admin lists) and in the walkthrough: on the old build it reported 24 hidden controls on Officers, 11 on Citizens, 14 on Categories and a clipped dashboard table; 0 after the fix |
| V-2 | **An encoded slash (`%2F`) in the URL got Tomcat's plain "HTTP Status 400" page** instead of the application's 400 page used for every other rejected request (doc 12 D85). No information was leaked and the request was refused either way. | Low (consistency) | E-400 walkthrough | `TomcatConfig` passes `%2F` through so Spring Security's firewall rejects it and the app's page is shown | `CookieAndFirewallHttpTest` TC-SEC-33 (encoded slash, encoded `..`, double slash): fails before, passes after |

The automated suites had missed all three: no test checked the rendered change text of an edit, the firewall test did not use an encoded slash, and the phone-layout check looked only at page-level scrolling.

## 6. Behaviour confirmed as designed (not defects)

| Observation | Why it is correct |
|---|---|
| A wrong file type is reported only after the title/description errors are fixed; a taken email is reported only after the password errors are fixed | Doc 12 §2.2: field rules (Bean Validation) come first, database and file-content checks (step 3) after. Browsers never keep selected files, so the user re-picks them anyway. |
| "Send a new code" is disabled for 60 s after an OTP is issued, and a forced resend is refused by the server | BR-85 resend cooldown |
| A citizen opening someone else's complaint gets **404**, not 403 | D10 / BR-60: the existence of other complaints is not revealed |
| Citizens see "Water Supply Dept." in the timeline, never the officer's name | D48 |

## 7. Not covered here

- **SCR-92 / 500 page from the UI**: no safe trigger in a production build; verified by TC-VE-14.
- **TC-NF-18** keyboard and screen-reader checklist: manual, to tick on the demo laptop.
- **Windows** start and the timing run on the reference laptop: see `docs/reports/test-report-1.0.0.md`.

## 8. Re-running

1. `mysql -u root -p < docs/deploy/scripts/reset-demo-db.sql`
2. `cd app && ./mvnw package -DskipTests && java -jar target/smart-grievance.jar --spring.profiles.active=dev`
3. In another terminal, from a folder with `node_modules` of `app/e2e` linked and a `shots/` folder: `node docs/validation/walkthrough.mjs`

Restart the app between runs: login lockouts and rate limits are kept in memory, and the walkthrough changes the demo data.
