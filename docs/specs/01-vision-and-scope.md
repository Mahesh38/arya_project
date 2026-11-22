# 01 — Vision & Scope

| | |
|---|---|
| **Status** | Draft |
| **Depends on** | 00 Specification Plan, `docs/Synopsis.pdf` |

---

## 1. Vision

A single web application where a citizen can report a local civic problem in a couple of minutes, get a complaint ID, and see who is working on it and what has happened, without visiting an office or making phone calls. The responsible department gets a clear queue of its own work, and the administrator can see what is pending and where.

## 2. Problem statement (from the synopsis)

Public problems such as uncollected garbage, water leakage, damaged roads, drainage issues and broken streetlights are reported today by office visits, phone calls or separate channels. As a result:

- Citizens don't know which department should get the complaint.
- Progress is hard to check after submitting.
- Records live in registers or separate files.
- Authorities struggle to identify and follow up pending complaints.
- Citizens get no clear update when the status changes.

## 3. Goals

| ID | Goal | How we'll know |
|---|---|---|
| G1 | Citizens can submit a complaint online with category, title, description, area, address and an optional photo. | A complaint ID is shown right after submission. |
| G2 | Citizens can see the current status and history of their own complaints. | The "My Complaints" page lists them, and each one has a timeline. |
| G3 | Each complaint reaches the right department. | The admin assigns it, and the department's officers see it in their queue. |
| G4 | Department officers update the status on time. | Each status change is recorded with who made it, when, and a remark. Overdue complaints are flagged. |
| G5 | Duplicate complaints don't create duplicate work. | An officer can mark a complaint as a duplicate of another. The second citizen becomes a **watcher** of the original and gets its updates. |
| G6 | The admin has an overview. | A dashboard shows counts by status, category and department. |
| G7 | Citizens are told when something changes. | In-app notifications appear on submission, each status change, and duplicate linking. |

## 4. Users

| Role | Description |
|---|---|
| **Citizen** | Registers, submits complaints, tracks their own complaints and the ones they watch, closes a resolved complaint. |
| **Department Officer** | Belongs to one department. Works the complaints assigned to that department: moves them through the statuses, adds remarks, marks duplicates and rejects invalid ones. |
| **Administrator** | Reviews new complaints, assigns them to departments, manages departments, officers, categories and areas, monitors all statuses and views reports. |

Details are in **02 Glossary & Actors**.

## 5. Scope

### 5.1 In scope (this release)

| Module | Included |
|---|---|
| M1 Registration & Login | Citizen self-registration, login and logout for all three roles. Officer and admin accounts are created by the admin or the seed data, not by self-registration. |
| M2 Complaint Registration | Category, title, description, **area (dropdown) + free-text address**, one optional photo, and a generated complaint ID. |
| M3 Complaint Tracking | "My Complaints" list, a complaint detail page with a status timeline, and a "Watching" list. Only the owner, the watchers, the assigned department's officers and the admin can view a complaint. |
| M4 Admin Dashboard | List and filter all complaints, review them, assign or reassign a department, view the full history. |
| M5 Department Management | CRUD for departments and officer accounts. Each category maps to a default department, which is suggested when assigning. |
| M6 Reports & Summary | Counts by status, category, department and area, plus a list of overdue complaints. |
| M7 Notifications | In-app only: a bell icon with an unread count and a notification list. |
| Duplicate handling | Mark complaint B as a duplicate of complaint A, with a note. B's citizen becomes a watcher of A and is notified when A changes or is resolved. |
| Extra statuses | `REJECTED`, `DUPLICATE`, `REOPENED` and `CLOSED`, in addition to the five statuses in the synopsis. |
| Resolution review | The owner closes or reopens a `RESOLVED` complaint. It auto-closes after the review window. |

### 5.2 Out of scope (future, from the synopsis and these decisions)

- Google Maps: there are **no latitude/longitude fields yet**, but the location data is kept in its own columns so they can be added later without redesigning anything.
- Email or SMS alerts.
- A mobile app or public REST API.
- Automatic similar-complaint detection. Duplicates are marked manually for now.
- Public or anonymous complaint lookup by ID.
- Several photos per complaint, comments or chat threads, and citizen ratings.
- Multiple languages.

## 6. Constraints

| ID | Constraint |
|---|---|
| C1 | Java with Spring Boot, MySQL and JDBC, as required by the synopsis. |
| C2 | One deployable unit: Spring Boot with server-rendered Thymeleaf pages, packaged as an **executable JAR with embedded Tomcat**. |
| C3 | Use the latest **stable** releases. The JDK must be an LTS version (**JDK 25**). Exact versions are fixed in doc 07. |
| C4 | It must run on a student laptop (Core i3, 4 GB RAM) with a local MySQL. |
| C5 | Keep it simple: no microservices, message brokers or SPA frameworks. |

## 7. Assumptions

- A1: One complaint has exactly one category and, once assigned, exactly one responsible department.
- A2: Each officer belongs to exactly one department.
- A3: Photos are stored on the server's file system, and their paths are stored in the database.
- A4: Areas (wards or localities) are a fixed list maintained by the admin.
- A5: Every user has an email address, which is used as the login ID.

## 8. Success criteria for the project demo

1. A citizen registers, submits a complaint with a photo, and gets a complaint ID.
2. The admin assigns it to a department.
3. The officer moves it to In Progress and then Resolved, with remarks. The citizen sees each step and a notification for it.
4. A second citizen submits a similar complaint. The officer marks it as a duplicate, and the second citizen now sees the original complaint and its updates.
5. The citizen reopens a resolved complaint, the officer resolves it again, and the citizen closes it. Another resolved complaint is auto-closed by the scheduled job.
6. The admin dashboard shows the correct counts.

## Change log
| Date | Change |
|---|---|
| 2026-11-22 | Initial draft |
| 2026-11-22 | Added reopen and auto-close (D8, D9) |
