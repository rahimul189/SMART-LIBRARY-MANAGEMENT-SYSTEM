package com.smartlibrary.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The domain objects use JavaFX properties so they can be bound straight to a
 * TableView. These tests cover the accessor contract the repositories and the
 * {@code PropertyValueFactory} cells depend on: get/set must agree, and the
 * {@code xxxProperty()} accessor must expose the same underlying value.
 */
@DisplayName("Domain objects - property accessors")
class DomainObjectsTest {

    @Test
    @DisplayName("a Book round-trips every field through its getters")
    void bookAccessors() {
        Book book = new Book(7, "Clean Code", "Robert C. Martin", "Programming",
                "ISBN-0007", "A craftsman guide.", "covers/7.png", "AVAILABLE");

        assertEquals(7, book.getId());
        assertEquals("Clean Code", book.getTitle());
        assertEquals("Robert C. Martin", book.getAuthor());
        assertEquals("Programming", book.getCategory());
        assertEquals("ISBN-0007", book.getIsbn());
        assertEquals("A craftsman guide.", book.getDescription());
        assertEquals("covers/7.png", book.getCoverPath());
        assertEquals("AVAILABLE", book.getStatus());
    }

    @Test
    @DisplayName("a Book's setters are seen by the property accessors")
    void bookSettersUpdateProperties() {
        Book book = new Book(1, "Old", "Someone", "Cat", "I", "D", null, "AVAILABLE");

        book.setTitle("New");
        book.setAuthor("Someone Else");
        book.setStatus("BORROWED");

        assertEquals("New", book.titleProperty().get());
        assertEquals("Someone Else", book.authorProperty().get());
        assertEquals("BORROWED", book.statusProperty().get());
        assertEquals("New", book.getTitle());
    }

    @Test
    @DisplayName("a Book prints as its title, which is what the combo box shows")
    void bookToStringIsTheTitle() {
        assertEquals("Clean Code", new Book(1, "Clean Code", "A", "C", "I", "D", null, "AVAILABLE")
                .toString());
    }

    @Test
    @DisplayName("a Member round-trips every field, gender included")
    void memberAccessors() {
        Member member = new Member(3, "2025003", "Rahim Uddin", "rahim@example.com",
                "CSE", "01700000000", "Male");

        assertEquals(3, member.getId());
        assertEquals("2025003", member.getStudentId());
        assertEquals("Rahim Uddin", member.getName());
        assertEquals("rahim@example.com", member.getEmail());
        assertEquals("CSE", member.getDepartment());
        assertEquals("01700000000", member.getPhone());
        assertEquals("Male", member.getGender());
    }

    @Test
    @DisplayName("a Member prints as \"name (studentId)\"")
    void memberToString() {
        Member member = new Member(1, "2025001", "Rahim", "r@e.com", "CSE", null, null);

        assertEquals("Rahim (2025001)", member.toString());
    }

    @Test
    @DisplayName("a null gender is carried through, because the column is nullable")
    void memberGenderMayBeNull() {
        Member member = new Member(1, "2025001", "N", "e", "d", "p", null);

        assertNull(member.getGender());
        assertNull(Gender.fromLabel(member.getGender()),
                "an unset gender must resolve to null, not to a default");
    }

    @Test
    @DisplayName("a BorrowRecord round-trips every field")
    void borrowRecordAccessors() {
        BorrowRecord record = new BorrowRecord(9, "2025001", 4,
                "2026-03-01", "2026-03-20", "RETURNED");

        assertEquals(9, record.getId());
        assertEquals("2025001", record.getStudentId());
        assertEquals(4, record.getBookId());
        assertEquals("2026-03-01", record.getIssueDate());
        assertEquals("2026-03-20", record.getReturnDate());
        assertEquals("RETURNED", record.getStatus());
    }

    @Test
    @DisplayName("an open loan's null return date becomes an empty string")
    void borrowRecordNormalisesNullReturnDate() {
        BorrowRecord open = new BorrowRecord(1, "2025001", 2, "2026-03-01", null, "BORROWED");

        assertEquals("", open.getReturnDate(),
                "a null would render as \"null\" in the table cell");
        assertEquals("", open.returnDateProperty().get());
    }

    @Test
    @DisplayName("every property accessor is non-null, which TableView binding requires")
    void propertyAccessorsArePresent() {
        Book book = new Book(1, "T", "A", "C", "I", "D", "p", "AVAILABLE");
        assertNotNull(book.titleProperty());
        assertNotNull(book.authorProperty());
        assertNotNull(book.categoryProperty());
        assertNotNull(book.isbnProperty());
        assertNotNull(book.descriptionProperty());
        assertNotNull(book.coverPathProperty());
        assertNotNull(book.statusProperty());

        Member member = new Member(1, "s", "n", "e", "d", "p", "Male");
        assertNotNull(member.studentIdProperty());
        assertNotNull(member.nameProperty());
        assertNotNull(member.emailProperty());
        assertNotNull(member.departmentProperty());
        assertNotNull(member.phoneProperty());
        assertNotNull(member.genderProperty());

        BorrowRecord record = new BorrowRecord(1, "s", 1, "d", "r", "BORROWED");
        assertNotNull(record.studentIdProperty());
        assertNotNull(record.issueDateProperty());
        assertNotNull(record.returnDateProperty());
        assertNotNull(record.statusProperty());
    }

    @Test
    @DisplayName("a property is writable, so a cell edit reaches the model")
    void propertiesAreWritable() {
        Book book = new Book(1, "T", "A", "C", "I", "D", null, "AVAILABLE");

        book.titleProperty().set("Edited through the property");

        assertEquals("Edited through the property", book.getTitle());
    }

    @Test
    @DisplayName("changing the property notifies its listeners")
    void propertyChangesAreObservable() {
        Book book = new Book(1, "Before", "A", "C", "I", "D", null, "AVAILABLE");
        StringBuilder seen = new StringBuilder();

        book.titleProperty().addListener((obs, oldValue, newValue) ->
                seen.append(oldValue).append("->").append(newValue));

        book.setTitle("After");

        assertEquals("Before->After", seen.toString());
        assertTrue(seen.length() > 0, "the listener must have been called");
    }

    @Test
    @DisplayName("OverviewStats carries the dashboard's figures as given")
    void overviewStatsIsAValueHolder() {
        OverviewStats stats = new OverviewStats(10, 7, 4, 3, 2,
                List.of("CSE", "EEE"), List.of("Programming"));

        assertEquals(10, stats.totalBooks());
        assertEquals(7, stats.availableBooks());
        assertEquals(4, stats.totalMembers());
        assertEquals(3, stats.issuedBooks());
        assertEquals(2, stats.membersBorrowing());
        assertEquals(2, stats.departments().size());
        assertEquals(1, stats.categories().size());
    }

    @Test
    @DisplayName("two identical OverviewStats are equal, which a record gives for free")
    void overviewStatsEquality() {
        OverviewStats first = new OverviewStats(1, 1, 1, 1, 1, List.of("A"), List.of("B"));
        OverviewStats second = new OverviewStats(1, 1, 1, 1, 1, List.of("A"), List.of("B"));

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }
}
