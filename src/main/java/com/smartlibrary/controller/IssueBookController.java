package com.smartlibrary.controller;

import com.smartlibrary.concurrency.AppExecutors;
import com.smartlibrary.concurrency.RefreshQueue;
import com.smartlibrary.data.AvailableBookRepository;
import com.smartlibrary.data.LibraryRepository;
import com.smartlibrary.data.MemberRepository;
import com.smartlibrary.database.SQLiteConnection;
import com.smartlibrary.model.Book;
import com.smartlibrary.model.Member;
import com.smartlibrary.ui.FormMessage;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Step 11 - Admin: Issue Book.
 * Its own screen (issue-book.fxml), with nothing on it but what's needed
 * to issue a book - no return-book controls anywhere on it.
 */
public class IssueBookController {

    @FXML private ComboBox<Member> studentComboBox;
    @FXML private ComboBox<Book> bookComboBox;
    @FXML private DatePicker issueDatePicker;
    @FXML private Label errorLabel;

    @FXML
    public void initialize() {
        issueDatePicker.setValue(LocalDate.now());
        loadStudents();
        loadAvailableBooks();
    }

    private void loadStudents() {
        // Background query; only the combo box is touched on the JavaFX thread.
        RefreshQueue.request(() -> {
            List<Member> members = new ArrayList<>();
            try {
                LibraryRepository<Member> repository = new MemberRepository();
                members.addAll(repository.findAll());
            } catch (SQLException e) {
                e.printStackTrace();
            }

            AppExecutors.runFx(() -> studentComboBox.setItems(FXCollections.observableArrayList(members)));
        });
    }

    private void loadAvailableBooks() {
        RefreshQueue.request(() -> {
            List<Book> books = new ArrayList<>();
            try {
                // Subclass of BookRepository: same mapping, overridden SELECT.
                LibraryRepository<Book> repository = new AvailableBookRepository();
                books.addAll(repository.findAll());
            } catch (SQLException e) {
                e.printStackTrace();
            }

            AppExecutors.runFx(() -> bookComboBox.setItems(FXCollections.observableArrayList(books)));
        });
    }

    @FXML
    private void handleIssue() {
        FormMessage.clear(errorLabel);

        Member student = studentComboBox.getValue();
        Book book = bookComboBox.getValue();
        LocalDate issueDate = issueDatePicker.getValue();

        if (student == null || book == null || issueDate == null) {
            FormMessage.error(errorLabel, "Select a student, a book and an issue date.");
            return;
        }

        Connection conn = null;
        try {
            conn = SQLiteConnection.connect();
            conn.setAutoCommit(false);

            // Re-check availability inside the transaction in case another
            // admin window issued the same book a moment ago.
            try (PreparedStatement check = conn.prepareStatement(
                    "SELECT status FROM books WHERE id = ?")) {
                check.setInt(1, book.getId());
                try (ResultSet rs = check.executeQuery()) {
                    if (!rs.next() || !"AVAILABLE".equals(rs.getString("status"))) {
                        conn.rollback();
                        FormMessage.error(errorLabel, "That book is no longer available.");
                        loadAvailableBooks();
                        return;
                    }
                }
            }

            try (PreparedStatement insert = conn.prepareStatement(
                    "INSERT INTO borrow_records (student_id, book_id, issue_date, status) " +
                            "VALUES (?, ?, ?, 'BORROWED')")) {
                insert.setString(1, student.getStudentId());
                insert.setInt(2, book.getId());
                insert.setString(3, issueDate.toString());
                insert.executeUpdate();
            }

            try (PreparedStatement update = conn.prepareStatement(
                    "UPDATE books SET status = 'BORROWED' WHERE id = ?")) {
                update.setInt(1, book.getId());
                update.executeUpdate();
            }

            conn.commit();

            String bookTitle = book.getTitle();
            String studentName = student.getName();

            loadAvailableBooks();
            bookComboBox.setValue(null);
            issueDatePicker.setValue(LocalDate.now());

            FormMessage.success(errorLabel,
                    "\"" + bookTitle + "\" issued to " + studentName + ".");

        } catch (SQLException e) {
            e.printStackTrace();
            rollbackQuietly(conn);
            FormMessage.error(errorLabel, "Failed to issue book: " + e.getMessage());
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
}
