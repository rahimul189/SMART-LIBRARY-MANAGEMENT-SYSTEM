package com.smartlibrary.model;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * Represents one row of the "borrow_records" table - a single
 * issue/return event linking a student (student_id) to a book (book_id).
 */
public class BorrowRecord {

    private final SimpleIntegerProperty id;
    private final StringProperty studentId;
    private final SimpleIntegerProperty bookId;
    private final StringProperty issueDate;
    private final StringProperty returnDate;
    private final StringProperty status; // "BORROWED" or "RETURNED"

    public BorrowRecord(int id, String studentId, int bookId,
                         String issueDate, String returnDate, String status) {
        this.id = new SimpleIntegerProperty(id);
        this.studentId = new SimpleStringProperty(studentId);
        this.bookId = new SimpleIntegerProperty(bookId);
        this.issueDate = new SimpleStringProperty(issueDate);
        this.returnDate = new SimpleStringProperty(returnDate == null ? "" : returnDate);
        this.status = new SimpleStringProperty(status);
    }

    public int getId() { return id.get(); }
    public void setId(int value) { id.set(value); }

    public String getStudentId() { return studentId.get(); }
    public void setStudentId(String value) { studentId.set(value); }
    public StringProperty studentIdProperty() { return studentId; }

    public int getBookId() { return bookId.get(); }
    public void setBookId(int value) { bookId.set(value); }

    public String getIssueDate() { return issueDate.get(); }
    public void setIssueDate(String value) { issueDate.set(value); }
    public StringProperty issueDateProperty() { return issueDate; }

    public String getReturnDate() { return returnDate.get(); }
    public void setReturnDate(String value) { returnDate.set(value); }
    public StringProperty returnDateProperty() { return returnDate; }

    public String getStatus() { return status.get(); }
    public void setStatus(String value) { status.set(value); }
    public StringProperty statusProperty() { return status; }
}
