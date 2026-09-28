package com.smartlibrary.controller;

import com.smartlibrary.concurrency.AppExecutors;
import com.smartlibrary.concurrency.RefreshQueue;
import com.smartlibrary.data.BookRepository;
import com.smartlibrary.data.LibraryRepository;
import com.smartlibrary.database.SQLiteConnection;
import com.smartlibrary.model.Book;
import com.smartlibrary.ui.FormMessage;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Step 9 - Admin: Manage Books.
 * Full CRUD (Add / Edit / Delete / Search) over the "books" table,
 * including a description TextArea, a category ComboBox, and a
 * cover image picked with FileChooser and previewed in an ImageView.
 */
public class BookController {

    private static final String COVERS_DIR = "covers";

    @FXML private TableView<Book> bookTable;
    @FXML private TableColumn<Book, String> titleColumn;
    @FXML private TableColumn<Book, String> authorColumn;
    @FXML private TableColumn<Book, String> categoryColumn;
    @FXML private TableColumn<Book, String> isbnColumn;
    @FXML private TableColumn<Book, String> statusColumn;

    @FXML private TextField searchField;

    @FXML private TextField titleField;
    @FXML private TextField authorField;
    @FXML private ComboBox<String> categoryChoiceBox;
    @FXML private TextField isbnField;
    @FXML private TextArea descriptionArea;
    @FXML private javafx.scene.image.ImageView coverImageView;
    @FXML private Label formErrorLabel;

    private final ObservableList<Book> masterData = FXCollections.observableArrayList();
    private FilteredList<Book> filteredData;

    /** The image file the user just picked via "Choose Cover...", not yet saved to disk. */
    private File pendingCoverFile;
    /** The cover_path already stored in the DB for the currently-selected book (kept if no new file is chosen). */
    private String currentCoverPath;

    @FXML
    public void initialize() {
        categoryChoiceBox.setItems(FXCollections.observableArrayList(
                "Programming",
                "Data Structures & Algorithms",
                "Database",
                "Computer Networks",
                "Operating Systems",
                "Artificial Intelligence",
                "Software Engineering",
                "Electrical & Electronic Systems",
                "Power & Energy",
                "Structural Engineering",
                "Thermodynamics & Fluid Mechanics",
                "Manufacturing & Production",
                "Architecture & Design",
                "Biomedical Science & Engineering",
                "Mathematics",
                "Materials & Textile Technology"));

        // Several category names are longer than the form card is wide, so both
        // the selected value and the dropdown rows wrap instead of being clipped.
        categoryChoiceBox.setCellFactory(list -> new WrappingTextCell());
        categoryChoiceBox.setButtonCell(new WrappingTextCell());

        titleColumn.setCellValueFactory(new PropertyValueFactory<>("title"));
        authorColumn.setCellValueFactory(new PropertyValueFactory<>("author"));
        categoryColumn.setCellValueFactory(new PropertyValueFactory<>("category"));
        isbnColumn.setCellValueFactory(new PropertyValueFactory<>("isbn"));
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));

        filteredData = new FilteredList<>(masterData, b -> true);
        bookTable.setItems(filteredData);
        // UNCONSTRAINED policy: the columns keep their readable widths and the
        // table scrolls horizontally whenever it is narrower than the sum of
        // the columns (see the minWidth values in books.fxml).
        bookTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);

        searchField.textProperty().addListener((obs, oldVal, newVal) -> applySearchFilter(newVal));

        bookTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) -> {
            if (newSel != null) {
                populateForm(newSel);
            }
        });

        loadBooksFromDb();
    }

    private void applySearchFilter(String query) {
        String q = query == null ? "" : query.trim().toLowerCase();
        filteredData.setPredicate(book -> {
            if (q.isEmpty()) return true;
            String title = book.getTitle() != null ? book.getTitle().toLowerCase() : "";
            String author = book.getAuthor() != null ? book.getAuthor().toLowerCase() : "";
            String isbn = book.getIsbn() != null ? book.getIsbn().toLowerCase() : "";
            return title.contains(q) || author.contains(q) || isbn.contains(q);
        });
    }

    private void loadBooksFromDb() {
        // The SQL runs on the background refresh worker; the JavaFX thread
        // keeps showing the current rows until the fresh ones arrive, so the
        // table never blanks out (and reloads always apply in request order).
        RefreshQueue.request(() -> {
            List<Book> loaded = new ArrayList<>();
            String failure = null;

            try {
                // Interface-typed reference: any LibraryRepository<Book> fits here.
                LibraryRepository<Book> repository = new BookRepository();
                loaded.addAll(repository.findAll());
            } catch (SQLException e) {
                e.printStackTrace();
                failure = e.getMessage();
            }

            final String error = failure;
            AppExecutors.runFx(() -> {
                masterData.setAll(loaded); // empty on error, same as before
                if (error != null) {
                    FormMessage.error(formErrorLabel, "Failed to load books: " + error);
                }
            });
        });
    }

    private void populateForm(Book book) {
        titleField.setText(book.getTitle());
        authorField.setText(book.getAuthor());
        categoryChoiceBox.setValue(book.getCategory());
        isbnField.setText(book.getIsbn());
        descriptionArea.setText(book.getDescription());
        currentCoverPath = book.getCoverPath();
        pendingCoverFile = null;
        showCoverPreview(currentCoverPath);
        FormMessage.clear(formErrorLabel);
    }

    private void showCoverPreview(String coverPath) {
        if (coverPath == null || coverPath.isBlank()) {
            coverImageView.setImage(null);
            return;
        }
        File file = new File(coverPath);
        if (file.exists()) {
            coverImageView.setImage(new Image(file.toURI().toString(), 55, 75, true, true));
        } else {
            coverImageView.setImage(null);
        }
    }

    @FXML
    private void handleChooseCover() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose a cover image");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Image files", "*.png", "*.jpg", "*.jpeg", "*.gif"));

        File file = chooser.showOpenDialog(coverImageView.getScene().getWindow());
        if (file != null) {
            pendingCoverFile = file;
            coverImageView.setImage(new Image(file.toURI().toString(), 55, 75, true, true));
        }
    }

    /** Copies pendingCoverFile into the covers/ folder and returns the relative path to store, or null if none. */
    private String savePendingCoverIfAny() throws IOException {
        if (pendingCoverFile == null) {
            return currentCoverPath; // keep whatever was already stored (may be null)
        }

        Path coversDir = Path.of(COVERS_DIR);
        Files.createDirectories(coversDir);

        String originalName = pendingCoverFile.getName();
        String extension = "";
        int dot = originalName.lastIndexOf('.');
        if (dot >= 0) extension = originalName.substring(dot);

        String newName = System.currentTimeMillis() + extension;
        Path target = coversDir.resolve(newName);
        Files.copy(pendingCoverFile.toPath(), target, StandardCopyOption.REPLACE_EXISTING);

        return target.toString();
    }

    @FXML
    private void handleAdd() {
        FormMessage.clear(formErrorLabel);
        String title = titleField.getText();
        String author = authorField.getText();

        if (title == null || title.isBlank() || author == null || author.isBlank()) {
            FormMessage.error(formErrorLabel, "Title and Author are required.");
            return;
        }

        String category = categoryChoiceBox.getValue();
        String isbn = emptyToNull(isbnField.getText());
        String description = emptyToNull(descriptionArea.getText());

        String coverPath;
        try {
            coverPath = savePendingCoverIfAny();
        } catch (IOException e) {
            FormMessage.error(formErrorLabel, "Failed to save cover image: " + e.getMessage());
            return;
        }

        String sql = "INSERT INTO books (title, author, category, isbn, description, cover_path, status) " +
                "VALUES (?, ?, ?, ?, ?, ?, 'AVAILABLE')";

        try (Connection conn = SQLiteConnection.connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, title.trim());
            ps.setString(2, author.trim());
            ps.setString(3, category);
            ps.setString(4, isbn);
            ps.setString(5, description);
            ps.setString(6, coverPath);
            ps.executeUpdate();

            loadBooksFromDb();
            handleClear();
            FormMessage.success(formErrorLabel, "Book added.");
        } catch (SQLException e) {
            e.printStackTrace();
            FormMessage.error(formErrorLabel, "Failed to add book: " + e.getMessage());
        }
    }

    @FXML
    private void handleUpdate() {
        FormMessage.clear(formErrorLabel);
        Book selected = bookTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            FormMessage.error(formErrorLabel, "Select a book in the table first.");
            return;
        }

        String title = titleField.getText();
        String author = authorField.getText();
        if (title == null || title.isBlank() || author == null || author.isBlank()) {
            FormMessage.error(formErrorLabel, "Title and Author are required.");
            return;
        }

        String category = categoryChoiceBox.getValue();
        String isbn = emptyToNull(isbnField.getText());
        String description = emptyToNull(descriptionArea.getText());

        String coverPath;
        try {
            coverPath = savePendingCoverIfAny();
        } catch (IOException e) {
            FormMessage.error(formErrorLabel, "Failed to save cover image: " + e.getMessage());
            return;
        }

        String sql = "UPDATE books SET title = ?, author = ?, category = ?, isbn = ?, " +
                "description = ?, cover_path = ? WHERE id = ?";

        try (Connection conn = SQLiteConnection.connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, title.trim());
            ps.setString(2, author.trim());
            ps.setString(3, category);
            ps.setString(4, isbn);
            ps.setString(5, description);
            ps.setString(6, coverPath);
            ps.setInt(7, selected.getId());
            ps.executeUpdate();

            loadBooksFromDb();
            handleClear();
            FormMessage.success(formErrorLabel, "Book updated.");
        } catch (SQLException e) {
            e.printStackTrace();
            FormMessage.error(formErrorLabel, "Failed to update book: " + e.getMessage());
        }
    }

    @FXML
    private void handleDelete() {
        FormMessage.clear(formErrorLabel);
        Book selected = bookTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            FormMessage.error(formErrorLabel, "Select a book in the table first.");
            return;
        }

        if ("BORROWED".equals(selected.getStatus())) {
            FormMessage.error(formErrorLabel,
                    "This book is currently borrowed. It must be returned before it can be deleted.");
            return;
        }

        // borrow_records.book_id is a real foreign key into books.id (enforced
        // with PRAGMA foreign_keys = ON), so a book that appears in any borrow
        // record can no longer be deleted - SQLite would reject the DELETE and
        // those records would be left pointing at a book that is gone.
        if (hasBorrowHistory(selected.getId())) {
            FormMessage.error(formErrorLabel,
                    "This book appears in the library's borrow history, so it cannot be "
                            + "deleted while those borrow records exist.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete \"" + selected.getTitle() + "\"? This cannot be undone.");
        confirm.setHeaderText(null);
        if (confirm.showAndWait().filter(bt -> bt == ButtonType.OK).isEmpty()) {
            return;
        }

        String sql = "DELETE FROM books WHERE id = ?";
        try (Connection conn = SQLiteConnection.connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, selected.getId());
            ps.executeUpdate();

            loadBooksFromDb();
            handleClear();
            FormMessage.success(formErrorLabel, "Book deleted.");
        } catch (SQLException e) {
            e.printStackTrace();
            FormMessage.error(formErrorLabel, "Failed to delete book: " + e.getMessage());
        }
    }

    /** True when any borrow record (borrowed or returned) points at this book. */
    private boolean hasBorrowHistory(int bookId) {
        String sql = "SELECT COUNT(*) FROM borrow_records WHERE book_id = ?";
        try (Connection conn = SQLiteConnection.connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, bookId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    @FXML
    private void handleClear() {
        bookTable.getSelectionModel().clearSelection();
        titleField.clear();
        authorField.clear();
        categoryChoiceBox.setValue(null);
        isbnField.clear();
        descriptionArea.clear();
        coverImageView.setImage(null);
        pendingCoverFile = null;
        currentCoverPath = null;
        FormMessage.clear(formErrorLabel);
    }

    private String emptyToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    /**
     * Category cell that wraps onto a second line instead of clipping, so a
     * long name like "Thermodynamics &amp; Fluid Mechanics" stays readable in the
     * narrow form card. Used for both the popup rows and the button itself.
     */
    private static class WrappingTextCell extends ListCell<String> {

        WrappingTextCell() {
            setWrapText(true);
        }

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            setText(empty ? null : item);
        }
    }
}
