# Test report — Smart Grievance

| | |
|---|---|
| Date | 23-Nov-2026 01:42 PM IST |
| Commit | `dcd3b08` (dcd3b08) |
| Java (on PATH) | openjdk version "25.0.5.1" 2026-11-17 |
| Reports | `app/target` |

## Tests per level

| Level | Classes | Tests | Failed | Skipped | Time (s) |
|---|---:|---:|---:|---:|---:|
| Unit | 21 | 1065 | 0 | 0 | 12.1 |
| Repo | 3 | 10 | 0 | 0 | 0.5 |
| Web | 19 | 301 | 0 | 0 | 17.8 |
| HTTP | 3 | 9 | 0 | 0 | 3.5 |
| Scan | 5 | 16 | 0 | 0 | 0.2 |
| MySQL | 20 | 117 | 0 | 0 | 60.4 |
| **Total** | **71** | **1518** | **0** | **0** | **94.6** |

## Failures

None.

## Slowest classes

| Class | Tests | Time (s) |
|---|---:|---:|
| `PerformanceMySqlIT` | 2 | 12.1 |
| `WorkflowQueriesMySqlIT` | 31 | 9.1 |
| `DuplicateQueriesMySqlIT` | 26 | 7.9 |
| `AppUserDetailsServiceTest` | 2 | 7.8 |
| `SchemaRepositoryMySqlIT` | 3 | 7.6 |

## Coverage (JaCoCo, doc 13 §6.1)

| Package | Line | Branch |
|---|---:|---:|
| `com.smartgrievance.access` | 100.0 % | 100.0 % |
| `com.smartgrievance.attachment` | 92.8 % | 77.5 % |
| `com.smartgrievance.auth` | 100.0 % | 92.2 % |
| `com.smartgrievance.common` | 94.4 % | 70.0 % |
| `com.smartgrievance.common.exception` | 100.0 % | – |
| `com.smartgrievance.common.validation` | 97.4 % | 83.3 % |
| `com.smartgrievance.common.web` | 97.6 % | 86.8 % |
| `com.smartgrievance.complaint` | 98.9 % | 89.2 % |
| `com.smartgrievance.complaint.web` | 97.8 % | 89.8 % |
| `com.smartgrievance.config` | 79.3 % | 77.4 % |
| `com.smartgrievance.dashboard` | 100.0 % | 100.0 % |
| `com.smartgrievance.duplicate` | 100.0 % | 93.8 % |
| `com.smartgrievance.history` | 100.0 % | 92.6 % |
| `com.smartgrievance.housekeeping` | 100.0 % | 100.0 % |
| `com.smartgrievance.masterdata` | 97.7 % | 96.2 % |
| `com.smartgrievance.notification` | 99.2 % | 87.1 % |
| `com.smartgrievance.otp` | 98.7 % | 82.9 % |
| `com.smartgrievance.security` | 98.0 % | 77.4 % |
| `com.smartgrievance.seed` | 93.4 % | 95.2 % |
| `com.smartgrievance.staff` | 97.1 % | 81.9 % |
| `com.smartgrievance.user` | 93.8 % | 75.0 % |
| `com.smartgrievance.watch` | 100.0 % | 94.4 % |
| `com.smartgrievance.workflow` | 96.6 % | 97.0 % |
| **Bundle** | **96.7 %** | **87.2 %** |

## To fill in by hand

- `-Pmysql` run date and result; `-Pe2e` run date and result (Playwright list output)
- NFR-10 times on the reference laptop (TC-NF-01, doc 14 §11.5)
- Open defects by severity (doc 13 §7.1); result of the `check-*` scripts; ticked demo checklist (doc 13 §5)

## Browser tests (`./mvnw verify -Pe2e`)

Playwright 1.65.1, Chromium, projects js-on/js-off × 360/1280 px: **81 passed, 15 skipped** (checks that only apply with JavaScript on or off), 0 failed. Includes axe-core WCAG 2.1 AA on 14 screens, no sideways scroll, no CSP violations, the inline-style CSP control, error-summary focus, the no-JS and JS smoke flows, and a 27 MB upload with JavaScript off.

## NFR-10 page times at 10,000 complaints (MySQL 8.0, this build machine)

```
NFR-10 page times with 10011 complaints (best / median of 5 runs, ms)
/citizen/complaints                                  24     36
/citizen/watching                                    18     26
/complaints/GRV-2026-000001                          19     25
/notifications                                       18     27
/officer/queue                                       37     50
/officer/queue?status=ALL                            46     49
/admin/complaints?status=ASSIGNED&page=20            37     52
/admin/complaints?q=load+test                        41     62
/admin/dashboard                                     95    104
/admin/complaints/export.csv?status=PENDING          39     46
```

Target: under 2,000 ms (NFR-10). Repeat `./mvnw verify -Pmysql` on the reference laptop (Core i3, 4 GB) before the viva and paste the new times here.

## Manual checks

| Case | Result |
|---|---|
| TC-NF-12 `java -jar` with the prod profile | ✅ on Linux with MySQL: refused with the default admin password (P-2); with strong env values it started in 5.1 s, Flyway migrated as `grievance_owner`, admin login and dashboard answered 200, graceful shutdown. Windows still to check on the demo laptop. |
| TC-NF-18 keyboard and screen-reader checklist (doc 10 §1.4) | ☐ to tick by hand on the demo laptop |

## Open defects

None known.
