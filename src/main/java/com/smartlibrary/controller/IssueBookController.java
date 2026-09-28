package com.smartlibrary.controller;

import com.smartlibrary.concurrency.AppExecutors;
import com.smartlibrary.concurrency.RefreshQueue;
import com.smartlibrary.data.AvailableBookRepository;
import com.smartlibrary.data.LibraryRepository;
import com.smartlibrary.data.MemberRepository;
import com.smartlibrary.database.Transactions;
import com.smartlibrary.model.Book;
import com.smartlibrary.model.Member;
import com.smartlibrary.ui.FormMessage;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;

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

    /**
     * Records the loan: one borrow record plus the book's new status, applied
     * together in a single transaction.
     *
     * The SQL runs on a pool worker, not on the JavaFX thread - the window
     * stays responsive and the combo boxes are only touched once the outcome
     * is known. Availability is re-checked inside the transaction, so two
     * admin windows racing for the same book cannot both succeed.
     *
     * @return null on success, or the message to show on failure
     */
    private static String issueLoan(String studentId, int bookId, String issueDate) {
        try (Transactions tx = Transactions.begin()) {

            int available = tx.queryInt(
                    "SELECT COUNT(*) FROM books WHERE id = ? AND status = 'AVAILABLE'", bookId);
            if (available == 0) {
                return "That book is no longer available.";
            }

            tx.update("INSERT INTO borrow_records (student_id, book_id, issue_date, status) "
                    + "VALUES (?, ?, ?, 'BORROWED')", studentId, bookId, issueDate);
            tx.update("UPDATE books SET status = 'BORROWED' WHERE id = ?", bookId);

            tx.commit();
            return null;
        } catch (SQLException e) {
            e.printStackTrace();
            return "Failed to issue book: " + e.getMessage();
        }
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

        // Read the values the background task needs now: JavaFX controls may
        // not be touched from another thread, and the admin may well change
        // the selection before the write finishes.
        String studentId = student.getStudentId();
        int bookId = book.getId();
        String issueDateText = issueDate.toString();
        String bookTitle = book.getTitle();
        String studentName = student.getName();

        AppExecutors.execute(() -> {
            String failure = issueLoan(studentId, bookId, issueDateText);

            final String error = failure;
            AppExecutors.runFx(() -> {
                // Either way the list is reloaded: a failed availability check
                // means another window took the book, so the combo box has to
                // catch up with whatever is really on the shelf.
                loadAvailableBooks();

                if (error != null) {
                    FormMessage.error(errorLabel, error);
                    return;
                }
                bookComboBox.setValue(null);
                issueDatePicker.setValue(LocalDate.now());
                FormMessage.success(errorLabel,
                        "\"" + bookTitle + "\" issued to " + studentName + ".");
            });
        });
    }
}
