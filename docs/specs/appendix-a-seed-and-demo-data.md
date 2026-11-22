# Appendix A — Seed & Demo Data

| | |
|---|---|
| **Status** | Draft |
| **Depends on** | 03 Requirements §4, 05 Domain Model |

There are two kinds of starting data:

| Kind | Loaded by | Profiles | Contents |
|---|---|---|---|
| **Reference data** | Flyway migration (always) | all | Departments, categories, areas |
| **Bootstrap admin** | Startup runner from config (FR-07) | all | One Admin account |
| **Demo data** | Dev-only seeder | `dev` only, **never `prod`** | Officers, citizens, sample complaints, history, watchers, notifications |

---

## 1. Areas (Pune Municipal Corporation)

Well-known PMC localities. The ward-office column is only a description, to make the demo realistic.

| # | Area | Ward office (PMC) | Landmark used in demo addresses |
|---|---|---|---|
| 1 | Shivajinagar | Shivajinagar–Ghole Road | FC Road, near Fergusson College |
| 2 | Kothrud | Kothrud–Bavdhan | Karve Road, near Kothrud Depot |
| 3 | Aundh | Aundh–Baner | ITI Road |
| 4 | Baner | Aundh–Baner | Baner Road, near Balewadi Phata |
| 5 | Hadapsar | Hadapsar–Mundhwa | Near Magarpatta City gate |
| 6 | Viman Nagar | Nagar Road–Wadgaonsheri | Near Phoenix Marketcity |
| 7 | Kasba Peth | Kasba–Vishrambaug Wada | Near Kasba Ganpati temple |

## 2. Departments

| # | Department | Description |
|---|---|---|
| 1 | Sanitation | Garbage collection and street cleaning |
| 2 | Water Supply | Water pipelines, leakages and supply timing |
| 3 | Road Maintenance | Potholes, damaged roads and footpaths |
| 4 | Drainage | Sewer lines, blocked drains and chambers |
| 5 | Electrical | Streetlights and public electrical fittings |

## 3. Categories

| # | Category | Default department | SLA days |
|---|---|---|---|
| 1 | Garbage | Sanitation | 3 |
| 2 | Water Leakage | Water Supply | 2 |
| 3 | Road Damage | Road Maintenance | 10 |
| 4 | Drainage | Drainage | 5 |
| 5 | Streetlight | Electrical | 3 |
| 6 | Other | — (admin decides) | 7 |

## 4. Users

All emails use the reserved `example.com` domain, and all mobile numbers are fictitious.
**Demo password for every demo account: `Demo@1234`** (dev profile only).

### 4.1 Admin (bootstrap, every profile)
| Name | Email | Mobile | Note |
|---|---|---|---|
| System Administrator | `admin@example.com` | 9000000000 | Email, password, name and mobile come from `app.admin.email` / `app.admin.password` / `app.admin.name` / `app.admin.mobile` (D26). In `prod`, a strong password **must** be set through an environment variable. |

### 4.2 Department officers (dev only, created as if by the Admin)
| Name | Email | Mobile | Department |
|---|---|---|---|
| Suresh Patil | `suresh.patil@example.com` | 9000000101 | Sanitation |
| Anita Deshmukh | `anita.deshmukh@example.com` | 9000000201 | Water Supply |
| Rohan Kulkarni | `rohan.kulkarni@example.com` | 9000000202 | Water Supply |
| Vikram Jadhav | `vikram.jadhav@example.com` | 9000000301 | Road Maintenance |
| Pooja Shinde | `pooja.shinde@example.com` | 9000000401 | Drainage |
| Amit Pawar | `amit.pawar@example.com` | 9000000501 | Electrical |
| Kiran More | `kiran.more@example.com` | 9000000502 | Electrical (**inactive**, to demo FR-05) |

### 4.3 Citizens (dev only, as if self-registered)
| Name | Email | Mobile | Verified | Purpose in demo |
|---|---|---|:-:|---|
| Mahesh Joshi | `mahesh@example.com` | 9800000001 | ✔ | Owner of the original complaint (S1) |
| Jayesh Gaikwad | `jayesh@example.com` | 9800000002 | ✔ | Owner of the duplicate, watcher (S2) |
| Sneha Kale | `sneha@example.com` | 9800000003 | ✔ | Several statuses |
| Rahul Bhosale | `rahul@example.com` | 9800000004 | ✘ | Shows the OTP verification flow (UC-04) |

New citizens can also register live during the demo using the normal registration and OTP flow.

## 5. Sample complaints (dev only)

They cover all **10 statuses**. Dates are relative to the moment the seeder runs, so overdue and review-window cases always look correct.

| No. | Category / Area | Owner | Status | Dept | Story |
|---|---|---|---|---|---|
| GRV-2026-000001 | Water Leakage / Kothrud | Mahesh | `IN_PROGRESS` | Water Supply | "Pipeline leaking on Karve Road near Kothrud Depot". 1 day old, officer Anita started work. |
| GRV-2026-000002 | Water Leakage / Kothrud | Jayesh | `DUPLICATE` → 000001 | Water Supply | Same leak. Jayesh is an active watcher of 000001. |
| GRV-2026-000003 | Garbage / Hadapsar | Sneha | `SUBMITTED` | — | "Garbage not collected for 4 days near Magarpatta gate". 2 photos. |
| GRV-2026-000004 | Streetlight / Aundh | Mahesh | `RESOLVED` | Electrical | Resolved 2 days ago, so the review window is open: demo of Close and Reopen. |
| GRV-2026-000005 | Road Damage / Shivajinagar | Jayesh | `ASSIGNED` | Road Maintenance | "Large pothole on FC Road". 15 days old with a 10-day SLA, so it is **overdue**. |
| GRV-2026-000006 | Drainage / Kasba Peth | Sneha | `CLOSED` | Drainage | Closed by the owner with feedback "Fixed quickly, thanks". |
| GRV-2026-000007 | Garbage / Viman Nagar | Jayesh | `REOPENED` | Sanitation | Reopened once: "Garbage collected once but piled up again". |
| GRV-2026-000008 | Other / Baner | Sneha | `UNDER_REVIEW` | — | "Illegal hoarding blocking footpath on Baner Road". Category Other, so the admin must pick a department. Edited once, so it shows a revision. |
| GRV-2026-000009 | Road Damage / Baner | Mahesh | `REJECTED` | Road Maintenance | "Road is on a private society premises, outside PMC scope." |
| GRV-2026-000010 | Streetlight / Kasba Peth | Sneha | `WITHDRAWN` | — | "Light started working again", withdrawn by the owner. |
| GRV-2026-000011 | Streetlight / Viman Nagar | Mahesh | `RESOLVED` | Electrical | Resolved **8 days ago**, so the next auto-close job run will close it (S9). |

Each sample complaint gets the full chain of `ComplaintEvent` rows matching its status path, plus the matching notifications, so the timelines and the bell look realistic. The complaint sequence for that year starts after the seeded numbers (`complaint_sequence.last_number = 11`). The numbers use the **current** year when the seeder runs (`GRV-2026-…` in 2026), and the seeder skips itself if any complaint already exists (doc 08 §4.5, D27).

## 6. Demo login cheat-sheet

| Role | Login | What to show |
|---|---|---|
| Admin | `admin@example.com` | Dashboard counts, overdue 000005, assign 000008 |
| Officer (Water) | `anita.deshmukh@example.com` | Queue, resolve 000001 → Mahesh and Jayesh notified |
| Officer (Electrical) | `amit.pawar@example.com` | 000004 awaiting citizen review |
| Citizen | `mahesh@example.com` | Close or reopen 000004 |
| Citizen | `jayesh@example.com` | Watching 000001, Unwatch / Watch again |
| Citizen | `rahul@example.com` | OTP verification before filing |

## Change log
| Date | Change |
|---|---|
| 2026-11-22 | Initial draft (D15, D16) |
| 2026-11-22 | Sequence column name, current-year numbering, admin name/mobile settings (doc 08, D17, D26, D27) |
