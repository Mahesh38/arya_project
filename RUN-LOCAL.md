# Run Smart Grievance on your computer

This guide covers everything needed to run the application on your own computer (Windows, macOS or Linux) from the ready-made JAR file: no database server, no build tools, no IDE.
The app uses an **embedded H2 database** that is saved in a folder next to the JAR, loads **demo data** on the first start, and includes the **H2 web console** for looking at the data.

> This setup is for demos, the viva and development on your own computer. It is not for a public server: production uses MySQL (see `docs/specs/14-build-and-deployment.md`) and refuses to start with this setup.

---

## 1. Prerequisites

| What | Why | Check |
|---|---|---|
| **Java 25 (JDK or JRE)** | runs the JAR | `java -version` must say `25` |
| A web browser (Chrome, Edge, Firefox) | to use the app | — |
| Port **8080** free | the app listens there (you can change it, see §4) | — |
| About 150 MB of free disk | JAR (~53 MB) + data folder | — |

Not needed: MySQL, Maven, Node.js, Docker or an IDE.

### Install Java 25 (if `java -version` does not show 25)

| System | Command |
|---|---|
| Windows 10/11 | `winget install EclipseAdoptium.Temurin.25.JDK` (then open a **new** terminal) |
| macOS | `brew install --cask temurin@25` |
| Ubuntu / Debian | `sudo apt install openjdk-25-jdk-headless` |
| Any system | download the installer from <https://adoptium.net> (Temurin 25) |

---

## 2. Download the JAR

Latest release page: **<https://github.com/Mahesh38/arya_project/releases/latest>**

Each release has:

| File | Use |
|---|---|
| `smart-grievance.jar` | the application (same file as `smart-grievance-<version>.jar`) |
| `smart-grievance-<version>.jar` | the application, with its version in the name |
| `SHA256SUMS.txt` | checksum to verify the download |

Direct download of the newest JAR (the link never changes):

```sh
# macOS / Linux
curl -L -o smart-grievance.jar https://github.com/Mahesh38/arya_project/releases/latest/download/smart-grievance.jar
```

```powershell
# Windows PowerShell
Invoke-WebRequest -Uri https://github.com/Mahesh38/arya_project/releases/latest/download/smart-grievance.jar -OutFile smart-grievance.jar
```

Optional: compare the checksum with the line for `smart-grievance-<version>.jar` in `SHA256SUMS.txt`: `sha256sum smart-grievance.jar` (Linux), `shasum -a 256 smart-grievance.jar` (macOS) or `certutil -hashfile smart-grievance.jar SHA256` (Windows).

Put the JAR in a folder of its own, for example `C:\smart-grievance` or `~/smart-grievance`: the app creates its `local-data` folder next to where you start it.

---

## 3. Run it

Open a terminal **in the folder that contains the JAR** and run (same command on every system):

```sh
java -jar smart-grievance.jar --spring.profiles.active=local
```

Wait for the line `Started SmartGrievanceApplication in … seconds` (about 5 seconds). Keep the terminal open while you use the app; press **Ctrl+C** to stop it.

The first start creates `local-data/` in that folder:

| Folder | Contents |
|---|---|
| `local-data/db/` | the H2 database file `smart-grievance.mv.db` |
| `local-data/uploads/` | uploaded photos and PDFs |
| `local-data/logs/` | `app.log` and `security.log` |

Your data is kept between runs. To start again with fresh demo data, stop the app and delete the `local-data` folder.

---

## 4. Use the application

Open **<http://localhost:8080>** and log in with a demo account:

| Role | Email | Password | Try |
|---|---|---|---|
| Admin | `admin@example.com` | `Admin@1234` | Dashboard, All Complaints, CSV export, master data and users |
| Officer (Water Supply) | `anita.deshmukh@example.com` | `Demo@1234` | Department queue, start work, resolve |
| Citizen | `mahesh@example.com` | `Demo@1234` | My Complaints, close or reopen a resolved complaint |
| Citizen | `jayesh@example.com` | `Demo@1234` | Watching list, unwatch and watch again |
| Citizen (mobile not verified) | `rahul@example.com` | `Demo@1234` | OTP verification: the code is shown on the page (demo mode, no SMS) |

You can also register a new citizen on the login page.
The full demo script for the viva is in `docs/specs/13-test-spec-and-traceability.md` §5.

Options (add them after the command in §3):

| Need | Add |
|---|---|
| Another port, e.g. 9090 | `--server.port=9090` (then open http://localhost:9090) |
| Keep the data somewhere else | set `LOCAL_DATA_DIR` before starting: `LOCAL_DATA_DIR=/path/to/data java -jar …` (macOS/Linux) or `set LOCAL_DATA_DIR=C:\data` then `java -jar …` (Windows cmd) |

---

## 5. H2 database console

The console lets you browse the tables and run SQL while the app is running. It only answers on this computer (`localhost`).

1. Open **<http://localhost:8080/h2-console>**
2. Fill in the login form exactly like this:

   | Field | Value |
   |---|---|
   | Saved Settings | Generic H2 (Embedded) |
   | Driver Class | `org.h2.Driver` |
   | JDBC URL | `jdbc:h2:file:./local-data/db/smart-grievance` |
   | User Name | `admin` |
   | Password | `admin` |

3. Click **Connect**. The tables are listed on the left; type SQL on the right and click **Run**, for example:

   ```sql
   SELECT complaint_no, status, title FROM complaint ORDER BY id;
   SELECT status, COUNT(*) FROM complaint GROUP BY status;
   SELECT full_name, email, role, active FROM app_user ORDER BY role, full_name;
   SELECT * FROM complaint_event WHERE complaint_id = 1 ORDER BY created_at;
   ```

Notes:

- The JDBC URL is the one the app prints at start: `H2 console available at '/h2-console'. Database available at 'jdbc:h2:file:./local-data/db/smart-grievance'`. The app's full URL (with `;MODE=MySQL;DATABASE_TO_LOWER=TRUE`) works too. If you started the app with `LOCAL_DATA_DIR`, use that folder instead of `./local-data` in the URL.
- `admin` / `admin` is the database login, set when the database file is first created. To use other values, set `LOCAL_DB_USER` and `LOCAL_DB_PASSWORD` **before the first start** (or delete `local-data` and start again); changing them later does not change an existing database file.
- The database login is not an app login: the app accounts are the ones in §4.
- Changing data in the console bypasses the app's rules. Use `SELECT` for looking around; delete `local-data` to get a clean demo again.

---

## 6. Build and run from the source code instead (optional)

Needs Git and Java 25 (the Maven Wrapper downloads Maven itself):

```sh
git clone https://github.com/Mahesh38/arya_project.git
cd arya_project/app
./run-local.sh --build        # Windows: run-local.bat --build   (clean build with all tests, then start)
```

`./run-local.sh` starts the built JAR, `./run-local.sh --reset` deletes `app/local-data` for fresh demo data. In IntelliJ IDEA open the project and use the run configuration **Smart Grievance (local H2)**. The built JAR is `app/target/smart-grievance.jar`.

---

## 7. Troubleshooting

| Problem | Fix |
|---|---|
| `UnsupportedClassVersionError … class file version 69.0` | Java is older than 25. Install Java 25 (§1) and open a new terminal; check `java -version`. |
| `Port 8080 was already in use` | Another program uses 8080. Stop it or add `--server.port=9090`. |
| The app starts without demo data / you see old data | You started it in a folder with an existing `local-data`. Delete that folder for fresh demo data. |
| H2 console: `Wrong user name or password` | Use `admin` / `admin`, or the `LOCAL_DB_USER`/`LOCAL_DB_PASSWORD` values the database was first created with. |
| H2 console: `Database … not found` or an empty database | The JDBC URL does not point at the app's file. Copy it from §5 (or from the start-up log) and make sure you started the app in the same folder. |
| H2 console page is not shown | It only works on `localhost` and only with `--spring.profiles.active=local`. |
| Login page says "Too many failed attempts" | Wait 15 minutes or restart the app (the counter is kept in memory). |
