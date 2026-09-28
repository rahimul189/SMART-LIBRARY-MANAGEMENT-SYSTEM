package com.smartlibrary.database;

import com.smartlibrary.util.DbPaths;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Handles the connection to the SQLite database (library.db) and makes
 * sure the required tables exist. The database file is created
 * automatically in the project's working directory the first time
 * this class is used - no manual setup needed.
 */
public class SQLiteConnection {

    /** Resolved per call so the test suite can point it at a temporary file. */
    private static String dbUrl() {
        return DbPaths.jdbcUrl();
    }

    /**
     * Opens (and if necessary creates) the connection to library.db.
     * Call this whenever you need to run a query - each call returns
     * a fresh connection, which the caller is responsible for closing
     * (use try-with-resources).
     */
    public static Connection connect() throws SQLException {
        Connection conn = DriverManager.getConnection(dbUrl());
        try {
            // Reads now also run on background threads (AppExecutors /
            // RefreshQueue) while the JavaFX thread may be writing. Without
            // a busy timeout SQLite answers SQLITE_BUSY immediately instead
            // of waiting the moment another connection holds the write lock.
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("PRAGMA busy_timeout = 5000");
                // SQLite ships with foreign keys OFF and the setting is
                // per-connection, so it has to be switched on for EVERY
                // connection - that is what makes the borrow_records ->
                // members / books constraints actually reject orphan rows.
                stmt.execute("PRAGMA foreign_keys = ON");
            }
            return conn;
        } catch (SQLException e) {
            try {
                conn.close();
            } catch (SQLException ignored) {
                // best effort - the original failure is what matters
            }
            throw e;
        }
    }

    /**
     * Creates the members, books and borrow_records tables if they
     * do not already exist. Safe to call every time the app starts -
     * the file is opened, never deleted or recreated, so existing rows
     * (and the tables themselves) survive every restart.
     *
     * Relationships chosen from the existing model/controller code:
     *
     *   books.id              &lt;- borrow_records.book_id     (a record always
     *                                    references one real book)
     *   members.student_id    &lt;- borrow_records.student_id   (a record always
     *                                    references one real member; the same
     *                                    student_id string is the application
     *                                    level key shared with JSONBin.io)
     *
     * members.student_id is UNIQUE, which is what makes it a valid foreign-key
     * target in SQLite. No table points at anything online: the JSONBin.io
     * data is not part of the relational schema.
     */
    public static void initializeDatabase() {
        String createMembers = """
                CREATE TABLE IF NOT EXISTS members (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    student_id TEXT UNIQUE NOT NULL,
                    name TEXT NOT NULL,
                    email TEXT UNIQUE NOT NULL,
                    department TEXT,
                    phone TEXT
                );
                """;

        String createBooks = """
                CREATE TABLE IF NOT EXISTS books (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    title TEXT NOT NULL,
                    author TEXT NOT NULL,
                    category TEXT,
                    isbn TEXT UNIQUE,
                    description TEXT,
                    cover_path TEXT,
                    status TEXT NOT NULL DEFAULT 'AVAILABLE'
                );
                """;

        String createBorrowRecords = """
                CREATE TABLE IF NOT EXISTS borrow_records (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    student_id TEXT NOT NULL,
                    book_id INTEGER NOT NULL,
                    issue_date TEXT NOT NULL,
                    return_date TEXT,
                    status TEXT NOT NULL DEFAULT 'BORROWED',
                    FOREIGN KEY (student_id) REFERENCES members(student_id),
                    FOREIGN KEY (book_id) REFERENCES books(id)
                );
                """;

        // Both foreign-key columns are joined on in ReturnBookController and
        // StudentDashboardController; plain indexes keep those joins cheap.
        String indexStudentId =
                "CREATE INDEX IF NOT EXISTS idx_borrow_records_student_id ON borrow_records(student_id);";
        String indexBookId =
                "CREATE INDEX IF NOT EXISTS idx_borrow_records_book_id ON borrow_records(book_id);";

        try (Connection conn = connect();
             Statement stmt = conn.createStatement()) {

            stmt.execute(createMembers);
            stmt.execute(createBooks);
            stmt.execute(createBorrowRecords);
            stmt.execute(indexStudentId);
            stmt.execute(indexBookId);

            System.out.println("Database ready: members, books, borrow_records tables OK.");

        } catch (SQLException e) {
            System.err.println("Failed to initialize database:");
            e.printStackTrace();
        }

        migrateAddGenderColumn();
        logForeignKeyStatus();
    }

    /**
     * Startup report for requirement "foreign keys &amp; relationships": confirms
     * that enforcement is switched on for the connection and prints the
     * constraints SQLite actually sees on borrow_records. If an old database
     * file ever turns up without them, the warning says so instead of failing
     * silently.
     */
    private static void logForeignKeyStatus() {
        String sql = "PRAGMA foreign_key_list(borrow_records)";
        boolean enforced = false;
        int foreignKeys = 0;

        try (Connection conn = connect();
             Statement stmt = conn.createStatement()) {

            try (ResultSet rs = stmt.executeQuery("PRAGMA foreign_keys")) {
                enforced = rs.next() && rs.getInt(1) == 1;
            }

            try (ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    foreignKeys++;
                    System.out.println("Foreign key: borrow_records." + rs.getString("from")
                            + " -> " + rs.getString("table") + "(" + rs.getString("to") + ")"
                            + ", ON DELETE " + rs.getString("on_delete"));
                }
            }

            try (ResultSet rs = stmt.executeQuery("PRAGMA foreign_key_check")) {
                if (rs.next()) {
                    System.err.println("WARNING: orphan records found (foreign_key_check reported "
                            + "rows pointing at missing parents).");
                }
            }

        } catch (SQLException e) {
            System.err.println("Failed to inspect foreign keys:");
            e.printStackTrace();
            return;
        }

        if (!enforced) {
            System.err.println("WARNING: PRAGMA foreign_keys is OFF - constraints are not being enforced.");
        } else if (foreignKeys == 0) {
            System.err.println("WARNING: borrow_records has no foreign keys - the schema predates "
                    + "the current SQLiteConnection.initializeDatabase().");
        } else {
            System.out.println("Foreign-key enforcement ON (" + foreignKeys + " constraint(s) on borrow_records).");
        }
    }

    /**
     * Step 10 added a "gender" column to members (for the Registration
     * RadioButton). CREATE TABLE IF NOT EXISTS won't add a column to a
     * members table that already existed from an earlier run, so this
     * adds it if missing. Safe to call every time - if the column is
     * already there, SQLite throws "duplicate column name", which is
     * simply ignored.
     */
    private static void migrateAddGenderColumn() {
        try (Connection conn = connect();
             Statement stmt = conn.createStatement()) {
            stmt.execute("ALTER TABLE members ADD COLUMN gender TEXT");
        } catch (SQLException e) {
            // Column already exists - nothing to do. Any other failure is logged.
            if (e.getMessage() == null || !e.getMessage().toLowerCase().contains("duplicate column")) {
                System.err.println("Failed to migrate 'gender' column:");
                e.printStackTrace();
            }
        }
    }
}
