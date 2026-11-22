# 02 — Glossary & Actors

| | |
|---|---|
| **Status** | Draft |
| **Depends on** | 01 Vision & Scope |

---

## 1. Glossary

| Term | Meaning |
|---|---|
| **Complaint** | A public problem reported by a citizen. It has a category, title, description, area, address, optional photo, status and history. |
| **Complaint ID** (`complaint_no`) | The human-readable unique number shown to users, e.g. `GRV-2026-000123`. The format is finalised in doc 05. It is separate from the database primary key. |
| **Category** | The type of problem: Garbage, Water Leakage, Road Damage, Drainage, Streetlight, Other. Each category has a **default department**. |
| **Department** | The authority unit that fixes a problem, e.g. Sanitation, Water Supply, Road Maintenance, Electrical. |
| **Area** | A ward or locality chosen from a dropdown. Together with the free-text address, it forms the complaint's location. |
| **Status** | The current lifecycle state of a complaint (see §3). |
| **Status History** | A record of every status change: from-status, to-status, who made it, when, and a remark. It drives the tracking timeline. |
| **Remark** | A short note entered with a status change, e.g. "Team visited, pipe replacement scheduled". |
| **Assignment** | Linking a complaint to the responsible department. It is done by the admin. |
| **Duplicate** | A complaint describing the same problem as an earlier one that is already being handled. It is linked to the **original** (master) complaint. |
| **Original complaint** | The complaint that is actually being worked on, which duplicates point to. |
| **Watcher** | A citizen, other than the owner, who follows an original complaint. They can view it and get its notifications. A citizen becomes a watcher when their own complaint is marked as a duplicate of it. |
| **Owner** | The citizen who created the complaint. |
| **Notification** | An in-app message for a user, such as "Your complaint GRV-2026-000123 is now In Progress". It can be read or unread. |
| **Overdue** | A complaint that is not yet resolved after its category's target number of days (`sla_days`). |
| **Dashboard** | The admin overview: counts by status, category, department and area. |

## 2. Actors

### 2.1 Citizen (`ROLE_CITIZEN`)
- Registers using their name, email, mobile number and password.
- Submits complaints.
- Views **only** the complaints they own or watch.
- Sees the status timeline and the officers' remarks.
- Closes their own complaint once it is `RESOLVED`.
- Receives notifications.

### 2.2 Department Officer (`ROLE_OFFICER`)
- The account is created by the admin and linked to **one department**.
- Sees only the complaints assigned to their department.
- Changes the status: `ASSIGNED → IN_PROGRESS → RESOLVED`, always with a remark.
- Marks a complaint in their department as a `DUPLICATE` of another complaint (with a note). This adds the duplicate's owner as a watcher of the original.
- Can `REJECT` a complaint with a reason, e.g. it is not a public issue or is fake.

### 2.3 Administrator (`ROLE_ADMIN`)
- Sees all complaints.
- Moves new complaints from `SUBMITTED` to `UNDER_REVIEW`, then assigns them to a department (`ASSIGNED`). The category's default department is pre-selected.
- Can reassign the department, reject, or mark duplicates.
- Monitors status changes and overdue complaints.
- Manages departments, officers, categories (including `sla_days` and the default department) and areas.
- Views reports.

### 2.4 System (non-human)
- Generates the complaint ID.
- Records every status change in the history.
- Creates notifications for the owner and the watchers.
- Flags overdue complaints.

## 3. Complaint statuses

| Status | Meaning | Set by |
|---|---|---|
| `SUBMITTED` | Just created | System |
| `UNDER_REVIEW` | The admin is checking it | Admin |
| `ASSIGNED` | Handed to a department | Admin |
| `IN_PROGRESS` | The department is working on it | Officer |
| `RESOLVED` | The work is done | Officer |
| `CLOSED` | The citizen confirmed the fix (final) | Citizen (owner) |
| `REJECTED` | Invalid, not a public issue, or fake (final) | Admin / Officer |
| `DUPLICATE` | Linked to an original complaint (final for this record) | Admin / Officer |

The exact allowed transitions, such as whether a resolved complaint can be reopened and whether `RESOLVED` closes automatically after N days, will be defined as a state machine in **doc 05**.

## 4. Permission matrix (high level)

| Capability | Citizen | Officer | Admin |
|---|:-:|:-:|:-:|
| Register self | ✔ | | |
| Submit complaint | ✔ | | |
| View complaint | own + watched | own department | all |
| Review / assign department | | | ✔ |
| Update status (In Progress / Resolved) | | own department | ✔ |
| Reject | | own department | ✔ |
| Mark duplicate and add watcher | | own department | ✔ |
| Close resolved complaint | owner | | |
| Manage departments / officers / categories / areas | | | ✔ |
| View reports and dashboard | | own-department summary | ✔ |
| Receive notifications | ✔ | ✔ (new assignment) | ✔ (new complaint) |

## 5. Worked example: duplicate and watcher

1. Mahesh submits `GRV-2026-000101`, "Water pipe leaking near Shivaji Chowk". The admin assigns it to Water Supply, and an officer sets it to `IN_PROGRESS`.
2. Jayesh submits `GRV-2026-000107` about the same leak.
3. The officer opens 000107, chooses **Mark as duplicate of** `GRV-2026-000101`, and adds the note "Already being repaired, you will be updated on the original complaint."
4. The system sets 000107 to `DUPLICATE`, links it to 000101, adds Jayesh as a **watcher** of 000101, and notifies Jayesh.
5. Jayesh's "Watching" list now shows 000101 with its timeline. When the officer resolves 000101, Mahesh (the owner) and Jayesh (the watcher) are both notified.
6. Only Mahesh can close 000101.

## Change log
| Date | Change |
|---|---|
| 2026-11-22 | Initial draft |
