package com.smartlibrary.model;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * Represents one row of the "books" table.
 * Uses JavaFX properties so this can be bound directly to a TableView
 * later, the same way the earlier LibraryManagementSystem project did.
 */
public class Book {

    private final SimpleIntegerProperty id;
    private final StringProperty title;
    private final StringProperty author;
    private final StringProperty category;
    private final StringProperty isbn;
    private final StringProperty description;
    private final StringProperty coverPath;
    private final StringProperty status; // "AVAILABLE" or "BORROWED"

    public Book(int id, String title, String author, String category,
                String isbn, String description, String coverPath, String status) {
        this.id = new SimpleIntegerProperty(id);
        this.title = new SimpleStringProperty(title);
        this.author = new SimpleStringProperty(author);
        this.category = new SimpleStringProperty(category);
        this.isbn = new SimpleStringProperty(isbn);
        this.description = new SimpleStringProperty(description);
        this.coverPath = new SimpleStringProperty(coverPath);
        this.status = new SimpleStringProperty(status);
    }

    public int getId() { return id.get(); }
    public void setId(int value) { id.set(value); }

    public String getTitle() { return title.get(); }
    public void setTitle(String value) { title.set(value); }
    public StringProperty titleProperty() { return title; }

    public String getAuthor() { return author.get(); }
    public void setAuthor(String value) { author.set(value); }
    public StringProperty authorProperty() { return author; }

    public String getCategory() { return category.get(); }
    public void setCategory(String value) { category.set(value); }
    public StringProperty categoryProperty() { return category; }

    public String getIsbn() { return isbn.get(); }
    public void setIsbn(String value) { isbn.set(value); }
    public StringProperty isbnProperty() { return isbn; }

    public String getDescription() { return description.get(); }
    public void setDescription(String value) { description.set(value); }
    public StringProperty descriptionProperty() { return description; }

    public String getCoverPath() { return coverPath.get(); }
    public void setCoverPath(String value) { coverPath.set(value); }
    public StringProperty coverPathProperty() { return coverPath; }

    public String getStatus() { return status.get(); }
    public void setStatus(String value) { status.set(value); }
    public StringProperty statusProperty() { return status; }

    @Override
    public String toString() {
        return title.get(); // shows nicely in ComboBox / ListView
    }
}
