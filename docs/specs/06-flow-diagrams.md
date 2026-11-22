# 06 — Flow Diagrams

| | |
|---|---|
| **Status** | Draft |
| **Depends on** | 04 Use Cases, 05 Domain Model & Business Rules |

All diagrams are in **Mermaid**, so GitHub renders them directly. For the project report, export them as PNG (see §6).
Class names in the sequence diagrams (`ComplaintController`, `ComplaintService`, …) are indicative and are fixed in doc 07.

| Section | Diagrams |
|---|---|
| §1 Data Flow Diagrams | DFD-0 (context), DFD-1 |
| §2 End-to-end lifecycle | Swimlane overview |
| §3 Activity diagrams | AD-01 … AD-08 |
| §4 Sequence diagrams | SD-01 … SD-07 |
| §5 Web request pattern | Post/Redirect/Get |

---

## 1. Data Flow Diagrams

Notation: rectangle = external entity, rounded = process, cylinder = data store.

### DFD-0 — Context diagram

```mermaid
flowchart LR
    CIT[Citizen]
    OFF[Department Officer]
    ADM[Administrator]
    CLK[System Clock / Scheduler]

    SYS((0.0<br/>Smart Public Grievance<br/>& Complaint<br/>Management System))

    CIT -- "registration details, OTP,<br/>complaint + attachments,<br/>edits, withdraw, close/reopen" --> SYS
    SYS -- "OTP, complaint number,<br/>status timeline, notifications" --> CIT

    OFF -- "start work, remarks, resolve,<br/>duplicate link, reject, send back" --> SYS
    SYS -- "department queue, complaint details,<br/>notifications, summary" --> OFF

    ADM -- "review, assign/reassign,<br/>master data, officer accounts" --> SYS
    SYS -- "all complaints, dashboard,<br/>reports, notifications" --> ADM

    CLK -- "hourly tick" --> SYS
```

### DFD-1 — Main processes

```mermaid
flowchart TB
    CIT[Citizen]
    OFF[Officer]
    ADM[Admin]
    CLK[Scheduler]

    P1(["1.0 Manage accounts<br/>& OTP verification"])
    P2(["2.0 Register / edit /<br/>withdraw complaint"])
    P3(["3.0 Process complaint<br/>(review, assign, work,<br/>resolve, reject, duplicate)"])
    P4(["4.0 Track & review<br/>(view, close, reopen, watch)"])
    P5(["5.0 Generate<br/>notifications"])
    P6(["6.0 Manage master data<br/>& staff accounts"])
    P7(["7.0 Dashboard<br/>& reports"])
    P8(["8.0 Auto-close<br/>resolved complaints"])

    D1[(D1 Users)]
    D2[(D2 Mobile OTP)]
    D3[(D3 Complaints)]
    D4[(D4 Attachment files<br/>+ metadata)]
    D5[(D5 Complaint events<br/>& revisions)]
    D6[(D6 Watchers)]
    D7[(D7 Notifications)]
    D8[(D8 Departments,<br/>Categories, Areas)]

    CIT -- "register, login, OTP" --> P1
    OFF -- login --> P1
    ADM -- login --> P1
    P1 <--> D1
    P1 <--> D2

    CIT -- "complaint, files, edits, withdraw" --> P2
    D8 -- "active categories, areas" --> P2
    P2 --> D3
    P2 --> D4
    P2 --> D5
    P2 -- event --> P5

    ADM -- "review, assign, reject, duplicate" --> P3
    OFF -- "start, remark, resolve,<br/>reject, duplicate, send back" --> P3
    P3 <--> D3
    P3 --> D5
    P3 <--> D6
    D8 -- "departments" --> P3
    P3 -- event --> P5

    CIT -- "view, close, reopen, unwatch" --> P4
    D3 --> P4
    D4 --> P4
    D5 --> P4
    P4 <--> D6
    P4 --> D3
    P4 -- event --> P5
    P4 -- "timeline, files" --> CIT

    D1 -- "recipients" --> P5
    D6 -- "watchers" --> P5
    P5 --> D7
    D7 -- "bell, list" --> CIT
    D7 --> OFF
    D7 --> ADM

    ADM -- "departments, categories,<br/>areas, officers, resets" --> P6
    P6 <--> D8
    P6 <--> D1

    D3 --> P7
    D8 --> P7
    P7 -- "dashboard, overdue" --> ADM
    P7 -- "dept summary" --> OFF

    CLK -- tick --> P8
    P8 <--> D3
    P8 --> D5
    P8 -- event --> P5
```

---

## 2. End-to-end lifecycle (swimlane overview)

```mermaid
flowchart LR
    subgraph Citizen
        C1[Register + verify OTP] --> C2[Submit complaint]
        C2 --> C3{{Complaint no. shown}}
        C8[Review resolution]
        C8 -->|Satisfied| C9[Close]
        C8 -->|Not satisfied| C10[Reopen with reason]
    end

    subgraph Admin
        A1[Start review] --> A2{Valid?}
        A2 -->|No| A3[Reject]
        A2 -->|Same as active complaint| A4[Mark duplicate]
        A2 -->|Yes| A5[Assign department]
    end

    subgraph Officer
        O1[Department queue] --> O2{Right department?}
        O2 -->|No| O3[Send back to admin]
        O2 -->|Yes| O4[Start work]
        O4 --> O5[Progress remarks]
        O5 --> O6[Resolve]
    end

    subgraph System
        S1[Generate number,<br/>store, notify]
        S2[Notify owner<br/>+ watchers]
        S3{Review window<br/>expired?}
        S4[Auto-close]
    end

    C2 --> S1 --> A1
    A5 --> S2 --> O1
    O3 --> A1
    O6 --> S2
    S2 --> C8
    O6 --> S3
    S3 -->|Yes, no response| S4
    C10 --> O1
```

---

## 3. Activity diagrams

### AD-01 Registration & mobile OTP verification (UC-01, UC-04)

```mermaid
flowchart TD
    start([Start]) --> f[Open Register form]
    f --> e[Enter name, email, mobile,<br/>password, confirm]
    e --> v{Valid and<br/>email/mobile unique?}
    v -- No --> err[Show field errors] --> e
    v -- Yes --> c[Create CITIZEN account<br/>mobileVerified = false<br/>password BCrypt-hashed]
    c --> li[Auto login]
    li --> g[Generate 6-digit OTP<br/>store hash, expiry +5 min]
    g --> s[OtpSender.send<br/>demo: show on page + log]
    s --> in[Citizen enters OTP]
    in --> chk{Matches and<br/>not expired?}
    chk -- Yes --> ok[mobileVerified = true<br/>invalidate OTP]
    ok --> home[Redirect to My Complaints<br/>'Mobile verified']
    home --> stop([End])
    chk -- No --> att{Attempts < 3<br/>and not expired?}
    att -- Yes --> inc[attempts++<br/>'Incorrect OTP, N left'] --> in
    att -- No --> exp['OTP expired, request new']
    exp --> rs{Resend allowed?<br/>60 s cooldown, 5 per hour}
    rs -- Yes --> g
    rs -- No --> wait['Please wait and retry'] --> rs
```

### AD-02 Submit complaint (UC-10)

```mermaid
flowchart TD
    start([Start]) --> ver{Citizen verified?}
    ver -- No --> otp[Redirect to Verify mobile] --> stop1([End])
    ver -- Yes --> form[Show form<br/>active categories and areas]
    form --> fill[Fill title, description, category,<br/>area, address, 0–5 files]
    fill --> val{Fields valid?}
    val -- No --> ferr[Re-show form with errors] --> fill
    val -- Yes --> fv{Each file JPG/PNG/PDF by magic bytes,<br/>≤ 5 MB, count ≤ 5?}
    fv -- No --> ferr
    fv -- Yes --> tx[[Begin transaction]]
    tx --> seq[Lock complaint_sequence row<br/>for current year, increment]
    seq --> no[Build GRV-YYYY-NNNNNN]
    no --> ins[Insert complaint status SUBMITTED]
    ins --> files[Write files to uploadDir/yyyy/MM/uuid.ext<br/>insert attachment rows]
    files --> ev[Insert event CREATED]
    ev --> nt[Insert notifications:<br/>owner + active admins]
    nt --> ok{All succeeded?}
    ok -- Yes --> commit[[Commit]] --> conf[Show confirmation<br/>with complaint number] --> stop2([End])
    ok -- No --> rb[[Rollback + delete written files]] --> msg['Could not save, try again'] --> fill
```

### AD-03 Admin review & assignment (UC-20, UC-21, UC-22)

```mermaid
flowchart TD
    start([Admin opens SUBMITTED complaint]) --> r[Click Start Review<br/>status: UNDER_REVIEW, notify owner]
    r --> d{Decision}
    d -- Invalid / not a public issue --> rej[Enter reason → REJECTED<br/>notify owner + watchers]
    d -- Same as an active complaint --> dup[[AD-06 Mark duplicate]]
    d -- Valid --> pre[Department pre-selected<br/>from category default]
    pre --> has{Default exists?<br/>category 'Other' has none}
    has -- No --> pick[Admin picks department]
    has -- Yes --> keep[Admin keeps or changes it]
    pick --> act{Department active?}
    keep --> act
    act -- No --> pick
    act -- Yes --> asg[status: ASSIGNED, set department<br/>event + notify owner, watchers,<br/>department officers]
    rej --> stop([End])
    dup --> stop
    asg --> stop
```

### AD-04 Officer work & resolution (UC-30 to UC-34)

```mermaid
flowchart TD
    start([Officer opens Department Queue]) --> q[List ASSIGNED / IN_PROGRESS / REOPENED<br/>overdue first, then oldest]
    q --> open[Open complaint]
    open --> chk{Belongs to my department?}
    chk -- No --> nf[404 Not Found] --> stop([End])
    chk -- Yes --> st{Current status}
    st -- ASSIGNED / REOPENED --> d1{Right department?}
    d1 -- No --> sb[Send back with remark<br/>status: UNDER_REVIEW, department cleared<br/>notify admins] --> stop
    d1 -- Yes --> sw[Start work with remark<br/>status: IN_PROGRESS<br/>notify owner + watchers]
    sw --> ip
    st -- IN_PROGRESS --> ip{Work done?}
    ip -- Not yet --> rm[Add progress remark<br/>notify owner + watchers] --> ip
    ip -- Yes --> res[Resolve with remark<br/>optional work-done files<br/>status: RESOLVED, resolvedAt = now]
    res --> nt[Notify owner + watchers<br/>'Review by date']
    nt --> stop
    st -- Not valid at any point --> rj[Reject with reason → REJECTED] --> stop
```

### AD-05 Citizen review of resolution (UC-15)

```mermaid
flowchart TD
    start([Owner opens RESOLVED complaint]) --> w{now ≤ resolvedAt + review window?}
    w -- No --> ro[Read-only<br/>auto-close pending or done] --> stop([End])
    w -- Yes --> ch{Satisfied?}
    ch -- Yes --> cl[Close, optional feedback<br/>status: CLOSED<br/>notify department officers] --> stop
    ch -- No --> lim{reopenCount < maxReopens?}
    lim -- No --> msg['Reopen limit reached,<br/>contact the department'<br/>only Close offered] --> ch
    lim -- Yes --> rs[Enter reason 10–500 chars]
    rs --> rp[status: REOPENED, reopenCount++<br/>resolvedAt = null<br/>notify department officers + admins]
    rp --> back[Back in department queue → AD-04] --> stop
```

### AD-06 Mark complaint as duplicate (UC-23)

```mermaid
flowchart TD
    start([Actor on complaint B clicks Mark as Duplicate]) --> in[Enter original number A + note]
    in --> e{A exists and A ≠ B?}
    e -- No --> er1['Complaint not found / same complaint'] --> in
    e -- Yes --> s{A is ASSIGNED, IN_PROGRESS<br/>or REOPENED?}
    s -- No --> er2['A cannot be used as original'] --> in
    s -- Yes --> role{Actor is Officer?}
    role -- Yes --> dep{A and B both in<br/>officer's department?}
    dep -- No --> er3['Different department:<br/>use Send back to admin'] --> stop([End])
    dep -- Yes --> cf
    role -- No, Admin --> cf[Show A's title, area, status<br/>Confirm?]
    cf -- Cancel --> stop
    cf -- Confirm --> tx[[Begin transaction]]
    tx --> b[B: status DUPLICATE, duplicateOf = A<br/>event DUPLICATE_LINKED with note]
    b --> a[A: event DUPLICATE_RECEIVED]
    a --> wt[For B.owner and B's active watchers:<br/>skip A.owner / already active<br/>else insert or reactivate watcher on A]
    wt --> n[Notify B.owner + B's watchers<br/>'Linked to A, updates will follow']
    n --> cm[[Commit]] --> stop
```

### AD-07 Edit or withdraw complaint (UC-11, UC-12)

```mermaid
flowchart TD
    start([Owner on complaint detail]) --> act{Action}
    act -- Edit --> es{Status SUBMITTED<br/>or UNDER_REVIEW?}
    es -- No --> no1[Button hidden / POST refused] --> stop([End])
    es -- Yes --> ef[Pre-filled form, current files<br/>with remove checkbox, add files]
    ef --> ev{Valid and active files ≤ 5?}
    ev -- No --> ef
    ev -- Yes --> diff{Any field changed?}
    diff -- No --> nc['No changes to save'] --> stop
    diff -- Yes --> rev[Event EDITED + one revision per changed field<br/>files: added / soft-deleted<br/>notify admins] --> stop
    act -- Withdraw --> ws{Status SUBMITTED, UNDER_REVIEW<br/>or ASSIGNED?}
    ws -- No --> no2['Work already started'] --> stop
    ws -- Yes --> dl{Any duplicates linked<br/>to this complaint?}
    dl -- Yes --> no3['Other citizens are following this,<br/>cannot withdraw'] --> stop
    dl -- No --> wr[Enter reason → WITHDRAWN<br/>notify admins + department officers if assigned] --> stop
```

### AD-08 Auto-close job (UC-70)

```mermaid
flowchart TD
    tick([Hourly cron fires]) --> q[Select RESOLVED complaints where<br/>resolvedAt + reviewWindow < now]
    q --> any{Any found?}
    any -- No --> log0[Log 'closed 0'] --> stop([End])
    any -- Yes --> loop[Next complaint]
    loop --> tx[[Own transaction]]
    tx --> up[UPDATE status = CLOSED<br/>WHERE id = ? AND version = ? AND status = RESOLVED]
    up --> ok{1 row updated?}
    ok -- Yes --> ev[Event AUTO_CLOSED, actor SYSTEM<br/>notify owner + department officers] --> cm[[Commit]]
    ok -- No, citizen acted meanwhile --> skip[[Rollback, skip]]
    cm --> more{More?}
    skip --> more
    more -- Yes --> loop
    more -- No --> log[Log 'closed N'] --> stop
```

---

## 4. Sequence diagrams

### SD-01 Submit complaint (UC-10)

```mermaid
sequenceDiagram
    autonumber
    actor C as Citizen (Browser)
    participant SEC as Spring Security
    participant CC as ComplaintController
    participant CS as ComplaintService
    participant FS as FileStorageService
    participant SEQ as ComplaintNumberGenerator
    participant CR as ComplaintRepository (JDBC)
    participant ER as EventRepository
    participant NS as NotificationService
    participant DB as MySQL

    C->>SEC: POST /citizen/complaints (multipart, CSRF token)
    SEC->>SEC: authenticated? role CITIZEN? CSRF valid?
    SEC->>CC: forward
    CC->>CC: @Valid ComplaintForm (BR-20)
    alt validation errors
        CC-->>C: 200 re-render form with errors
    else valid
        CC->>CS: submit(form, files, currentUser)
        CS->>CS: check mobileVerified (BR-86)
        CS->>FS: validate(files) magic bytes, size, count
        Note over CS,DB: @Transactional begins
        CS->>SEQ: next(year)
        SEQ->>DB: SELECT last_value ... FOR UPDATE, then UPDATE
        SEQ-->>CS: GRV-2026-000012
        CS->>CR: insert(complaint SUBMITTED)
        CR->>DB: INSERT complaint
        CS->>FS: store(files) → uuid names
        FS-->>CS: stored metadata
        CS->>CR: insertAttachments(...)
        CS->>ER: insert(CREATED event)
        CS->>NS: notify(SUBMITTED, complaint)
        NS->>DB: INSERT notifications (owner, admins)
        Note over CS,DB: commit (on exception: rollback + FS.delete(stored))
        CS-->>CC: complaintNo
        CC-->>C: 302 redirect /citizen/complaints/GRV-2026-000012/submitted
        C->>CC: GET confirmation page
        CC-->>C: 200 "Your complaint number is GRV-2026-000012"
    end
```

### SD-02 Generic status transition with access check and optimistic lock (T02–T13)

```mermaid
sequenceDiagram
    autonumber
    actor U as User (Officer example)
    participant WC as WorkflowController
    participant WS as ComplaintWorkflowService
    participant AP as ComplaintAccessPolicy
    participant SM as TransitionRules (state machine)
    participant CR as ComplaintRepository
    participant ER as EventRepository
    participant NS as NotificationService
    participant DB as MySQL

    U->>WC: POST /officer/complaints/{no}/resolve {remark, version}
    WC->>WS: resolve(no, remark, version, user)
    WS->>CR: findByNo(no)
    CR->>DB: SELECT ...
    WS->>AP: canView(user, complaint)?
    alt not visible
        AP-->>WS: false
        WS-->>WC: ComplaintNotFoundException
        WC-->>U: 404 page
    else visible
        WS->>SM: check(RESOLVE, status, role, guards)
        alt not allowed
            SM-->>WS: InvalidTransitionException
            WC-->>U: redirect back with "Action not allowed when IN_PROGRESS..."
        else allowed
            Note over WS,DB: @Transactional
            WS->>CR: updateStatus(id, RESOLVED, version)
            CR->>DB: UPDATE complaint SET status=?, resolved_at=?, version=version+1 WHERE id=? AND version=?
            alt 0 rows updated
                WS-->>WC: ConcurrentUpdateException (rollback)
                WC-->>U: redirect with "Updated by someone else, please refresh"
            else 1 row
                WS->>ER: insert(STATUS_CHANGED IN_PROGRESS→RESOLVED, remark)
                WS->>NS: notify(RESOLVED, complaint, actor)
                NS->>DB: INSERT notifications (owner + active watchers, minus actor, de-duplicated)
                Note over WS,DB: commit
                WC-->>U: 302 redirect to detail, flash "Complaint resolved"
            end
        end
    end
```

### SD-03 Mark duplicate (UC-23)

```mermaid
sequenceDiagram
    autonumber
    actor O as Officer
    participant WC as WorkflowController
    participant DS as DuplicateService
    participant AP as ComplaintAccessPolicy
    participant CR as ComplaintRepository
    participant WR as WatcherRepository
    participant ER as EventRepository
    participant NS as NotificationService

    O->>WC: GET /officer/complaints/{B}/duplicate?original={A}
    WC->>DS: preview(B, A, officer)
    DS->>AP: canView(officer, B)
    DS->>DS: rules BR-40..43 (exists, ≠, status, same dept)
    DS-->>WC: A summary (title, area, status)
    WC-->>O: confirmation page
    O->>WC: POST confirm {A, note, versionB}
    WC->>DS: markDuplicate(B, A, note, versionB, officer)
    Note over DS,NS: @Transactional
    DS->>DS: re-check rules (state may have changed)
    DS->>CR: update B: status DUPLICATE, duplicate_of = A (version check)
    DS->>ER: event DUPLICATE_LINKED on B
    DS->>ER: event DUPLICATE_RECEIVED on A
    DS->>WR: activeWatchers(B) + B.owner
    loop each person, skipping A.owner and already-active watchers
        DS->>WR: upsert watcher(A, person, active=true)
    end
    DS->>NS: notify(DUPLICATE_LINKED, B owner + B watchers)
    Note over DS,NS: commit
    WC-->>O: redirect to B detail, flash "Linked to A"
```

### SD-04 OTP verification (UC-04)

```mermaid
sequenceDiagram
    autonumber
    actor C as Citizen
    participant VC as VerificationController
    participant OS as OtpService
    participant OR as OtpRepository
    participant SND as OtpSender (DemoOtpSender)
    participant UR as UserRepository

    C->>VC: GET /verify-mobile
    VC->>OS: issue(user)
    OS->>OS: check cooldown (60 s) and hourly limit (5)
    OS->>OR: invalidate previous unused OTPs
    OS->>OS: code = SecureRandom 6 digits
    OS->>OR: insert(hash(code), expiresAt = now+5m, attempts=0)
    OS->>SND: send(mobile, code)
    SND-->>OS: (demo) returns code for display + logs it
    VC-->>C: page with OTP input (+ "Demo OTP: 482913")
    C->>VC: POST /verify-mobile {code}
    VC->>OS: verify(user, code)
    OS->>OR: latest active OTP
    alt expired or attempts ≥ 3
        VC-->>C: "OTP expired, request a new one"
    else hash mismatch
        OS->>OR: attempts++
        VC-->>C: "Incorrect OTP, N attempts left"
    else match
        OS->>OR: mark used
        OS->>UR: set mobile_verified = true
        VC-->>C: 302 /citizen/complaints, "Mobile verified"
    end
```

### SD-05 Auto-close job (UC-70)

```mermaid
sequenceDiagram
    autonumber
    participant SCH as Spring @Scheduled
    participant AJ as AutoCloseJob
    participant WS as ComplaintWorkflowService
    participant CR as ComplaintRepository
    participant ER as EventRepository
    participant NS as NotificationService

    SCH->>AJ: run() (cron hourly)
    AJ->>CR: findExpiredResolved(now − reviewWindow)
    CR-->>AJ: [ids + versions]
    loop each complaint
        AJ->>WS: autoClose(id, version)  [REQUIRES_NEW transaction]
        WS->>CR: UPDATE ... SET status=CLOSED WHERE id=? AND version=? AND status='RESOLVED'
        alt 1 row
            WS->>ER: event AUTO_CLOSED (actor null)
            WS->>NS: notify owner + department officers
        else 0 rows
            WS-->>AJ: skipped (citizen acted first)
        end
    end
    AJ->>AJ: log "Auto-closed N complaints"
```

### SD-06 View attachment (UC-16)

```mermaid
sequenceDiagram
    autonumber
    actor U as User
    participant AC as AttachmentController
    participant AS as AttachmentService
    participant AP as ComplaintAccessPolicy
    participant AR as AttachmentRepository
    participant FS as FileStorageService

    U->>AC: GET /attachments/{id}
    AC->>AS: load(id, user)
    AS->>AR: findById(id) with complaint
    AS->>AP: canView(user, complaint)
    alt not allowed, or deleted and user not admin
        AC-->>U: 404
    else allowed
        AS->>FS: open(storedName)
        alt file missing on disk
            AC-->>U: 404 (error logged)
        else ok
            AC-->>U: 200 stream, Content-Type from DB,<br/>Content-Disposition inline, X-Content-Type-Options nosniff
        end
    end
```

### SD-07 Login and role-based landing (UC-02)

```mermaid
sequenceDiagram
    autonumber
    actor U as User
    participant SEC as Spring Security filter chain
    participant UDS as AppUserDetailsService
    participant UR as UserRepository
    participant SH as RoleBasedSuccessHandler

    U->>SEC: POST /login {email, password, _csrf}
    SEC->>UDS: loadUserByUsername(email lower-cased)
    UDS->>UR: findByEmail
    UR-->>UDS: user (hash, role, active)
    UDS-->>SEC: UserDetails (enabled = active)
    SEC->>SEC: BCrypt match + enabled check
    alt failure
        SEC-->>U: 302 /login?error "Invalid email or password, or account inactive"
    else success
        SEC->>SH: onAuthenticationSuccess
        SH-->>U: 302 by role: CITIZEN → /citizen/complaints<br/>OFFICER → /officer/queue, ADMIN → /admin/dashboard
    end
```

---

## 5. Web request pattern (applies to every form)

All state-changing actions use **Post/Redirect/Get**, so a browser refresh never resubmits a form:

```mermaid
sequenceDiagram
    actor B as Browser
    participant C as Controller
    B->>C: POST /.../action (form + _csrf + version)
    alt validation error
        C-->>B: 200 same view with errors (no redirect)
    else success or business-rule refusal
        C-->>B: 302 Location: detail page (flash message)
        B->>C: GET detail page
        C-->>B: 200 view (shows flash once)
    end
```

---

## 6. Exporting diagrams for the report

- **GitHub / IntelliJ:** Mermaid renders in the Markdown preview.
- **PNG export:** paste a diagram into <https://mermaid.live> and download PNG/SVG, or use the CLI: `npx -p @mermaid-js/mermaid-cli mmdc -i docs/specs/06-flow-diagrams.md -o docs/diagrams/flow.png`.
- Store exported images in `docs/diagrams/`, named by diagram ID (e.g. `AD-06.png`).

## Change log
| Date | Change |
|---|---|
| 2026-11-22 | Initial draft |
