package com.smartlibrary.controller;

import com.smartlibrary.concurrency.AppExecutors;
import com.smartlibrary.concurrency.RefreshQueue;
import com.smartlibrary.database.SQLiteConnection;
import com.smartlibrary.database.Transactions;
import com.smartlibrary.model.LoanRules;
import com.smartlibrary.ui.FormMessage;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Step 11 - Admin: Return Book.
 * Its own screen (return-book.fxml), with nothing on it but what's needed
 * to return a book - no issue-book controls anywhere on it.
 */
public class ReturnBookController {

    @FXML private TableView<BorrowRow> borrowedTable;
    @FXML private TableColumn<BorrowRow, String> studentNameColumn;
    @FXML private TableColumn<BorrowRow, String> studentIdColumn;
    @FXML private TableColumn<BorrowRow, String> bookTitleColumn;
    @FXML private TableColumn<BorrowRow, String> issueDateColumn;
    /** Deadline = issue date + the 60-day rule, shown before a return. */
    @FXML private TableColumn<BorrowRow, String> deadlineColumn;
    @FXML private TableColumn<BorrowRow, String> daysKeptColumn;
    @FXML private TableColumn<BorrowRow, String> statusColumn;
    @FXML private DatePicker returnDatePicker;
    @FXML private Label errorLabel;

    @FXML
    public void initialize() {
        returnDatePicker.setValue(LocalDate.now());

        studentNameColumn.setCellValueFactory(new PropertyValueFactory<>("studentName"));
        studentIdColumn.setCellValueFactory(new PropertyValueFactory<>("studentId"));
        bookTitleColumn.setCellValueFactory(new PropertyValueFactory<>("bookTitle"));
        issueDateColumn.setCellValueFactory(new PropertyValueFactory<>("issueDate"));
        deadlineColumn.setCellValueFactory(new PropertyValueFactory<>("deadline"));
        daysKeptColumn.setCellValueFactory(new PropertyValueFactory<>("daysKept"));
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        // An overdue row is told apart from the healthy ones by colour; the
        // text alone still carries the number, so nothing is lost in print.
        statusColumn.setCellFactory(column -> new TableCell<BorrowRow, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item);
                getStyleClass().remove("error-text");
                if (!empty && item != null && item.startsWith("Overdue")) {
                    getStyleClass().add("error-text");
                }
            }
        });
        // UNCONSTRAINED policy: the columns keep their readable widths and the
        // table scrolls horizontally when it is too narrow (see return-book.fxml).
        borrowedTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        borrowedTable.setPlaceholder(new Label("No books are currently borrowed."));

        loadBorrowedRows();
    }

    private void loadBorrowedRows() {
        String sql = "SELECT br.id AS record_id, br.student_id, m.name AS student_name, " +
                "br.book_id, b.title AS book_title, br.issue_date " +
                "FROM borrow_records br " +
                "JOIN members m ON m.student_id = br.student_id " +
                "JOIN books b ON b.id = br.book_id " +
                "WHERE br.status = 'BORROWED' " +
                "ORDER BY br.issue_date DESC";

        // Query on the background worker, table update back on the JavaFX thread.
        RefreshQueue.request(() -> {
            List<BorrowRow> rows = new ArrayList<>();
            LocalDate today = LocalDate.now();

            try (Connection conn = SQLiteConnection.connect();
                 PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {

                while (rs.next()) {
                    rows.add(withDeadline(new BorrowRow(
                            rs.getInt("record_id"),
                            rs.getString("student_id"),
                            rs.getString("student_name"),
                            rs.getInt("book_id"),
                            rs.getString("book_title"),
                            rs.getString("issue_date")), today));
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }

            AppExecutors.runFx(() -> borrowedTable.setItems(FXCollections.observableArrayList(rows)));
        });
    }

    /**
     * Adds the three deadline fields to a row: deadline, days kept, status.
     *
     * The measurement itself is {@link LoanRules}' - the same code the student
     * dashboard uses - so a loan cannot show one deadline here and a different
     * one on the student's screen. An issue date that is not a valid ISO date
     * leaves the three fields empty rather than failing the whole query.
     */
    private static BorrowRow withDeadline(BorrowRow row, LocalDate today) {
        LoanRules.Loan measured = LoanRules.measure(row.getBookTitle(), row.getIssueDate(), today);

        if (measured == null) {
            return new BorrowRow(row.getRecordId(), row.getStudentId(), row.getStudentName(),
                    row.getBookId(), row.getBookTitle(), row.getIssueDate());
        }

        return new BorrowRow(row.getRecordId(), row.getStudentId(), row.getStudentName(),
                row.getBookId(), row.getBookTitle(), row.getIssueDate(),
                measured.deadline(),
                measured.daysKept() + " / " + LoanRules.MAX_LOAN_DAYS,
                measured.statusText());
    }

    /**
     * Closes a loan: the borrow record becomes RETURNED and the book goes back
     * on the shelf, applied together in one transaction.
     *
     * @return null on success, or the message to show on failure
     */
    private static String returnLoan(int recordId, int bookId, String returnDate) {
        try (Transactions tx = Transactions.begin()) {
            tx.update("UPDATE borrow_records SET status = 'RETURNED', return_date = ? WHERE id = ?",
                    returnDate, recordId);
            tx.update("UPDATE books SET status = 'AVAILABLE' WHERE id = ?", bookId);
            tx.commit();
            return null;
        } catch (SQLException e) {
            e.printStackTrace();
            return "Failed to return book: " + e.getMessage();
        }
    }

    @FXML
    private void handleReturn() {
        FormMessage.clear(errorLabel);

        BorrowRow selected = borrowedTable.getSelectionModel().getSelectedItem();
        LocalDate returnDate = returnDatePicker.getValue();

        if (selected == null) {
            FormMessage.error(errorLabel, "Select a borrowed book from the table first.");
            return;
        }
        if (returnDate == null) {
            FormMessage.error(errorLabel, "Pick a return date.");
            return;
        }

        // Copy the row's values out before the write leaves the FX thread: the
        // selection may change while the transaction is in flight, and reading
        // a control from a worker thread is not allowed.
        int recordId = selected.getRecordId();
        int bookId = selected.getBookId();
        String returnDateText = returnDate.toString();
        String bookTitle = selected.getBookTitle();
        String studentName = selected.getStudentName();

        AppExecutors.execute(() -> {
            String failure = returnLoan(recordId, bookId, returnDateText);

            final String error = failure;
            AppExecutors.runFx(() -> {
                loadBorrowedRows();
                if (error != null) {
                    FormMessage.error(errorLabel, error);
                    return;
                }
                returnDatePicker.setValue(LocalDate.now());
                FormMessage.success(errorLabel,
                        "\"" + bookTitle + "\" returned for " + studentName + ".");
            });
        });
    }

    /**
     * Row shown in the borrowed-books table. Neither BorrowRecord nor Book
     * alone carries both the student's name and the book's title, so this
     * is a small read-only join result rather than a real model class.
     */
    public static class BorrowRow {
        private final int recordId;
        private final String studentId;
        private final String studentName;
        private final int bookId;
        private final String bookTitle;
        private final String issueDate;
        private final String deadline;
        private final String daysKept;
        private final String status;

        public BorrowRow(int recordId, String studentId, String studentName,
                          int bookId, String bookTitle, String issueDate) {
            this(recordId, studentId, studentName, bookId, bookTitle, issueDate, "", "", "");
        }

        /** Full constructor - the three deadline fields come from withDeadline(). */
        public BorrowRow(int recordId, String studentId, String studentName,
                          int bookId, String bookTitle, String issueDate,
                          String deadline, String daysKept, String status) {
            this.recordId = recordId;
            this.studentId = studentId;
            this.studentName = studentName;
            this.bookId = bookId;
            this.bookTitle = bookTitle;
            this.issueDate = issueDate;
            this.deadline = deadline;
            this.daysKept = daysKept;
            this.status = status;
        }

        public int getRecordId() { return recordId; }
        public String getStudentId() { return studentId; }
        public String getStudentName() { return studentName; }
        public int getBookId() { return bookId; }
        public String getBookTitle() { return bookTitle; }
        public String getIssueDate() { return issueDate; }
        public String getDeadline() { return deadline; }
        public String getDaysKept() { return daysKept; }
        public String getStatus() { return status; }
    }
}
