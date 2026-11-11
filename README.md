# UniVote - University Electronic Voting System (Android)

UniVote is a native Android app for running secure university elections. Students verify themselves, vote once per position, and keep a receipt that proves their vote was recorded without revealing their choices. Election staff manage voters, candidates and schedules, publish approved results, and monitor everything through a tamper-evident audit log.

Built with **Kotlin**, **Jetpack Compose (Material 3)**, **Room (SQLite)** and **MVVM**, following the *University Electronic Voting System SRS v1.0*.

> **Storage note:** this release keeps all data **locally on the device**. Every screen talks to repository classes, so a REST API and PostgreSQL backend can replace the local storage later without rewriting the user interface. See [Known limitations](#known-limitations) and [Roadmap](#roadmap-backend-phase).

---

## Table of contents

1. [Features](#features)
2. [Roles and demo accounts](#roles-and-demo-accounts)
3. [Tech stack](#tech-stack)
4. [Architecture](#architecture)
5. [Data model](#data-model)
6. [Key rules](#key-rules)
7. [Getting started](#getting-started)
8. [Demo walkthrough](#demo-walkthrough)
9. [Testing](#testing)
10. [Accessibility](#accessibility)
11. [Security and privacy](#security-and-privacy)
12. [Project structure](#project-structure)
13. [Documentation](#documentation)
14. [Known limitations](#known-limitations)
15. [Roadmap (backend phase)](#roadmap-backend-phase)
16. [Troubleshooting](#troubleshooting)
17. [Author and licence](#author-and-licence)

---

## Features

| SRS area | What the app does |
|---|---|
| **1. Login and verification** | Voters register against the approved student roll (registration number + name + eligibility), sign in, and recover passwords. Failed attempts lock the account, sessions expire after inactivity, and sign-out is one tap. |
| **2. Voters and departments** | Admins add, edit and import voters (CSV), manage departments, and mark voters eligible or not eligible. Voters are never deleted, so history stays intact. |
| **3. Candidates and parties** | Admins manage parties and candidates, and approve or reject candidates. Editing a candidate sends them back to pending. Only approved candidates appear to voters. |
| **4. Voting** | Voters see only the positions open to their department, choose a candidate or abstain, review the ballot, and submit once. A receipt code confirms the vote without revealing it. |
| **5. Election management** | Create elections with schedules and positions. Life cycle: draft, scheduled, open, suspended, closed. Setup locks once voting opens. Voting stops automatically at the end time. |
| **6. Counting and results** | Counting is allowed only after closing. Ballot counts are reconciled against participation records, ties are flagged instead of guessed, and admins approve before publishing. Published results are stored as a permanent snapshot. |
| **7. Notifications and feedback** | In-app inbox plus Android notifications for vote confirmations, schedule changes, openings, closings, results and support updates. Users can send feedback with a rating. |
| **8. Live statistics** | Turnout overall, by position and by department, updating as votes are recorded. No candidate totals are ever shown live, and very small groups are hidden. |
| **9. Monitoring and records** | Append-only audit log with a SHA-256 hash chain, automatic chain verification, search and CSV export. Observers have read-only access. |
| **10. Support and fraud reporting** | Voters and observers file technical or fraud reports with optional evidence and receive a reference number. Officers assign, update and resolve reports with outcome notes. |
| **11. Security, privacy, accessibility** | Salted password hashing, ballot secrecy by design, role-based screens, screenshot blocking on the ballot, WCAG AA contrast, 48 dp touch targets, screen-reader support. |

---

## Roles and demo accounts

On first launch the database is created and seeded with demo data.

| Username | Password | Role | Lands on |
|---|---|---|---|
| `admin` | `Admin@123` | Election Administrator | Admin menu |
| `observer` | `Observer@123` | Election Observer | Statistics, results, audit |
| `support` | `Support@123` | Support Officer | Report queue |
| `sysadmin` | `System@123` | System Administrator | Audit records, report queue |
| `CS/0001/2024` | `Voter@123` | Voter (Computer Science) | Elections list |
| `BBA/0001/2024` | `Voter@123` | Voter (Business Administration) | Elections list |

**Seed data:** 5 departments, 12 students on the roll, 2 parties, 1 election ("Student Council Elections 2026", status *scheduled*) with 4 positions and 9 candidates.

- `HS/0002/2022` (Lawrence Omondi) is on the roll but **not eligible**.
- Candidate *Collins Rotich* is **pending** approval.
- Other students can register from the sign-in screen, for example `CS/0002/2024` with the name `Brian Otieno`.

> **Remove the demo accounts and seed data before any real deployment** (`DatabaseSeeder.kt`).

**Passwords** need at least 8 characters with an uppercase letter, a lowercase letter and a digit.

---

## Tech stack

| Area | Choice |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose, Material 3, Navigation Compose |
| Architecture | MVVM with repositories and a pure-Kotlin domain layer |
| Database | Room (SQLite) with KSP, schema export enabled |
| Async | Kotlin Coroutines and Flow |
| Dependency injection | Manual container (`AppContainer`) |
| Security | PBKDF2-SHA256 hashing, SHA-256 audit hash chain |
| Testing | JUnit 4, AndroidX Test, Compose UI tests, in-memory Room |
| Minimum SDK | 26 (Android 8.0) |
| IDE | Android Studio |

---

## Architecture

```
UI (Compose screens)  ->  ViewModels  ->  Repositories  ->  Room (SQLite)
                                              |
                                              +-> Domain rules (pure Kotlin)
                                              +-> AuditLogger (hash chain)
                                              +-> SessionManager, PasswordHasher
```

- **Presentation:** Compose screens, ViewModels holding screen state, and `AppNavHost` with role guards.
- **Domain rules** (unit-testable, no Android code): election state machine, results calculator, audit-chain verifier, ticket workflow, password policy.
- **Data:** twelve repositories (Auth, Voting, Election, Voter, Department, Party, Results, Statistics, Support, Audit, Notification, Feedback), the Room database, and `AuditLogger`.
- **Core services:** session handling (15-minute inactivity timeout), password hashing, notification delivery.
- **Dependency injection:** a small manual container keeps the build simple and avoids extra annotation processors.

Diagrams (Mermaid) are in `docs/diagrams/`: architecture, entity-relationship diagram, use cases, and sequence diagrams for login, casting a vote and publishing results.

---

## Data model

Fourteen tables: `departments`, `voters`, `users`, `political_parties`, `elections`, `positions`, `candidates`, `ballots`, `participation`, `results`, `notifications`, `feedback`, `audit_logs`, `support_tickets`.

The most important design choice is the **separation of identity and choices**:

- `participation` records *that* a voter voted for a position (voter, receipt code, time).
- `ballots` records *what was chosen*, with **no voter and no timestamp**, a **random primary key**, and a shuffled insert order, so rows cannot be matched by position or order.
- A **unique index** on (election, position, voter) in `participation` is the final duplicate-vote guard.

---

## Key rules

- A voter can vote **once per position**, only while the election is **open and inside its time window**, and only for positions open to their **department**.
- The vote is saved in **one database transaction**. If any check fails, nothing is stored.
- Candidates must be **approved** to appear on a ballot or receive votes.
- Election setup (details, positions, candidates) is **locked** once voting opens.
- Results can be counted only when the election is **closed**, and only an **administrator** can publish them after ballot and participation counts match.
- A tie gives **no automatic winner**; the university's rules decide the next step.
- **5 failed sign-ins** lock an account for **5 minutes**. Sessions end after **15 minutes** of inactivity.
- Departments or positions with fewer than **3 eligible voters** are hidden in statistics (a demo value; use a larger one in production).

---

## Getting started

### Prerequisites

- Current stable **Android Studio**
- An emulator or phone running **Android 8.0 (API 26)** or newer

### Run the app

1. Clone or copy the project and open it in Android Studio.
2. Let Gradle sync finish.
3. Choose an emulator or connected device and press **Run**.
4. Allow notifications when asked (needed for system alerts on Android 13 and newer).

### Build a signed APK

**Build > Generate Signed App Bundle / APK > APK**, create a keystore, and pick the release variant. Release builds use code shrinking. **Never commit the keystore or its passwords.**

### Voter CSV import format

```
regNumber,fullName,departmentCode,yearOfStudy
ENG/0003/2024,Peter Mwangi,ENG,2
EDU/0003/2024,Rose Akinyi,EDU,1
```

The header row is optional. Names cannot contain commas. Existing registration numbers are skipped and bad rows are reported.

Election dates are typed as `yyyy-MM-dd HH:mm` (for example `2026-12-01 08:00`).

---

## Demo walkthrough

1. Sign in as `admin`, open **Elections**, open the seeded election, and tap **Open voting**.
2. Sign in as `CS/0001/2024`, tap **Vote now**, choose or abstain for each position, review, and submit. Keep the receipt code.
3. Open **Live turnout** from the election card and watch it update.
4. Back as `admin`: **Close voting**, then **Results and statistics > Count and publish results**, review the preview, and **Approve and publish**.
5. Sign in as a voter to see the **official results** and the alerts.
6. Sign in as `observer` to open **Audit records** and check that the chain banner says it is verified.
7. As a voter, use **Report a problem** to file a report, then sign in as `support` to assign and resolve it.

---

## Testing

**130 automated tests**

| Level | Count | What it covers |
|---|---|---|
| Unit (JVM) | 36 | Password hashing and policy, election state machine, results and tie logic, audit-chain verification, ticket workflow, date parsing, ballot privacy guard |
| Repository integration (in-memory Room) | 84 | Authentication and lockout, sessions, election life cycle, voter import, voting rules, results publishing, statistics, notifications, support workflow, audit access and tamper detection, feedback and schedule alerts |
| Compose UI | 10 | Login behaviour and touch-target size, full ballot flow (select, review, submit, receipt), discard confirmation |

Run them:

```bash
./gradlew testDebugUnitTest             # unit tests (no device needed)
./gradlew connectedDebugAndroidTest     # integration + UI tests (emulator or device)
```

Record results, manual test cases (M-01 to M-14), accessibility checks (A-01 to A-06) and security checks (S-01 to S-08) in the project report.

---

## Accessibility

- Text and background colours meet **WCAG 2.1 AA** (every measured pair is at least 6.2:1 for normal text, most are above 10:1) in both light and dark themes.
- Interactive controls are at least **48 dp**; text uses scalable units and every screen scrolls at large font sizes.
- Screen readers: screen titles are headings, errors are announced as live regions, choices use radio-button semantics, progress rows read as one sentence, and the password toggle says "Show password" or "Hide password".
- Status is always written as text, never colour alone.

---

## Security and privacy

- **Passwords:** salted PBKDF2-SHA256 (100,000 iterations), never stored in plain text; generic sign-in errors; timing equalised for unknown users.
- **Ballot secrecy:** ballots hold no voter id or time, use random keys and shuffled insert order, and a unit test fails if a voter or time field is ever added.
- **Duplicate-vote prevention:** eligibility, schedule, department and candidate checks run inside one transaction, backed by a unique index.
- **Audit log:** append-only, hash-chained, role-restricted, verified on every view. Exports are themselves audited and protected against spreadsheet formula injection. Audit entries never contain report text, feedback text or ballot choices.
- **Role control:** role-specific screens, route guards, and role checks inside repositories.
- **Device protection:** screenshots and the recent-apps preview are blocked on the ballot, cloud backup is disabled, notification content is hidden on the lock screen, and only identity details (never credentials) are kept in the session store.
- **Privacy:** only data needed for the election is stored; statistics never expose candidate totals and hide very small groups.

---

## Project structure

```
app/src/main/java/com/univote/evoting/
  UniVoteApp.kt, MainActivity.kt
  core/
    security/     PasswordHasher, PasswordPolicy
    session/      SessionManager
    notify/       NotificationCenter, SystemNotifier
    util/         DateFormats
  data/
    local/        UniVoteDatabase, DatabaseSeeder
      entity/     14 Room entities and enums
      dao/        DAOs
    audit/        AuditLogger (hash chain)
    repository/   Auth, Voting, Election, Voter, Department, Party,
                  Results, Statistics, Support, Audit, Notification, Feedback
  domain/         ElectionStateMachine, ResultsCalculator, AuditChain,
                  TicketWorkflow, results models
  di/             AppContainer
  ui/
    theme/        colours, typography, theme
    navigation/   Routes, AppNavHost
    auth/         login, register, password recovery
    voter/        voter home, ballot
    admin/        departments, voters, parties, elections, election detail
    stats/        live statistics
    results/      results and publishing
    insights/     observer home, admin results and statistics list
    support/      reports, queue, ticket detail, staff home
    audit/        audit records
    notifications/ alerts inbox
    feedback/     feedback form
    home/         fallback home
app/src/test/             JVM unit tests
app/src/androidTest/      repository integration and UI tests
docs/diagrams/            Mermaid diagrams
```

---

## Documentation

- **Project report:** requirements traceability matrix (every SRS requirement mapped to code and tests), test plan and results log, user guide, and limitations.
- **Diagrams** in `docs/diagrams/`:
  - `01-architecture.mermaid`
  - `02-erd.mermaid`
  - `03-use-cases.mermaid`
  - `04-sequence-login.mermaid`
  - `05-sequence-cast-vote.mermaid`
  - `06-sequence-publish-results.mermaid`

  Paste a file into [mermaid.live](https://mermaid.live) to export an image, or use a Mermaid plugin in your editor.

---

## Known limitations

This release is a **local-only** version of a system the SRS describes as client-server.

1. All data sits on one device. Someone with administrator or root access to the device could read the database directly, so ballot secrecy against privileged insiders is not guaranteed. The backend phase separates duties and encrypts ballot storage.
2. Role checks run in the app and repositories. Real enforcement must move to the backend (SRS 18 and 23).
3. Password recovery verifies roll details (name and year of study). A one-time code by email or SMS needs a backend.
4. The audit chain detects edits and mid-log deletions, but not deletion of the newest entries. Anchoring the latest hash on a server would close this.
5. System notifications appear only while the app is running. Push messaging needs a backend.
6. No candidate photo upload, no separate Candidate role, and no candidate browsing screen before voting opens.
7. Department-level results for open positions are not produced, by design, because that would require linking ballots to voters.
8. Ties are flagged, not resolved. The tie-break procedure is university policy.
9. Texts are written in English in the code. A translations file is future work.
10. No load testing or independent penetration test has been done.

---

## Roadmap (backend phase)

1. REST API over HTTPS mirroring the repository methods, with token authentication and multi-factor sign-in for privileged roles.
2. PostgreSQL with the same tables, and ballots in a separate, restricted schema.
3. Idempotency keys on vote submission so retries after network loss never create duplicates.
4. Server-side enforcement of roles, eligibility and the election schedule, keeping the same transaction and unique-index design.
5. Firebase Cloud Messaging for push, with optional email or SMS.
6. Rate limiting, protected backups with tested restore, monitoring, and a security test.
7. Network-aware repositories in the app with clear offline and retry states.

---

## Troubleshooting

| Problem | Fix |
|---|---|
| Gradle sync error mentioning KSP or Kotlin versions | The KSP plugin version must match your Kotlin version. Check the KSP releases page and update `ksp` in `gradle/libs.versions.toml`. |
| Demo data missing or the database looks wrong | Clear the app's storage (or uninstall) so it recreates and reseeds the database. |
| App crashes after you change a Room entity | There are no migrations in this release. Increase the database version and add a migration, or uninstall for development. |
| No system notifications on Android 13+ | Allow the notification permission when asked, or enable it in Settings > Apps > UniVote. |
| Release build fails on shrinking | Send the error and add the matching keep rules to `proguard-rules.pro`. |
| Signed out unexpectedly | Sessions end after 15 minutes of inactivity. |
| CSV import reports problems | Check four columns per row, an existing department code, and a year from 1 to 6. |

---

## Author and licence

- **Author:** [Your name]
- **Course / project:** [Course name], Software Engineering Project
- **Licence:** [Add a licence, for example MIT, or write "Coursework - all rights reserved"]

## Support and fraud reporting (Option 10)

The support reporting screen is available from the signed-in home screen. Voters can submit a technical-support or fraud/suspicious-activity report and receive a unique `UV-XXXXXXXX` reference number. Reports are stored locally in the Room database and can be followed from the same signed-in account.

Accounts with the `SUPPORT_OFFICER`, `SYSTEM_ADMIN`, or `ELECTION_ADMIN` role see the reports inbox and can move an open report to **In Review** or **Resolved**. A resolution note is stored with resolved reports.

> This project currently uses local Room storage. Reports are therefore stored on the device/database instance and are not yet synchronized between devices or sent to a central support server. A production deployment should connect this repository to an authenticated backend API and enforce support-role permissions on the server as well.
