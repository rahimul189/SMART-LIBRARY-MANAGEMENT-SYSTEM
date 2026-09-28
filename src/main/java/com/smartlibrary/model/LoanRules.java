package com.smartlibrary.model;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

/**
 * The borrowing rule, in one place.
 *
 * A book may be kept for {@link #MAX_LOAN_DAYS} days from its issue date. The
 * student dashboard ({@code StudentHome}) and the admin return screen
 * ({@code ReturnBookController}) both measure loans against this class, so the
 * two screens can never show a different deadline or different wording for the
 * same loan.
 *
 * Pure computation with no UI and no database, which is what makes it easy to
 * unit test.
 */
public final class LoanRules {

    /** A book may be kept for at most this many days from its issue date. */
    public static final int MAX_LOAN_DAYS = 60;

    /** A deadline within this many days is highlighted as "due soon". */
    public static final int SOON_DAYS = 7;

    /** From this many days kept on, the progress bar turns amber (red at 60). */
    public static final int AMBER_FROM_DAYS = 40;

    private LoanRules() {
        // static helper
    }

    /**
     * One loan measured against the borrowing rule.
     *
     * @param title    the book
     * @param issue    the day it was issued (ISO text)
     * @param deadline issue date + {@link #MAX_LOAN_DAYS}
     * @param daysKept days from the issue date to today, never negative
     * @param daysLeft {@link #MAX_LOAN_DAYS} minus daysKept - negative once overdue
     */
    public record Loan(String title, String issueDate, String deadline,
                       int daysKept, int daysLeft) {

        /** True when the book is past its deadline. */
        public boolean isOverdue() {
            return daysLeft < 0;
        }

        /** True when the deadline falls within {@link #SOON_DAYS}. */
        public boolean isDueSoon() {
            return LoanRules.isDueSoon(daysLeft);
        }

        /** The status wording for this loan. */
        public String statusText() {
            return LoanRules.statusText(daysLeft);
        }
    }

    /**
     * Measures a loan that was issued on the given day.
     *
     * @param title the book
     * @param issue the issue date; a null date is treated as issued today, which
     *              yields zero days kept instead of an error
     * @param today the day the loan is being measured on
     */
    public static Loan measure(String title, LocalDate issue, LocalDate today) {
        LocalDate from = issue == null ? today : issue;
        int kept = (int) Math.max(0, ChronoUnit.DAYS.between(from, today));
        return new Loan(title,
                from.toString(),
                from.plusDays(MAX_LOAN_DAYS).toString(),
                kept,
                MAX_LOAN_DAYS - kept);
    }

    /**
     * Measures a loan from the ISO date text as stored in the database.
     *
     * @return the measured loan, or null when the issue date cannot be parsed
     */
    public static Loan measure(String title, String issueDate, LocalDate today) {
        LocalDate issue = parseDate(issueDate);
        return issue == null ? null : measure(title, issue, today);
    }

    /**
     * Parses an ISO {@code yyyy-MM-dd} date as stored in the database.
     *
     * @return the date, or null when the text is null, blank or not an ISO date
     */
    public static LocalDate parseDate(String iso) {
        if (iso == null || iso.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(iso.trim());
        } catch (DateTimeParseException e) {
            return null; // unexpected format: the row simply gets no progress bar
        }
    }

    /**
     * True when a deadline falls inside the "due soon" window, i.e. today up to
     * and including {@link #SOON_DAYS} days from now. An overdue loan is not
     * "due soon" - it gets its own message.
     */
    public static boolean isDueSoon(int daysLeft) {
        return daysLeft >= 0 && daysLeft <= SOON_DAYS;
    }

    /** "Overdue 5 days" / "Due today" / "Due in 3 days" / "46 days left". */
    public static String statusText(int daysLeft) {
        if (daysLeft < 0) {
            int overdue = -daysLeft;
            return "Overdue " + overdue + (overdue == 1 ? " day" : " days");
        }
        if (daysLeft == 0) {
            return "Due today";
        }
        if (daysLeft == 1) {
            return "Due in 1 day";
        }
        if (isDueSoon(daysLeft)) {
            return "Due in " + daysLeft + " days";
        }
        return daysLeft + " days left";
    }

    /** "46 days left" / "3 days overdue" - the phrasing used beside a title. */
    public static String daysLeftText(int daysLeft) {
        if (daysLeft < 0) {
            return (-daysLeft) + (daysLeft == -1 ? " day overdue" : " days overdue");
        }
        return daysLeft + (daysLeft == 1 ? " day left" : " days left");
    }

    /**
     * True when a kept-days count is far enough along for the amber progress
     * bar. Anything at or past the full loan period is handled by the caller as
     * the red "late" style.
     */
    public static boolean isAmber(int daysKept) {
        return daysKept >= AMBER_FROM_DAYS && daysKept < MAX_LOAN_DAYS;
    }
}
