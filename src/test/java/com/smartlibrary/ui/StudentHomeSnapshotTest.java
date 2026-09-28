package com.smartlibrary.ui;

import com.smartlibrary.model.LoanRules;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("StudentHome.build - the snapshot behind the student dashboard")
class StudentHomeSnapshotTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 3, 15);
    private static final String NAME = "Rahim Uddin";

    private static StudentHome.Entry borrowed(String title, String issueDate) {
        return new StudentHome.Entry(title, issueDate, "", "BORROWED");
    }

    private static StudentHome.Entry returned(String title, String issueDate, String returnDate) {
        return new StudentHome.Entry(title, issueDate, returnDate, "RETURNED");
    }

    @Test
    @DisplayName("a student with no loans gets a snapshot with empty lists, not nulls")
    void noLoans() {
        StudentHome.Snapshot snapshot = StudentHome.build(NAME, List.of(), TODAY);

        assertEquals(NAME, snapshot.name());
        assertEquals(0, snapshot.totalIssued());
        assertEquals(0, snapshot.returnedCount());
        assertEquals(0, snapshot.activeCount());
        assertTrue(snapshot.loans().isEmpty());
        assertTrue(snapshot.dueSoon().isEmpty());
        assertTrue(snapshot.recent().isEmpty());
    }

    @Test
    @DisplayName("the empty placeholder matches what an empty build produces")
    void emptyMatchesAnEmptyBuild() {
        StudentHome.Snapshot empty = StudentHome.Snapshot.empty();
        StudentHome.Snapshot built = StudentHome.build(null, List.of(), TODAY);

        assertEquals(empty.totalIssued(), built.totalIssued());
        assertEquals(empty.activeCount(), built.activeCount());
        assertEquals(empty.loans().size(), built.loans().size());
        assertEquals(empty.recent().size(), built.recent().size());
    }

    @Test
    @DisplayName("open loans count as active, returned ones do not")
    void countsSplitByStatus() {
        List<StudentHome.Entry> entries = List.of(
                borrowed("Clean Code", "2026-03-01"),
                borrowed("Refactoring", "2026-02-01"),
                returned("Effective Java", "2025-11-01", "2025-11-20"));

        StudentHome.Snapshot snapshot = StudentHome.build(NAME, entries, TODAY);

        assertEquals(3, snapshot.totalIssued(), "every record counts towards the total");
        assertEquals(1, snapshot.returnedCount());
        assertEquals(2, snapshot.activeCount());
        assertEquals(2, snapshot.loans().size());
    }

    @Test
    @DisplayName("the most urgent loan comes first")
    void loansAreSortedByUrgency() {
        List<StudentHome.Entry> entries = List.of(
                borrowed("Plenty of time", "2026-03-14"),
                borrowed("Overdue already", "2026-01-01"),
                borrowed("Due soon", "2026-03-11"));

        List<StudentHome.Loan> loans = StudentHome.build(NAME, entries, TODAY).loans();

        assertEquals("Overdue already", loans.get(0).title(), "overdue first");
        assertEquals("Due soon", loans.get(1).title());
        assertEquals("Plenty of time", loans.get(2).title());
    }

    @Test
    @DisplayName("the due-soon list holds only loans inside the coming week")
    void dueSoonWindowIsRespected() {
        // TODAY is 2026-03-15 and the loan is 60 days, so an issue date of
        // 2026-01-14 is exactly on the deadline and each day later is one
        // further inside the window:
        List<StudentHome.Entry> entries = List.of(
                borrowed("Way overdue", "2025-12-25"),    // 80 days kept -> -20 left
                borrowed("Due today", "2026-01-14"),     // 60 kept -> 0 left
                borrowed("Due in three", "2026-01-17"),  // 57 kept -> 3 left
                borrowed("Comfortable", "2026-03-01"));  // 14 kept -> 46 left

        List<StudentHome.Loan> dueSoon = StudentHome.build(NAME, entries, TODAY).dueSoon();

        assertEquals(2, dueSoon.size(), "only the two inside the window");
        assertTrue(dueSoon.stream().allMatch(l -> LoanRules.isDueSoon(l.daysLeft())));
        assertFalse(dueSoon.stream().anyMatch(l -> l.title().equals("Way overdue")),
                "an overdue loan is not 'due soon'");
    }

    @Test
    @DisplayName("a returned loan produces both an issue and a return event")
    void returnAddsTwoEvents() {
        List<StudentHome.Entry> entries = List.of(
                returned("Effective Java", "2025-11-01", "2025-11-20"));

        List<StudentHome.Activity> recent = StudentHome.build(NAME, entries, TODAY).recent();

        assertEquals(2, recent.size());
        assertTrue(recent.stream().anyMatch(a -> a.returned() && a.date().equals("2025-11-20")));
        assertTrue(recent.stream().anyMatch(a -> !a.returned() && a.date().equals("2025-11-01")));
    }

    @Test
    @DisplayName("the activity feed is newest first and capped at five")
    void recentIsNewestFirstAndCapped() {
        List<StudentHome.Entry> entries = List.of(
                returned("Book A", "2026-01-01", "2026-01-10"),
                returned("Book B", "2026-01-05", "2026-01-15"),
                returned("Book C", "2026-01-10", "2026-01-20"),
                returned("Book D", "2026-01-15", "2026-01-25"),
                returned("Book E", "2026-01-20", "2026-02-25"),
                returned("Book F", "2026-01-25", "2026-03-05"));

        List<StudentHome.Activity> recent = StudentHome.build(NAME, entries, TODAY).recent();

        assertEquals(5, recent.size(), "only the last five events are kept");
        assertEquals("2026-03-05", recent.get(0).date(), "newest first");
        for (int i = 1; i < recent.size(); i++) {
            assertTrue(recent.get(i - 1).date().compareTo(recent.get(i).date()) >= 0,
                    "the feed must stay in descending date order");
        }
    }

    @Test
    @DisplayName("a row with no parsable issue date is skipped, not fatal")
    void unparsableDateIsSkipped() {
        List<StudentHome.Entry> entries = List.of(
                borrowed("Broken", "not-a-date"),
                borrowed("Fine", "2026-03-01"));

        StudentHome.Snapshot snapshot = StudentHome.build(NAME, entries, TODAY);

        assertEquals(2, snapshot.totalIssued(), "it still counts towards the total");
        assertEquals(1, snapshot.loans().size(), "only the parsable one becomes a loan");
        assertEquals("Fine", snapshot.loans().get(0).title());
    }

    @Test
    @DisplayName("a null entry or a null issue date is skipped")
    void nullsAreSkipped() {
        List<StudentHome.Entry> entries = new java.util.ArrayList<>();
        entries.add(null);
        entries.add(new StudentHome.Entry("No date", null, "", "BORROWED"));
        entries.add(borrowed("Fine", "2026-03-01"));

        StudentHome.Snapshot snapshot = StudentHome.build(NAME, entries, TODAY);

        assertEquals(1, snapshot.loans().size());
        assertEquals("Fine", snapshot.loans().get(0).title());
    }

    @Test
    @DisplayName("a missing name is kept as null so the page can show a neutral welcome")
    void nullNameSurvives() {
        assertNull(StudentHome.build(null, List.of(), TODAY).name());
    }

    @Test
    @DisplayName("the deadlines in the snapshot match the shared rule")
    void deadlinesUseLoanRules() {
        StudentHome.Snapshot snapshot = StudentHome.build(NAME,
                List.of(borrowed("Clean Code", "2026-03-05")), TODAY);

        StudentHome.Loan loan = snapshot.loans().get(0);

        assertEquals(LoanRules.MAX_LOAN_DAYS, loan.daysLeft() + loan.daysKept());
        assertEquals(LocalDate.of(2026, 3, 5).plusDays(LoanRules.MAX_LOAN_DAYS).toString(),
                loan.deadline());
        assertEquals(LoanRules.statusText(loan.daysLeft()),
                StudentHome.statusText(loan.daysLeft()));
    }

    @Test
    @DisplayName("the status wording is the shared rule's, not a second copy")
    void statusTextComesFromLoanRules() {
        for (int daysLeft = -5; daysLeft <= 70; daysLeft++) {
            assertEquals(LoanRules.statusText(daysLeft), StudentHome.statusText(daysLeft),
                    "for " + daysLeft + " days left");
        }
    }
}
