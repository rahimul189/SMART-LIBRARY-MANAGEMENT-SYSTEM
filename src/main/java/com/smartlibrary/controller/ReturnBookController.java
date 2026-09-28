package com.smartlibrary.controller;

import com.smartlibrary.concurrency.AppExecutors;
import com.smartlibrary.concurrency.RefreshQueue;
import com.smartlibrary.database.SQLiteConnection;
import com.smartlibrary.ui.FormMessage;
import com.smartlibrary.ui.StudentHome;
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

    /** Adds the three deadline fields to a row: deadline, days kept, status. */
    private static BorrowRow withDeadline(BorrowRow row, LocalDate today) {
        String deadline = "";
        String daysKept = "";
        String status = "";

        try {
            LocalDate issue = LocalDate.parse(row.getIssueDate());
            LocalDate due = issue.plusDays(StudentHome.MAX_LOAN_DAYS);
            long kept = Math.max(0, java.time.temporal.ChronoUnit.DAYS.between(issue, today));
            long left = StudentHome.MAX_LOAN_DAYS - kept;

            deadline = due.toString();
            daysKept = kept + " / " + StudentHome.MAX_LOAN_DAYS;
            status = StudentHome.statusText((int) left);
        } catch (RuntimeException e) {
            // Unexpected date format: the row still lists the issue date.
        }

        return new BorrowRow(row.getRecordId(), row.getStudentId(), row.getStudentName(),
                row.getBookId(), row.getBookTitle(), row.getIssueDate(),
                deadline, daysKept, status);
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

        Connection conn = null;
        try {
            conn = SQLiteConnection.connect();
            conn.setAutoCommit(false);

            try (PreparedStatement updateRecord = conn.prepareStatement(
                    "UPDATE borrow_records SET status = 'RETURNED', return_date = ? WHERE id = ?")) {
                updateRecord.setString(1, returnDate.toString());
                updateRecord.setInt(2, selected.getRecordId());
                updateRecord.executeUpdate();
            }

            try (PreparedStatement updateBook = conn.prepareStatement(
                    "UPDATE books SET status = 'AVAILABLE' WHERE id = ?")) {
                updateBook.setInt(1, selected.getBookId());
                updateBook.executeUpdate();
            }

            conn.commit();

            String bookTitle = selected.getBookTitle();
            String studentName = selected.getStudentName();

            loadBorrowedRows();
            returnDatePicker.setValue(LocalDate.now());

            FormMessage.success(errorLabel,
                    "\"" + bookTitle + "\" returned for " + studentName + ".");

        } catch (SQLException e) {
            e.printStackTrace();
            rollbackQuietly(conn);
            FormMessage.error(errorLabel, "Failed to return book: " + e.getMessage());
        } finally {
            closeQuietly(conn);
        }
    }

    private void rollbackQuietly(Connection conn) {
        if (conn == null) return;
        try {
            conn.rollback();
        } catch (SQLException ignored) {
            // best effort
        }
    }

    private void closeQuietly(Connection conn) {
        if (conn == null) return;
        try {
            conn.setAutoCommit(true);
            conn.close();
        } catch (SQLException ignored) {
            // best effort
        }
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
