# 13a — Test Catalogue (appendix to doc 13)

| | |
|---|---|
| **Status** | Draft |
| **Depends on** | 13 Test Spec & Traceability (levels, fixtures, helpers, tags), 03 Requirements, 04 Use Cases, 05 Domain Model & Business Rules (§3 transition table, §4 rules, §5 scenarios), 08 Database Design (Q01–Q31), 09 Web & API Contracts (R-xx), 10 UI Spec (SCR-xx), 11 Security Spec (SEC-xx), 12 Validation & Error Handling, Appendix A |

This appendix lists every planned test case. Doc 13 explains the levels, the fixtures and the helpers used here, and holds the traceability matrix. `docs/specs/verify/check-13-traceability.py` reads this file and checks it against the source documents (doc 13 §8).

---

## 0. How to read the tables

**Columns.** Every catalogue table has the test ID in the first column, and its last two columns are always **Traces** (the requirement, rule, use case, route, query, transition or security-test IDs the case proves) and **Pri** (`Must` = must pass before the demo, `Later` = after the demo or only in an extra profile; doc 13 §4.4). The check script reads exactly these two columns.

**Levels** (doc 13 §1):

| Level | Meaning | Runs in |
|---|---|---|
| Unit | Plain JUnit, no Spring context | `mvn test` |
| Repo | `@JdbcTest` on H2 (MySQL mode) with the real Flyway migrations | `mvn test` |
| Web | `@SpringBootTest` + MockMvc + Spring Security, H2, real services | `mvn test` |
| Flow | `@SpringBootTest` without MockMvc: service calls inside the full context, H2 | `mvn test` |
| HTTP | `@SpringBootTest(RANDOM_PORT)`: real Tomcat, `java.net.http.HttpClient`, `Accept: text/html` | `mvn test` |
| Scan | A JUnit test that reads templates, classes, handler mappings or `messages.properties` | `mvn test` |
| MySQL | Against a real MySQL 8.4 (failsafe, tag `mysql`) | `mvn verify -Pmysql` |
| Browser | Playwright + Chromium (+ axe-core) against the running JAR | `mvn verify -Pe2e` |
| Manual | Done by a person with a checklist (doc 13 §5, §7) | before the viva |

**Fixtures** (doc 13 §2.5): **DW** = the demo world of Appendix A (officers, citizens and the 11 sample complaints) created by the real `DevDataSeeder` logic at the test clock time **23-Nov-2026 10:00 IST**; **TD** = a `TestData` builder that inserts exactly the rows a test needs. Users are named by their Appendix A first name (`mahesh`, `jayesh`, `sneha`, `rahul` (unverified), `anita` and `rohan` (Water Supply), `suresh` (Sanitation), `vikram` (Road Maintenance), `pooja` (Drainage), `amit` (Electrical), `kiran` (Electrical, inactive), `admin`). Complaint numbers are shortened to their last six digits (`000001` = `GRV-2026-000001`). A new complaint filed in a test against DW gets `000012`.

**Relations** used in §A (who the actor is for this complaint): **O** owner · **W** active watcher (not the owner) · **D** owner of a linked duplicate without an active watcher row · **C** any other citizen · **F** officer of the complaint's current department · **X** officer of another department · **A** admin · **S** system (scheduler).

---

## A. State machine (doc 05 §3)

### A.1 One test per transition row and actor

Level **Flow**: the test builds the complaint in the *from* status with **TD** (a consistent row: department, `resolved_at`, links), calls the `ComplaintWorkflowService` / `ComplaintService` / `DuplicateService` / `WatchService` method as the actor with the current `version` and a valid 10–500 character remark, then reads the database. Each row is one `@ParameterizedTest` over its *from* statuses. "Notifies" lists exactly the recipients expected in `notification` for the new event (BR-67, BR-68); everyone else gets nothing.

| ID | Title | Level | Fixture | Steps | Expected | Traces | Pri |
|---|---|---|---|---|---|---|---|
| TC-SM-01 | T01 Submit by a verified citizen | Flow | TD: `mahesh` verified, `admin` + a second active admin + one inactive admin; empty `complaint_sequence` | `ComplaintService.submit(valid form, one PNG, mahesh)` | Status `SUBMITTED`; number `GRV-2026-000001`; `complaint_sequence(2026) = 1`; one `CREATED` event (from NULL, to `SUBMITTED`, actor mahesh); one attachment row + file; notifies mahesh (`notify.submitted.owner`, the one deliberate exception to "actor not notified", D104) and both **active** admins (`notify.submitted.admin`), not the inactive admin | T01, FR-12, FR-13, FR-15, BR-10, BR-67, UC-10 | Must |
| TC-SM-02 | T02 Start review by admin | Flow | TD: complaint `SUBMITTED` owned by mahesh | `startReview(no, version, admin)` | `UNDER_REVIEW`; `STATUS_CHANGED` event `SUBMITTED → UNDER_REVIEW`, actor admin; `version + 1`; notifies mahesh only (`notify.reviewStarted`) | T02, FR-31, UC-20 | Must |
| TC-SM-03 | T03 Assign by admin | Flow | TD: `UNDER_REVIEW`; jayesh active watcher; officers anita, rohan (Water Supply), suresh (Sanitation) | `assign(no, version, Water Supply, null, admin)` | `ASSIGNED`, `department_id` = Water Supply; event with `department_id` = Water Supply; notifies owner + jayesh (`notify.assigned.citizen`) and anita + rohan (`notify.assigned.officer`), not suresh | T03, FR-32, FR-70, BR-67, UC-21 | Must |
| TC-SM-04 | T04 Reassign by admin, from `ASSIGNED`, `IN_PROGRESS`, `REOPENED` | Flow | TD: complaint with Water Supply in each from status; pooja (Drainage) | `reassign(no, version, Drainage, remark, admin)` | `ASSIGNED`, department Drainage; `REASSIGNED` event (from = old status, to `ASSIGNED`, department Drainage, remark); notifies owner + watchers (`notify.reassigned.citizen`) and pooja (`notify.reassigned.officer`); Water Supply officers get nothing | T04, FR-33, UC-21 | Later |
| TC-SM-05 | T05 Start work by department officer, from `ASSIGNED`, `REOPENED` | Flow | TD: Water Supply complaint; jayesh watcher | `startWork(no, version, remark, anita)` | `IN_PROGRESS`; event with remark; notifies owner + jayesh (`notify.workStarted`); anita not notified | T05, FR-41, UC-31 | Must |
| TC-SM-06 | T05 Start work by admin (FR-36) | Flow | as TC-SM-05 | `startWork(…, admin)` | as TC-SM-05; officers of the department are not notified (not in the T05 list) | T05, FR-36 | Later |
| TC-SM-07 | Progress remark by department officer, from `IN_PROGRESS` | Flow | TD: `IN_PROGRESS` | `addRemark(no, version, remark, anita)` | status unchanged; `REMARK` event (no from/to) with the remark; `version + 1`; notifies owner + watchers (`notify.remark`) | FR-43, UC-32 | Later |
| TC-SM-08 | Progress remark by admin | Flow | as TC-SM-07 | `addRemark(…, admin)` | as TC-SM-07 | FR-36, FR-43 | Later |
| TC-SM-09 | T06 Resolve by department officer, from `IN_PROGRESS` | Flow | TD: `IN_PROGRESS`, jayesh watcher; clock 23-Nov-2026 10:00 | `resolve(no, version, remark, no files, anita)` | `RESOLVED`, `resolved_at` = 23-Nov-2026 10:00; event; notifies owner with `notify.resolved.owner` containing "Please review it by 30-Nov-2026" and jayesh with `notify.resolved.watcher` | T06, FR-42, BR-30, BR-33, UC-33 | Must |
| TC-SM-10 | T06 Resolve by admin | Flow | as TC-SM-09 | `resolve(…, admin)` | as TC-SM-09 | T06, FR-36 | Later |
| TC-SM-11 | T07 Close by owner within the review window | Flow | TD: `RESOLVED` 2 days ago (Electrical), amit active, kiran inactive | `close(no, version, "Fixed, thanks", mahesh)` | `CLOSED`; event remark = feedback (also with feedback `null`); notifies amit (`notify.closed`), not kiran, not mahesh | T07, FR-25, FR-26, UC-15 | Must |
| TC-SM-12 | T08 Reopen by owner within the window | Flow | as TC-SM-11, `reopen_count` 0 | `reopen(no, version, reason, mahesh)` | `REOPENED`, `reopen_count` 1, `resolved_at` NULL, department unchanged; notifies amit and the admins (`notify.reopened`); watchers get nothing | T08, FR-26, UC-15 | Must |
| TC-SM-13 | T09 Auto-close by the system | Flow | TD: one complaint resolved 8 days ago, one resolved 6 days ago; amit active | `AutoCloseJob.run()` | first: `CLOSED`, `AUTO_CLOSED` event with actor NULL and remark "Auto-closed: no response within 7 days", notifies owner and amit (`notify.autoClosed`); second unchanged; the job logs "closed 1" | T09, FR-82, UC-70 | Must |
| TC-SM-14 | T10 Send back by department officer, from `ASSIGNED`, `REOPENED` | Flow | TD: Road Maintenance complaint; vikram | `sendBack(no, version, remark, vikram)` | `UNDER_REVIEW`, `department_id` NULL; event `department_id` = Road Maintenance (the sender, doc 10 §7.3); notifies all active admins (`notify.sentBack` with "Road Maintenance"); vikram can no longer view it (BR-63) | T10, FR-46, BR-63, UC-34 | Later |
| TC-SM-15 | T11 Reject by admin, from the five open statuses | Flow | TD: complaint in each open status, jayesh watcher | `reject(no, version, reason, admin)` | `REJECTED`; event remark = reason; notifies owner + jayesh (`notify.rejected`) | T11, FR-34, UC-22 | Must |
| TC-SM-16 | T11 Reject by department officer, from `ASSIGNED`, `IN_PROGRESS`, `REOPENED` | Flow | TD: Road Maintenance complaint | `reject(…, vikram)` | as TC-SM-15 | T11, FR-45, UC-22 | Must |
| TC-SM-17 | T12 Mark duplicate by admin, from the five open statuses (original in another department) | Flow | TD: A `ASSIGNED` (Water Supply, owner mahesh); B in each open status (Road Maintenance or none), owner jayesh, watcher sneha | `markDuplicate(B, A, version, note, admin)` | B `DUPLICATE`, `duplicate_of_id` = A; `DUPLICATE_LINKED` on B (note, related A); `DUPLICATE_RECEIVED` on A (related B); jayesh and sneha are active watchers of A; notifies jayesh (`notify.duplicate.owner`) and sneha (`notify.duplicate.watcher`) | T12, FR-35, BR-44, UC-23 | Must |
| TC-SM-18 | T12 Mark duplicate by department officer, from `ASSIGNED`, `IN_PROGRESS`, `REOPENED`, both in the officer's department | Flow | TD: A and B in Water Supply | `markDuplicate(B, A, version, note, anita)` | as TC-SM-17 | T12, FR-44, BR-43, BR-44, UC-23 | Must |
| TC-SM-19 | T13 Withdraw by owner, from `SUBMITTED`, `UNDER_REVIEW`, `ASSIGNED` | Flow | TD: no linked duplicate; for `ASSIGNED` the department is Water Supply | `withdraw(no, version, reason, mahesh)` | `WITHDRAWN`; event remark = reason; notifies the admins (`notify.withdrawn`) and, only for `ASSIGNED`, anita + rohan | T13, FR-18, UC-12 | Must |
| TC-SM-20 | Edit by owner, from `SUBMITTED`, `UNDER_REVIEW` | Flow | TD: complaint with one attachment | `edit(no, form with new title and address, one new PDF, remove the old file, reason, mahesh)` | status unchanged; one `EDITED` event with four revisions (title, address, attachment added, attachment removed); old file soft-deleted; `version + 1`; notifies the admins (`notify.edited`) | FR-16, FR-17, BR-23, BR-26, UC-11 | Must |
| TC-SM-21 | Unwatch by an active watcher, in all ten statuses | Flow | TD: jayesh active watcher | `unwatch(no, jayesh)` | watcher row `active = FALSE`; no event, no notification; the complaint leaves jayesh's Watching list | FR-29, BR-51, UC-17 | Must |
| TC-SM-22 | Watch again by the owner of a linked duplicate, in all ten statuses | Flow | TD: B duplicate of A owned by jayesh; jayesh's row on A inactive (and a second case with no row at all) | `watch(A, jayesh)` | the same row is reactivated (`since` = now) or one new row is inserted; no event | FR-29, BR-52, UC-17 | Must |

### A.2 Expected matrix (test oracle)

Who may perform each detail-page action in each status (doc 05 §3, doc 09 §7.1). A letter means "allowed for this relation"; `·` means nobody. **C** and **X** never appear: they cannot view the complaint, so they get **404** before any rule is applied (BR-61), which TC-UT-01 and the 404 sweep (TC-RT, doc 13 §3.4) test. **F** does not exist for `SUBMITTED` and `UNDER_REVIEW` (no department). Guards are assumed true here and tested in A.3.

| Action | SUBMITTED | UNDER_REVIEW | ASSIGNED | IN_PROGRESS | RESOLVED | REOPENED | CLOSED | REJECTED | DUPLICATE | WITHDRAWN |
|---|---|---|---|---|---|---|---|---|---|---|
| EDIT | O | O | · | · | · | · | · | · | · | · |
| WITHDRAW | O | O | O | · | · | · | · | · | · | · |
| CLOSE | · | · | · | · | O | · | · | · | · | · |
| REOPEN | · | · | · | · | O | · | · | · | · | · |
| UNWATCH | W | W | W | W | W | W | W | W | W | W |
| WATCH | D | D | D | D | D | D | D | D | D | D |
| START_REVIEW | A | · | · | · | · | · | · | · | · | · |
| ASSIGN | · | A | · | · | · | · | · | · | · | · |
| REASSIGN | · | · | A | A | · | A | · | · | · | · |
| START_WORK | · | · | F A | · | · | F A | · | · | · | · |
| ADD_REMARK | · | · | · | F A | · | · | · | · | · | · |
| RESOLVE | · | · | · | F A | · | · | · | · | · | · |
| SEND_BACK | · | · | F | · | · | F | · | · | · | · |
| REJECT | A | A | F A | F A | · | F A | · | · | · | · |
| MARK_DUPLICATE | A | A | F A | F A | · | F A | · | · | · | · |

58 allowed cells. The same matrix is stored as `src/test/resources/transition-matrix.csv` (one line per action, cells such as `FA`); the test reads the file, not the production code, so a wrong rule in `TransitionRules` cannot hide itself (D97). The check script verifies that this table agrees with doc 05 §3 for every transition row.

### A.3 Refused combinations and guards

| ID | Title | Level | Fixture | Steps | Expected | Traces | Pri |
|---|---|---|---|---|---|---|---|
| TC-SM-30 | Matrix: `TransitionRules.decide` for every action × status × relation | Unit | `transition-matrix.csv`; fixed `now`; facts with guards satisfied | 15 actions × 10 statuses × relations O, W, D, F, A (720 cases) | `allowed` equals "the cell contains the relation" for all 720 | T02, T03, T04, T05, T06, T07, T08, T10, T11, T12, T13, FR-19, FR-83, BR-61, D39 | Must |
| TC-SM-31 | `allowedActions` equals the allowed cells | Unit | as TC-SM-30 | for each status × relation (50 cases) | the returned `EnumSet` equals the set of actions whose cell contains the relation | FR-19, D39 | Must |
| TC-SM-32 | The service refuses every not-allowed cell for a viewer | Flow | TD: one complaint per status; actors O, W, D, F, A | call the service method of each not-allowed (action, status, relation) | `InvalidTransitionException` (or `BusinessRuleException` for guards) with the key of doc 12 §5.6; status, `version`, event count, watcher rows and notification count unchanged | FR-83, BR-61, BR-70 | Must |
| TC-SM-33 | Final statuses are read-only | Unit | facts in `CLOSED`, `REJECTED`, `DUPLICATE`, `WITHDRAWN` | `allowedActions` for O, F, A | empty set; for W only `UNWATCH`, for D only `WATCH` | BR-46, FR-83 | Must |
| TC-SM-34 | Review deadline is inclusive | Unit | `resolvedAt` = t | `decide(CLOSE/REOPEN)` at `t + 7 days` and at `t + 7 days + 1 s` | allowed; then refused with `showNotice = true` and key `error.review.ended` | T07, T08, BR-30, BR-31 | Must |
| TC-SM-35 | Auto-close boundary | Flow | TD: resolved exactly 7 days before the clock | run the job; advance the clock 1 s; run again | first run closes nothing; second run closes it | T09, BR-30, FR-82 | Must |
| TC-SM-36 | Reopen limit | Unit | `reopenCount` 2, `max-reopens` 2; then 3 | `decide(REOPEN)`, `decide(CLOSE)` | REOPEN refused with notice `error.reopen.limit`, CLOSE allowed; with limit 3 REOPEN allowed | T08, FR-27, BR-32 | Later |
| TC-SM-37 | Withdraw refused while duplicates are linked (BR-47) | Unit, Flow | `hasLinkedDuplicates = true`; TD: A `ASSIGNED` with B linked | `decide(WITHDRAW)`; POST through the service | notice `error.withdraw.linkedDuplicates`; service refuses with the same key, nothing changed | T13, BR-47, FR-18 | Must |
| TC-SM-38 | Withdraw messages by status | Unit | owner; statuses `IN_PROGRESS`, `RESOLVED`, `REOPENED`, then the four final ones | `decide(WITHDRAW)` | `error.withdraw.workStarted` for the first three; `error.state.notAllowed` for the final ones | T13, FR-18, UC-12 | Must |
| TC-SM-39 | Assign to an inactive department | Flow | TD: `UNDER_REVIEW`; Drainage deactivated | `assign(…, Drainage)` | `FormRejectedException` with `validation.departmentId.inactive` on `departmentId`; complaint unchanged | T03, FR-56, UC-21 | Must |
| TC-SM-40 | Reassign to the same or an inactive department | Flow | TD: `ASSIGNED` Water Supply | `reassign(…, Water Supply)`, `reassign(…, inactive)` | `validation.departmentId.same`; `validation.departmentId.inactive`; unchanged | T04, FR-33 | Later |
| TC-SM-41 | Owner-only actions for watchers and duplicate owners | Unit | relations W and D on a complaint in every status that allows the owner | `decide(EDIT/WITHDRAW/CLOSE/REOPEN)` | refused with `error.owner.only` | FR-28, BR-50 | Must |
| TC-SM-42 | Stale `version` on every versioned action | Flow | TD: complaint in a from status of each action; the stored version is one higher than the form's | each service method with the old version | `ConcurrentUpdateException`, key `error.concurrent.complaint`; nothing written | FR-84, BR-71 | Later |
| TC-SM-43 | Submit refusals | Flow | TD: `rahul` unverified; `mahesh` with 10 complaints in the last 24 h (and one filed 24 h + 1 s ago) | `submit` as rahul; as mahesh | rahul: `BusinessRuleException` BR-86 (`flash.otp.verifyFirst`); mahesh: global error `validation.complaint.dailyLimit` with 10; after moving the clock 1 s past the oldest of the 10 it succeeds | T01, FR-09a, BR-86, BR-88 | Must |
| TC-SM-44 | Original-complaint rules for duplicates | Flow | TD: B `ASSIGNED` Water Supply; candidates: unknown number, B itself, a `DUPLICATE`, one complaint in each of `SUBMITTED`, `UNDER_REVIEW`, `RESOLVED` and the four final statuses, a Road Maintenance `ASSIGNED` complaint | `DuplicateService.preview` and `markDuplicate` as anita and as admin | `error.duplicate.notFound`, `.self`, `.isDuplicate` (names A's original), `.notActive` (names the status label), `.otherDepartment` for anita only; the admin may use the Road Maintenance complaint | T12, BR-40, BR-41, BR-42, BR-43, UC-23 | Must |
| TC-SM-45 | Access follows the current department | Web | DW: `000005` (Road Maintenance), vikram; admin reassigns it to Drainage | vikram: GET `/complaints/…000005`, GET and POST `/officer/complaints/…000005/start` | 404 for all three after the reassignment; pooja gets 200 | BR-63, FR-47 | Must |
| TC-SM-46 | Auto-close skips and continues | Flow | TD: two expired `RESOLVED` complaints; the owner of the first closes it between selection and update (simulated by bumping its version); the second's notification insert is forced to fail once | run the job | first: 0 rows updated, no event; second: rolled back alone and logged at ERROR; a third expired complaint is still closed; next run closes the second | T09, FR-82, UC-70, NFR-30 | Must |

---

## B. Business rules (doc 05 §4)

Rules already proved by §A (BR-30, BR-31, BR-32, BR-40 … BR-43, BR-47, BR-61) are not repeated. The check script proves that every BR of doc 05 is traced by at least one test somewhere in this catalogue.

| ID | Title | Level | Fixture | Steps | Expected | Traces | Pri |
|---|---|---|---|---|---|---|---|
| TC-BR-01 | Number format and yearly restart in IST | Flow | TD: clock 31-Dec-2026 23:59 IST, sequence 2026 = 41 | submit; set the clock to 31-Dec-2026 18:30 **UTC** (= 01-Jan-2027 00:00 IST); submit | `GRV-2026-000042`; then `GRV-2027-000001` and a new `complaint_sequence(2027)` row | BR-10, FR-12, D19 | Must |
| TC-BR-02 | No gap after a rolled-back submit | Flow | TD: storage forced to fail on the next write | submit (fails), submit again | the failed submit leaves no complaint row and `last_number` unchanged; the second gets the number the first would have had | BR-11, BR-25 | Must |
| TC-BR-03 | Number is unique, never changes, ids never shown | Flow, Scan | TD: complaint edited twice | edit; read the row; scan templates | `complaint_no` unchanged; inserting a second row with the same number fails on `uq_complaint_no`; no template builds a URL from `c.id` (only `complaintNo`) | BR-12 | Must |
| TC-BR-04 | Inactive category or area on create and edit | Flow | TD: Garbage and Kothrud deactivated; complaint created earlier with Garbage | submit with Garbage; edit the old complaint keeping Garbage; edit changing to another inactive category | create: field error `validation.categoryId.inactive` / `validation.areaId.inactive`; edit keeping it: accepted; edit to inactive: field error | BR-21, FR-56 | Must |
| TC-BR-05 | File type by magic bytes | Unit | `FileTypeDetector` with byte arrays | detect JPEG `FF D8 FF`, PNG signature, PDF `%PDF-`; an EXE renamed `x.jpg`, HTML renamed `x.jpg`, SVG, GIF, ZIP renamed `x.pdf`, 0-byte file, 3-byte file | first three accepted with the right type; all others refused | BR-22, FR-11, SEC-39 | Must |
| TC-BR-06 | At most 5 active complaint files, counted after the edit | Flow | TD: complaint with 5 active files | edit removing 1 and adding 1; edit adding 1; resolve with 3 and with 4 work-done files | accepted; refused with `validation.files.tooManyAfterEdit` (5, 5, 1); resolution: 3 accepted, 4 refused (`validation.files.tooManyResolution`) | BR-23, FR-11, FR-48 | Must |
| TC-BR-07 | Storage path and cleaned original name | Unit | `FileStorageService` with a temp `app.upload.dir`; clock 23-Nov-2026 | store `../../evil.png`, `a"b<c>.png`, a 300-character name | files at `{dir}/2026/11/{uuid}.png`; names `evil.png`, `abc.png`, cut to 255; canonical path stays inside `{dir}` | BR-24, NFR-05, SEC-40 | Must |
| TC-BR-08 | Files deleted when the transaction rolls back | Flow | TD: notification insert forced to fail after the files were written | submit with 2 files | exception → no complaint; the 2 files no longer exist in the upload dir | BR-25, NFR-30, UC-10 | Must |
| TC-BR-09 | Soft-deleted attachment | Flow, Web | TD: owner removes 1 of 2 files | read rows; GET R-73 as owner, officer, admin | row `deleted = TRUE` with `deleted_by`, `deleted_at`; file still on disk; owner and officer 404, admin 200; timeline "removed on …" | BR-26, BR-62, FR-17 | Must |
| TC-BR-10 | Edit without changes | Flow | TD: `SUBMITTED` complaint | `edit` with identical values and no files | `EditResult(changed = false)`; no event, no revision, `version` unchanged, no notification | BR-27, UC-11 | Must |
| TC-BR-11 | Each resolve starts a full review window | Flow | TD: resolved on 23-Nov | reopen; clock +3 days; start work; resolve | new `resolved_at` = 26-Nov; deadline 03-Dec-2026 in the notification and on the page | BR-33, BR-30 | Must |
| TC-BR-12 | Watchers move to the original, with skips | Flow | TD: A owned by mahesh; B owned by jayesh; B watchers: sneha (active), mahesh (active), rahul (inactive); A watcher rows: sneha inactive | mark B duplicate of A | A gets jayesh (new row) and sneha (row reactivated); mahesh skipped (owner of A); rahul not moved (inactive on B); no duplicate rows | BR-44, FR-44 | Must |
| TC-BR-13 | B's watcher rows stay and receive nothing more | Flow | after TC-BR-12 | resolve A | B's watcher rows unchanged; notifications only for A's owner and A's active watchers; nothing refers to B | BR-45, BR-50 | Must |
| TC-BR-14 | A duplicate is read-only and links to the original | Web | DW: `000002` | GET `/complaints/…000002` as jayesh, anita, admin | banner "Linked to GRV-2026-000001 …" with a link; no action buttons for anyone (watch toggles excepted) | BR-46, FR-24 | Must |
| TC-BR-15 | Watchers get the owner's status notifications | Flow | DW: `000001` (owner mahesh, watcher jayesh) | resolve, then reject a second complaint with a watcher | every status event notifies owner and active watchers with the same template family | BR-50, FR-70 | Must |
| TC-BR-16 | Unwatch keeps access for a duplicate owner | Web | DW: jayesh watches `000001` through `000002`; TD: sneha a plain watcher of another complaint | unwatch as jayesh, then GET `000001`; unwatch as sneha, then GET | jayesh: `302 /complaints/…000001`, then 200 with "Watch again"; sneha: `302 /citizen/watching`, then 404 | BR-51, BR-52, FR-29 | Must |
| TC-BR-17 | Attachment access follows the complaint | Web | DW + TD files on `000001` | GET the file as mahesh, jayesh, anita, admin, sneha, suresh | 200 for the first four, 404 for sneha and suresh | BR-62, NFR-05, UC-16 | Must |
| TC-BR-18 | Lists never show what a user cannot open | Web | DW | list pages R-20, R-21, R-30, R-31 for every DW user; for each listed number GET the detail | every listed complaint opens with 200; no complaint of another owner or department is listed; totals equal the number of rows | BR-64, FR-23, FR-47 | Must |
| TC-BR-19 | Overdue only for open complaints, computed at query time | Flow | TD: Garbage complaint (SLA 3) created 3 days ago; clock moved ±1 s around the due time; one `RESOLVED` past due | read the queue and the dashboard | not overdue at due time, overdue 1 s later; the `RESOLVED` one never overdue; no column stores overdue | BR-65, FR-80 | Must |
| TC-BR-20 | Days overdue in Java, shown to staff only | Web | TD: Water Leakage complaint due 21-Nov-2026 23:00; clock 22-Nov-2026 01:00, then 25-Nov-2026 10:00 | officer queue, admin dashboard overdue list, citizen detail | at 01:00 overdue with `daysOverdue` = 0 (floor, not the H2/MySQL `TIMESTAMPDIFF`), badge "Overdue 0 days"; on 25-Nov "Overdue 3 days"; the owner's detail page has no overdue badge | BR-66, FR-62, D23 | Must |
| TC-BR-21 | Recipients are active staff of the current department | Flow | TD: Electrical complaint; amit active, kiran inactive; one admin inactive | reopen as owner | amit and the active admins notified; kiran and the inactive admin not | BR-67 | Must |
| TC-BR-22 | One notification per recipient and none for the actor | Flow | TD: complaint A whose owner also has an (artificial) active watcher row on A; the admin acts | resolve A as the admin; then force a second insert for the same event and recipient | the owner gets exactly one notification for the event; the acting admin none; the forced duplicate insert fails on `uq_notification_event_recipient` and the whole action rolls back (500 with reference) | BR-68, D22, D84 | Must |
| TC-BR-23 | Fixed templates without names | Unit, Flow | `NotificationMessages` | render every `notify.*` template with the longest arguments; run every transition and read the stored texts | each text ≤ 300 characters; contains the complaint number; contains no user name or email; links (`complaint_id`) point to the complaint | BR-69, D70, D89 | Must |
| TC-BR-24 | One transaction per action | Flow | TD: the notification insert of a resolve forced to fail | resolve | status, event and notifications all absent; `version` unchanged | BR-70, NFR-30 | Must |
| TC-BR-25 | Events and revisions are insert-only | Scan | repository source and SQL | search all repositories for `UPDATE complaint_event`, `DELETE FROM complaint_event`, `UPDATE complaint_revision`, `DELETE FROM complaint_revision` | none found (the test-only `TestDatabase` is excluded) | BR-72, FR-81 | Must |
| TC-BR-26 | Master data and users are never hard-deleted | Scan, Web | handler mappings and SQL | list handler methods with `DELETE` or `/delete`; search repositories for `DELETE FROM department/category/area/app_user` | none (only `HousekeepingJob` deletes notifications and OTPs) | BR-73, FR-50 | Must |
| TC-BR-27 | Timestamps in IST from the clock | Flow, MySQL | TD; MySQL URL with `connectionTimeZone=Asia/Kolkata` | submit at 23-Nov-2026 10:00 IST; read `created_at` through JDBC and with the `mysql` client | `2026-11-23 10:00:00` in both; on MySQL the session time zone is `+05:30` | BR-74, NFR-23, D18 | Later |
| TC-BR-28 | Email stored lower-case, unique across roles | Flow | TD: officer `Anita.Deshmukh@Example.com` exists | register a citizen with `ANITA.deshmukh@example.com` | field error `validation.email.taken`; stored emails all lower-case (`ck_app_user_email_lower`) | BR-80, FR-02 | Must |
| TC-BR-29 | Officer needs a department, others must not have one | Flow, Repo | TD | create an officer without department (service); insert a citizen with a department (SQL) | `validation.departmentId.required`; `ck_app_user_officer_dept` violation | BR-81 | Must |
| TC-BR-30 | The last active admin cannot be deactivated | Flow | TD: one active admin | `UserAdminService.setActive(admin, false)` | `BusinessRuleException` `error.admin.last`; with a second active admin it succeeds | BR-82, D45 | Later |
| TC-BR-31 | Deactivation ends access at the next request | Web | DW: jayesh logged in (session) | admin deactivates jayesh; jayesh's next GET; R-77 | `302 /login?inactive`, session invalid; R-77 `401`; jayesh's complaints unchanged | BR-83, FR-05, SEC-26 | Must |
| TC-BR-32 | Mobile rules | Unit, Flow | `@ValidMobile`; TD: mobile 9800000001 used by mahesh | validate `5800000001`, `98000000`, `+91 98765-43210`; register with 9800000001 | first two refused, third normalised and accepted; duplicate → `validation.mobile.taken` | BR-84, FR-02, FR-09 | Must |
| TC-BR-33 | OTP rules | Flow | TD: rahul; clock fixed | issue; verify wrong ×2 then right; issue; wait 5 min + 1 s; verify; resend before 60 s; 6 sends in an hour | right code accepted after 2 wrong; after expiry `validation.otp.expired`; cooldown `error.otp.cooldown`; 6th send `error.otp.hourlyLimit`; a new OTP marks older ones used | BR-85, FR-09, UC-04 | Must |
| TC-BR-34 | Submit needs a verified mobile | Web | DW: rahul | GET R-22, POST R-23 (hand-made) | both `302 /verify-mobile` with `flash.otp.verifyFirst`; no complaint | BR-86, FR-09a | Must |
| TC-BR-35 | Staff are created verified | Flow, Repo | TD | create an officer; insert an unverified officer by SQL | `mobile_verified = TRUE`; `ck_app_user_staff_verified` violation | BR-87, FR-09c | Must |
| TC-BR-36 | Daily complaint limit (sequential) | Flow | TD: clock fixed; mahesh | submit 10 times, then an 11th; move the clock 24 h past the first; submit | 11th refused with `validation.complaint.dailyLimit`; after the window it succeeds | BR-88, SEC-42 | Must |
| TC-BR-37 | Every user update increments `version` | Flow | TD: officer | change department, deactivate, reset password, mark verified (citizen) | `app_user.version` +1 each time; a form with the old version gets `error.concurrent.user` | BR-89, D59 | Must |

---

## C. Use-case acceptance tests (doc 04)

Given/When/Then, one row per main flow and per alternate flow. Level **Web** unless marked: the test drives the real routes with MockMvc (CSRF token, `version` from the rendered page, redirects followed by hand), starting from **DW** at 23-Nov-2026 10:00 IST. These tests are the executable form of doc 04; their class is `…AcceptanceTest` in the package of the use case (doc 13 §2.8).

| ID | Flow | Given | When | Then | Level | Traces | Pri |
|---|---|---|---|---|---|---|---|
| TC-UC-01a | UC-01 main | a visitor | `POST /register` with Priya Nair, `Priya@Example.com`, `98200 00009`, `Strong@123` twice | `302 /verify-mobile`; an active, unverified CITIZEN `priya@example.com` / `9820000009` with a BCrypt hash; the session is authenticated as Priya; `demoOtp` flash present | Web | UC-01, FR-01, FR-08, FR-09b | Must |
| TC-UC-01b | UC-01 3a | `mahesh@example.com` and `9800000001` exist | register with that email, then with that mobile | `200` same form; `validation.email.taken` on `email`, `validation.mobile.taken` on `mobile`; no user created | Web | UC-01, FR-02 | Must |
| TC-UC-01c | UC-01 3b | a visitor | register with mismatching passwords; with `password1`; with `short1` | field errors `validation.confirmPassword.mismatch`, `validation.password.common`, `validation.password.tooShort`; typed name/email/mobile kept, password fields empty | Web | UC-01, FR-02, NFR-22 | Must |
| TC-UC-02a | UC-02 main | mahesh, anita, admin | each logs in (email in capitals), then logs out | `302` to `/citizen/complaints`, `/officer/queue`, `/admin/dashboard`; logout → `302 /login?logout` with "You have been logged out" | Web | UC-02, FR-03, FR-04 | Must |
| TC-UC-02b | UC-02 2a | kiran is inactive | wrong password for mahesh; right password for kiran; unknown email | all `302 /login?error` with the same text "Invalid email or password, or account inactive" | Web | UC-02, FR-05 | Must |
| TC-UC-02c | UC-02 timeout | mahesh's session expired (invalid requested session id) | GET `/citizen/complaints`; POST a form | `302 /login?expired` for both | Web | UC-02, FR-04 | Must |
| TC-UC-03a | UC-03 main | mahesh logged in | `POST /account/password` with the right current and a new strong password | `302 /account/password` flash "Password changed"; new session id; old password fails at the next login, the new one works | Web | UC-03, FR-06 | Later |
| TC-UC-03b | UC-03 alt | mahesh logged in | current password wrong; new = current | `validation.currentPassword.wrong`; `validation.newPassword.sameAsCurrent` | Web | UC-03, FR-06 | Later |
| TC-UC-04a | UC-04 main | rahul (unverified) logged in | GET `/verify-mobile`; POST the shown demo code | page shows "Demo OTP" and `******0004`; then verified, `302 /citizen/complaints` flash "Mobile verified, you can now file complaints"; New Complaint opens without logging in again (K3) | Web | UC-04, FR-09, FR-09b, SEC-28 | Must |
| TC-UC-04b | UC-04 3a | rahul has an OTP | POST a wrong code twice | "Incorrect OTP, 2 attempts left.", then "1 attempt left." | Web | UC-04, FR-09, BR-85 | Must |
| TC-UC-04c | UC-04 3b | rahul has an OTP | clock + 5 min 1 s, POST the right code; or 3 wrong codes then the right one | global error "OTP expired, please request a new one." in both cases | Web | UC-04, FR-09, SEC-36 | Must |
| TC-UC-04d | UC-04 resend | rahul received an OTP 10 s ago | resend; clock + 60 s, resend; then 4 more resends within the hour | first: flash error "Please wait 50 seconds …"; second: new OTP, the old code no longer works; the 6th send in the hour: `error.otp.hourlyLimit` | Web | UC-04, FR-09, BR-85 | Must |
| TC-UC-04e | UC-04 skip | rahul unverified | GET `/citizen/complaints`; GET `/citizen/complaints/new` | My Complaints shows the verify banner; New Complaint → `302 /verify-mobile` with "Verify your mobile number to file complaints" | Web | UC-04, FR-09a | Must |
| TC-UC-10a | UC-10 main | mahesh verified | GET the form (only active categories and areas), POST valid fields + one PNG | `302 /citizen/complaints/GRV-2026-000012/submitted`; the page shows the number and "View complaint"; detail shows `SUBMITTED`, timeline "Complaint submitted", the thumbnail; mahesh and admin have a notification | Web | UC-10, FR-10, FR-11, FR-12, FR-13, FR-14, FR-15 | Must |
| TC-UC-10b | UC-10 4a | mahesh | POST with a 4-character title and no area | `200`; `validation.title.size`, `validation.areaId.required`; the other values kept; nothing saved | Web | UC-10, FR-10, NFR-22 | Must |
| TC-UC-10c | UC-10 4b | mahesh | POST with an EXE renamed `.jpg`; with a 6 MB PNG; with 6 files | one message per file (`validation.files.type`, `.tooLarge`, `.tooMany`) linked to `#files`; "Please choose your files again" notice; no complaint row, no file on disk | Web | UC-10, FR-11, BR-22 | Must |
| TC-UC-10d | UC-10 6a | the storage service fails | POST a valid complaint with a file | `200` same form; global error containing a reference `ERR-XXXX-XXXX`; no complaint row; sequence unchanged | Web | UC-10, BR-25, NFR-06 | Must |
| TC-UC-11a | UC-11 main | sneha owns `000008` (`UNDER_REVIEW`) | GET edit (pre-filled); POST a new title with reason "Typo" | `302 /complaints/…000008` "Complaint updated"; timeline "Edited: Title changed from '…' to '…'" with the reason; admins notified | Web | UC-11, FR-16, FR-17 | Must |
| TC-UC-11b | UC-11 attachments | `000008` has one photo | remove it and add a PDF | revisions "attachment removed" and "attachment added"; removed file listed as "removed on 23-Nov-2026"; admin can still open it, sneha cannot | Web | UC-11, FR-17, BR-26 | Must |
| TC-UC-11c | UC-11 1a | mahesh owns `000001` (`IN_PROGRESS`) | open the detail; GET and POST edit by hand | no Edit button; GET and POST → `302 /complaints/…000001` with "This complaint can no longer be edited because it is In Progress." | Web | UC-11, FR-19 | Must |
| TC-UC-11d | UC-11 5a | sneha owns `000008` | POST the unchanged form | flash info "No changes to save"; no event | Web | UC-11, BR-27 | Must |
| TC-UC-12a | UC-12 main | sneha owns `000003` (`SUBMITTED`) | GET withdraw page; POST reason "Problem solved by neighbours" | `WITHDRAWN`; detail "Withdrawn on 23-Nov-2026 10:00 AM: Problem solved by neighbours"; admins notified, no officer | Web | UC-12, FR-18 | Must |
| TC-UC-12b | UC-12 assigned | jayesh owns `000005` (`ASSIGNED`, Road Maintenance) | withdraw | Road Maintenance officers (vikram) and admins notified | Web | UC-12, FR-18, FR-70 | Must |
| TC-UC-12c | UC-12 work started | mahesh owns `000001` (`IN_PROGRESS`) | detail; POST withdraw by hand | no Withdraw button; `302` + "Work has already started; it can no longer be withdrawn." | Web | UC-12, FR-19 | Must |
| TC-UC-12d | UC-12 BR-47 | TD: mahesh's complaint `ASSIGNED` with jayesh's duplicate linked | detail; POST withdraw | notice and flash "Other citizens are following this complaint; it cannot be withdrawn."; unchanged | Web | UC-12, BR-47, S6 | Must |
| TC-UC-13a | UC-13 main | mahesh | GET `/citizen/complaints`; with `status=RESOLVED`; with `q=GRV-2026-000004` | his four complaints only, newest first; RESOLVED → `000004`, `000011`; search → `000004` | Web | UC-13, FR-20 | Must |
| TC-UC-13b | UC-13 watching | jayesh | GET `/citizen/watching` | `000001` only (same columns as My Complaints) | Web | UC-13, FR-22 | Must |
| TC-UC-13c | UC-13 empty | a new verified citizen | GET both lists | "No complaints yet …" with New Complaint; "You are not watching any complaints." | Web | UC-13, FR-20, FR-22 | Must |
| TC-UC-14a | UC-14 owner | mahesh | GET `000004` | all fields, attachments, department Electrical, timeline oldest first with "Electrical Dept." as actor, Close and Reopen only, the "Please review by …" box (date checked in TC-UC-15a); no overdue badge | Web | UC-14, FR-21, FR-25 | Must |
| TC-UC-14b | UC-14 officer | anita | GET `000001` | buttons Add update, Resolve, Reject, Mark as duplicate (in doc 09 §7.1 order); staff names in the timeline; no owner contact data | Web | UC-14, FR-21, FR-47, SEC-50 | Must |
| TC-UC-14c | UC-14 3a | jayesh | GET `000002`, follow the banner link | banner "Linked to GRV-2026-000001 which is already being handled"; the link opens `000001` (200) | Web | UC-14, FR-24 | Must |
| TC-UC-14d | UC-14 3b | jayesh | GET `000001` | label "You are watching this complaint"; only Unwatch; no owner actions; owner shown as "The complainant" | Web | UC-14, FR-28 | Must |
| TC-UC-14e | UC-14 access | sneha | GET `000001`; GET `GRV-2026-999999` | the same 404 page for both | Web | UC-14, FR-23, SEC-21 | Must |
| TC-UC-15a | UC-15 close | mahesh owns `000004` (resolved 2 days ago) | detail; GET close; POST feedback "Fixed quickly" | detail shows "Please review by" the date resolved + 7 days and both buttons; after POST `CLOSED`, "Thank you. The complaint is closed"; amit notified, kiran not | Web | UC-15, FR-25, FR-26 | Must |
| TC-UC-15b | UC-15 2a–2b reopen | mahesh owns `000004` | POST reopen "Light failed again last night" | `REOPENED`, reopen count 1, in amit's queue; amit and admin notified | Web | UC-15, FR-26 | Must |
| TC-UC-15c | UC-15 2c | TD: mahesh's complaint `RESOLVED` with reopen count 2 | detail; POST reopen by hand | no Reopen button, notice "Reopen limit reached; please contact the department."; POST refused | Web | UC-15, FR-27 | Later |
| TC-UC-15d | UC-15 exception | mahesh owns `000004`; clock + 6 days (8 days after resolve) | detail; POST close | no buttons; notice "The review period ended on …"; POST refused with the same text | Web | UC-15, BR-31 | Must |
| TC-UC-16a | UC-16 main | `000001` has a PNG and a PDF | GET `/attachments/{png}`; GET `/attachments/{pdf}?download=true` | `200 image/png`, `inline`, `nosniff`; `attachment; filename=…; filename*=UTF-8''…` | Web | UC-16, FR-21, NFR-05 | Must |
| TC-UC-16b | UC-16 missing file | the PNG file was deleted from disk | GET it | 404 page; one ERROR log line with the attachment id | Web | UC-16, NFR-06 | Must |
| TC-UC-16c | UC-16 soft-deleted | the PDF was removed in an edit | GET it as mahesh, anita, admin | 404, 404, 200 | Web | UC-16, BR-26 | Must |
| TC-UC-17a | UC-17 unwatch | jayesh watches `000001` | POST unwatch; GET Watching; anita resolves `000001` | `302 /complaints/…000001` "You will no longer receive updates"; Watching empty; jayesh gets no notification | Web | UC-17, FR-29, S5 | Must |
| TC-UC-17b | UC-17 watch again | after TC-UC-17a | open `000002`, follow the link, POST watch | "You are watching this complaint again"; `000001` back in Watching; the next update notifies him | Web | UC-17, FR-29, BR-52 | Must |
| TC-UC-20a | UC-20 main | admin | GET `/admin/complaints?status=SUBMITTED`; open `000003`; POST Start Review | `000003` listed; `UNDER_REVIEW`, "Review started"; sneha notified | Web | UC-20, FR-30, FR-31 | Must |
| TC-UC-21a | UC-21 assign | `000003` `UNDER_REVIEW` (after TC-UC-20a), category Garbage | GET assign; POST | Sanitation pre-selected; `ASSIGNED` to Sanitation, "Assigned to Sanitation"; sneha and suresh notified | Web | UC-21, FR-32 | Must |
| TC-UC-21b | UC-21 reassign | `000005` `ASSIGNED` to Road Maintenance | POST reassign to Drainage with remark | `ASSIGNED` Drainage, "Reassigned to Drainage"; pooja and jayesh notified; vikram now gets 404 | Web | UC-21, FR-33, BR-63 | Later |
| TC-UC-21c | UC-21 1a | `000008` (category Other, no default) | GET assign; POST without a department | nothing pre-selected; `validation.departmentId.required` | Web | UC-21, FR-32 | Must |
| TC-UC-21d | UC-21 2a | Drainage deactivated meanwhile | POST assign with Drainage's id | `validation.departmentId.inactive`; unchanged | Web | UC-21, FR-56 | Must |
| TC-UC-22a | UC-22 admin | `000003` `SUBMITTED` | admin rejects with reason | `REJECTED` "Complaint rejected"; sneha notified | Web | UC-22, FR-34 | Must |
| TC-UC-22b | UC-22 officer | `000005` `ASSIGNED` Road Maintenance | vikram rejects | `REJECTED`; jayesh notified | Web | UC-22, FR-45 | Must |
| TC-UC-22c | UC-22 not allowed | `000008` (`UNDER_REVIEW`), `000004` (`RESOLVED`) | vikram GETs `000008` reject; admin POSTs reject on `000004` | 404 (no department, not visible); `302` + `error.state.notAllowed` | Web | UC-22, FR-47, FR-83 | Must |
| TC-UC-23a | UC-23 main | TD: `000012` "Leak again near Karve Road" by sneha, `ASSIGNED` Water Supply | anita: GET duplicate, GET step 2 with `original=grv-2026-000001`, POST note | step 2 shows A's title, area Kothrud, status In Progress; then `000012` `DUPLICATE` "Linked to GRV-2026-000001 as a duplicate"; sneha watches `000001`; `000001` timeline "GRV-2026-000012 linked as a duplicate" | Web | UC-23, FR-44, BR-44 | Must |
| TC-UC-23b | UC-23 3a | as TC-UC-23a | step 2 with `000006` (Closed), `000012` (self), `000002` (duplicate), `GRV-2026-999999` | step 1 again with the matching `error.duplicate.*` text on `originalNo` | Web | UC-23, BR-40, BR-41, BR-42 | Must |
| TC-UC-23c | UC-23 cross-department officer | as TC-UC-23a | anita: step 2 with `000005` (Road Maintenance) | "GRV-2026-000005 is not in your department. Use Send back …"; no title, area or status of `000005` in the HTML | Web | UC-23, BR-43, SEC-29, S8 | Must |
| TC-UC-23d | UC-23 admin across departments | TD: Road Maintenance complaint B `ASSIGNED` | admin marks B duplicate of `000001` (Water Supply) | allowed; B `DUPLICATE` | Web | UC-23, FR-35 | Must |
| TC-UC-30a | UC-30 main | anita | GET `/officer/queue`; with `status=ALL`; with `q` | Water Supply complaints only, default ACTIVE group, overdue first then oldest; ALL also shows `000002`; search finds by number | Web | UC-30, FR-40, FR-47 | Must |
| TC-UC-30b | UC-30 resolved row | amit | GET the queue | `000004` and `000011` as "Awaiting citizen until …", no action buttons in the list | Web | UC-30, D34 | Must |
| TC-UC-30c | UC-30 alt | an officer principal whose department cannot be loaded (stubbed; the DB check forbids it) | GET the queue | `200`, page error "Your account has no department; contact the admin.", empty list | Web | UC-30 | Later |
| TC-UC-31a | UC-31 main | TD: Water Supply complaint `ASSIGNED` | anita starts work with remark | `IN_PROGRESS` "Work started"; owner and watchers notified | Web | UC-31, FR-41 | Must |
| TC-UC-31b | UC-31 reopened | `000007` `REOPENED` (Sanitation) | suresh starts work | `IN_PROGRESS`; jayesh notified | Web | UC-31, FR-41 | Must |
| TC-UC-32a | UC-32 main | `000001` | anita adds "Pipe ordered" | timeline entry "Water Supply Dept." for citizens; mahesh and jayesh get "New update on …" | Web | UC-32, FR-43 | Later |
| TC-UC-33a | UC-33 main | `000001` | anita resolves with remark | `RESOLVED`; flash "… The citizen has until 30-Nov-2026 to review it"; mahesh (review text) and jayesh notified | Web | UC-33, FR-42 | Must |
| TC-UC-33b | UC-33 work-done files | `000001` | resolve with 2 PDFs; with 4 files | 2 `RESOLUTION` attachments shown; 4 → `validation.files.tooManyResolution` | Web | UC-33, FR-48 | Later |
| TC-UC-34a | UC-34 main | `000005` `ASSIGNED` Road Maintenance | vikram sends back "Belongs to Drainage" | `302 /officer/queue` "GRV-2026-000005 sent back to the admin"; `UNDER_REVIEW`, no department; admins notified; vikram GET detail → 404 | Web | UC-34, FR-46, BR-63 | Later |
| TC-UC-40a | UC-40 main | admin | add "Parks"; edit its description; deactivate; activate | list shows it with Active/Inactive; flashes as doc 12 §8.4 | Web | UC-40, FR-50 | Must |
| TC-UC-40b | UC-40 unique name | Sanitation exists | add "sanitation " | `validation.name.taken` (case-insensitive, trimmed) | Web | UC-40, FR-50, D29 | Must |
| TC-UC-40c | UC-40 default department | Electrical is the default of Streetlight | deactivate Electrical | `302` + "Electrical is the default department of Streetlight. Change that category first."; still active | Web | UC-40, D42 | Must |
| TC-UC-40d | UC-40 hidden from forms | "Parks" deactivated; TD: a complaint assigned to Parks before | GET assign page; GET that complaint | Parks not offered; the complaint still shows "Parks" | Web | UC-40, FR-56 | Must |
| TC-UC-41a | UC-41 main | admin | add category "Tree Fall" → Parks, SLA 4; edit SLA to 5 | saved; New Complaint offers "Tree Fall" | Web | UC-41, FR-51 | Must |
| TC-UC-41b | UC-41 rules | admin | SLA 0, 91, `abc`; inactive default department | `validation.slaDays.range` (twice), `typeMismatch.slaDays`; `validation.defaultDepartmentId.inactive` | Web | UC-41, FR-51 | Must |
| TC-UC-41c | UC-41 deactivate | Garbage | deactivate | not offered on New Complaint; `000003` still shows Garbage; editing `000003` keeps it (BR-21) | Web | UC-41, FR-56, BR-21 | Must |
| TC-UC-42a | UC-42 main | admin | add "Wakad", rename, deactivate | saved; deactivated area not offered on New Complaint; filters still list it | Web | UC-42, FR-52, FR-56 | Must |
| TC-UC-43a | UC-43 add | admin | add officer Neha (Drainage, strong password) | listed, active, verified; Neha logs in → `/officer/queue` with Drainage complaints | Web | UC-43, FR-53, BR-87 | Must |
| TC-UC-43b | UC-43 duplicates | admin | add an officer with `mahesh@example.com`; with mobile `9800000002` | field errors `validation.email.taken`, `validation.mobile.taken` | Web | UC-43, FR-53, FR-02 | Must |
| TC-UC-43c | UC-43 change department | vikram logged in elsewhere | admin moves vikram to Drainage | vikram's next request sees Drainage; `000005` → 404 for him | Web | UC-43, FR-53, BR-63, SEC-27 | Must |
| TC-UC-43d | UC-43 deactivate | rohan | admin deactivates rohan | rohan cannot log in (`/login?error`); Water Supply complaints unchanged; anita still sees them | Web | UC-43, FR-53, FR-05 | Must |
| TC-UC-43e | UC-43 reset password | admin | reset rohan's password | "Password reset. Give the new password to the user"; the new password works; login lock cleared | Web | UC-43, FR-55 | Later |
| TC-UC-44a | UC-44 deactivate | jayesh | admin deactivates jayesh | jayesh cannot log in; his complaints stay in queues and lists | Web | UC-44, FR-54 | Later |
| TC-UC-44b | UC-44 reset, verify | rahul unverified | admin resets rahul's password; marks rahul verified; marks him again | both work; the second "verify" → "This mobile number is already verified." | Web | UC-44, FR-55, FR-09c | Later |
| TC-UC-50a | UC-50 main | DW | admin GET `/admin/dashboard`, then follow every card and table count | Total 11, Pending 2, Assigned 1, In Progress 1, Reopened 1, Resolved 2, Closed 1, Rejected 1, Duplicate 1, Withdrawn 1; Overdue = rows in the overdue list (incl. `000005`); each link opens R-31 whose total equals the clicked number | Web | UC-50, FR-60, FR-61, FR-62 | Must |
| TC-UC-50b | UC-50 CSV | DW | GET `/admin/complaints/export.csv?status=NOT_FINAL` | `text/csv`, BOM, header + 7 rows (`000001`, `000003`, `000004`, `000005`, `000007`, `000008`, `000011`; none of the final ones) | Web | UC-50, FR-65 | Later |
| TC-UC-50c | UC-50 charts | DW | GET the dashboard | `data-chart` attribute holds the same numbers as the tables | Web | UC-50, FR-64 | Later |
| TC-UC-51a | UC-51 main | anita | GET `/officer/queue` | summary cards for Water Supply only (In Progress 1, Duplicate 1, …) | Web | UC-51, FR-63 | Later |
| TC-UC-60a | UC-60 main | DW: mahesh has unread notifications | GET any page; GET `/notifications`; POST open on the newest; POST read-all | bell `aria-label` "Notifications, N unread"; list newest first; open → `302 /complaints/…` and N − 1; read-all → 0; R-77 → `{"unread":0}` | Web | UC-60, FR-71, FR-72 | Must |
| TC-UC-60b | UC-60 lost access | vikram has a notification about `000005`; `000005` reassigned to Drainage | POST open | `302 /complaints/…000005` → 404 | Web | UC-60, BR-63 | Must |
| TC-UC-70a | UC-70 main | DW (`000011` resolved 8 days ago, `000004` 2 days ago) | run `AutoCloseJob` | `000011` `CLOSED` with an `AUTO_CLOSED` entry "System"; `000004` unchanged; mahesh and amit notified; log "Auto-close: 1 complaint(s) closed" | Flow | UC-70, FR-82, S9 | Must |
| TC-UC-70b | UC-70 exception | see TC-SM-46 | — | one failure does not stop the run | Flow | UC-70, FR-82 | Must |

---

## D. Route tests (doc 09 §3, K11)

One MockMvc test class per controller, one nested group per route row (doc 13 §3.4). Each row below is the **happy path** plus the **route-specific error columns** of doc 09. The three generated sweeps cover every route at once, driven by `src/test/resources/routes.csv` (the doc 09 catalogue as data, D98):

| ID | Title | Level | Fixture | Steps | Expected | Traces | Pri |
|---|---|---|---|---|---|---|---|
| TC-RT-G1 | Wrong-role sweep | Web | DW; one user per role | for each of the 85 role-restricted routes and each of the two wrong roles: the request with a valid CSRF token and valid-looking input | `403` page `error/403`; database unchanged (row counts and `version`s of DW compared before/after) | SEC-16, SEC-17, SEC-18, NFR-02 | Must |
| TC-RT-G2 | Not-visible and unknown sweep | Web | DW; sneha (citizen), suresh (Sanitation officer), admin | every route with `{no}`: as the right role who cannot view it (`000001` for sneha and suresh), and with `GRV-2026-999999`; every route with `{id}`: unknown id `999999`, malformed id `abc` | `404` page, the same body for "not visible" and "unknown"; nothing changed | SEC-20, SEC-21, SEC-22, SEC-29b, FR-23, FR-47, BR-60 | Must |
| TC-RT-G3 | Anonymous sweep | Web | none | every route that is not public, without a session | `302 /login`; R-77 `401` JSON; no session created for R-77 | SEC-01, SEC-02, FR-03 | Must |
| TC-RT-G4 | Route inventory | Scan | the running context | compare `RequestMappingHandlerMapping` with `routes.csv` both ways | same 99 (method, path) pairs; every path under a known prefix; a test controller under `/x/` makes the comparison fail | SEC-19, D65 | Must |

Columns of the per-route rows: **Fixture / user** (DW unless noted) · **Happy path** · **Route-specific variants** (V = validation → `200` same view, B = refusal → `302` + `flashError`). Wrong role (403), not visible (404) and anonymous are covered by G1–G3 and are not repeated.

| ID | Route | Level | Fixture / user | Happy path | Route-specific variants | Traces | Pri |
|---|---|---|---|---|---|---|---|
| TC-RT-01 | R-01 `GET /` | Web | visitor; mahesh; anita; admin | visitor `302 /login`; each role `302` to its home | — | R-01, FR-03 | Must |
| TC-RT-02 | R-02 `GET /login` | Web | visitor | `200 auth/login`; flags `error`, `locked`, `logout`, `expired`, `inactive` each show their alert | logged in → `302 /`; email shown once after a failed login (SEC-05) | R-02, FR-03, FR-04, FR-05 | Must |
| TC-RT-03 | R-03 `POST /login` | Web | each role | `302` by role; saved GET request honoured if the role may open it | wrong / inactive / unknown → `/login?error`; 5 failures → `/login?locked`; no CSRF → 403 | R-03, FR-03, FR-05, SEC-07 | Must |
| TC-RT-04 | R-04 `POST /logout` | Web | mahesh | `302 /login?logout`, session invalidated, cookie deleted | `GET /logout` → 404 | R-04, FR-04, SEC-15 | Must |
| TC-RT-05 | R-05 `GET /register` | Web | visitor | `200 auth/register`, empty `form`, privacy notice | logged in → `302 /` | R-05, FR-01 | Must |
| TC-RT-06 | R-06 `POST /register` | Web | visitor | `302 /verify-mobile`, logged in, `flashSuccess`, `demoOtp` | V: each §3.2 message; taken email/mobile; 11th from one IP → global error; extra `role=ADMIN` ignored | R-06, FR-01, FR-02, FR-08, SEC-09, SEC-46 | Must |
| TC-RT-07 | R-07 `GET /account/password` | Web | each role | `200 auth/change-password` | — | R-07, FR-06 | Later |
| TC-RT-08 | R-08 `POST /account/password` | Web | mahesh | `302 /account/password` "Password changed", new session id | V: wrong current, same as current, weak, mismatch | R-08, FR-06, FR-02 | Later |
| TC-RT-10 | R-10 `GET /verify-mobile` | Web | rahul | `200 auth/verify-mobile`, OTP issued once (a refresh issues none), `otp.demoCode` set only in the issuing response | verified citizen → `302 /citizen/complaints` + `flashInfo` | R-10, FR-09, FR-09b, D41 | Must |
| TC-RT-11 | R-11 `POST /verify-mobile` | Web | rahul | right code → verified, principal refreshed, `302 /citizen/complaints` | V: blank, 5 digits, wrong (attempts left), expired / used up (global) | R-11, FR-09, BR-85 | Must |
| TC-RT-12 | R-12 `POST /verify-mobile/resend` | Web | rahul | `302 /verify-mobile`, "A new OTP was sent to ******0004", `demoOtp` | B: cooldown; hourly limit | R-12, FR-09, BR-85 | Must |
| TC-RT-20 | R-20 `GET /citizen/complaints` | Web | mahesh; rahul | `200 citizen/my-complaints`, 4 rows newest first; rahul sees `verifyBanner` | `status=FOO`, `page=0`, `size=500` ignored or clamped; `status=PENDING` group | R-20, FR-20, FR-09a, NFR-11 | Must |
| TC-RT-21 | R-21 `GET /citizen/watching` | Web | jayesh | `200 citizen/watching`, `000001` | inactive watcher rows not listed | R-21, FR-22, BR-51 | Must |
| TC-RT-22 | R-22 `GET /citizen/complaints/new` | Web | mahesh | `200 citizen/complaint-form`, `mode=CREATE`, only active categories/areas, `maxFiles` 5 | unverified → `302 /verify-mobile` | R-22, FR-10, FR-56, FR-09a | Must |
| TC-RT-23 | R-23 `POST /citizen/complaints` | Web | mahesh | `302 /citizen/complaints/{no}/submitted` | V: fields, files, inactive lookups, daily limit, storage failure; unverified → `/verify-mobile` (oversize: TC-SEC-38, HTTP) | R-23, FR-10, FR-11, FR-12, BR-88 | Must |
| TC-RT-24 | R-24 `GET /citizen/complaints/{no}/submitted` | Web | mahesh after submit | `200 citizen/submitted` with number and link | jayesh (watcher of `000001`) → 404 | R-24, FR-14, SEC-25 | Must |
| TC-RT-25 | R-25 `GET /citizen/complaints/{no}/edit` | Web | sneha, `000008` | `200`, `mode=EDIT`, pre-filled, current attachments, current inactive category kept in the list | B: `000001` (In Progress); jayesh on `000001` → `error.owner.only` | R-25, FR-16, FR-19, BR-21 | Must |
| TC-RT-26 | R-26 `POST /citizen/complaints/{no}/edit` | Web | sneha, `000008` | `302 /complaints/{no}` "Complaint updated" or "No changes to save" | V: fields, files after edit > 5; B: status, stale version; foreign `removeAttachmentIds` ignored | R-26, FR-16, FR-17, BR-23, BR-27 | Must |
| TC-RT-27 | R-27 `GET /citizen/complaints/{no}/withdraw` | Web | sneha, `000003` | `200 complaint/action-form`, `action=WITHDRAW` | B: In Progress; BR-47 | R-27, FR-18 | Must |
| TC-RT-28 | R-28 `POST /citizen/complaints/{no}/withdraw` | Web | sneha, `000003` | `302` "Complaint withdrawn" | V: reason < 10 / > 500; B: status, BR-47, stale version | R-28, FR-18, FR-83 | Must |
| TC-RT-29 | R-29 `GET /complaints/{no}` | Web | owner, watcher, dup owner, officer, admin | `200 complaint/detail`; `actionLinks` equal TC-SM-31 for that viewer; banners per status | unknown / not visible → identical 404 | R-29, FR-21, FR-23, FR-24, FR-28, FR-47 | Must |
| TC-RT-30 | R-30 `GET /officer/queue` | Web | anita; amit | `200 officer/queue`, ACTIVE default, overdue first, `summary` cards | filters `status=ALL`, `categoryId`, `areaId`, `q`, bad values ignored | R-30, FR-40, FR-47, FR-63 | Must |
| TC-RT-31 | R-31 `GET /admin/complaints` | Web | admin | `200 admin/complaints`, 11 rows over 2 pages, newest first | every filter and group, `departmentId=NONE`, `overdue=true`, `from > to` page error, search `50%` literal | R-31, FR-30, FR-61, FR-62 | Must |
| TC-RT-32 | R-32 `GET /admin/complaints/export.csv` | Web | admin | `200 text/csv`, BOM, `Content-Disposition` with timestamp name, escaped cells, no email/mobile column | B: more than `app.export.max-rows` (set to 5 in the test) → `302 /admin/complaints?…` + "Too many rows" | R-32, FR-65, SEC-47, SEC-51 | Later |
| TC-RT-40 | R-40 `GET /citizen/complaints/{no}/close` | Web | mahesh, `000004` | `200 action-form`, `action=CLOSE` | B: `000001`; jayesh; window expired | R-40, FR-25 | Must |
| TC-RT-41 | R-41 `POST /citizen/complaints/{no}/close` | Web | mahesh, `000004` | `302` "Thank you. The complaint is closed" | V: feedback > 500; B: status, window, stale version | R-41, FR-26, FR-83 | Must |
| TC-RT-42 | R-42 `GET /citizen/complaints/{no}/reopen` | Web | mahesh, `000004` | `200`, `action=REOPEN`, `reopensLeft` 2 | B: as R-40; limit reached | R-42, FR-25, FR-27 | Must |
| TC-RT-43 | R-43 `POST /citizen/complaints/{no}/reopen` | Web | mahesh, `000004` | `302` "Complaint reopened and sent back to the department" | V: reason length; B: as R-42, stale version | R-43, FR-26, FR-27, FR-83 | Must |
| TC-RT-44 | R-44 `GET /officer/complaints/{no}/start` | Web | suresh, `000007` | `200`, `action=START_WORK` | B: anita on `000001` (In Progress) | R-44, FR-41, FR-47 | Must |
| TC-RT-45 | R-45 `POST /officer/complaints/{no}/start` | Web | suresh, `000007` | `302` "Work started" | V: remark; B: status, stale version | R-45, FR-41, FR-83 | Must |
| TC-RT-46 | R-46 `GET /officer/complaints/{no}/remark` | Web | anita, `000001` | `200`, `action=ADD_REMARK` | B: suresh on `000007` (Reopened) | R-46, FR-43 | Later |
| TC-RT-47 | R-47 `POST /officer/complaints/{no}/remark` | Web | anita, `000001` | `302` "Update added" | V; B | R-47, FR-43, FR-83 | Later |
| TC-RT-48 | R-48 `GET /officer/complaints/{no}/resolve` | Web | anita, `000001` | `200`, `action=RESOLVE`, `maxFiles` 3 | B: vikram on `000005` (Assigned) | R-48, FR-42 | Must |
| TC-RT-49 | R-49 `POST /officer/complaints/{no}/resolve` | Web | anita, `000001` | `302` with the review date in the flash | V: remark, files; B: status, stale version | R-49, FR-42, FR-48, FR-83 | Must |
| TC-RT-50 | R-50 `GET /officer/complaints/{no}/send-back` | Web | vikram, `000005` | `200`, `action=SEND_BACK` | B: anita on `000001` | R-50, FR-46 | Later |
| TC-RT-51 | R-51 `POST /officer/complaints/{no}/send-back` | Web | vikram, `000005` | **`302 /officer/queue`** "GRV-2026-000005 sent back to the admin" | V; B | R-51, FR-46, FR-83 | Later |
| TC-RT-52 | R-52 `GET /officer/complaints/{no}/reject` | Web | vikram, `000005` | `200`, `action=REJECT` | B: amit on `000004` (Resolved) | R-52, FR-45 | Must |
| TC-RT-53 | R-53 `POST /officer/complaints/{no}/reject` | Web | vikram, `000005` | `302` "Complaint rejected" | V; B | R-53, FR-45, FR-83 | Must |
| TC-RT-54 | R-54 `GET /officer/complaints/{no}/duplicate` | Web | anita, TD `000012` | step 1; step 2 with `original=000001` shows the preview | rule failures → step 1 with field error; other department without preview; B: B not allowed | R-54, FR-44, BR-43, SEC-29 | Must |
| TC-RT-55 | R-55 `POST /officer/complaints/{no}/duplicate` | Web | anita, TD `000012` | `302` "Linked to GRV-2026-000001 as a duplicate" | V: note (step 2 again); B: A changed meanwhile, stale version | R-55, FR-44, FR-83 | Must |
| TC-RT-56 | R-56 `POST /admin/complaints/{no}/review` | Web | admin, `000003` | `302` "Review started" | B: `000008` (already Under Review), stale version | R-56, FR-31, FR-83 | Must |
| TC-RT-57 | R-57 `GET /admin/complaints/{no}/assign` | Web | admin, `000008` | `200`, `action=ASSIGN`, active departments, no default (Other) | B: `000003` (Submitted) | R-57, FR-32, FR-56 | Must |
| TC-RT-58 | R-58 `POST /admin/complaints/{no}/assign` | Web | admin, `000008` | `302` "Assigned to Road Maintenance" | V: none, inactive; B: status, stale version | R-58, FR-32, FR-83 | Must |
| TC-RT-59 | R-59 `GET /admin/complaints/{no}/reassign` | Web | admin, `000005` | `200`, departments without the current one | B: `000003` | R-59, FR-33 | Later |
| TC-RT-60 | R-60 `POST /admin/complaints/{no}/reassign` | Web | admin, `000005` | `302` "Reassigned to Drainage" | V: same, inactive, remark; B | R-60, FR-33, FR-83 | Later |
| TC-RT-61 | R-61 `GET /admin/complaints/{no}/reject` | Web | admin, `000003` | `200`, `action=REJECT` | B: `000004` (Resolved), `000006` (Closed) | R-61, FR-34 | Must |
| TC-RT-62 | R-62 `POST /admin/complaints/{no}/reject` | Web | admin, `000003` | `302` "Complaint rejected" | V; B | R-62, FR-34, FR-83 | Must |
| TC-RT-63 | R-63 `GET /admin/complaints/{no}/duplicate` | Web | admin, `000005` | step 2 with `original=000001` (other department) shows the preview | rule failures except BR-43 | R-63, FR-35 | Must |
| TC-RT-64 | R-64 `POST /admin/complaints/{no}/duplicate` | Web | admin, `000005` | `302` "Linked to GRV-2026-000001 as a duplicate" | V; B | R-64, FR-35, FR-83 | Must |
| TC-RT-65 | R-65 `GET /admin/complaints/{no}/start` | Web | admin, `000005` | `200`, `action=START_WORK` | B: `000001` | R-65, FR-36 | Later |
| TC-RT-66 | R-66 `POST /admin/complaints/{no}/start` | Web | admin, `000005` | `302` "Work started" | V; B | R-66, FR-36, FR-83 | Later |
| TC-RT-67 | R-67 `GET /admin/complaints/{no}/remark` | Web | admin, `000001` | `200`, `action=ADD_REMARK` | B: `000005` | R-67, FR-36, FR-43 | Later |
| TC-RT-68 | R-68 `POST /admin/complaints/{no}/remark` | Web | admin, `000001` | `302` "Update added" | V; B | R-68, FR-36, FR-43, FR-83 | Later |
| TC-RT-69 | R-69 `GET /admin/complaints/{no}/resolve` | Web | admin, `000001` | `200`, `action=RESOLVE` | B: `000005` | R-69, FR-36, FR-42 | Later |
| TC-RT-70 | R-70 `POST /admin/complaints/{no}/resolve` | Web | admin, `000001` | `302` with the review date | V; B | R-70, FR-36, FR-42, FR-83 | Later |
| TC-RT-71 | R-71 `POST /citizen/complaints/{no}/unwatch` | Web | jayesh, `000001` | `302 /complaints/…000001` (still visible via `000002`) "You will no longer receive updates" | B: not watching; owner → `error.watch.ownComplaint` | R-71, FR-29, BR-51 | Must |
| TC-RT-72 | R-72 `POST /citizen/complaints/{no}/watch` | Web | jayesh after unwatch | `302` "You are watching this complaint again" | B: already watching; owner | R-72, FR-29, BR-52 | Must |
| TC-RT-73 | R-73 `GET /attachments/{id}` | Web | mahesh, file on `000001` | `200` stream, headers of doc 09 §9.4; `?download=true` | deleted → 404 except admin; file missing → 404 + ERROR; `?download=maybe` → 400 | R-73, FR-21, NFR-05, SEC-41 | Must |
| TC-RT-74 | R-74 `GET /notifications` | Web | mahesh | `200 notification/list`, newest first, paged | `size=500` clamped to 50 | R-74, FR-72, NFR-11 | Must |
| TC-RT-75 | R-75 `POST /notifications/{id}/open` | Web | mahesh | marks read, `302 /complaints/{no}` (or `/notifications` without complaint) | another user's id → 404, row unchanged | R-75, FR-72, SEC-24 | Must |
| TC-RT-76 | R-76 `POST /notifications/read-all` | Web | mahesh | `302 /notifications`, own unread = 0, others' unchanged | — | R-76, FR-72, SEC-24 | Must |
| TC-RT-77 | R-77 `GET /notifications/unread-count` | Web | mahesh | `200 application/json {"unread":n}` | anonymous `401 {"error":"unauthenticated"}`; server error → `500` JSON with reference | R-77, FR-71, SEC-02 | Must |
| TC-RT-78 | R-78 `GET /admin/dashboard` | Web | admin | `200 admin/dashboard`, `dash` as TC-UC-50a, links to R-31 | — | R-78, FR-60, FR-61, FR-62 | Must |
| TC-RT-80 | R-80 `GET /admin/departments` | Web | admin | `200 admin/departments`, 5 rows; `active=false` filter | — | R-80, FR-50 | Must |
| TC-RT-81 | R-81 `GET /admin/departments/new` | Web | admin | `200 admin/department-form`, `mode=CREATE` | — | R-81, FR-50 | Must |
| TC-RT-82 | R-82 `POST /admin/departments` | Web | admin | `302 /admin/departments` "Department saved" | V: blank, 1 char, taken (case-insensitive), race on `uq_department_name` | R-82, FR-50, D84 | Must |
| TC-RT-83 | R-83 `GET /admin/departments/{id}/edit` | Web | admin | `200`, `mode=EDIT`, values | unknown id → 404 | R-83, FR-50 | Must |
| TC-RT-84 | R-84 `POST /admin/departments/{id}` | Web | admin | `302` "… saved" | V as R-82 (own name allowed) | R-84, FR-50 | Must |
| TC-RT-85 | R-85 `POST /admin/departments/{id}/activate` | Web | admin | `302` "… activated" | — | R-85, FR-50 | Must |
| TC-RT-86 | R-86 `POST /admin/departments/{id}/deactivate` | Web | admin, new department | `302` "… deactivated …"; hidden from assign forms | B: default of an active category (D42) | R-86, FR-50, FR-56 | Must |
| TC-RT-87 | R-87 `GET /admin/categories` | Web | admin | `200 admin/categories`, 6 rows with default department and SLA | — | R-87, FR-51 | Must |
| TC-RT-88 | R-88 `GET /admin/categories/new` | Web | admin | `200 admin/category-form`, active departments + "Admin decides" | — | R-88, FR-51 | Must |
| TC-RT-89 | R-89 `POST /admin/categories` | Web | admin | `302` "Category saved" | V: name, SLA 0 / 91 / `abc`, inactive default | R-89, FR-51 | Must |
| TC-RT-90 | R-90 `GET /admin/categories/{id}/edit` | Web | admin | `200`, `mode=EDIT` | unknown id → 404 | R-90, FR-51 | Must |
| TC-RT-91 | R-91 `POST /admin/categories/{id}` | Web | admin | `302` "… saved" | V as R-89 | R-91, FR-51 | Must |
| TC-RT-92 | R-92 `POST /admin/categories/{id}/activate` | Web | admin | `302` "… activated" | — | R-92, FR-51 | Must |
| TC-RT-93 | R-93 `POST /admin/categories/{id}/deactivate` | Web | admin | `302`; not offered on New Complaint | — | R-93, FR-51, FR-56 | Must |
| TC-RT-94 | R-94 `GET /admin/areas` | Web | admin | `200 admin/areas`, 7 rows | — | R-94, FR-52 | Must |
| TC-RT-95 | R-95 `GET /admin/areas/new` | Web | admin | `200 admin/area-form` | — | R-95, FR-52 | Must |
| TC-RT-96 | R-96 `POST /admin/areas` | Web | admin | `302` "Area saved" | V: name rules, taken | R-96, FR-52 | Must |
| TC-RT-97 | R-97 `GET /admin/areas/{id}/edit` | Web | admin | `200`, `mode=EDIT` | unknown id → 404 | R-97, FR-52 | Must |
| TC-RT-98 | R-98 `POST /admin/areas/{id}` | Web | admin | `302` "… saved" | V | R-98, FR-52 | Must |
| TC-RT-99 | R-99 `POST /admin/areas/{id}/activate` | Web | admin | `302` "… activated" | — | R-99, FR-52 | Must |
| TC-RT-100 | R-100 `POST /admin/areas/{id}/deactivate` | Web | admin | `302`; not offered on New Complaint | — | R-100, FR-52, FR-56 | Must |
| TC-RT-101 | R-101 `GET /admin/officers` | Web | admin | `200 admin/officers`, 7 officers; filters department, active, `q` | — | R-101, FR-53 | Must |
| TC-RT-102 | R-102 `GET /admin/officers/new` | Web | admin | `200 admin/officer-form`, active departments | — | R-102, FR-53 | Must |
| TC-RT-103 | R-103 `POST /admin/officers` | Web | admin | `302` "Officer account created"; active, verified | V: §3.3 rules, taken, inactive department; extra `role=ADMIN` ignored | R-103, FR-53, FR-02, BR-87 | Must |
| TC-RT-104 | R-104 `GET /admin/officers/{id}/edit` | Web | admin, anita | `200`, `OfficerEditForm` with `version` | mahesh's id → 404 (SEC-29b) | R-104, FR-53 | Must |
| TC-RT-105 | R-105 `POST /admin/officers/{id}` | Web | admin, anita | `302` "Officer updated"; `version` + 1 | V; B: stale version; extra fields ignored (SEC-46) | R-105, FR-53, BR-89 | Must |
| TC-RT-106 | R-106 `POST /admin/officers/{id}/activate` | Web | admin, kiran | `302` "Account activated" | B: stale version | R-106, FR-53 | Must |
| TC-RT-107 | R-107 `POST /admin/officers/{id}/deactivate` | Web | admin, rohan | `302` "Account deactivated"; his session ends | B: stale version | R-107, FR-53, BR-83 | Must |
| TC-RT-108 | R-108 `GET /admin/officers/{id}/password` | Web | admin, rohan | `200 admin/reset-password`, `backUrl` = officers | citizen id → 404 | R-108, FR-55 | Later |
| TC-RT-109 | R-109 `POST /admin/officers/{id}/password` | Web | admin, rohan | `302` "Password reset …"; lock cleared | V: weak, mismatch, personal data | R-109, FR-55, FR-02 | Later |
| TC-RT-110 | R-110 `GET /admin/citizens` | Web | admin | `200 admin/citizens`, 4 citizens; filters `q`, `active`, `verified` | — | R-110, FR-54 | Later |
| TC-RT-111 | R-111 `POST /admin/citizens/{id}/activate` | Web | admin | `302` "Account activated" | B: stale version | R-111, FR-54 | Later |
| TC-RT-112 | R-112 `POST /admin/citizens/{id}/deactivate` | Web | admin, jayesh | `302` "Account deactivated"; session ends | B | R-112, FR-54, BR-83 | Later |
| TC-RT-113 | R-113 `POST /admin/citizens/{id}/verify` | Web | admin, rahul | `302` "Mobile marked as verified" | B: already verified | R-113, FR-09c | Later |
| TC-RT-114 | R-114 `GET /admin/citizens/{id}/password` | Web | admin, sneha | `200 admin/reset-password`, `backUrl` = citizens | officer id → 404 | R-114, FR-55 | Later |
| TC-RT-115 | R-115 `POST /admin/citizens/{id}/password` | Web | admin, sneha | `302` "Password reset" | V | R-115, FR-55 | Later |

---

## E. Security tests (doc 11 §12)

Doc 11 §12 defines what each `SEC-xx` checks and what it expects; this table only maps each one to its test ID, level, test class and priority, so the expectations are not written twice. `TC-SEC-nn` always tests `SEC-nn`. The doc 11 MockMvc note applies to every test that reuses a session: it must use `Sessions.requestedSession(session)` (doc 13 §2.6). Real-Tomcat tests send `Accept: text/html` (doc 12 F-3).

| ID | Title | Level | Test class (under `com.smartgrievance`) | Traces | Pri |
|---|---|---|---|---|---|
| TC-SEC-01 | Anonymous HTML requests go to the login page | Web | `security.AuthenticationWebTest` | SEC-01, FR-03, NFR-02 | Must |
| TC-SEC-02 | Anonymous unread-count gets 401 JSON, no session | Web | `security.AuthenticationWebTest` | SEC-02, FR-71, R-77 | Must |
| TC-SEC-03 | Login after a 401 lands on the role home | Web | `security.AuthenticationWebTest` | SEC-03, FR-03 | Must |
| TC-SEC-04 | Login per role, email in capitals | Web | `security.AuthenticationWebTest` | SEC-04, FR-03, BR-80 | Must |
| TC-SEC-05 | Failed login keeps the lower-cased email once | Web | `security.AuthenticationWebTest` | SEC-05, FR-05, D71 | Must |
| TC-SEC-06 | Inactive, wrong and unknown give the same answer | Web | `security.AuthenticationWebTest` | SEC-06, FR-05, D57 | Must |
| TC-SEC-07 | Account lockout, unlock by time and by reset | Web | `security.LoginThrottleWebTest` | SEC-07, FR-05, D56 | Must |
| TC-SEC-08 | Per-IP lockout | Web | `security.LoginThrottleWebTest` | SEC-08, D56 | Must |
| TC-SEC-09 | Registration limit per IP | Web | `security.LoginThrottleWebTest` | SEC-09, FR-01, D56 | Must |
| TC-SEC-10 | CSRF token required on POST | Web | `security.CsrfAndSessionWebTest` | SEC-10, NFR-03 | Must |
| TC-SEC-11 | Stale token: expired session and no session | Web | `security.CsrfAndSessionWebTest` | SEC-11, NFR-03, D62 | Must |
| TC-SEC-12 | Session id changes at login (uses `requestedSession`) | Web | `security.CsrfAndSessionWebTest` | SEC-12, FR-03, D58 | Must |
| TC-SEC-13 | Registration auto-login changes the session id | Web | `security.CsrfAndSessionWebTest` | SEC-13, FR-01, D60 | Must |
| TC-SEC-14 | Invalid requested session id → `/login?expired` | Web | `security.CsrfAndSessionWebTest` | SEC-14, FR-04, D61 | Must |
| TC-SEC-15 | Logout only by POST; cookie deleted | Web | `security.CsrfAndSessionWebTest` | SEC-15, FR-04 | Must |
| TC-SEC-16 | Every role-restricted route as each wrong role (generated, = TC-RT-G1) | Web | `security.RoleSweepWebTest` | SEC-16, NFR-02, D98 | Must |
| TC-SEC-17 | Staff on `/verify-mobile` get 403 | Web | `security.RoleSweepWebTest` | SEC-17, FR-09, NFR-02 | Must |
| TC-SEC-18 | Wrong-role POSTs change nothing | Web | `security.RoleSweepWebTest` | SEC-18, NFR-02 | Must |
| TC-SEC-19 | Route inventory equals `routes.csv` (= TC-RT-G4) | Scan | `security.RouteInventoryScanTest` | SEC-19, D65, D98 | Must |
| TC-SEC-20 | Other citizen's complaint and its owner pages: 404 | Web | `access.ObjectAccessWebTest` | SEC-20, FR-23, BR-60 | Must |
| TC-SEC-21 | Unknown vs. not visible: identical 404 | Web | `access.ObjectAccessWebTest` | SEC-21, FR-23, D10 | Must |
| TC-SEC-22 | Officer of another department: 404 | Web | `access.ObjectAccessWebTest` | SEC-22, FR-47, BR-63 | Must |
| TC-SEC-23 | Attachment of a not-visible complaint; deleted attachment | Web | `access.ObjectAccessWebTest` | SEC-23, BR-62, BR-26 | Must |
| TC-SEC-24 | Another user's notification; mark-all touches own rows | Web | `access.ObjectAccessWebTest` | SEC-24, FR-72 | Must |
| TC-SEC-25 | Watcher on the owner-only pages | Web | `access.ObjectAccessWebTest` | SEC-25, FR-28, BR-50 | Must |
| TC-SEC-26 | Deactivated user's next request | Web | `security.UserStateWebTest` | SEC-26, BR-83, D59 | Must |
| TC-SEC-27 | Department change seen at the next request | Web | `security.UserStateWebTest` | SEC-27, BR-63, BR-89 | Must |
| TC-SEC-28 | Verification seen without logging in again (K3) | Web | `security.UserStateWebTest` | SEC-28, FR-09, FR-09c, D60 | Must |
| TC-SEC-29 | Officer duplicate form with another department's number (K7) | Web | `access.ObjectAccessWebTest` | SEC-29, BR-43, D69 | Must |
| TC-SEC-29a | `DUPLICATE_RECEIVED` number is a link only when viewable (U2) | Web | `access.ObjectAccessWebTest` | SEC-29a, D69 | Must |
| TC-SEC-29b | User-admin routes with an id of the wrong role | Web | `access.ObjectAccessWebTest` | SEC-29b, FR-53, FR-54 | Must |
| TC-SEC-30 | Security headers on page, JSON, attachment, error page | Web | `security.HeadersWebTest` | SEC-30, D64 | Must |
| TC-SEC-30a | No CSP violations in Chromium; inline-style control | Browser | e2e `csp.spec.ts` | SEC-30a, D64, NFR-07 | Later |
| TC-SEC-31 | Session cookie flags, no `jsessionid` in links | HTTP | `security.CookieAndFirewallHttpTest` | SEC-31, D58 | Must |
| TC-SEC-32 | 500 page and JSON without internals, with reference | Web | `common.ErrorMappingWebTest` | SEC-32, NFR-06, D85 | Must |
| TC-SEC-33 | Firewall paths → 400 (`Accept: text/html`) | HTTP | `security.CookieAndFirewallHttpTest` | SEC-33, NFR-06 | Must |
| TC-SEC-34 | No open redirect after login or anywhere | Web | `security.HeadersWebTest` | SEC-34, FR-03 | Must |
| TC-SEC-35 | PUT, DELETE, TRACE never 2xx | HTTP | `security.CookieAndFirewallHttpTest` | SEC-35, NFR-03 | Must |
| TC-SEC-36 | Three wrong OTPs, then the right one fails | Web | `otp.OtpSecurityWebTest` | SEC-36, FR-09, BR-85 | Must |
| TC-SEC-37 | Parallel OTP guesses on MySQL (≤ 3 attempts) | MySQL | `otp.OtpConcurrencyMySqlIT` | SEC-37, BR-85, D66 | Later |
| TC-SEC-37a | OTP hash never contains the code; new key after restart | Unit | `otp.OtpHasherTest` | SEC-37a, BR-85, D66 | Must |
| TC-SEC-38 | Oversize uploads: redirect + flash once, 413 elsewhere, expired session (K1) | HTTP | `attachment.UploadLimitsHttpTest` | SEC-38, FR-11, D63 | Must |
| TC-SEC-38a | 6 MB single file reaches the controller | HTTP | `attachment.UploadLimitsHttpTest` | SEC-38a, FR-11, D36 | Must |
| TC-SEC-38b | 27 MB upload in Chromium with JS off (swallow 64 MB vs. 2 MB) | Browser | e2e `upload.spec.ts` | SEC-38b, FR-11, D63 | Later |
| TC-SEC-39 | HTML-as-JPG, SVG, ZIP-as-PDF, GIF, empty file | Web | `attachment.UploadSecurityWebTest` | SEC-39, FR-11, BR-22 | Must |
| TC-SEC-40 | Path-like and quoted original names | Web | `attachment.UploadSecurityWebTest` | SEC-40, BR-24, NFR-05 | Must |
| TC-SEC-41 | Download headers of R-73 | Web | `attachment.UploadSecurityWebTest` | SEC-41, NFR-05, D44, R-73 | Must |
| TC-SEC-42 | Daily limit, 6 files, 6 after edit | Web | `attachment.UploadSecurityWebTest` | SEC-42, BR-88, BR-23 | Must |
| TC-SEC-43 | SQL-looking search and filter values | Web | `security.InputOutputWebTest` | SEC-43, NFR-04, D25, D33 | Must |
| TC-SEC-44 | Script-like text escaped on every page that shows it | Web | `security.InputOutputWebTest` | SEC-44, NFR-07 | Must |
| TC-SEC-45 | Template scan (no `th:utext`, inline script/style, plain `action`, `javascript:`) | Scan | `scan.TemplateScanTest` | SEC-45, NFR-07, NFR-03, D64 | Must |
| TC-SEC-46 | Mass-assignment parameters ignored on R-06, R-23, R-26, R-105 | Web | `security.InputOutputWebTest` | SEC-46, FR-08, BR-86 | Must |
| TC-SEC-47 | CSV cells prefixed against formula injection | Web | `complaint.CsvExportWebTest` | SEC-47, FR-65, D43, D75 | Later |
| TC-SEC-48 | Long Devanagari, common and personal passwords | Web | `security.InputOutputWebTest` | SEC-48, FR-02, D67 | Must |
| TC-SEC-49 | No staff names on citizen pages and in their notifications (U1) | Web | `privacy.StaffNameScanWebTest` | SEC-49, D48, D70 | Must |
| TC-SEC-50 | Officer views without owner email and mobile | Web | `privacy.StaffNameScanWebTest` | SEC-50, D76 | Must |
| TC-SEC-51 | CSV without email or mobile | Web | `complaint.CsvExportWebTest` | SEC-51, FR-65 | Later |
| TC-SEC-52 | Credentials erased from the session principal | Web | `security.AuthenticationWebTest` | SEC-52, NFR-01 | Must |
| TC-SEC-53 | `ProdSafetyCheck` refuses unsafe prod start-ups | Unit | `config.ProdSafetyCheckTest` | SEC-53, D73 | Later |
| TC-SEC-54 | Security log events with masked data | Unit | `security.SecurityLogTest` | SEC-54, D74 | Later |
| TC-SEC-55 | No password, OTP or session id in any log line | Unit | `security.SecurityLogTest` | SEC-55, NFR-01, D74 | Must |
| TC-SEC-56 | BCrypt strength 12 in prod | Unit | `security.BCryptConfigTest` | SEC-56, NFR-01, D67 | Must |

---

## F. Validation and error handling (doc 12)

"One test per row of doc 12 §3, §5.2 and §6.1" (T-2) is implemented as parameterised tests: each row below names the doc 12 table it walks through; every table row becomes one parameter set with the exact key and English text from `messages.properties`. The prototype tests of doc 12 §11.1 are the seeds of these classes (doc 13 §3.6).

| ID | Title | Level | Fixture | Steps | Expected | Traces | Pri |
|---|---|---|---|---|---|---|---|
| TC-VE-01 | `RegistrationForm` messages (doc 12 §3.2, one parameter per row) | Web | visitor | POST R-06 with the value that breaks exactly one rule | `200 auth/register`; the field shows exactly that message; summary lists it with `href="#field"` | FR-01, FR-02, BR-80, BR-84, R-06 | Must |
| TC-VE-02 | Staff and password forms (§3.3) | Web | admin; mahesh | POST R-103, R-105, R-109, R-115, R-08, R-11 with one broken rule each | the §3.3 message on the right field; `OfficerEditForm` allows the officer's own mobile | FR-02, FR-06, FR-53, FR-55, BR-81 | Must |
| TC-VE-03 | Complaint form and edit form (§3.4) | Web | mahesh; sneha | POST R-23 / R-26 with one broken rule each, incl. a 2000-character description with CR LF line breaks | the §3.4 message; CR LF counted as one character; edit with missing `version` → summary line without link | FR-10, FR-16, BR-20, BR-21, R-23, R-26 | Must |
| TC-VE-04 | Workflow forms (§3.5) | Web | DW | POST each of R-28, R-41, R-43, R-45, R-49, R-53, R-58, R-60, R-62 with blank, 9 and 501 characters, and 500 with line breaks | `validation.remark.required` / `.size` / `validation.optionalText.size`; 500 accepted | FR-18, FR-25, FR-41, FR-42, FR-45, FR-32 | Must |
| TC-VE-05 | Duplicate form and its two steps (§3.6) | Web | anita, TD `000012` | `original` absent, blank, `GRV-26-1`, lower-case valid number; POST note blank | plain step 1; `validation.originalNo.required`; `.format`; normalised to upper case and preview shown; note error keeps step 2 | FR-44, BR-10, R-54, R-55 | Must |
| TC-VE-06 | Master-data forms (§3.7) | Web | admin | POST R-82, R-89, R-96 with each broken rule | §3.7 messages; `slaDays` 1 and 90 accepted | FR-50, FR-51, FR-52 | Must |
| TC-VE-07 | Filters never fail, except the date range (§3.8) | Web | admin; mahesh | R-31 with `from` after `to`; R-20/R-30/R-31 with `status=FOO`, `categoryId=abc`, `page=-1`, `size=0`, `q` of 300 characters | page error "From date must not be after To date." with both dates ignored; all others ignored or clamped, `200` | FR-30, NFR-11, D33 | Must |
| TC-VE-08 | Cross-field rules (§3.9) | Web | as TC-VE-01/02/03 | mismatch, same-as-current, personal data, reassign same department, 5 active − 0 + 1 files, inactive unchanged category | the error on the field named in §3.9 | FR-02, FR-16, FR-33, BR-21, BR-23 | Must |
| TC-VE-09 | File checks, order and messages (§3.10) | Web | mahesh | 7 files where one is 6 MB and one is an EXE; empty file input; 0-byte named file | count message first **and** the per-file messages (all problems at once); empty input ignored; 0-byte file → type message; names cleaned in the text | FR-11, BR-22, BR-23, BR-24 | Must |
| TC-VE-10 | Hidden fields (§3.11) | Web | sneha | POST R-26 with `version=` and with `version=x` | summary line "This page is out of date. Reload it and try again." without a link; no inline message | BR-71, D87 | Must |
| TC-VE-11 | Binding errors (§3.12) | Web | admin | `slaDays=abc`, `departmentId=abc` on R-58, `?download=maybe` on R-73, `/attachments/abc` | `typeMismatch.slaDays`; `typeMismatch.java.lang.Long`; `400 error/4xx`; `404` | D83 | Must |
| TC-VE-12 | Normalisation (§3.1) for every kind | Unit, Web | `FormBindingAdvice` | single-line, multi-line, email, mobile, complaint number, OTP, password, `q` | each example of the §3.1 table produces its expected value; passwords unchanged | D79 | Must |
| TC-VE-13 | Custom validators (§4) | Unit | Hibernate Validator | parameter tables for `@ValidMobile`, `@StrongPassword` (first failing rule only, 72-byte limit), `@FieldsMatch`, `@FieldsDiffer`; `null` and empty are valid | doc 12 §4 behaviour; one message per field | FR-02, BR-84, D81 | Must |
| TC-VE-14 | Exception mapping X1–X16 (§5.2, one parameter per row) | Web | test-only controller under `/test-only/` that throws each exception (own context, doc 13 §2.10) | GET/POST it | status, view or redirect, flash and log level exactly as the §5.2 row; R-77 path gets JSON for X16 | NFR-06, D83 | Must |
| TC-VE-15 | Redirect targets (§5.3) | Unit, Web | `RedirectTargets` | each `Subject` with a hostile `Referer` header and query | the fixed path of the table; never the `Referer` | D82, SEC-34 | Must |
| TC-VE-16 | Unique-key races (§5.4) | Flow | TD; the pre-check bypassed by a test double | insert duplicates for the five constraints | `FormRejectedException` with the §5.4 field and key; only the constraint name logged | D84 | Must |
| TC-VE-17 | Other integrity violations are bugs | Flow | TD: a forced duplicate notification; a forced `CHECK` failure | run the action | rollback; `500` with reference; ERROR log | D84, NFR-30 | Must |
| TC-VE-18 | Errors outside the advice (§5.5) | HTTP | real Tomcat, `Accept: text/html` | firewall path; exception in a filter; 27 MB to a non-upload route; POST without token | `400 error/4xx` without `Set-Cookie`; `500 error/500` with a reference logged by `AppErrorViewResolver`; `413 error/4xx`; `403` CSRF variant | NFR-06, D85, SEC-33 | Must |
| TC-VE-19 | Refusal messages (§5.6, one parameter per key) | Flow | TD per situation | trigger each `error.*` situation | the exception carries that key and its arguments are labels and dates formatted by `ComplaintStatus` / `DisplayFormat` ("In Progress", "29-Nov-2026") | FR-83, FR-84, D82 | Must |
| TC-VE-20 | Error pages (§6.1, one parameter per status) | Web, HTTP | anonymous and logged in | produce 400, 403, 403 CSRF, 404, 405, 413, 415, 500 | right template, title and text keys; no `<form>`; no session created for anonymous; `Allow` header on 405; home link vs. log-in link | NFR-06, D85 | Must |
| TC-VE-21 | Reference id (§6.3) | Unit, Web | `ErrorReference`; failing handler | generate 10 000 ids; fail one request through advice and resolver | format `ERR-[0-9A-HJKMNP-TV-Z]{4}-…{4}`; one id per request in page, log line and MDC `ref` | NFR-06, D85 | Must |
| TC-VE-22 | Error properties (§6.4) | HTTP | JSON client (no `Accept`) | firewall path | body has only `timestamp`, `status`, `error` | NFR-06, D86 | Must |
| TC-VE-23 | Flash shown once, PRG (§7.2) | Web | mahesh | POST a refusal; follow; reload | the message once, then gone; never used for validation errors | D88 | Must |
| TC-VE-24 | Error summary and field markup (§7.3) | Web | visitor | POST R-06 blank | global errors first, then fields in the page's order, hidden-field errors without link; `is-invalid`, `aria-invalid`, `aria-describedby="x-help x-error"`; `<title>Error: Register</title>`; heading "Please correct 5 problems:" | NFR-22, D87 | Must |
| TC-VE-25 | Field id scheme (§7.4) | Web | each form page with errors | collect `id`s | ids unique per page; `files`, `files-help`, `files-error`; summary links resolve to an element | D87 | Must |
| TC-VE-26 | T-1: every constraint has an explicit existing key | Scan | all classes under `com.smartgrievance` with constraint annotations | reflect over fields and class-level constraints | every annotation has `message = "{key}"`; the key exists in `messages.properties` | D81, D77 | Must |
| TC-VE-27 | T-3: message catalogue checks in JUnit | Scan | `messages.properties` | port of `check-12-messages.py` checks 1, 2 and 5 (parse, duplicates, key convention, writing rules, `MessageFormat` with sample arguments, notification length ≤ 300); every `#{…}` / `"key"` used in templates and Java exists | pass; a new key without text or an unused key fails | D77, D78, D89 | Must |
| TC-VE-28 | T-4: `errorSummary` field lists match the inputs | Scan | templates with `th:object` | parse each form template | the `errorSummary(fields)` list equals the `th:field` names in document order (hidden fields excluded); no hand-written `id` on a `th:field` input | D87 | Must |
| TC-VE-29 | T-5: focus and summary in the browser | Browser | running JAR | submit the empty register form with JS on; click each summary link; run axe-core | focus on `#error-summary`; each link focuses its field; 0 axe violations | NFR-22, D87 | Later |
| TC-VE-30 | T-6: BR-88 under concurrency is a soft limit | MySQL | mahesh with 9 complaints | 2 parallel submissions | both may succeed: assert 10 ≤ count ≤ 11 (never a hard 10); no 500 | BR-88, D84 | Later |
| TC-VE-31 | `Accept-Language` is ignored | Web | visitor | POST R-06 blank with `Accept-Language: de-DE,hi-IN` | English messages | D77 | Must |
| TC-VE-32 | Logging rules (§9) | Unit | log capture during TC-VE-01/14/19 | inspect log events | refusals one INFO `REFUSED` line; client errors DEBUG; no rejected values, passwords, OTPs or constraint messages with PII | D91, SEC-55 | Must |

---

## G. Worked scenarios (doc 05 §5)

Level **Flow** (service calls as the named users) with a final MockMvc check of the pages where the scenario talks about what someone *sees*. S1 starts from Appendix A users without complaints (TD); S2–S5 continue from the state the previous scenario leaves (one ordered test class, `ScenarioChainTest`, D100); the others start from their own Given.

| ID | Title | Level | Fixture | Steps | Expected | Traces | Pri |
|---|---|---|---|---|---|---|---|
| TC-SC-01 | S1 Submit → review → assign → start → resolve | Flow, Web | TD: Appendix A users, no complaints | mahesh submits; admin starts review and assigns Water Supply; anita starts work and resolves | `GRV-2026-000001`; timeline `CREATED`, `UNDER_REVIEW`, `ASSIGNED`, `IN_PROGRESS`, `RESOLVED` in that order; mahesh has **5** notifications: the registration confirmation plus 4 status changes (doc 05 S1 corrected, D104) | S1, T01, T02, T03, T05, T06, FR-15, FR-70 | Must |
| TC-SC-02 | S2 Duplicate of an active complaint | Flow | after TC-SC-01's start-work step (000001 `IN_PROGRESS`) | jayesh submits the same leak; admin reviews and assigns Water Supply; anita marks `000002` duplicate of `000001` | `000002` `DUPLICATE` → `000001`; jayesh active watcher of `000001`; `DUPLICATE_RECEIVED` on `000001` | S2, T12, BR-44, FR-44 | Must |
| TC-SC-03 | S3 Resolve reaches owner and watcher | Flow, Web | end of TC-SC-02 | anita resolves `000001` | mahesh (`notify.resolved.owner`) and jayesh (`notify.resolved.watcher`) notified; mahesh's detail shows Close and Reopen, jayesh's only Unwatch | S3, T06, BR-50, FR-28 | Must |
| TC-SC-04 | S4 Reopen, resolve again, close | Flow | end of TC-SC-03 | mahesh reopens; anita starts work and resolves; mahesh closes | `reopen_count` 1; final `CLOSED`; jayesh gets the new In Progress and Resolved notifications (2), **not** reopen or close, which go to staff (FR-70; doc 05 S4 corrected, D104) | S4, T08, T05, T06, T07, FR-26 | Must |
| TC-SC-05 | S5 Unwatch, still visible, watch again | Flow, Web | end of TC-SC-02 | jayesh unwatches `000001`; anita adds a remark; jayesh opens `000001` from `000002`; watches again | no notification for the remark; `200` with "Watch again"; after watching, the row is active again | S5, BR-51, BR-52, FR-29 | Must |
| TC-SC-06 | S6 Withdraw blocked by a linked duplicate | Flow, Web | TD: `000001` `ASSIGNED` (Water Supply) with `000002` linked | mahesh withdraws | refused with "Other citizens are following this complaint; it cannot be withdrawn."; unchanged | S6, BR-47, T13 | Must |
| TC-SC-07 | S7 Other department gets 404 | Web | DW | suresh GET `/complaints/GRV-2026-000001` | 404 (same page as unknown) | S7, BR-60, FR-47 | Must |
| TC-SC-08 | S8 Officer cannot link across departments | Web | TD: Sanitation complaint `ASSIGNED`; Water Supply complaint `IN_PROGRESS` | suresh marks the Sanitation complaint duplicate of the Water Supply one | step 1 with `error.duplicate.otherDepartment`; no preview; Send back is offered on the detail page | S8, BR-43, T10 | Must |
| TC-SC-09 | S9 Auto-close after 8 days | Flow | DW | run `AutoCloseJob` | `000011` `CLOSED` with `AUTO_CLOSED` | S9, T09, FR-82 | Must |
| TC-SC-10 | S10 Two officers resolve at the same moment | Flow, MySQL | TD: `IN_PROGRESS`; anita and rohan loaded the page at version v | H2: resolve as anita with v, then as rohan with v. MySQL: two threads released together by a latch | exactly one succeeds; the other gets "This complaint was updated by someone else, please refresh."; one `RESOLVED` event only | S10, BR-71, FR-84 | Later |
| TC-SC-11 | S11 Edit while under review | Flow | TD: `UNDER_REVIEW` owned by sneha | sneha changes the title | `EDITED` event with revision `title: old → new`; admins notified | S11, FR-16, FR-17 | Must |
| TC-SC-12 | S12 EXE renamed to JPG | Web | mahesh | submit with `photo.jpg` containing `MZ…` | `validation.files.type` for `photo.jpg`; nothing saved | S12, BR-22, FR-11 | Must |

---

## H. Queries and repositories (doc 08 §5, Q01–Q31)

Level **Repo**: `@JdbcTest` on H2 (MySQL mode) with the real `V1`/`V2` migrations. Before each test `TestDatabase.reset()` empties the tables, restarts the identity counters and re-inserts V2, so `docs/db/verify/sample-data.sql` (copied unchanged to `src/test/resources/sql/q-fixture.sql`) loads with its known ids; "now" is 22-Nov-2026 12:00 as in `key-queries.sql`. Each test calls the **repository method** that owns the query (doc 07 §1.1) and asserts the result recorded in doc 08 §9.1. The `-Pmysql` profile runs the same class against MySQL (`QueriesMySqlIT extends QueriesRepositoryTest`, TC-Q-40).

| ID | Title | Level | Fixture | Steps | Expected | Traces | Pri |
|---|---|---|---|---|---|---|---|
| TC-Q-01 | My Complaints page and total | Repo | q-fixture | `ComplaintRepository.findByOwner(mahesh, filter, page 1)` | 3 rows (`000001`, `000004`, `000011`) newest first; total 3 | Q01, FR-20, BR-64 | Must |
| TC-Q-02 | My Complaints by status | Repo | q-fixture | same with `RESOLVED` | 2 rows | Q02, FR-20 | Must |
| TC-Q-03 | Watching list | Repo | q-fixture | `findWatched(jayesh)` | `000001` only; sneha's inactive row excluded | Q03, FR-22, BR-51 | Must |
| TC-Q-04 | canView facts, visible | Repo | q-fixture | `accessFacts(000001, jayesh)` | active watcher 1, owns linked duplicate 1 | Q04, BR-60 | Must |
| TC-Q-05 | canView facts, not visible | Repo | q-fixture | `accessFacts(000005, sneha)` | 0, 0 | Q05, BR-60 | Must |
| TC-Q-06 | Department Queue order | Repo | q-fixture | `departmentQueue(Water Supply, ACTIVE, now)` | `000013` (overdue) before `000012`, then `000001` | Q06, FR-40, BR-65 | Must |
| TC-Q-07 | All Complaints filters and search | Repo | q-fixture | `search(from 01-Nov, to 22-Nov, q "garbage")` | `000003`, `000007`; each optional fragment appended only when set | Q07, FR-30, D25 | Must |
| TC-Q-08 | Count per status | Repo | q-fixture | `DashboardRepository.countByStatus()` | the per-status counts of doc 08 §9.1 | Q08, FR-60 | Must |
| TC-Q-09 | Overdue count, all and one department | Repo | q-fixture | `countOverdue(now)`, `countOverdue(now, Water Supply)` | 4; 1 | Q09, FR-60, FR-63, FR-80, BR-65 | Must |
| TC-Q-10 | By category, open vs. closed | Repo | q-fixture | `byCategory()` | one row per category incl. zero rows; `RESOLVED` counted as open (D24) | Q10, FR-61 | Must |
| TC-Q-11 | By department incl. "(Not assigned)" | Repo | q-fixture | `byDepartment()` | "(Not assigned)" row present | Q11, FR-61 | Must |
| TC-Q-12 | By area | Repo | q-fixture | `byArea()` | areas without complaints have 0 / 0 | Q12, FR-61 | Must |
| TC-Q-13 | Overdue list order | Repo | q-fixture | `overdueList(now, 10)` | `000005`, `000007`, `000013`, `000003`; `due_at` returned, no day count from SQL | Q13, FR-62, BR-66 | Must |
| TC-Q-14 | Timeline with revisions | Repo | q-fixture | `EventRepository.timeline(complaint)` | 7 rows; the `EDITED` event once per revision (title, address); `DUPLICATE_RECEIVED` carries the related number | Q14, FR-21, FR-17, FR-81 | Must |
| TC-Q-15 | Unread count | Repo | q-fixture | `NotificationRepository.unreadCount(user)` | 3 | Q15, FR-71 | Must |
| TC-Q-16 | Recipients owner + active watchers | Repo | q-fixture | `recipients(000001)` | users 4 and 5, no duplicates | Q16, BR-67, BR-68 | Must |
| TC-Q-17 | Linked-duplicate guard | Repo | q-fixture | `countLinkedDuplicates(000001)` | 1 | Q17, BR-47 | Must |
| TC-Q-18 | Auto-close selection | Repo | q-fixture | `findExpiredResolved(cutoff = now − 7 days, 500)` | only `000011` | Q18, FR-82, BR-30 | Must |
| TC-Q-19 | Optimistic update wins | Repo | q-fixture | `updateStatus(id 1, version 3, IN_PROGRESS → RESOLVED)` | 1 row | Q19, BR-71 | Must |
| TC-Q-20 | Stale version updates nothing | Repo | after TC-Q-19's update | same call with version 3 | 0 rows; row is `RESOLVED`, version 4 | Q20, BR-71, FR-84 | Must |
| TC-Q-21 | Auto-close update | Repo | q-fixture | `autoClose(000011, version 4)` | 1 row; version 5; a second call 0 rows | Q21, T09 | Must |
| TC-Q-22 | Number for an existing year | Repo | q-fixture (2026 = 13) | `ComplaintNumberGenerator.next(2026)` in a transaction | 14 | Q22, BR-10, BR-11 | Must |
| TC-Q-23 | Number for a new year | Repo | q-fixture | `next(2027)` | row created, 1 | Q23, BR-10, D19 | Must |
| TC-Q-24 | Mark read, mark all read | Repo | q-fixture | `markRead(id, owner)`, `markRead(id, other user)`, `markAllRead(owner)` | 1 row; 0 rows; unread 0 | Q24, FR-72 | Must |
| TC-Q-25 | `RESOLVED` without `resolved_at` refused | Repo | q-fixture | direct `UPDATE` | `DataIntegrityViolationException` (`ck_complaint_resolved_at`) | Q25, NFR-32, D21 | Must |
| TC-Q-26 | Escaped LIKE search | Repo | q-fixture | `search(q "50%")` | only the title that contains "50%" literally | Q26, FR-30, SEC-43 | Must |
| TC-Q-27 | Notification list order | Repo | q-fixture | `list(user, page 1)` | newest first, ties by id | Q27, FR-72 | Must |
| TC-Q-28 | Unwatch and watch again on the same row | Repo | q-fixture | `deactivate(c, u)`, `activateOrInsert(c, u, now)` | the same primary key reactivated, `since` = now | Q28, BR-51, BR-52 | Must |
| TC-Q-29 | Guards BR-23 and BR-82 | Repo | q-fixture | `countActive(complaint, COMPLAINT)`, `countOtherActiveAdmins(admin)` | the doc 08 §9.1 values | Q29, BR-23, BR-82 | Must |
| TC-Q-30 | OTP statements | Repo | q-fixture + OTP rows | invalidate, insert, `sendFacts(user, oneHourAgo)`, `latestUsable(user)`, `reserveAttempt(id, 3, now)` three and four times | facts as recorded; reserve returns 1, 1, 1, then 0 | Q30, BR-85, D66 | Must |
| TC-Q-31 | One notification per event and recipient | Repo | q-fixture | insert the same (event, recipient) twice | `DuplicateKeyException` on `uq_notification_event_recipient` | Q31, BR-68, D22 | Must |
| TC-Q-40 | The same suite on MySQL 8.4 | MySQL | MySQL database `grievance_test`, Flyway clean-free: `TestDatabase.reset()` | run TC-Q-01 … TC-Q-31 through `QueriesMySqlIT` | all pass; Q25 error 3819, Q31 error 1062 | NFR-31, NFR-32, D92 | Later |
| TC-Q-41 | Number generator under parallel load | MySQL | year without a row | 8 threads × 50 numbers; 16 × 25 on three new years (Java port of `concurrency-test.sh`) | 400 distinct numbers 1–400, `last_number` 400, no deadlock or duplicate-key error | BR-11, D19 | Later |
| TC-Q-42 | `TIMESTAMPDIFF` is never used | Scan | repository SQL strings | search for `TIMESTAMPDIFF`, `NOW()`, `DATE_ADD`, `INTERVAL`, `START TRANSACTION` | none (doc 08 §6) | D18, D23 | Must |
| TC-Q-43 | Unique-key names as the driver reports them | MySQL | duplicate email, mobile, three names | insert twice; pass the exception to `UniqueKeys.translate` | MySQL message contains `app_user.uq_app_user_email` etc. and maps to the right field (H2 variant in TC-VE-16) | D84 | Later |

---

## I. Non-functional tests (doc 03 §2)

| ID | Title | Level | Fixture | Steps | Expected | Traces | Pri |
|---|---|---|---|---|---|---|---|
| TC-NF-01 | NFR-10: pages under 2 s with 10,000 complaints | MySQL | DW + `perf/load-10k.sql` (doc 08's load script adapted to the app's ids: +10,000 complaints, +60,000 events, +30,000 notifications, +2,000 citizens) | real Tomcat, `HttpClient` with a logged-in session: My Complaints, Watching, Department Queue, All Complaints (status filter, page 20, title search), dashboard, detail, notifications; 1 warm-up + 5 runs each | best and median of 5 below 2,000 ms on the CI runner; the same script is run once by hand on the reference laptop (Core i3, 4 GB) before the viva and the times are recorded in the test report (doc 13 §7.3) | NFR-10, NFR-11 | Later |
| TC-NF-02 | NFR-12: the indexes are used at 10k | MySQL | as TC-NF-01 | `EXPLAIN` of Q01, Q06, Q07 (status), Q08, Q10, Q15, Q18, Q14 | the keys of doc 08 §9.3 (`ix_complaint_owner_created`, `ix_complaint_dept_status_created`, …); only the title search scans | NFR-12 | Later |
| TC-NF-03 | Accessibility (U8): axe-core on the main screens | Browser | JAR with `dev,e2e` profile (DW) | Chromium, JS on, 1280 px: SCR-01, 02, 03, 10, 12 (with errors), 20 (owner and officer), 21, 30, 40, 41, 60, 91 | 0 violations for the tags `wcag2a`, `wcag2aa`, `wcag21a`, `wcag21aa`; control page with an unlabelled input reports `label` (proves the check runs) | NFR-20, NFR-21, NFR-22, D99 | Must |
| TC-NF-04 | Responsive (NFR-20): 360 and 1280 px | Browser | as TC-NF-03 | the same screens at 360 × 800 and 1280 × 800; screenshots saved as artefacts | `document.documentElement.scrollWidth <= innerWidth` on every screen (only `.table-responsive` may scroll); bell visible at 360; no JS errors | NFR-20, D51 | Must |
| TC-NF-05 | Contrast of the status colours | Scan | `docs/ui-mockups/css/app.css` → `static/css/app.css` | `check-10-contrast.py` on the app's CSS | all pairs ≥ 4.5:1 | NFR-21, D47 | Must |
| TC-NF-06 | Paging limits (NFR-11) | Web | DW + 60 TD complaints for mahesh | R-20 with `size` 0, 10, 50, 51, 500, `abc`; `page` 0, −1, 99 | sizes 1, 10, 50, 50, 50, 10; page 1, 1, last page | NFR-11, D33 | Must |
| TC-NF-07 | Date format (NFR-23) on every page | Web | DW | render SCR-10, 20, 30, 40, 41, 60 | every date matches `\d{2}-[A-Z][a-z]{2}-\d{4}( \d{2}:\d{2} (AM|PM))?`; no ISO dates | NFR-23, D50 | Must |
| TC-NF-08 | All or nothing for every step of an action (NFR-30) | Flow | TD: complaint `IN_PROGRESS` with a watcher; test doubles that fail once at a chosen step | mark duplicate and resolve, failing in turn at the event insert, the revision insert, the watcher change and the notification insert | after each failure the complaint row, events, revisions, watcher rows, notifications and files are exactly as before | NFR-30, BR-70, BR-25 | Must |
| TC-NF-09 | Flyway on an empty database (NFR-31) | Repo, MySQL | empty H2; empty MySQL | start the context | `V1`, `V2` applied; 12 tables; a changed checksum stops start-up (MySQL run) | NFR-31, ADR-03 | Must |
| TC-NF-10 | Constraints exist in the database (NFR-32) | Repo | H2 metadata | read `INFORMATION_SCHEMA` | every FK, unique and check named in doc 08 §4 exists | NFR-32, D21 | Must |
| TC-NF-11 | Passwords only as BCrypt (NFR-01) | Flow | register, create officer, reset password | read `password_hash` | `$2a$`/`$2b$` hashes; never the plain text; strength 4 in tests, 12 in prod (TC-SEC-56) | NFR-01, D67 | Must |
| TC-NF-12 | Runs with `java -jar` (NFR-42) | Manual, MySQL | built JAR, MySQL, env vars | `java -jar target/smart-grievance.jar --spring.profiles.active=prod` on Linux and Windows | starts, Flyway migrates, `/login` 200; configuration overridable by env vars | NFR-42, D6 | Later |
| TC-NF-13 | Every status change logged at INFO (NFR-43) | Unit | log capture during TC-SM-01 … TC-SM-19 | inspect events | one INFO line per transition with `complaintNo`, from, to, actor id | NFR-43 | Must |
| TC-NF-14 | Layer rules (NFR-40) | Scan | compiled classes | for every `*Controller`: no field or constructor parameter of a `*Repository` type; repositories only in their own feature package's services (doc 07 §3 rule) | none found | NFR-40, ADR-01 | Must |
| TC-NF-15 | Coverage gate (NFR-41) | Scan | JaCoCo report of `mvn verify` | `jacoco:check` with the limits of doc 13 §6.1 | build fails below the limits | NFR-41, D102 | Must |
| TC-NF-16 | No-JS smoke (U7) | Browser | JAR (DW), `javaScriptEnabled: false` | log in as mahesh; submit a complaint with a PNG; log in as anita; mark `000012` duplicate of `000001` through steps 1 and 2; log out | every step works; the navbar menu is reachable (`.no-js` rules); the same results as TC-UC-10a / TC-UC-23a | NFR-20, D49, D99 | Must |
| TC-NF-17 | JS-on smoke (U7) | Browser | as TC-NF-16, JS on | the same flow; double-click Submit | one complaint only (submit disabled, "Saving…"); file pre-check lists the file; confirm prompt on Start Review; bell count refresh on `pageshow`; 0 CSP violations | D49, D72, SEC-30a | Must |
| TC-NF-18 | Keyboard and screen-reader checklist | Manual | running app | doc 10 §1.4 list: skip link, focus ring, labels, summary links, bell label, tables | every item ticked in the test report | NFR-20, NFR-22 | Later |

---

## J. Component unit tests and static scans

| ID | Title | Level | Fixture | Steps | Expected | Traces | Pri |
|---|---|---|---|---|---|---|---|
| TC-UT-01 | `ComplaintAccessPolicy` matrix | Unit | facts for relations O, W, D (active / inactive watcher), C, F, X, A × 10 statuses | `canView` | true for O, W (active only), D, F (current department only), A; false for C, X, an inactive watcher without a duplicate, an officer of the previous department | BR-60, BR-63, FR-23, FR-47 | Must |
| TC-UT-02 | `DisplayFormat` (U10) | Unit | default locale set to `hi-IN` during the test | `dateTime`, `date`, `days(0/1/3)`, `null` | "22-Nov-2026 03:45 PM", "29-Nov-2026", "0 days" / "1 day" / "3 days", "" | NFR-23, D50 | Must |
| TC-UT-03 | `ComplaintStatus` and `StatusGroup` label, badge class, icon (U10) | Unit, Scan | doc 10 §5.1 table as a parameter source; `static/css/app.css` | for all 10 statuses and 5 groups | label, `status-…` class and `bi-…` icon equal the table; every class exists in `app.css`; `isFinal` true for exactly the 4 final statuses | NFR-21, D47 | Must |
| TC-UT-04 | Status group expansion | Unit | — | expand `PENDING`, `ACTIVE`, `NOT_FINAL`, `FINAL`, `ALL`, a single status, `FOO` | the sets of doc 09 §1.4; `FOO` → no filter | D35, D24 | Must |
| TC-UT-05 | Complaint number format | Unit | `ComplaintNumberGenerator.format` | 2026/1, 2026/123, 2026/999999, 2026/1000000 | `GRV-2026-000001`, `GRV-2026-000123`, `GRV-2026-999999`; the last throws (sequence `CHECK` ≤ 999999) | BR-10, FR-12 | Must |
| TC-UT-06 | `OtpHasher` and code generator | Unit | two hasher instances | hash, verify, compare across instances; generate 10,000 codes | constant-time verify; the second instance refuses the first's hash; codes are 6 digits incl. leading zeros; distribution not constant | BR-85, D66, SEC-37a | Must |
| TC-UT-07 | CSV escaping | Unit | `CsvWriter` | cells `=1+1`, `+x`, `-x`, `@x`, `\tx`, `\rx`, `a"b`, `a,b`, Devanagari | `'`-prefixed for the first six; quotes doubled; commas quoted; UTF-8 with BOM | FR-65, D43, D75 | Later |
| TC-UT-08 | `PasswordPolicy.personalData` | Unit | user `mahesh@example.com`, 9800000001 | passwords containing `mahesh`, `MAHESH`, `9800000001`, neither | first three refused, last accepted | FR-02, D67 | Must |
| TC-UT-09 | `LoginAttemptService` | Unit | fixed clock | 5 failures in 15 min; wait 15 min; 10,001 distinct keys | locked, unlocked, map size capped at 10,000 with expired keys removed (T-24 of doc 11) | FR-05, D56 | Must |
| TC-UT-10 | `NotificationMessages` | Unit | longest arguments of doc 12 §8.6 | render all templates; excerpt a 500-character remark | ≤ 300 characters (longest 257); excerpt ≤ 120 + "…" cut at a word boundary | BR-69, D89 | Must |
| TC-UT-11 | `TimelineService` actor labels per viewer | Unit | events by owner, officer (dept in charge at that time), admin, system; viewers owner, watcher, officer, admin | build entries | the doc 10 §7.3 table cell by cell; "{Department} Dept." follows assign/reassign/send-back; `relatedLinkable` only when the viewer can view B | D48, D55, D69 | Must |
| TC-UT-12 | `ActionLinks` | Unit | every `Action` × viewer role | build links | URL `/{prefix}/complaints/{no}/{segment}`; `/admin` prefix for admin viewers of officer actions; method POST only for START_REVIEW, UNWATCH, WATCH; style per D54; order of doc 09 §7.1 | D39, D54 | Must |
| TC-UT-13 | Days overdue | Unit | `due_at` 21-Nov-2026 23:00 | `now` 22-Nov 01:00, 22-Nov 23:00, 25-Nov 10:00 | 0, 1, 3 (`floor`, D23) | BR-66, FR-80, D23 | Must |
| TC-UT-14 | `UniqueKeys.translate` | Unit | H2 and MySQL driver messages for the five keys and for `uq_complaint_no` | translate | the five map to their field and key; the others are not translated | D84 | Must |
| TC-UT-15 | `PageResult` and paging maths | Unit | totals 0, 1, 10, 11, 57 | pages and offsets for page/size inputs | last page when beyond; offset = (page − 1) × size; "Showing 11–20 of 57" | NFR-11, D33 | Must |
| TC-UT-16 | `AdminBootstrap` (FR-07) | Flow | empty `app_user`; then an inactive admin only | start the runner twice; then with only an inactive admin | one active, verified ADMIN from `app.admin.*` (BCrypt), no department; the second run adds nothing; with an inactive admin present nothing is created (D26) | FR-07, BR-82, BR-87, D26 | Must |
| TC-UT-17 | Demo world equals Appendix A | Flow | empty database, `TestClock` 23-Nov-2026 10:00 | run the `DevDataSeeder` logic twice | the users, statuses, departments, watchers and relative dates of Appendix A §4–§5 (e.g. `000005` 15 days old and overdue, `000011` resolved 8 days ago); `complaint_sequence(2026) = 11`; the second run inserts nothing (D27) | D27, FR-80, FR-82 | Must |
| TC-UT-18 | `HousekeepingJob` purge (FR-73) | Flow | TD: notifications 89 and 91 days old, OTP rows 29 and 31 days old | run the job | the 91-day notifications and 31-day OTP rows deleted in batches; the others and all `complaint_event` rows kept (D28) | FR-73, D28 | Later |
| TC-SCAN-01 | Templates use `@fmt` and never format dates | Scan | `templates/**/*.html` | search for `#temporals`, `#dates`, `.format(` | none | NFR-23, D50 | Must |
| TC-SCAN-02 | Templates never decide actions by status or role | Scan | `templates/**` | search for `c.status` / `sec:authorize` inside `complaint-actions.html` and around action buttons | only `c.can('…')` and `actionLinks` decide actions (doc 09 §7.3) | FR-19, D39 | Must |
| TC-SCAN-03 | No method security | Scan | classpath | search for `@PreAuthorize`, `@Secured`, `@EnableMethodSecurity` | none (D65) | D65, NFR-02 | Must |
| TC-SCAN-04 | Every test case ID in code is in this catalogue | Scan | `@DisplayName` values of all test classes | collect `TC-…` prefixes | each exists in 13a; each `Must` row of 13a has at least one test once its module is built (report, not failure, until M7) | NFR-41, D93 | Later |

---

## Change log
| Date | Change |
|---|---|
| 2026-11-23 | Initial draft: 446 test cases in groups A–J (state machine with the expected matrix, business rules, acceptance tests for all 30 use cases, one route test per doc 09 route plus three generated sweeps, the 62 security tests of doc 11, validation and error tests of doc 12 incl. T-1…T-6, scenarios S1–S12, queries Q01–Q31 and the MySQL suite, non-functional, component and scan tests) |
