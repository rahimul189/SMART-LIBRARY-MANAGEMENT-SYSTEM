package com.smartlibrary.data;

import com.smartlibrary.database.SQLiteConnection;
import com.smartlibrary.model.Book;
import com.smartlibrary.model.Member;
import com.smartlibrary.util.DbPaths;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("The repository hierarchy")
class RepositoryTest {

    private Path dbFile;

    @BeforeEach
    void createTemporaryDatabase() throws IOException, SQLException {
        dbFile = Files.createTempFile("smartlibrary-repo-", ".db");
        Files.delete(dbFile);
        DbPaths.useForTests(dbFile);
        com.smartlibrary.database.SQLiteConnection.initializeDatabase();
    }

    @AfterEach
    void removeTemporaryDatabase() throws IOException {
        DbPaths.useForTests(null);
        if (dbFile != null) {
            Files.deleteIfExists(dbFile);
        }
    }

    @Test
    @DisplayName("an empty table reads as an empty list, not null")
    void emptyTableIsEmptyList() throws SQLException {
        assertTrue(new BookRepository().findAll().isEmpty());
        assertTrue(new MemberRepository().findAll().isEmpty());
    }

    @Test
    @DisplayName("a book row is mapped onto every one of its fields")
    void bookRowIsMapped() throws SQLException {
        insertBook("Clean Code", "Robert C. Martin", "Programming", "ISBN-1");

        List<Book> books = new BookRepository().findAll();

        assertEquals(1, books.size());
        Book book = books.get(0);
        assertEquals("Clean Code", book.getTitle());
        assertEquals("Robert C. Martin", book.getAuthor());
        assertEquals("Programming", book.getCategory());
        assertEquals("ISBN-1", book.getIsbn());
        assertEquals("AVAILABLE", book.getStatus());
    }

    @Test
    @DisplayName("books come back sorted by title")
    void booksAreSortedByTitle() throws SQLException {
        insertBook("Zebra", "A", null, "I3");
        insertBook("Apple", "B", null, "I1");
        insertBook("Mango", "C", null, "I2");

        List<String> titles = new BookRepository().findAll().stream()
                .map(Book::getTitle).toList();

        assertEquals(List.of("Apple", "Mango", "Zebra"), titles);
    }

    @Test
    @DisplayName("a member row is mapped onto every one of its fields")
    void memberRowIsMapped() throws SQLException {
        insertMember("2025001", "Rahim", "CSE", "Female");

        var members = new MemberRepository().findAll();

        assertEquals(1, members.size());
        var member = members.get(0);
        assertEquals("2025001", member.getStudentId());
        assertEquals("Rahim", member.getName());
        assertEquals("CSE", member.getDepartment());
        assertEquals("Female", member.getGender());
    }

    @Test
    @DisplayName("members come back sorted by name")
    void membersAreSortedByName() throws SQLException {
        insertMember("1", "Zoe", null, null);
        insertMember("2", "Adam", null, null);

        var names = new MemberRepository().findAll().stream()
                .map(com.smartlibrary.model.Member::getName).toList();

        assertEquals(List.of("Adam", "Zoe"), names);
    }

    @Test
    @DisplayName("a null optional column maps to null, not to the text \"null\"")
    void nullColumnsMapToNull() throws SQLException {
        insertBook("No Category", "Author", null, null);
        insertMember("2025099", "Nobody", null, null);

        assertEquals(null, new BookRepository().findAll().get(0).getCategory());
        assertEquals(null, new MemberRepository().findAll().get(0).getDepartment());
        assertEquals(null, new MemberRepository().findAll().get(0).getGender());
    }

    @Test
    @DisplayName("the available-books subclass filters on status without duplicating the mapping")
    void availableRepositoryFilters() throws SQLException {
        insertBook("On shelf", "A", null, "I1");
        int borrowedId = insertBook("Out", "B", null, "I2");
        setStatus(borrowedId, "BORROWED");

        List<Book> available = new AvailableBookRepository().findAll();

        assertEquals(1, available.size());
        assertEquals("On shelf", available.get(0).getTitle(),
                "only the AVAILABLE book should come back");
    }

    @Test
    @DisplayName("the subclass still maps the same columns as its parent")
    void subclassInheritsTheMapping() throws SQLException {
        insertBook("Mapped", "Author", "Category", "ISBN-9");
        setStatus(1, "BORROWED");
        // Force it back to available so the subclass sees it, then compare both
        // mappings on the same row.
        setStatus(1, "AVAILABLE");

        Book viaParent = new BookRepository().findAll().get(0);
        Book viaChild = new AvailableBookRepository().findAll().get(0);

        assertEquals(viaParent.getTitle(), viaChild.getTitle());
        assertEquals(viaParent.getAuthor(), viaChild.getAuthor());
        assertEquals(viaParent.getCategory(), viaChild.getCategory());
        assertEquals(viaParent.getIsbn(), viaChild.getIsbn());
    }

    @Test
    @DisplayName("both repositories are usable through the LibraryRepository interface")
    void polymorphicThroughTheInterface() throws SQLException {
        insertBook("A", "B", null, "I1");
        insertMember("2025001", "Rahim", null, null);

        LibraryRepository<Book> books = new BookRepository();
        LibraryRepository<com.smartlibrary.model.Member> members = new MemberRepository();

        assertEquals(1, books.findAll().size());
        assertEquals(1, members.findAll().size());
    }

    @Test
    @DisplayName("the interface type hides the concrete class entirely")
    void interfaceTypeIsEnough() throws SQLException {
        insertBook("A", "B", null, "I1");

        // The local variable never names a concrete repository type.
        LibraryRepository<Book> repository = new AvailableBookRepository();

        assertNotNull(repository.findAll());
        assertEquals(1, repository.findAll().size());
    }

    @Test
    @DisplayName("findAll reports a SQLException instead of swallowing a broken query")
    void failuresPropagate() throws SQLException {
        // Drop the table the repository reads: the query must fail loudly
        // rather than returning an empty list that looks like "no books".
        try (Connection conn = com.smartlibrary.database.SQLiteConnection.connect();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE books");
        }

        assertThrows(SQLException.class, () -> new BookRepository().findAll());
    }

    @Test
    @DisplayName("each call opens and closes its own connection, so rows are always fresh")
    void connectionsAreNotShared() throws SQLException {
        insertBook("First", "A", null, "I1");
        assertEquals(1, new BookRepository().findAll().size());

        insertBook("Second", "B", null, "I2");
        assertEquals(2, new BookRepository().findAll().size(),
                "a second call must see the row the first connection committed");
    }

    // ---- helpers ------------------------------------------------------

    private int insertBook(String title, String author, String category, String isbn)
            throws SQLException {
        try (Connection conn = com.smartlibrary.database.SQLiteConnection.connect();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO books (title, author, category, isbn) VALUES (?, ?, ?, ?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, title);
            ps.setString(2, author);
            ps.setString(3, category);
            ps.setString(4, isbn);
            ps.executeUpdate();
            try (var keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getInt(1) : 1;
            }
        }
    }

    private void insertMember(String studentId, String name, String department, String gender)
            throws SQLException {
        try (Connection conn = com.smartlibrary.database.SQLiteConnection.connect();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO members (student_id, name, email, department, gender) "
                             + "VALUES (?, ?, ?, ?, ?)")) {
            ps.setString(1, studentId);
            ps.setString(2, name);
            ps.setString(3, studentId + "@example.com");
            ps.setString(4, department);
            ps.setString(5, gender);
            ps.executeUpdate();
        }
    }

    private void setStatus(int bookId, String status) throws SQLException {
        try (Connection conn = com.smartlibrary.database.SQLiteConnection.connect();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE books SET status = ? WHERE id = ?")) {
            ps.setString(1, status);
            ps.setInt(2, bookId);
            ps.executeUpdate();
        }
    }
}
