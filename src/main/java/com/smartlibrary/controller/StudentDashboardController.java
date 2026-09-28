package com.smartlibrary.controller;

import com.smartlibrary.Main;
import com.smartlibrary.concurrency.AppExecutors;
import com.smartlibrary.concurrency.RefreshQueue;
import com.smartlibrary.data.AvailableBookRepository;
import com.smartlibrary.data.LibraryRepository;
import com.smartlibrary.database.SQLiteConnection;
import com.smartlibrary.model.Book;
import com.smartlibrary.model.BorrowRecord;
import com.smartlibrary.ui.AppMenus;
import com.smartlibrary.ui.PasswordChangePage;
import com.smartlibrary.ui.StudentHome;
import com.smartlibrary.ui.UserGuide;
import com.smartlibrary.util.ProfileImageService;
import com.smartlibrary.util.Session;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.NumberExpression;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.time.LocalDate;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class StudentDashboardController {

    /** Sidebar limits used by the responsive width binding. */
    private static final double SIDEBAR_MIN_WIDTH = 190;
    private static final double SIDEBAR_PREF_WIDTH = 210;
    private static final double SIDEBAR_WIDTH_SHARE = 0.20;

    @FXML private BorderPane rootPane;
    @FXML private VBox sidebar;
    @FXML private ScrollPane profileScrollPane;
    @FXML private VBox profileView;

    /**
     * Center content to restore when a menu page (Change Password, User Guide,
     * About) is closed - "close brings me back to the previous state".
     * menuPageOwned tells us whether such a page currently owns the center,
     * so opening a second one never overwrites the real previous screen.
     */
    private Node previousCenter;
    private boolean menuPageOwned;

    @FXML private ImageView avatarImageView;

    @FXML private Label studentNameLabel;
    @FXML private Label studentIdLabel;
    @FXML private Label nameLabel;
    @FXML private Label emailLabel;
    @FXML private Label departmentLabel;
    @FXML private Label phoneLabel;
    @FXML private Label genderLabel;

    /** Picture on the profile page - read-only (no Choose File there). */
    @FXML private ImageView profileImageView;

    /** View menu entry whose label follows the stage's full-screen state. */
    @FXML private MenuItem fullScreenMenuItem;

    /** Sidebar buttons - the one for the current screen carries .nav-active. */
    @FXML private Button navDashboard;
    @FXML private Button navAvailable;
    @FXML private Button navBorrowed;
    @FXML private Button navHistory;

    @FXML
    public void initialize() {
        // Clip the profile picture to the circle behind it (the sidebar circle
        // and the bigger read-only copy on the profile page), then show any
        // image this student picked in a previous run.
        avatarImageView.setClip(new Circle(36, 36, 36));
        profileImageView.setClip(new Circle(45, 45, 45));
        showAvatar();

        // Responsive sidebar: 20% of the window width, clamped to 190-210px,
        // so narrow windows give the content area the space back (the nav
        // labels wrap rather than clip if a label ever gets too long).
        NumberExpression sidebarWidth = rootPane.widthProperty().multiply(SIDEBAR_WIDTH_SHARE);
        sidebar.prefWidthProperty().bind(
                Bindings.max(SIDEBAR_MIN_WIDTH, Bindings.min(SIDEBAR_PREF_WIDTH, sidebarWidth)));

        // The student section opens on the dashboard; the profile page itself
        // is reached from Account > My Profile.
        showDashboard();
    }

    /**
     * Sidebar "Dashboard" - the landing screen after login: a welcome line,
     * the 60-day borrowing rule, the two loan counters, every borrowed book
     * with its days-kept progress bar, the deadlines of the next week, the
     * last five events and the personal record.
     *
     * The page appears immediately with empty numbers; one background query
     * then fills it (name + all borrow records) and the result is applied in a
     * single JavaFX step, so no SQL ever runs on the JavaFX thread.
     */
    @FXML
    private void showDashboard() {
        setActiveNav(navDashboard);
        String studentId = Session.getCurrentStudentId();
        if (studentId == null) {
            Main.showLogin();
            return;
        }

        StudentHome home = new StudentHome();
        home.apply(StudentHome.Snapshot.empty());
        showContent(home.root());

        RefreshQueue.request(() -> {
            String name = null;
            String failure = null;
            List<StudentHome.Entry> entries = new ArrayList<>();

            try (Connection conn = SQLiteConnection.connect()) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT name FROM members WHERE student_id = ?")) {
                    ps.setString(1, studentId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            name = rs.getString("name");
                        }
                    }
                }

                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT b.title, br.issue_date, br.return_date, br.status " +
                        "FROM borrow_records br " +
                        "JOIN books b ON b.id = br.book_id " +
                        "WHERE br.student_id = ? " +
                        "ORDER BY br.issue_date DESC, br.id DESC")) {
                    ps.setString(1, studentId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            entries.add(new StudentHome.Entry(
                                    rs.getString("title"),
                                    rs.getString("issue_date"),
                                    rs.getString("return_date"),
                                    rs.getString("status")));
                        }
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
                failure = e.getMessage();
            }

            final StudentHome.Snapshot snapshot = StudentHome.build(name, entries, LocalDate.now());
            final String error = failure;

            AppExecutors.runFx(() -> {
                if (error != null) {
                    new Alert(Alert.AlertType.ERROR,
                            "Failed to load the dashboard: " + error).showAndWait();
                    return;
                }
                home.apply(snapshot);
                // Same sidebar line the profile page used to fill in.
                studentNameLabel.setText("Student#: " + studentId);
            });
        });
    }

    /**
     * Sidebar "Choose File" button: pick an image, copy it into avatars/,
     * remember the path in profile-images.json and show it in the circle.
     */
    @FXML
    private void handleChooseAvatar() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose a profile image");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Image files", "*.png", "*.jpg", "*.jpeg", "*.gif"));

        File file = chooser.showOpenDialog(avatarImageView.getScene().getWindow());
        if (file == null) {
            return; // cancelled
        }

        // The copy to disk and the JSON write are I/O: run them on the pool
        // and hop back to the JavaFX thread only to repaint the circle.
        String studentId = Session.getCurrentStudentId();
        AppExecutors.execute(() -> {
            try {
                String storedPath = ProfileImageService.store(file);
                ProfileImageService.saveAvatarPath(
                        ProfileImageService.studentKey(studentId), storedPath);
                AppExecutors.runFx(this::showAvatar);
            } catch (IOException e) {
                e.printStackTrace();
                AppExecutors.runFx(() -> new Alert(Alert.AlertType.ERROR,
                        "Failed to save profile image: " + e.getMessage()).showAndWait());
            }
        });
    }

    /** Shows this student's stored avatar, or the plain circle when there is none. */
    private void showAvatar() {
        String studentId = Session.getCurrentStudentId();
        if (studentId == null) {
            avatarImageView.setImage(null);
            profileImageView.setImage(null);
            return;
        }
        Image image = ProfileImageService.loadSquareImage(
                ProfileImageService.getAvatarPath(ProfileImageService.studentKey(studentId)));

        // Same picture in both places: the small sidebar circle and the large
        // read-only copy at the top of the profile page (Account > My Profile).
        avatarImageView.setImage(image);
        profileImageView.setImage(image);
    }

    @FXML
    private void showProfile() {
        setActiveNav(null); // reached through Account > My Profile, not the sidebar
        String studentId = Session.getCurrentStudentId();
        if (studentId == null) {
            // Shouldn't normally happen, but guard against it anyway
            Main.showLogin();
            return;
        }

        String sql = "SELECT student_id, name, email, department, phone, gender FROM members WHERE student_id = ?";

        // Switch to the profile screen right away - the labels keep their
        // previous values until the background query returns, so the screen
        // never flashes empty (Lab 2: SQL off the JavaFX thread). The whole
        // page sits in a ScrollPane, so a short window scrolls it instead of
        // cutting the card off.
        showContent(profileScrollPane);

        RefreshQueue.request(() -> {
            String[] profile = null;
            String failure = null;

            try (Connection conn = SQLiteConnection.connect();
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setString(1, studentId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        profile = new String[]{
                                rs.getString("student_id"),
                                rs.getString("name"),
                                rs.getString("email"),
                                rs.getString("department"),
                                rs.getString("phone"),
                                rs.getString("gender")
                        };
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
                failure = e.getMessage();
            }

            final String[] row = profile;
            final String error = failure;

            AppExecutors.runFx(() -> {
                if (row != null) {
                    studentNameLabel.setText("Student#: " + row[0]);
                    studentIdLabel.setText(row[0]);
                    nameLabel.setText(row[1]);
                    emailLabel.setText(row[2]);
                    departmentLabel.setText(row[3]);
                    phoneLabel.setText(row[4]);
                    genderLabel.setText(row[5]);
                } else if (error != null) {
                    new Alert(Alert.AlertType.ERROR, "Failed to load profile: " + error).showAndWait();
                } else {
                    new Alert(Alert.AlertType.WARNING,
                            "No member profile found for student ID " + studentId
                                    + ".\nAn admin needs to register this student's profile in the Members table.")
                            .showAndWait();
                }
            });
        });
    }

    @FXML
    private void showAvailableBooks() {
        setActiveNav(navAvailable);
        ObservableList<Book> books = FXCollections.observableArrayList();

        TableView<Book> table = new TableView<>();
        // UNCONSTRAINED policy: columns keep their readable widths and the
        // table scrolls horizontally when the window is too narrow for them.
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);

        TableColumn<Book, String> titleCol = new TableColumn<>("Title");
        titleCol.setCellValueFactory(new PropertyValueFactory<>("title"));
        titleCol.setPrefWidth(200);
        titleCol.setMinWidth(150);

        TableColumn<Book, String> authorCol = new TableColumn<>("Author");
        authorCol.setCellValueFactory(new PropertyValueFactory<>("author"));
        authorCol.setPrefWidth(170);
        authorCol.setMinWidth(130);

        TableColumn<Book, String> categoryCol = new TableColumn<>("Category");
        categoryCol.setCellValueFactory(new PropertyValueFactory<>("category"));
        categoryCol.setPrefWidth(130);
        categoryCol.setMinWidth(110);

        TableColumn<Book, String> isbnCol = new TableColumn<>("ISBN");
        isbnCol.setCellValueFactory(new PropertyValueFactory<>("isbn"));
        isbnCol.setPrefWidth(130);
        isbnCol.setMinWidth(110);

        table.getColumns().addAll(titleCol, authorCol, categoryCol, isbnCol);
        table.setItems(books);

        // The list is still empty while the background query runs, so the
        // placeholder is installed up front; it disappears as soon as rows land.
        table.setPlaceholder(new Label("No books are available right now."));

        Label title = new Label("Available Books");
        title.getStyleClass().add("page-title");

        VBox container = new VBox(16, title, table);
        container.setPadding(new Insets(30));
        container.getStyleClass().add("content-pane");
        VBox.setVgrow(table, Priority.ALWAYS);
        table.setMinHeight(0); // may shrink to the space that is actually left

        showContent(container);

        // The view is built on the JavaFX thread; only the query is pushed
        // to the background worker, and books.setAll() hops back here.
        RefreshQueue.request(() -> {
            List<Book> loaded = new ArrayList<>();
            String failure = null;

            try {
                // Interface-typed reference; this subclass filters on AVAILABLE.
                LibraryRepository<Book> repository = new AvailableBookRepository();
                loaded.addAll(repository.findAll());
            } catch (SQLException e) {
                e.printStackTrace();
                failure = e.getMessage();
            }

            final String error = failure;
            AppExecutors.runFx(() -> {
                if (error != null) {
                    new Alert(Alert.AlertType.ERROR, "Failed to load books: " + error).showAndWait();
                    return;
                }
                books.setAll(loaded);
            });
        });
    }

    /**
     * Step 8 — My Borrowed Books.
     * Shows only this student's currently-borrowed books (borrow_records.status = 'BORROWED'),
     * joined against books for the title/author/category, plus the issue date.
     */
    @FXML
    private void showBorrowedBooks() {
        setActiveNav(navBorrowed);
        String studentId = Session.getCurrentStudentId();
        if (studentId == null) {
            Main.showLogin();
            return;
        }

        ObservableList<BorrowRecord> records = FXCollections.observableArrayList();
        java.util.Map<Integer, Book> booksById = new java.util.HashMap<>();

        String sql = "SELECT br.id, br.student_id, br.book_id, br.issue_date, br.return_date, br.status, " +
                "b.title, b.author, b.category, b.isbn, b.description, b.cover_path, b.status AS book_status " +
                "FROM borrow_records br " +
                "JOIN books b ON b.id = br.book_id " +
                "WHERE br.student_id = ? AND br.status = 'BORROWED' " +
                "ORDER BY br.issue_date DESC";

        TableView<BorrowRecord> table = new TableView<>();
        // UNCONSTRAINED policy: columns keep their readable widths and the
        // table scrolls horizontally when the window is too narrow for them.
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);

        TableColumn<BorrowRecord, String> titleCol = new TableColumn<>("Title");
        titleCol.setPrefWidth(200);
        titleCol.setMinWidth(150);
        titleCol.setCellValueFactory(data -> {
            Book b = booksById.get(data.getValue().getBookId());
            return new javafx.beans.property.SimpleStringProperty(b != null ? b.getTitle() : "");
        });

        TableColumn<BorrowRecord, String> authorCol = new TableColumn<>("Author");
        authorCol.setPrefWidth(170);
        authorCol.setMinWidth(130);
        authorCol.setCellValueFactory(data -> {
            Book b = booksById.get(data.getValue().getBookId());
            return new javafx.beans.property.SimpleStringProperty(b != null ? b.getAuthor() : "");
        });

        TableColumn<BorrowRecord, String> categoryCol = new TableColumn<>("Category");
        categoryCol.setPrefWidth(130);
        categoryCol.setMinWidth(110);
        categoryCol.setCellValueFactory(data -> {
            Book b = booksById.get(data.getValue().getBookId());
            return new javafx.beans.property.SimpleStringProperty(b != null ? b.getCategory() : "");
        });

        TableColumn<BorrowRecord, String> issueDateCol = new TableColumn<>("Issue Date");
        issueDateCol.setPrefWidth(130);
        issueDateCol.setMinWidth(110);
        issueDateCol.setCellValueFactory(new PropertyValueFactory<>("issueDate"));

        table.getColumns().addAll(titleCol, authorCol, categoryCol, issueDateCol);
        table.setItems(records);

        // Installed while the list is still empty; the rows arrive from the
        // background query a moment later.
        table.setPlaceholder(new Label("You don't have any books borrowed right now."));

        Label title = new Label("My Borrowed Books");
        title.getStyleClass().add("page-title");

        VBox container = new VBox(16, title, table);
        container.setPadding(new Insets(30));
        container.getStyleClass().add("content-pane");
        VBox.setVgrow(table, Priority.ALWAYS);
        table.setMinHeight(0); // may shrink to the space that is actually left

        showContent(container);

        // Query on the background worker. The columns read booksById, so the
        // fetched map is merged in and table.refresh() repaints the cells.
        RefreshQueue.request(() -> {
            java.util.List<BorrowRecord> loadedRecords = new java.util.ArrayList<>();
            java.util.Map<Integer, Book> loadedBooks = new java.util.HashMap<>();
            String failure = null;

            try (Connection conn = SQLiteConnection.connect();
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setString(1, studentId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int bookId = rs.getInt("book_id");
                        loadedRecords.add(new BorrowRecord(
                                rs.getInt("id"),
                                rs.getString("student_id"),
                                bookId,
                                rs.getString("issue_date"),
                                rs.getString("return_date"),
                                rs.getString("status")
                        ));
                        loadedBooks.put(bookId, new Book(
                                bookId,
                                rs.getString("title"),
                                rs.getString("author"),
                                rs.getString("category"),
                                rs.getString("isbn"),
                                rs.getString("description"),
                                rs.getString("cover_path"),
                                rs.getString("book_status")
                        ));
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
                failure = e.getMessage();
            }

            final String error = failure;
            AppExecutors.runFx(() -> {
                if (error != null) {
                    new Alert(Alert.AlertType.ERROR, "Failed to load borrowed books: " + error).showAndWait();
                    return;
                }
                booksById.putAll(loadedBooks);
                records.setAll(loadedRecords);
                table.refresh();
            });
        });
    }

    /**
     * Step 8 — Borrowing History.
     * Shows every borrow record for this student (both BORROWED and RETURNED),
     * newest first, including the return date when available.
     */
    @FXML
    private void showHistory() {
        setActiveNav(navHistory);
        String studentId = Session.getCurrentStudentId();
        if (studentId == null) {
            Main.showLogin();
            return;
        }

        String sql = "SELECT br.id, br.student_id, br.book_id, br.issue_date, br.return_date, br.status, " +
                "b.title, b.author, b.category, b.isbn, b.description, b.cover_path, b.status AS book_status " +
                "FROM borrow_records br " +
                "JOIN books b ON b.id = br.book_id " +
                "WHERE br.student_id = ? " +
                "ORDER BY br.issue_date DESC";

        ListView<String> historyList = new ListView<>();
        ObservableList<String> lines = FXCollections.observableArrayList();
        historyList.setItems(lines);

        // Installed while the list is still empty; the lines arrive from the
        // background query a moment later.
        historyList.setPlaceholder(new Label("No borrowing history yet."));

        Label title = new Label("Borrowing History");
        title.getStyleClass().add("page-title");

        VBox container = new VBox(16, title, historyList);
        container.setPadding(new Insets(30));
        container.getStyleClass().add("content-pane");
        VBox.setVgrow(historyList, Priority.ALWAYS);
        historyList.setMinHeight(0); // may shrink to the space that is actually left

        showContent(container);

        // Query and text formatting run on the background worker (both are
        // pure data work); only lines.setAll() needs the JavaFX thread.
        RefreshQueue.request(() -> {
            java.util.List<BorrowRecord> records = new java.util.ArrayList<>();
            java.util.Map<Integer, Book> booksById = new java.util.HashMap<>();
            String failure = null;

            try (Connection conn = SQLiteConnection.connect();
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setString(1, studentId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int bookId = rs.getInt("book_id");
                        records.add(new BorrowRecord(
                                rs.getInt("id"),
                                rs.getString("student_id"),
                                bookId,
                                rs.getString("issue_date"),
                                rs.getString("return_date"),
                                rs.getString("status")
                        ));
                        booksById.put(bookId, new Book(
                                bookId,
                                rs.getString("title"),
                                rs.getString("author"),
                                rs.getString("category"),
                                rs.getString("isbn"),
                                rs.getString("description"),
                                rs.getString("cover_path"),
                                rs.getString("book_status")
                        ));
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
                failure = e.getMessage();
            }

            java.util.List<String> texts = new java.util.ArrayList<>();
            for (BorrowRecord r : records) {
                Book b = booksById.get(r.getBookId());
                String bookTitle = b != null ? b.getTitle() : ("Book #" + r.getBookId());
                String returnPart = "RETURNED".equals(r.getStatus())
                        ? ("returned " + r.getReturnDate())
                        : "still borrowed";
                texts.add(bookTitle + "  —  issued " + r.getIssueDate() + "  —  " + returnPart);
            }

            final String error = failure;
            AppExecutors.runFx(() -> {
                if (error != null) {
                    new Alert(Alert.AlertType.ERROR, "Failed to load borrowing history: " + error).showAndWait();
                    return;
                }
                lines.setAll(texts);
            });
        });
    }

    @FXML
    private void handleLogout() {
        Session.clear();
        Main.showLogin();
    }

    // ------------------------------------------------------------------
    // Application menu bar (File / View / Account / Help)
    //
    // Every entry switches the center scene - nothing opens its own window.
    // openMenuPage() remembers what was on screen before the first menu page
    // opened, and the page's Close button hands it back (closeMenuPage()).
    // ------------------------------------------------------------------

    /** File -&gt; Exit: leaves right away, no confirmation window. */
    @FXML
    private void handleExit() {
        AppMenus.exit();
    }

    /** View -&gt; Full Screen. */
    @FXML
    private void handleFullScreen() {
        AppMenus.toggleFullScreen(AppMenus.owner(rootPane), fullScreenMenuItem);
    }

    /** View -&gt; Reset View: back to the designed window size. */
    @FXML
    private void handleResetView() {
        AppMenus.resetView(AppMenus.owner(rootPane), rootPane,
                Main.DASHBOARD_WIDTH, Main.DASHBOARD_HEIGHT);
    }

    /**
     * Account -&gt; My Profile: the read-only profile page (picture plus all
     * six details, no Choose File - the avatar itself is changed from the
     * sidebar). The dashboard is the landing screen, so this menu is now the
     * only way to reach the profile.
     */
    @FXML
    private void handleAccountProfile() {
        showProfile();
    }

    /** Account -&gt; Change Password: a form in the center, results in-form. */
    @FXML
    private void handleChangePassword() {
        openMenuPage(PasswordChangePage.pane(this::closeMenuPage));
    }

    /** Help -&gt; User Guide: TreeView page, written for the student section. */
    @FXML
    private void handleUserGuide() {
        openMenuPage(UserGuide.pane(true, this::closeMenuPage));
    }

    /** Help -&gt; About: the project details as a center scene. */
    @FXML
    private void handleAbout() {
        openMenuPage(AppMenus.aboutPane(this::closeMenuPage));
    }

    // ------------------------------------------------------------------
    // Center scene switching
    // ------------------------------------------------------------------

    /**
     * Highlights the sidebar button of the screen that is open right now
     * (.nav-active: deeper background, bold text, accent stripe on the left)
     * and clears the others, so the sidebar always says which section the
     * student is in. null clears the highlight (Account &gt; My Profile is
     * not one of the sidebar sections).
     */
    private void setActiveNav(Button active) {
        Button[] all = {navDashboard, navAvailable, navBorrowed, navHistory};
        for (Button button : all) {
            button.getStyleClass().remove("nav-active");
        }
        if (active != null) {
            active.getStyleClass().add("nav-active");
        }
    }

    /**
     * Sidebar navigation: a normal screen takes the center, and any open menu
     * page is forgotten - the screen the user just picked *is* the new
     * "previous state" for the next Close.
     */
    private void showContent(Node content) {
        rootPane.setCenter(content);
        previousCenter = null;
        menuPageOwned = false;
    }

    /**
     * Menu navigation: remember the current center once (so a second menu
     * page does not overwrite the real previous screen), then show the page.
     */
    private void openMenuPage(Node page) {
        if (!menuPageOwned) {
            previousCenter = rootPane.getCenter();
            menuPageOwned = true;
        }
        rootPane.setCenter(page);
    }

    /** The Close button of a menu page: back to the previous state. */
    private void closeMenuPage() {
        if (menuPageOwned && previousCenter != null) {
            rootPane.setCenter(previousCenter);
        }
        previousCenter = null;
        menuPageOwned = false;
    }
}
