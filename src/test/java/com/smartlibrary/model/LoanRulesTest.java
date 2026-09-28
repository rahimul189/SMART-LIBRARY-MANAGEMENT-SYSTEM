package com.smartlibrary.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("LoanRules - the 60-day borrowing rule")
class LoanRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 3, 15);

    @Test
    @DisplayName("a book issued today has all 60 days left")
    void issuedToday() {
        LoanRules.Loan loan = LoanRules.measure("Clean Code", TODAY, TODAY);

        assertEquals(0, loan.daysKept());
        assertEquals(60, loan.daysLeft());
        assertEquals(TODAY.plusDays(60).toString(), loan.deadline());
        assertFalse(loan.isOverdue());
    }

    @Test
    @DisplayName("days kept and days left always add up to the loan period")
    void daysAddUpToThePeriod() {
        for (int kept = 0; kept <= 80; kept++) {
            LoanRules.Loan loan = LoanRules.measure("Any", TODAY.minusDays(kept), TODAY);

            assertEquals(kept, loan.daysKept(), "days kept for " + kept);
            assertEquals(LoanRules.MAX_LOAN_DAYS - kept, loan.daysLeft(),
                    "days left when " + kept + " days kept");
        }
    }

    @Test
    @DisplayName("a book issued in the future counts as zero days kept, never negative")
    void futureIssueDateIsClampedToZero() {
        LoanRules.Loan loan = LoanRules.measure("Time Traveller", TODAY.plusDays(10), TODAY);

        assertEquals(0, loan.daysKept());
        assertEquals(60, loan.daysLeft());
    }

    @Test
    @DisplayName("a loan becomes overdue one day after the deadline")
    void overdueStartsTheDayAfter() {
        LoanRules.Loan onDeadline = LoanRules.measure("Late", TODAY.minusDays(60), TODAY);
        assertEquals(0, onDeadline.daysLeft());
        assertFalse(onDeadline.isOverdue(), "the deadline day itself is not overdue");

        LoanRules.Loan nextDay = LoanRules.measure("Late", TODAY.minusDays(61), TODAY);
        assertEquals(-1, nextDay.daysLeft());
        assertTrue(nextDay.isOverdue());
    }

    @Test
    @DisplayName("a missing issue date measures from today instead of failing")
    void missingIssueDateIsTolerated() {
        LoanRules.Loan loan = LoanRules.measure("Mystery", (LocalDate) null, TODAY);

        assertEquals(0, loan.daysKept());
        assertEquals(TODAY.toString(), loan.issueDate());
    }

    @Test
    @DisplayName("the text form of an unparsable date measures to null")
    void unparsableIssueDateMeasuresToNull() {
        assertNull(LoanRules.measure("Broken", "not-a-date", TODAY));
        assertNull(LoanRules.measure("Broken", "", TODAY));
        assertNull(LoanRules.measure("Broken", (String) null, TODAY));
    }

    @Test
    @DisplayName("a valid ISO date parses, surrounding spaces are ignored")
    void parseDate() {
        assertEquals(TODAY, LoanRules.parseDate("2026-03-15"));
        assertEquals(TODAY, LoanRules.parseDate("  2026-03-15  "));
        assertNull(LoanRules.parseDate("15/03/2026"));
        assertNull(LoanRules.parseDate(null));
    }

    @ParameterizedTest(name = "{0} days left reads as \"{1}\"")
    @CsvSource({
            "-5, Overdue 5 days",
            "-1, Overdue 1 day",
            "0,  Due today",
            "1,  Due in 1 day",
            "2,  Due in 2 days",
            "7,  Due in 7 days",
            "8,  8 days left",
            "46, 46 days left"
    })
    @DisplayName("the status wording covers the whole range")
    void statusText(int daysLeft, String expected) {
        assertEquals(expected, LoanRules.statusText(daysLeft));
    }

    @Test
    @DisplayName("the status wording for the boundary days")
    void statusTextBoundaries() {
        assertEquals("Due in 7 days", LoanRules.statusText(7), "the last 'due soon' day");
        assertEquals("8 days left", LoanRules.statusText(8), "one past the 'due soon' window");
        assertEquals("Overdue 1 day", LoanRules.statusText(-1));
        assertEquals("Due today", LoanRules.statusText(0));
    }

    @Test
    @DisplayName("only non-overdue loans inside the window count as due soon")
    void dueSoonWindow() {
        assertTrue(LoanRules.isDueSoon(0));
        assertTrue(LoanRules.isDueSoon(LoanRules.SOON_DAYS));
        assertFalse(LoanRules.isDueSoon(LoanRules.SOON_DAYS + 1));
        assertFalse(LoanRules.isDueSoon(-1), "an overdue loan has its own message");
    }

    @Test
    @DisplayName("the amber band runs from 40 days kept up to the full period")
    void amberBand() {
        assertFalse(LoanRules.isAmber(0));
        assertFalse(LoanRules.isAmber(LoanRules.AMBER_FROM_DAYS - 1));
        assertTrue(LoanRules.isAmber(LoanRules.AMBER_FROM_DAYS));
        assertTrue(LoanRules.isAmber(LoanRules.MAX_LOAN_DAYS - 1));
        assertFalse(LoanRules.isAmber(LoanRules.MAX_LOAN_DAYS), "the full period is the red band");
    }

    @ParameterizedTest(name = "{0} days left reads as \"{1}\" beside a title")
    @CsvSource({
            "60, 60 days left",
            "20, 20 days left",
            "1,  1 day left",
            "0,  0 days left",
            "-1, 1 day overdue",
            "-4, 4 days overdue"
    })
    @DisplayName("the short form keeps its singular/plural wording")
    void daysLeftText(int daysLeft, String expected) {
        assertEquals(expected, LoanRules.daysLeftText(daysLeft));
    }

    @Test
    @DisplayName("the record carries the wording it was measured with")
    void loanDelegatesItsWording() {
        LoanRules.Loan loan = LoanRules.measure("Book", TODAY.minusDays(3), TODAY);

        assertEquals(LoanRules.statusText(loan.daysLeft()), loan.statusText());
        assertEquals(LoanRules.isDueSoon(loan.daysLeft()), loan.isDueSoon());
        assertFalse(loan.isOverdue());
    }
}
