package com.smartlibrary.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import com.smartlibrary.model.LoanRules;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/**
 * Student landing screen - the page that replaces the profile page as the
 * centre of the student section.
 *
 * It answers, at a glance:
 * <ul>
 *   <li>"Welcome, &lt;name&gt;" - nothing else about the profile lives here,</li>
 *   <li>the borrowing rule (keep a book for at most 60 days = 2 months),</li>
 *   <li>how many books were issued in total / are issued right now,</li>
 *   <li>every borrowed book with a days-kept progress bar,</li>
 *   <li>the deadlines falling inside the next week,</li>
 *   <li>the last five events (issue or return),</li>
 *   <li>the personal record (total / returned / issued now).</li>
 * </ul>
 *
 * The whole page is built from one immutable {@link Snapshot}, so a background
 * query fills it in a single JavaFX step (Lab 2: SQL never runs on the FX
 * thread). Styling reuses the application's own classes - content-pane,
 * page-title, stat-card, login-card, section-title, stat-hint - plus the small
 * dash-* row styles added to style.css for the progress bar and the status pill.
 */
public final class StudentHome {

    /**
     * How many activities the "Recent activity" card shows.
     *
     * The 60-day period, the "due soon" window and the status wording live in
     * {@link LoanRules}, shared with the admin return screen.
     */
    private static final int RECENT_LIMIT = 5;

    // ------------------------------------------------------------------
    // Data
    // ------------------------------------------------------------------

    /** One borrow record as the query returns it (dates as ISO strings). */
    public record Entry(String title, String issueDate, String returnDate, String status) {
    }

    /** A borrowed book plus its position against the 60-day rule. */
    public record Loan(String title, String issueDate, String deadline,
                       int daysKept, int daysLeft) {

        static Loan of(LoanRules.Loan measured) {
            return new Loan(measured.title(), measured.issueDate(), measured.deadline(),
                    measured.daysKept(), measured.daysLeft());
        }
    }

    /** One line of the activity feed: an issue or a return of a book. */
    public record Activity(boolean returned, String title, String date) {
    }

    /** Everything the page needs, computed once in the background. */
    public record Snapshot(String name, int totalIssued, int returnedCount, int activeCount,
                           List<Loan> loans, List<Loan> dueSoon, List<Activity> recent) {

        /** The shape shown while the query is still running. */
        public static Snapshot empty() {
            return new Snapshot(null, 0, 0, 0, List.of(), List.of(), List.of());
        }
    }

    /**
     * Turns the raw rows into the page's snapshot: counters, the loans sorted
     * with the most urgent first, the deadlines of the next week and the last
     * {@link #RECENT_LIMIT} events (issue date of a record, return date of a
     * returned one). Pure computation - safe to run off the JavaFX thread.
     *
     * @param name    the student's name (null when no member row exists yet)
     * @param entries every borrow record of this student
     * @param today   the day the page is rendered for
     */
    public static Snapshot build(String name, List<Entry> entries, LocalDate today) {
        List<Loan> loans = new ArrayList<>();
        List<Activity> events = new ArrayList<>();
        int returned = 0;

        for (Entry entry : entries) {
            if (entry == null || entry.issueDate() == null) {
                continue;
            }
            events.add(new Activity(false, entry.title(), entry.issueDate()));

            if ("RETURNED".equals(entry.status())) {
                returned++;
                if (entry.returnDate() != null) {
                    events.add(new Activity(true, entry.title(), entry.returnDate()));
                }
            }
            if ("BORROWED".equals(entry.status())) {
                LoanRules.Loan measured = LoanRules.measure(entry.title(), entry.issueDate(), today);
                if (measured != null) {
                    loans.add(Loan.of(measured));
                }
            }
        }

        loans.sort(Comparator.comparingInt(Loan::daysLeft));

        List<Loan> dueSoon = new ArrayList<>();
        for (Loan loan : loans) {
            if (LoanRules.isDueSoon(loan.daysLeft())) {
                dueSoon.add(loan);
            }
        }

        // ISO dates compare chronologically as text, so a plain sort by the
        // date string already puts the newest event first.
        events.sort(Comparator.comparing(Activity::date).reversed());
        List<Activity> recent = new ArrayList<>();
        for (int i = 0; i < events.size() && i < RECENT_LIMIT; i++) {
            recent.add(events.get(i));
        }

        return new Snapshot(name, entries.size(), returned, loans.size(), loans, dueSoon, recent);
    }

    /** "Overdue 5 days" / "Due today" / "Due in 3 days" / "46 days left". */
    public static String statusText(int daysLeft) {
        return LoanRules.statusText(daysLeft);
    }

    // ------------------------------------------------------------------
    // Page
    // ------------------------------------------------------------------

    private final ScrollPane root = new ScrollPane();
    private final Label welcome = new Label("Welcome");
    private final Label totalValue = new Label("0");
    private final Label activeValue = new Label("0");

    private final VBox loansBox = new VBox(12);
    private final VBox activityBox = new VBox(6);
    private final VBox dueBox = new VBox(12);
    private final VBox recordBox = new VBox(10);

    // Cards that disappear instead of showing an empty box: the dashboard
    // never carries an "empty state" note (removed by request), so a card
    // with nothing to say is simply not there.
    private final VBox loansCard;
    private final VBox activityCard;
    private final VBox dueCard;
    private final VBox recordCard;

    public StudentHome() {
        // Welcome line, rule, counters, then the grid of cards.
        VBox page = new VBox(16);
        page.setPadding(new Insets(30));
        page.setMinWidth(0);
        page.setMaxWidth(Double.MAX_VALUE);
        page.getStyleClass().add("content-pane");

        loansCard = card("Your books - how long you have kept them", loansBox);
        activityCard = card("Recent activity", activityBox);
        dueCard = card("Deadline in the next 7 days", dueBox);
        recordCard = card("Your record", recordBox);
        // the record card shares its row with the activity feed, so it is
        // usually taller than its three lines: let the body take the height
        // and the lines spread out over it instead of piling up at the top
        VBox.setVgrow(recordBox, Priority.ALWAYS);

        welcome.getStyleClass().add("page-title");
        page.getChildren().addAll(welcome, ruleCard(), kpiRow(), mainGrid());

        // Same shell as the profile page and Help > About: a ScrollPane with
        // fitToWidth, so a short window scrolls instead of cutting cards off.
        root.setContent(page);
        root.setFitToWidth(true);
        root.setMinWidth(0);
        root.getStyleClass().addAll("content-pane", "page-scroll");
    }

    /** The page to hand to the controller's showContent(). */
    public ScrollPane root() {
        return root;
    }

    /** Fills the whole page in one step - always on the JavaFX thread. */
    public void apply(Snapshot snapshot) {
        welcome.setText(snapshot.name() == null || snapshot.name().isBlank()
                ? "Welcome" : "Welcome, " + snapshot.name());

        totalValue.setText(String.valueOf(snapshot.totalIssued()));
        activeValue.setText(String.valueOf(snapshot.activeCount()));

        fill(loansBox, snapshot.loans(), this::loanRow);
        fill(activityBox, snapshot.recent(), this::activityRow);
        fill(dueBox, snapshot.dueSoon(), this::dueRow);

        boolean loans = !snapshot.loans().isEmpty();
        boolean due = !snapshot.dueSoon().isEmpty();
        boolean activity = !snapshot.recent().isEmpty();

        setShown(loansCard, loans);
        setShown(activityCard, activity);
        setShown(dueCard, due);
        alignGrid(loans, due, activity);

        List<Node> rows = new ArrayList<>();
        rows.add(recordRow("Total issued", String.valueOf(snapshot.totalIssued()), null));
        rows.add(recordRow("Returned", String.valueOf(snapshot.returnedCount()),
                "record-value-back"));
        rows.add(recordRow("Issued now", String.valueOf(snapshot.activeCount()),
                "record-value-now"));
        // A hairline between the lines - but none under the last one.
        for (int i = 0; i < rows.size() - 1; i++) {
            rows.get(i).getStyleClass().add("dash-record-div");
        }
        recordBox.getChildren().setAll(rows);
    }

    /** Replaces a card's rows; a card with no row is hidden as a whole. */
    private <T> void fill(VBox rows, List<T> items, Function<T, Node> renderer) {
        List<Node> nodes = new ArrayList<>();
        for (T item : items) {
            nodes.add(renderer.apply(item));
        }
        rows.getChildren().setAll(nodes);
    }

    /** Visible and managed together, so a hidden card takes no space at all. */
    private static void setShown(Node node, boolean shown) {
        node.setVisible(shown);
        node.setManaged(shown);
    }

    /**
     * Keeps the grid whole when a card has nothing to show: the card next to
     * a missing one takes over its half of the row (column span), instead of
     * leaving a hole of empty background beside it. So with no deadline in
     * sight the books card runs the full width, and without recent activity
     * the record card slides to the left column.
     */
    private void alignGrid(boolean loans, boolean due, boolean activity) {
        // row 1: my books | the deadlines of the coming week
        GridPane.setColumnIndex(loansCard, 0);
        GridPane.setColumnSpan(loansCard, loans && due ? 1 : 2);
        GridPane.setColumnIndex(dueCard, loans ? 1 : 0);
        GridPane.setColumnSpan(dueCard, loans ? 1 : 2);

        // row 2: recent activity | my record
        GridPane.setColumnIndex(activityCard, 0);
        GridPane.setColumnSpan(activityCard, 1);
        GridPane.setColumnIndex(recordCard, activity ? 1 : 0);
        GridPane.setColumnSpan(recordCard, activity ? 1 : 2);
    }

    // ---- cards -------------------------------------------------------

    /** The 60-day rule, in the card style every other page already uses. */
    private VBox ruleCard() {
        Label head = new Label("Keep each book for up to "
                + (LoanRules.MAX_LOAN_DAYS / 30) + " months ("
                + LoanRules.MAX_LOAN_DAYS + " days) from the issue date");
        head.getStyleClass().add("section-title");
        head.setWrapText(true);
        head.setMaxWidth(Double.MAX_VALUE);

        Label body = new Label("Need it longer? Ask the librarian to re-issue it before the deadline - "
                + "returning the book and issuing it again resets the counter. "
                + "A book kept longer than 60 days counts as overdue.");
        body.getStyleClass().add("stat-hint");
        body.setWrapText(true);
        body.setMaxWidth(Double.MAX_VALUE);

        VBox card = new VBox(6, head, body);
        decorate(card);
        card.getStyleClass().add("dash-rule");
        return card;
    }

    /** The two counters, styled exactly like the admin dashboard's KPI cards. */
    private HBox kpiRow() {
        HBox row = new HBox(18,
                statCard("Books issued in total", totalValue),
                statCard("Issued in your name right now", activeValue));
        row.setMinWidth(0);
        row.setMaxWidth(Double.MAX_VALUE);
        return row;
    }

    private VBox statCard(String text, Label value) {
        Label label = new Label(text);
        label.getStyleClass().add("stat-label");
        label.setWrapText(true);
        label.setMaxWidth(Double.MAX_VALUE);

        value.getStyleClass().add("stat-value");

        VBox card = new VBox(4, label, value);
        card.getStyleClass().add("stat-card");
        card.setAlignment(Pos.CENTER);
        card.setMinWidth(0);
        card.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(card, Priority.ALWAYS);
        return card;
    }

    /**
     * The four cards as one grid: the loans above the activity feed on the
     * left, the deadlines above the record on the right. A grid (instead of
     * two independent columns) keeps the cards of a row starting and ending
     * on the same line, so the page reads as a set of boxes rather than as
     * two stacks of different heights.
     *
     * The left column is the wider one (62 / 38) because the book rows carry
     * a title, two dates, a status pill and the progress bar; the right
     * column keeps a readable width and never grows past it.
     */
    private GridPane mainGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(18);
        grid.setVgap(18);
        grid.setMinWidth(0);
        grid.setMaxWidth(Double.MAX_VALUE);

        ColumnConstraints left = new ColumnConstraints();
        left.setPercentWidth(62);
        left.setHgrow(Priority.ALWAYS);
        left.setMinWidth(0);

        ColumnConstraints right = new ColumnConstraints();
        right.setPercentWidth(38);
        right.setMinWidth(230);
        right.setMaxWidth(420);

        grid.getColumnConstraints().addAll(left, right);

        // Row 1: the borrowed books | the deadlines of the coming week.
        // Row 2: the recent activity | the personal record.
        grid.add(loansCard, 0, 0);
        grid.add(dueCard, 1, 0);
        grid.add(activityCard, 0, 1);
        grid.add(recordCard, 1, 1);
        return grid;
    }

    private VBox card(String title, Region body) {
        Label label = new Label(title);
        label.getStyleClass().add("card-title");
        label.setWrapText(true);
        label.setMaxWidth(Double.MAX_VALUE);

        body.setMinWidth(0);
        body.setMaxWidth(Double.MAX_VALUE);

        VBox card = new VBox(12, label, body);
        decorate(card);
        return card;
    }

    /** White, rounded, softly shadowed - the look of every card in the app. */
    private static void decorate(VBox card) {
        card.getStyleClass().add("login-card");
        card.setPadding(new Insets(16, 18, 18, 18));
        card.setMinWidth(0);
        card.setMaxWidth(Double.MAX_VALUE);
    }

    // ---- rows --------------------------------------------------------

    /** One borrowed book: title, dates, status pill and the 60-day bar. */
    private Node loanRow(Loan loan) {
        Label title = new Label(loan.title());
        title.getStyleClass().add("section-title");
        title.setWrapText(true);
        title.setMaxWidth(Double.MAX_VALUE);

        Label meta = new Label("Issued " + loan.issueDate() + "  -  deadline " + loan.deadline());
        meta.getStyleClass().add("stat-hint");

        Label chip = new Label(statusText(loan.daysLeft()));
        chip.getStyleClass().add("dash-chip");
        if (loan.daysLeft() < 0) {
            chip.getStyleClass().add("dash-chip-late");
        } else if (LoanRules.isDueSoon(loan.daysLeft())) {
            chip.getStyleClass().add("dash-chip-soon");
        }

        VBox head = new VBox(3, title, meta);
        head.setMinWidth(0);
        head.setMaxWidth(Double.MAX_VALUE);

        HBox top = new HBox(10, head, chip);
        top.setAlignment(Pos.CENTER_LEFT);
        top.setMinWidth(0);
        HBox.setHgrow(head, Priority.ALWAYS);

        double fraction = Math.min(1.0, loan.daysKept() / (double) LoanRules.MAX_LOAN_DAYS);
        ProgressBar bar = new ProgressBar(fraction);
        bar.getStyleClass().add("dash-progress");
        // Colour follows the days kept: green up to 39, amber 40-59, red from
        // the full 60 (the status pill above it follows the days left).
        if (loan.daysKept() >= LoanRules.MAX_LOAN_DAYS) {
            bar.getStyleClass().add("dash-progress-late");
        } else if (LoanRules.isAmber(loan.daysKept())) {
            bar.getStyleClass().add("dash-progress-soon");
        }
        bar.setMinWidth(0);
        bar.setMaxWidth(Double.MAX_VALUE);

        Label kept = new Label(loan.daysKept() + " of " + LoanRules.MAX_LOAN_DAYS + " days kept");
        kept.getStyleClass().add("stat-hint");

        Label left = new Label(LoanRules.daysLeftText(loan.daysLeft()));
        left.getStyleClass().add("stat-hint");

        HBox days = new HBox(10, kept, left);
        days.setAlignment(Pos.CENTER_LEFT);
        days.setMinWidth(0);
        HBox.setHgrow(kept, Priority.ALWAYS);

        VBox row = new VBox(8, top, bar, days);
        row.getStyleClass().add("dash-loan");
        row.setMinWidth(0);
        row.setMaxWidth(Double.MAX_VALUE);
        return row;
    }

    /** One deadline of the coming week. */
    private Node dueRow(Loan loan) {
        Label title = new Label(loan.title());
        title.getStyleClass().add("section-title");
        title.setWrapText(true);
        title.setMaxWidth(Double.MAX_VALUE);

        String left = loan.daysLeft() == 0 ? "today" : LoanRules.daysLeftText(loan.daysLeft());
        Label sub = new Label("Due " + loan.deadline() + " - " + left);
        sub.getStyleClass().add("stat-hint");
        sub.setWrapText(true);
        sub.setMaxWidth(Double.MAX_VALUE);

        VBox row = new VBox(3, title, sub);
        row.setMinWidth(0);
        row.setMaxWidth(Double.MAX_VALUE);
        return row;
    }

    /** One line of the feed: the book, what happened to it, and when. */
    private Node activityRow(Activity activity) {
        Label title = new Label(activity.title());
        title.getStyleClass().add("section-title");
        title.setWrapText(true);
        title.setMaxWidth(Double.MAX_VALUE);

        Label sub = new Label(activity.returned() ? "Book returned" : "Book issued");
        sub.getStyleClass().add("stat-hint");
        sub.setWrapText(true);
        sub.setMaxWidth(Double.MAX_VALUE);

        VBox texts = new VBox(2, title, sub);
        texts.setMinWidth(0);
        texts.setMaxWidth(Double.MAX_VALUE);

        Label date = new Label(activity.date());
        date.getStyleClass().add("stat-hint");

        HBox row = new HBox(10, texts, date);
        row.getStyleClass().add("dash-activity");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMinWidth(0);
        row.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(texts, Priority.ALWAYS);
        return row;
    }

    /**
     * One line of the "Your record" card: the name on the left, the number in a
     * column of its own on the right, a hairline under it (added by the
     * caller). The row grows with the card, so the three lines share the
     * height out evenly instead of piling up at the top and leaving the lower
     * half of the card blank.
     *
     * @param valueStyle colour of this number (a style after ".record-value");
     *                   null keeps the neutral colour
     */
    private Node recordRow(String key, String value, String valueStyle) {
        Label label = new Label(key);
        label.getStyleClass().add("record-hint");
        // stretches across the row, so the number is pushed into a column of
        // its own on the right instead of sitting beside the name
        label.setMaxWidth(Double.MAX_VALUE);

        Label number = new Label(value);
        number.getStyleClass().add("record-value");
        if (valueStyle != null && !valueStyle.isBlank()) {
            number.getStyleClass().add(valueStyle);
        }

        HBox row = new HBox(10, label, number);
        row.getStyleClass().add("dash-record");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMinWidth(0);
        row.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(label, Priority.ALWAYS);
        VBox.setVgrow(row, Priority.ALWAYS);
        return row;
    }
}
