package com.smartlibrary.controller;

import com.smartlibrary.Main;
import com.smartlibrary.concurrency.AppExecutors;
import com.smartlibrary.concurrency.RefreshQueue;
import com.smartlibrary.database.SQLiteConnection;
import com.smartlibrary.model.OverviewStats;
import com.smartlibrary.ui.AppMenus;
import com.smartlibrary.ui.UserGuide;
import com.smartlibrary.util.ProfileImageService;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.binding.NumberExpression;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Admin Dashboard â€” same single-window pattern as StudentDashboardController:
 * one persistent BorderPane (rootPane), a sidebar that never changes, and the
 * center content swapped out per screen. Manage Books, Manage Members, Issue
 * Book and Return Book are all loaded straight into rootPane's center, so the
 * whole app stays in one window.
 *
 * The "Dashboard" screen (homeView) is a live overview: five stat cards
 * (total books, available books, total members, currently-issued books and
 * the members who have a book out right now) plus two selectors - pick a
 * department to see how many members belong to it, or pick a book category
 * to see how many books are in it. Everything
 * is re-queried from SQLite every time the Dashboard is opened, so it never
 * goes stale after adding/editing books or members elsewhere in the app.
 *
 * The sidebar also holds the admin's circular profile picture: "Choose File"
 * picks an image from disk and ProfileImageService remembers it between runs.
 */
public class AdminDashboardController {

    /** Sidebar and card size limits used by the responsive bindings below. */
    private static final double SIDEBAR_MIN_WIDTH = 180;
    private static final double SIDEBAR_PREF_WIDTH = 210;
    private static final double SIDEBAR_WIDTH_SHARE = 0.20;
    // 116px is what lets all five stat cards share ONE row even while the
    // ScrollPane's vertical scrollbar is showing (5 x 116 + 4 x 18 = 652,
    // and the row only shrinks to about 669 with the scrollbar's 15px taken
    // out); on the designed 960px window they grow to a 120px share. A floor
    // above that would wrap them to 3 + 2 and push the filter cards below the
    // fold as soon as a scrollbar appears.
    private static final double STAT_CARD_MIN_WIDTH = 116;
    private static final double STAT_CARD_MAX_WIDTH = 340;
    // Same story for the three filter cards: 3 x 206 + 36 = 654 still fits a
    // row that has lost 15px to the scrollbar, so all three cards stay in one
    // line instead of wrapping the last answer chip below the fold; on the
    // designed window they grow to a 212px share, wide enough for the titles
    // and two lines of 15px bold chip text.
    private static final double FILTER_CARD_MIN_WIDTH = 206;
    private static final double FILTER_CARD_MAX_WIDTH = 420;
    // FlowPane snaps every child onto the pixel grid, so the children of a row
    // can add up to a few pixels MORE than the row itself, and the ScrollPane's
    // vertical scrollbar costs the row another 15px while it is showing. With
    // a zero-margin share the fifth KPI card sat within a fraction of a pixel
    // of the row's edge: rounding alone pushed it over, the row flipped between
    // one line and two while the window was dragged, and the content height
    // jumped with it - that is the stutter on resize. The row now reserves a
    // small margin, so it stays comfortably inside its own width at every
    // window size; the cards simply give up two or three pixels each.
    private static final double ROW_SAFETY_MARGIN = 12;

    @FXML private BorderPane rootPane;
    @FXML private VBox sidebar;
    @FXML private ScrollPane homeScrollPane;
    @FXML private VBox homeView;
    @FXML private FlowPane statsFlowPane;
    @FXML private FlowPane filterFlowPane;

    @FXML private ImageView avatarImageView;

    @FXML private Label totalBooksLabel;
    @FXML private Label availableBooksLabel;
    @FXML private Label totalMembersLabel;
    @FXML private Label issuedBooksLabel;
    @FXML private Label membersBorrowingLabel;

    /** Sidebar buttons - the one for the current screen carries .nav-active. */
    @FXML private Button navDashboard;
    @FXML private Button navBooks;
    @FXML private Button navMembers;
    @FXML private Button navIssue;
    @FXML private Button navReturn;

    @FXML private ComboBox<String> departmentFilterComboBox;
    @FXML private Label departmentCountLabel;

    @FXML private ComboBox<String> categoryFilterComboBox;
    @FXML private Label categoryCountLabel;

    @FXML private Slider borrowingActivitySlider;
    @FXML private Label borrowingActivityLabel;

    /** View menu entry whose label follows the stage's full-screen state. */
    @FXML private MenuItem fullScreenMenuItem;

    /**
     * Guard for the slider's background refresh: compareAndSet(true) is only
     * allowed to queue one job at a time, and that job reads the newest value
     * from pendingActivityDays when it actually runs.
     */
    private final AtomicBoolean activityRequestPending = new AtomicBoolean(false);
    private volatile int pendingActivityDays;

    /**
     * Center content to restore when a menu page (User Guide, About) is
     * closed - "close brings me back to the previous state". menuPageOwned
     * tells us whether such a page currently owns the center, so opening a
     * second one never overwrites the real previous screen.
     */
    private Node previousCenter;
    private boolean menuPageOwned;

    @FXML
    public void initialize() {
        // Clip the profile picture to the circle behind it, then show any
        // image the admin picked in a previous run.
        avatarImageView.setClip(new Circle(36, 36, 36));
        showAvatar();

        departmentFilterComboBox.setOnAction(e -> updateDepartmentCount());
        categoryFilterComboBox.setOnAction(e -> updateCategoryCount());
        borrowingActivitySlider.valueProperty().addListener((obs, oldVal, newVal) ->
                updateBorrowingActivity(newVal.intValue()));
        bindResponsiveLayout();
        showHome();
    }

    /**
     * Layout responsiveness, done with real property bindings so everything
     * re-flows while the window is being dragged (no manual resize handling):
     *
     *  - the sidebar gives up width on narrow windows (20% of the window,
     *    clamped to 180-210px) so the content area keeps as much room as
     *    possible, and
     *  - every dashboard card's prefWidth is bound to (row width - gaps) / N,
     *    rounded down to whole pixels and clamped between a readable floor and
     *    a sensible cap. Above the floor the cards share the row exactly; when
     *    the row gets too narrow the floor wins, the card no longer fits, and
     *    the FlowPane wraps it onto the next line instead of squeezing the
     *    text.
     */
    private void bindResponsiveLayout() {
        NumberExpression sidebarWidth = rootPane.widthProperty().multiply(SIDEBAR_WIDTH_SHARE);
        sidebar.prefWidthProperty().bind(
                Bindings.max(SIDEBAR_MIN_WIDTH, Bindings.min(SIDEBAR_PREF_WIDTH, sidebarWidth)));

        bindCardsToRow(statsFlowPane, 5, STAT_CARD_MIN_WIDTH, STAT_CARD_MAX_WIDTH);
        bindCardsToRow(filterFlowPane, 3, FILTER_CARD_MIN_WIDTH, FILTER_CARD_MAX_WIDTH);
    }

    /**
     * Card heights are equalised by a fixed minHeight written on the cards in
     * admin-dashboard.fxml, not by a binding between siblings. Binding one
     * card's minHeight to another card's live height made every resize pass
     * re-layout the whole row (the tallest card changes height while the
     * window is dragged, every other card follows, the row height changes
     * again) - the dashboard visibly stuttered while the window was resized.
     * The static floor gives the same tidy row with no feedback loop, and each
     * card still grows past the floor whenever its own text needs more room.
     */

    /** Binds each child of a FlowPane row to its exact share of the row. */
    private void bindCardsToRow(FlowPane row, int columns, double minCardWidth, double maxCardWidth) {
        double gaps = row.getHgap() * (columns - 1);
        // Whole pixels, rounded *down*, and short of the row by
        // ROW_SAFETY_MARGIN: FlowPane snaps every child to whole pixels, and a
        // share that fills the row to the very last pixel makes the last card
        // overflow by rounding alone - the FlowPane would then wrap it for no
        // visible reason at all (and un-wrap it again on the next pass, which
        // is what made the dashboard jump while the window was resized).
        DoubleBinding shareOfRow = Bindings.createDoubleBinding(
                () -> Math.floor((row.getWidth() - gaps - ROW_SAFETY_MARGIN) / columns),
                row.widthProperty());
        for (Node node : row.getChildren()) {
            if (!(node instanceof Region card)) {
                continue; // only Regions have a bindable prefWidth
            }
            card.prefWidthProperty().bind(
                    Bindings.max(minCardWidth, Bindings.min(maxCardWidth, shareOfRow)));
        }
    }

    @FXML
    private void showHome() {
        setActiveNav(navDashboard);
        refreshOverview();
        // Swap the whole ScrollPane (not just homeView) into the center, so the
        // dashboard keeps scrolling when the window is smaller than its content.
        showContent(homeScrollPane);
    }

    /**
     * Refreshes every Dashboard widget. All seven queries are read-only, so
     * they are submitted to the shared thread pool as Callable tasks and run
     * in parallel (Lab 2 Tasks 9-10: submit + Future). A final coordinator
     * task collects their results - its Future.get() blocks a pool worker,
     * never the JavaFX thread - and pushes one combined UI update back with
     * runFx(). The pool queue is FIFO, so a later refresh always overwrites
     * an earlier one and the labels cannot end up stale.
     */
    private void refreshOverview() {
        Future<Integer> totalBooks = AppExecutors.submit(
                () -> countRows("SELECT COUNT(*) FROM books"));
        Future<Integer> availableBooks = AppExecutors.submit(
                () -> countRows("SELECT COUNT(*) FROM books WHERE status = 'AVAILABLE'"));
        Future<Integer> totalMembers = AppExecutors.submit(
                () -> countRows("SELECT COUNT(*) FROM members"));
        Future<Integer> issuedBooks = AppExecutors.submit(
                () -> countRows("SELECT COUNT(*) FROM borrow_records WHERE status = 'BORROWED'"));
        // How many different members have at least one book out right now -
        // one member borrowing three books still counts as one borrower.
        Future<Integer> membersBorrowing = AppExecutors.submit(
                () -> countRows("SELECT COUNT(DISTINCT student_id) FROM borrow_records "
                        + "WHERE status = 'BORROWED'"));
        Future<List<String>> departments = AppExecutors.submit(this::loadDepartmentNames);
        Future<List<String>> categories = AppExecutors.submit(this::loadCategoryNames);

        AppExecutors.execute(() -> {
            try {
                int totalBooksValue = totalBooks.get();
                int availableBooksValue = availableBooks.get();
                int totalMembersValue = totalMembers.get();
                int issuedBooksValue = issuedBooks.get();
                int membersBorrowingValue = membersBorrowing.get();
                List<String> departmentNames = departments.get();
                List<String> categoryNames = categories.get();

                // The seven results travel to the JavaFX thread as one immutable
                // snapshot (record) instead of seven loose parameters.
                OverviewStats snapshot = new OverviewStats(totalBooksValue, availableBooksValue,
                        totalMembersValue, issuedBooksValue, membersBorrowingValue,
                        departmentNames, categoryNames);
                AppExecutors.runFx(() -> applyOverview(snapshot));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // restore the interrupt request
            } catch (ExecutionException e) {
                System.err.println("Dashboard query failed: " + e.getCause());
            }
        });

        // The slider card is refreshed separately: it goes to the queue
        // (not the pool) because its label must always settle on the value
        // the slider is currently sitting on.
        updateBorrowingActivity((int) borrowingActivitySlider.getValue());
    }

    /** Applies the collected snapshot - always on the JavaFX thread. */
    private void applyOverview(OverviewStats stats) {
        totalBooksLabel.setText(String.valueOf(stats.totalBooks()));
        availableBooksLabel.setText(String.valueOf(stats.availableBooks()));
        totalMembersLabel.setText(String.valueOf(stats.totalMembers()));
        issuedBooksLabel.setText(String.valueOf(stats.issuedBooks()));
        membersBorrowingLabel.setText(String.valueOf(stats.membersBorrowing()));

        applyDepartmentOptions(stats.departments());
        applyCategoryOptions(stats.categories());
    }

    /**
     * "Recent Borrowing Activity" slider (0-30). Slider value N means
     * "how many books were issued in the last N days, counting today" -
     * i.e. issue_date >= today-N. issue_date is stored as an ISO yyyy-MM-dd
     * string, so a plain string comparison sorts/compares correctly.
     *
     * Dragging the slider fires this many times a second, so the job goes to
     * RefreshQueue and only one refresh is ever queued: the AtomicBoolean
     * compareAndSet is the guard, and the queued job reads the newest value
     * when it actually runs (trailing-edge coalescing - no query storm, and
     * the label still ends up showing the value the slider stopped on).
     */
    private void updateBorrowingActivity(int days) {
        pendingActivityDays = days;
        if (!activityRequestPending.compareAndSet(false, true)) {
            return; // a refresh is already queued - it will pick up the new value
        }

        RefreshQueue.request(() -> {
            activityRequestPending.set(false); // accept the next request first...
            int requestedDays = pendingActivityDays; // ...then read the newest value

            LocalDate threshold = LocalDate.now().minusDays(requestedDays);
            int count = countRowsWithParam(
                    "SELECT COUNT(*) FROM borrow_records WHERE issue_date >= ?", threshold.toString());

            AppExecutors.runFx(() -> {
                String dayWord = (requestedDays == 1) ? "day" : "days";
                String activityWord = (count == 1) ? "borrowing activity" : "borrowing activities";
                borrowingActivityLabel.setText("Last " + requestedDays + " " + dayWord
                        + ":  " + count + " " + activityWord);
            });
        });
    }

    /** Reads the department names off the JavaFX thread (background job). */
    private List<String> loadDepartmentNames() {
        List<String> departments = new ArrayList<>();
        String sql = "SELECT DISTINCT department FROM members " +
                "WHERE department IS NOT NULL AND department <> '' ORDER BY department";

        try (Connection conn = SQLiteConnection.connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                departments.add(rs.getString(1));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return departments;
    }

    /** Puts the loaded departments back into the combo box (JavaFX thread). */
    private void applyDepartmentOptions(List<String> departments) {
        String previouslySelected = departmentFilterComboBox.getValue();
        departmentFilterComboBox.setItems(FXCollections.observableArrayList(departments));

        if (previouslySelected != null && departments.contains(previouslySelected)) {
            departmentFilterComboBox.setValue(previouslySelected);
            updateDepartmentCount();
        } else {
            departmentFilterComboBox.setValue(null);
            departmentCountLabel.setText("Select a department to see its member count.");
        }
    }

    private void updateDepartmentCount() {
        String department = departmentFilterComboBox.getValue();
        if (department == null) {
            departmentCountLabel.setText("Select a department to see its member count.");
            return;
        }

        RefreshQueue.request(() -> {
            int count = countRowsWithParam("SELECT COUNT(*) FROM members WHERE department = ?", department);
            AppExecutors.runFx(() -> departmentCountLabel.setText(
                    department + ":  " + count + (count == 1 ? " member" : " members")));
        });
    }

    /** Reads the book categories off the JavaFX thread (background job). */
    private List<String> loadCategoryNames() {
        List<String> categories = new ArrayList<>();
        String sql = "SELECT DISTINCT category FROM books " +
                "WHERE category IS NOT NULL AND category <> '' ORDER BY category";

        try (Connection conn = SQLiteConnection.connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                categories.add(rs.getString(1));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return categories;
    }

    /** Puts the loaded categories back into the combo box (JavaFX thread). */
    private void applyCategoryOptions(List<String> categories) {
        String previouslySelected = categoryFilterComboBox.getValue();
        categoryFilterComboBox.setItems(FXCollections.observableArrayList(categories));

        if (previouslySelected != null && categories.contains(previouslySelected)) {
            categoryFilterComboBox.setValue(previouslySelected);
            updateCategoryCount();
        } else {
            categoryFilterComboBox.setValue(null);
            categoryCountLabel.setText("Select a category to see its book count.");
        }
    }

    private void updateCategoryCount() {
        String category = categoryFilterComboBox.getValue();
        if (category == null) {
            categoryCountLabel.setText("Select a category to see its book count.");
            return;
        }

        RefreshQueue.request(() -> {
            int count = countRowsWithParam("SELECT COUNT(*) FROM books WHERE category = ?", category);
            AppExecutors.runFx(() -> categoryCountLabel.setText(
                    category + ":  " + count + (count == 1 ? " book" : " books")));
        });
    }

    private int countRows(String sql) {
        try (Connection conn = SQLiteConnection.connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }

    private int countRowsWithParam(String sql, String param) {
        try (Connection conn = SQLiteConnection.connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }

    @FXML
    private void showManageBooks() {
        setActiveNav(navBooks);
        loadIntoCenter("/com/smartlibrary/fxml/books.fxml");
    }

    @FXML
    private void showManageMembers() {
        setActiveNav(navMembers);
        loadIntoCenter("/com/smartlibrary/fxml/members.fxml");
    }

    @FXML
    private void showIssueBook() {
        setActiveNav(navIssue);
        loadIntoCenter("/com/smartlibrary/fxml/issue-book.fxml");
    }

    @FXML
    private void showReturnBook() {
        setActiveNav(navReturn);
        loadIntoCenter("/com/smartlibrary/fxml/return-book.fxml");
    }

    /**
     * Highlights the sidebar button of the screen that is open right now
     * (.nav-active: deeper background, bold text, accent stripe on the left)
     * and clears the others, so the sidebar always says which section the
     * user is in.
     */
    private void setActiveNav(Button active) {
        Button[] all = {navDashboard, navBooks, navMembers, navIssue, navReturn};
        for (Button button : all) {
            button.getStyleClass().remove("nav-active");
        }
        if (active != null) {
            active.getStyleClass().add("nav-active");
        }
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

        // The copy to disk and the JSON write are I/O, so they leave the
        // JavaFX thread (AppExecutors pool) and only the repaint hops back.
        AppExecutors.execute(() -> {
            try {
                String storedPath = ProfileImageService.store(file);
                ProfileImageService.saveAvatarPath(ProfileImageService.ADMIN_KEY, storedPath);
                AppExecutors.runFx(this::showAvatar);
            } catch (IOException e) {
                e.printStackTrace();
                AppExecutors.runFx(() -> new Alert(Alert.AlertType.ERROR,
                        "Failed to save profile image: " + e.getMessage()).showAndWait());
            }
        });
    }

    /** Shows the stored admin avatar, or the plain circle when there is none. */
    private void showAvatar() {
        avatarImageView.setImage(ProfileImageService.loadSquareImage(
                ProfileImageService.getAvatarPath(ProfileImageService.ADMIN_KEY)));
    }

    @FXML
    private void handleLogout() {
        Main.showLogin();
    }

    // ------------------------------------------------------------------
    // Application menu bar (File / View / Help - the admin bar has no
    // Account menu; the shared behaviour lives in AppMenus/UserGuide).
    // Every entry switches the center scene - nothing opens its own window.
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

    /** Help -&gt; User Guide: TreeView page, written for the admin section. */
    @FXML
    private void handleUserGuide() {
        openMenuPage(UserGuide.pane(false, this::closeMenuPage));
    }

    /** Help -&gt; About: the project details as a center scene. */
    @FXML
    private void handleAbout() {
        openMenuPage(AppMenus.aboutPane(this::closeMenuPage));
    }

    // ------------------------------------------------------------------
    // Center scene switching
    // ------------------------------------------------------------------

    /** Loads an FXML sub screen into the center of the dashboard. */
    private void loadIntoCenter(String fxmlPath) {
        try {
            FXMLLoader loader = new FXMLLoader(AdminDashboardController.class.getResource(fxmlPath));
            Parent view = loader.load();
            showContent(view);
        } catch (Exception e) {
            e.printStackTrace();
            new Alert(Alert.AlertType.ERROR, "Failed to load screen: " + e.getMessage()).showAndWait();
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
