package com.smartlibrary.database;

import com.smartlibrary.util.DbPaths;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the real schema against a throwaway database file, so the
 * constraints are tested as SQLite actually enforces them rather than as the
 * DDL claims.
 *
 * <p>DbPaths redirects the connection to a temporary file for the duration of
 * the test, leaving the developer's own library.db untouched.
 */
@DisplayName("SQLiteConnection - the schema and its constraints")
class SQLiteConnectionSchemaTest {

    private Path dbFile;

    @BeforeEach
    void createTemporaryDatabase() throws IOException {
        dbFile = Files.createTempFile("smartlibrary-test-", ".db");
        Files.delete(dbFile); // SQLite should create the file itself
        DbPaths.useForTests(dbFile);
        SQLiteConnection.initializeDatabase();
    }

    @AfterEach
    void removeTemporaryDatabase() throws IOException {
        DbPaths.useForTests(null);
        if (dbFile != null) {
            Files.deleteIfExists(dbFile);
            Files.deleteIfExists(Path.of(dbFile + "-journal"));
        }
    }

    @Test
    @DisplayName("initializing twice is harmless and keeps the rows")
    void initializeIsIdempotent() throws SQLException {
        try (Connection conn = SQLiteConnection.connect();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("INSERT INTO books (title, author) VALUES ('Kept', 'Someone')");
        }

        SQLiteConnection.initializeDatabase(); // second call, must not wipe anything

        try (Connection conn = SQLiteConnection.connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM books")) {
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1), "the existing row must survive a second initialize");
        }
    }

    @Test
    @DisplayName("the database file is created on first use")
    void fileIsCreated() {
        assertTrue(Files.exists(dbFile), "SQLite must create the file itself");
    }

    @Test
    @DisplayName("the members table has every column the registration form writes")
    void membersColumns() throws SQLException {
        assertEquals("NOT NULL", nullabilityOf("members", "student_id"));
        assertEquals("NOT NULL", nullabilityOf("members", "name"));
        assertEquals("NOT NULL", nullabilityOf("members", "email"));
        assertEquals("", nullabilityOf("members", "department"), "department is optional");
        assertEquals("", nullabilityOf("members", "phone"), "phone is optional");
        assertEquals("", nullabilityOf("members", "gender"), "gender is optional");
    }

    @Test
    @DisplayName("the books table has every column the book form writes")
    void booksColumns() throws SQLException {
        assertEquals("NOT NULL", nullabilityOf("books", "title"));
        assertEquals("NOT NULL", nullabilityOf("books", "author"));
        assertEquals("", nullabilityOf("books", "isbn"), "isbn is optional");
        assertEquals("", nullabilityOf("books", "cover_path"), "cover is optional");
        assertEquals("NOT NULL", nullabilityOf("books", "status"));
    }

    @Test
    @DisplayName("a new book defaults to AVAILABLE")
    void bookDefaultsToAvailable() throws SQLException {
        try (Connection conn = SQLiteConnection.connect();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("INSERT INTO books (title, author) VALUES ('New', 'Author')");
            try (ResultSet rs = stmt.executeQuery("SELECT status FROM books")) {
                assertTrue(rs.next());
                assertEquals("AVAILABLE", rs.getString("status"));
            }
        }
    }

    @Test
    @DisplayName("a new borrow record defaults to BORROWED")
    void borrowRecordDefaultsToBorrowed() throws SQLException {
        seedMember("2025001");
        seedBook(); // book_id 1 must exist, or the foreign key rejects the row

        try (Connection conn = SQLiteConnection.connect();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("INSERT INTO borrow_records (student_id, book_id, issue_date) "
                    + "VALUES ('2025001', 1, '2026-03-01')");
            try (ResultSet rs = stmt.executeQuery("SELECT status, return_date FROM borrow_records")) {
                assertTrue(rs.next());
                assertEquals("BORROWED", rs.getString("status"));
                assertEquals(null, rs.getString("return_date"), "an open loan has no return date");
            }
        }
    }

    @Test
    @DisplayName("a duplicate student_id is rejected")
    void studentIdIsUnique() throws SQLException {
        seedMember("2025001");

        assertThrows(SQLException.class, () -> {
            try (Connection conn = SQLiteConnection.connect();
                 Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("INSERT INTO members (student_id, name, email) "
                        + "VALUES ('2025001', 'Copy', 'copy@example.com')");
            }
        }, "the UNIQUE constraint on student_id must reject the second row");
    }

    @Test
    @DisplayName("a duplicate email is rejected, even under a different student id")
    void emailIsUnique() throws SQLException {
        seedMember("2025001");

        assertThrows(SQLException.class, () -> {
            try (Connection conn = SQLiteConnection.connect();
                 Statement stmt = conn.createStatement()) {
                // A different student_id, the same email as the seeded member:
                // only the UNIQUE constraint on email can stop this.
                stmt.executeUpdate("INSERT INTO members (student_id, name, email) "
                        + "VALUES ('2025009', 'Other', '2025001@example.com')");
            }
        });
    }

    @Test
    @DisplayName("a borrow record for a student who does not exist is rejected")
    void foreignKeyOnStudentId() throws SQLException {
        seedBook();

        assertThrows(SQLException.class, () -> {
            try (Connection conn = SQLiteConnection.connect();
                 Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("INSERT INTO borrow_records (student_id, book_id, issue_date) "
                        + "VALUES ('ghost', 1, '2026-03-01')");
            }
        }, "borrow_records.student_id must reference a real member");
    }

    @Test
    @DisplayName("a borrow record for a book that does not exist is rejected")
    void foreignKeyOnBookId() throws SQLException {
        seedMember("2025001");

        assertThrows(SQLException.class, () -> {
            try (Connection conn = SQLiteConnection.connect();
                 Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("INSERT INTO borrow_records (student_id, book_id, issue_date) "
                        + "VALUES ('2025001', 9999, '2026-03-01')");
            }
        }, "borrow_records.book_id must reference a real book");
    }

    @Test
    @DisplayName("a member with borrow history cannot be deleted")
    void memberWithHistoryIsProtected() throws SQLException {
        seedMember("2025001");
        seedBook();
        try (Connection conn = SQLiteConnection.connect();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("INSERT INTO borrow_records (student_id, book_id, issue_date) "
                    + "VALUES ('2025001', 1, '2026-03-01')");
        }

        assertThrows(SQLException.class, () -> {
            try (Connection conn = SQLiteConnection.connect();
                 Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("DELETE FROM members WHERE student_id = '2025001'");
            }
        }, "the foreign key must stop the delete");
    }

    @Test
    @DisplayName("a member with no history can be deleted")
    void memberWithoutHistoryIsDeletable() throws SQLException {
        seedMember("2025001");

        try (Connection conn = SQLiteConnection.connect();
             Statement stmt = conn.createStatement()) {
            assertEquals(1, stmt.executeUpdate("DELETE FROM members WHERE student_id = '2025001'"));
        }
    }

    @Test
    @DisplayName("the gender column is added to an older members table")
    void genderColumnIsMigrated() throws SQLException, IOException {
        // A members table from before the gender column existed.
        Path legacy = Files.createTempFile("smartlibrary-legacy-", ".db");
        Files.delete(legacy);
        DbPaths.useForTests(legacy);
        try (Connection conn = SQLiteConnection.connect();
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE members (id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "student_id TEXT UNIQUE NOT NULL, name TEXT NOT NULL, "
                    + "email TEXT UNIQUE NOT NULL, department TEXT, phone TEXT)");
        }

        SQLiteConnection.initializeDatabase();

        DbPaths.useForTests(null);
        try (Connection conn = SQLiteConnection.connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT gender FROM members LIMIT 0")) {
            assertNotNull(rs);
            assertEquals("", nullabilityOf("members", "gender"),
                    "the migration must add a nullable gender column");
        } finally {
            Files.deleteIfExists(legacy);
        }
    }

    @Test
    @DisplayName("foreign key enforcement is actually switched on for every connection")
    void foreignKeysAreEnforced() throws SQLException {
        // A second connection, not the one initializeDatabase() used: the
        // PRAGMA is per-connection, so this proves connect() sets it every time.
        try (Connection conn = SQLiteConnection.connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA foreign_keys")) {
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1), "PRAGMA foreign_keys must be ON");
        }
    }

    @Test
    @DisplayName("both join columns are indexed, which the two JOIN queries rely on")
    void joinColumnsAreIndexed() throws SQLException, IOException {
        assertTrue(hasIndex("borrow_records", "idx_borrow_records_student_id"));
        assertTrue(hasIndex("borrow_records", "idx_borrow_records_book_id"));
    }

    @Test
    @DisplayName("the busy timeout is set, so a concurrent write waits instead of failing")
    void busyTimeoutIsSet() throws SQLException {
        try (Connection conn = SQLiteConnection.connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA busy_timeout")) {
            assertTrue(rs.next());
            assertEquals(5000, rs.getInt(1));
        }
    }

    @Test
    @DisplayName("borrowing history survives across connections")
    void historyIsPermanent() throws SQLException {
        seedMember("2025001");
        seedBook();
        try (Connection conn = SQLiteConnection.connect();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("INSERT INTO borrow_records (student_id, book_id, issue_date, status, "
                    + "return_date) VALUES ('2025001', 1, '2026-01-01', 'RETURNED', '2026-01-15')");
        }

        // A brand new connection, as a restart would use.
        try (Connection conn = SQLiteConnection.connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT return_date FROM borrow_records")) {
            assertTrue(rs.next());
            assertEquals("2026-01-15", rs.getString("return_date"));
        }
    }

    // ---- helpers ------------------------------------------------------

    private void seedMember(String studentId) throws SQLException {
        try (Connection conn = SQLiteConnection.connect();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("INSERT INTO members (student_id, name, email, gender) "
                    + "VALUES ('" + studentId + "', 'Test Student', '" + studentId + "@example.com', 'Male')");
        }
    }

    private void seedBook() throws SQLException {
        try (Connection conn = SQLiteConnection.connect();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("INSERT INTO books (title, author) VALUES ('Test Book', 'Test Author')");
        }
    }

    /** "NOT NULL" for a mandatory column, "" for a nullable one. */
    private String nullabilityOf(String table, String column) throws SQLException {
        try (Connection conn = SQLiteConnection.connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (column.equals(rs.getString("name"))) {
                    return rs.getInt("notnull") == 1 ? "NOT NULL" : "";
                }
            }
        }
        return "missing";
    }

    private boolean hasIndex(String table, String indexName) throws SQLException, IOException {
        try (Connection conn = SQLiteConnection.connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA index_list(" + table + ")")) {
            while (rs.next()) {
                if (indexName.equals(rs.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }

    @Test
    @DisplayName("an index that is absent is reported as absent, not as an error")
    void missingIndexIsFalse() throws SQLException, IOException {
        assertFalse(hasIndex("books", "no_such_index"));
    }
}
