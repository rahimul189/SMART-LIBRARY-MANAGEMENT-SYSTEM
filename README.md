# Smart Library Management System

A JavaFX desktop application for managing a library's books, members and loans.
Built as an academic laboratory project at KUET, Department of Computer Science
and Engineering.

Admin and student logins are handled **online** through JSONBin.io; all library
records are stored **locally** in SQLite (`library.db`).

## Features

**Admin section**

| Screen | What it does |
| --- | --- |
| Dashboard | Five live stat cards, department and category filters, recent borrowing activity slider |
| Manage Books | Full CRUD over the collection, with cover images |
| Manage Members | Register, edit and delete members; registration creates the online login |
| Issue Book | Hand a book to a student, inside one transaction |
| Return Book | Take a book back, with deadline and days-kept columns |

**Student section**

| Screen | What it does |
| --- | --- |
| Dashboard | Welcome line, the 60-day rule, counters, borrowed books, upcoming deadlines, recent activity |
| Available Books | Every book currently on the shelf |
| My Borrowed Books | Open loans with issue dates |
| Borrowing History | Every loan ever, permanently |
| My Profile | Read-only profile plus the profile picture |

## Technology

- Java 17, Maven
- JavaFX 21 (FXML views + a stylesheet)
- SQLite via JDBC (sqlite-jdbc)
- Jackson for JSON, `java.net.http.HttpClient` for the JSONBin.io REST calls
- JUnit 5 for the unit tests

## Project layout

```
src/main/java/com/smartlibrary/
  Main.java              JavaFX Application, scene switching, graceful shutdown
  concurrency/           AppExecutors (shared pool), RefreshQueue (ordered worker)
  controller/            One controller per FXML view
  data/                  LibraryRepository<T> + JdbcRepository template base
  database/              SQLiteConnection (schema), DataSeeder (sample rows)
  json/                  JSONBin.io client, config and the authentication service
  model/                 Book, Member, BorrowRecord, Gender, OverviewStats
  ui/                    Programmatic pages: StudentHome, UserGuide, AppMenus, ...
src/main/resources/com/smartlibrary/
  fxml/                  The seven FXML views
  css/style.css          The whole stylesheet
```

## Design notes

**Repository hierarchy.** `LibraryRepository<T>` is the contract,
`JdbcRepository<T>` implements it with a Template Method: connection handling is
written once and `final`, while each subclass supplies only its own `SELECT` and
its own row mapping. `AvailableBookRepository` then overrides just the SQL and
inherits the mapping, which is the third level of the hierarchy.

**Threading.** The JavaFX Application Thread only ever paints. Database reads
go to `RefreshQueue` (a single FIFO worker, so a later refresh is never
overwritten by an earlier one) and independent queries go to `AppExecutors`
(a small fixed pool, `submit` + `Future`). Every background job finishes by
handing an immutable snapshot back through `AppExecutors.runFx(...)`. Writes
run inside a transaction on a pool worker.

**Authentication.** `JsonAuthService` reads and writes one JSON document on
JSONBin.io. `registerStudent()` and `changeStudentPassword()` do a
read-modify-write guarded by `AUTH_LOCK`, so two concurrent updates cannot lose
each other. A wrong password is *not* an exception - it makes the verify call
return `false`/`null`. Network and configuration problems surface as
`JsonBinException` with a message meant for the user.

**The 60-day rule.** `LoanRules` is the single place the loan period, the
"due soon" window and the status wording live. The student dashboard and the
admin return screen both read from it, so they can never disagree.

## Build and run

```bash
mvn compile
mvn javafx:run
mvn test
```

The database file, the avatar folder and `jsonbin.properties` are created next
to the `pom.xml` on first run. `library.db` is never deleted or recreated, so
records survive every restart.

## Configuration

Copy `jsonbin.properties.example` to `jsonbin.properties` and fill in your
JSONBin.io master key and bin id:

```properties
api.key=$YOUR_JSONBIN_MASTER_KEY
bin.id=$YOUR_BIN_ID
```

`jsonbin.properties` is git-ignored and must never be committed. Values may
also be supplied without a file, in this order of precedence:

1. system properties `-Djsonbin.api.key=...`, `-Djsonbin.bin.id=...`
2. environment variables `JSONBIN_API_KEY`, `JSONBIN_BIN_ID`
3. the properties file

The bin is expected to hold:

```json
{
  "admins":   [ { "username": "...", "password": "..." } ],
  "students": [ { "studentId": "...", "email": "...", "password": "..." } ]
}
```

The `studentId` here is the same string stored in `members.student_id`, which
is what links an online login to its local profile.
