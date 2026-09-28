package com.smartlibrary.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Shared actions of the application menu bar that sits on top of both
 * dashboards (File / View / Account / Help - defined in admin-dashboard.fxml
 * and student-dashboard.fxml, always in BorderPane.top so it stays visible on
 * every sub screen the dashboards load into their center).
 *
 * Design rules for this menu (project requirement):
 *  - no keyboard shortcuts, so no accelerator text is shown on the items;
 *  - every entry switches the center scene - nothing opens its own window,
 *    not even the About page or the exit confirmation used to;
 *  - each of those pages carries a Close button that restores whatever was
 *    on screen before (the dashboards keep that "previous center" themselves).
 *
 * The menu bar differs by role only in the Account menu: students get
 * "My Profile" and "Change Password", admins have no Account menu at all.
 *
 * This class also builds the About page (Help &gt; About) as a center scene:
 * flat banner, About the Application, Credits (student + instructors),
 * Technologies Used and Project Information.
 */
public final class AppMenus {

    /** Marks the full-screen label listener as already installed on a menu item. */
    private static final String FULL_SCREEN_WATCHED = "appMenus.fullScreenWatched";

    // ------------------------------------------------------------------
    // About page content (lab project details)
    // ------------------------------------------------------------------

    private static final String ABOUT_TITLE = "Smart Library Management System";
    private static final String ABOUT_SUBTITLE = "About the Application \u00b7 Academic Laboratory Project";

    private static final String ABOUT_PARAGRAPH =
            "Smart Library Management System is a JavaFX-based desktop application developed as "
                    + "an academic laboratory project to simplify and manage common library "
                    + "operations, including book management, member management, book issuing and "
                    + "returning, and borrowing history. Every record is kept permanently so the "
                    + "borrowing history stays available across sessions, and both admin and "
                    + "student logins are handled online.";

    /** {name, roll, department, section, year / term} shown in Credits. */
    private static final String[][] STUDENT_FACTS = {
            {"Name", "Md. Rahimul Haque Rinad"},
            {"Roll", "2307060"},
            {"Department", "Computer Science and Engineering"},
            {"Section", "A2"},
            {"Year / Term", "2nd Year \u00b7 2nd Term"}
    };

    /** {name, designation} of the lab instructors. */
    private static final String[][] INSTRUCTORS = {
            {"Safin Ahammed", "Assistant Professor"},
            {"Ehsanul Karim Talha", "Lecturer"}
    };

    private static final String INSTITUTE =
            "Department of Computer Science and Engineering\n"
                    + "Khulna University of Engineering & Technology (KUET)";

    /** {technology, what it is used for} - shown as cards. */
    private static final String[][] TECHNOLOGIES = {
            {"Java", "Programming language"},
            {"JavaFX", "User interface \u00b7 FXML + CSS"},
            {"SQLite", "Local database"},
            {"JDBC", "Database access"},
            {"JSON", "Data format"},
            {"Jackson", "JSON parsing"},
            {"java.net.http \u00b7 JSONBin.io", "Online accounts over HTTP"}
    };

    /** {label, value} of the Project Information tiles. */
    private static final String[][] PROJECT_FACTS = {
            {"PROJECT TYPE", "Academic Laboratory Project"},
            {"VERSION", "1.0"},
            {"SECTIONS", "Admin & Student"},
            {"DATABASE", "SQLite \u00b7 JSONBin.io"},
            {"YEAR", "2026"}
    };

    private AppMenus() {
    }

    /** The window a node currently belongs to, or null while it has no scene. */
    public static Window owner(Node node) {
        if (node == null || node.getScene() == null) {
            return null;
        }
        return node.getScene().getWindow();
    }

    /**
     * File -&gt; Exit. Direct - no confirmation window, the application just
     * leaves; Main.stop() then finishes the background work gracefully.
     */
    public static void exit() {
        Platform.exit();
    }

    /**
     * View -&gt; Full Screen. The menu bar itself disappears in full screen -
     * that is how full screen works - so the label follows the stage property:
     * Esc leaves full screen without going through this handler, and the next
     * time the menu is opened the entry reads "Windowed Mode" again.
     */
    public static void toggleFullScreen(Window owner, MenuItem item) {
        Stage stage = asStage(owner);
        if (stage == null || item == null) {
            return;
        }

        if (!Boolean.TRUE.equals(item.getProperties().get(FULL_SCREEN_WATCHED))) {
            item.getProperties().put(FULL_SCREEN_WATCHED, Boolean.TRUE);
            stage.fullScreenProperty().addListener((obs, wasFull, isFull) ->
                    item.setText(isFull ? "Windowed Mode" : "Full Screen"));
        }

        // No exit hint: full screen just shows the application, without the
        // "Press Esc..." overlay that used to appear on top of it. Esc still
        // leaves full screen - the window comes back exactly as it was.
        stage.setFullScreenExitHint("");
        stage.setFullScreen(!stage.isFullScreen());
        item.setText(stage.isFullScreen() ? "Windowed Mode" : "Full Screen");
    }

    /**
     * View -&gt; Reset View: leaves full screen / maximised, restores the
     * window to the size the dashboard was designed for and scrolls the
     * visible page back to the top-left - the safety net next to Full Screen.
     *
     * The root's preferred size is what a stage follows through sizeToScene(),
     * so setting it and asking the stage to re-measure brings the window back
     * to exactly width x height plus the OS decorations, whatever state it was
     * in before.
     */
    public static void resetView(Window owner, BorderPane root, int width, int height) {
        Stage stage = asStage(owner);
        if (stage == null) {
            return;
        }

        stage.setFullScreen(false);
        stage.setMaximized(false);

        if (stage.getScene() != null && stage.getScene().getRoot() instanceof Region content) {
            content.setPrefSize(width, height);
            stage.sizeToScene();
        }

        if (root != null && root.getCenter() instanceof ScrollPane scroll) {
            scroll.setHvalue(0);
            scroll.setVvalue(0);
        }
    }

    // ------------------------------------------------------------------
    // Help > About - a center scene, never a dialog
    // ------------------------------------------------------------------

    /**
     * Help -&gt; About: project, credits, technologies and version as a normal
     * center scene (no dialog window). The Close button hands control back to
     * the dashboard, which restores the screen that was showing before.
     *
     * The page lives in a ScrollPane (fitToWidth, exactly like the profile
     * screen): on a short window the page scrolls instead of stretching the
     * center - a stretched center would push the sidebar's Logout below the
     * window - and the labels get a real width to wrap at.
     *
     * @param onClose called by the page's Close button
     */
    public static ScrollPane aboutPane(Runnable onClose) {
        VBox content = new VBox(16);
        content.setPadding(new Insets(30));
        content.setMinWidth(0);     // the ScrollPane sets the width; text wraps
        content.getStyleClass().add("content-pane");

        content.getChildren().addAll(
                banner(ABOUT_TITLE, ABOUT_SUBTITLE),
                applicationCard(),
                creditsCard(),
                technologiesCard(),
                buttonRow(closeButton(onClose)));

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("content-pane");
        scroll.getStyleClass().add("page-scroll");
        return scroll;
    }

    /**
     * The flat header strip used at the top of the About and the User Guide
     * pages: big white title and a light subtitle on a single solid colour -
     * the same dark blue as the table headings, with no gradient and no
     * decorative circles behind the text.
     */
    public static StackPane banner(String titleText, String subtitleText) {
        Label title = new Label(titleText);
        title.getStyleClass().add("banner-title");

        Label subtitle = new Label(subtitleText);
        subtitle.getStyleClass().add("banner-subtitle");

        VBox text = new VBox(title, subtitle);
        StackPane.setAlignment(text, Pos.CENTER_LEFT);

        StackPane banner = new StackPane(text);
        banner.setMaxWidth(Double.MAX_VALUE);
        banner.getStyleClass().add("about-banner");

        return banner;
    }

    /** "About the Application" card: violet paper with an accent rail. */
    private static StackPane applicationCard() {
        VBox content = new VBox(10);
        content.setMaxWidth(Double.MAX_VALUE);
        content.setPadding(new Insets(18, 22, 20, 22));
        content.getChildren().add(sectionHead("About the Application"));

        Label paragraph = new Label(ABOUT_PARAGRAPH);
        paragraph.getStyleClass().add("about-para");
        paragraph.setWrapText(true);
        paragraph.setMaxWidth(Double.MAX_VALUE);
        content.getChildren().add(paragraph);

        Region rail = new Region();
        rail.getStyleClass().add("about-card-bar");
        rail.setMinWidth(5);
        rail.setPrefWidth(5);
        rail.setMaxWidth(5);
        StackPane.setAlignment(rail, Pos.CENTER_LEFT);

        StackPane card = new StackPane(rail, content);
        card.getStyleClass().add("about-card");
        return card;
    }

    /** Credits card: student facts on the left, the two instructors right. */
    private static VBox creditsCard() {
        Label head = new Label("Student Information");
        head.getStyleClass().add("col-head");

        VBox studentColumn = new VBox(8, head);
        studentColumn.setMaxWidth(Double.MAX_VALUE);

        Label name = new Label(STUDENT_FACTS[0][1]);
        name.getStyleClass().add("student-name");
        studentColumn.getChildren().add(name);

        GridPane facts = facts(java.util.Arrays.copyOfRange(STUDENT_FACTS, 1, STUDENT_FACTS.length));
        studentColumn.getChildren().add(facts);

        Label instructorsHead = new Label("Instructors");
        instructorsHead.getStyleClass().add("col-head");

        VBox instructorColumn = new VBox(10, instructorsHead);
        instructorColumn.setMaxWidth(Double.MAX_VALUE);
        for (String[] instructor : INSTRUCTORS) {
            instructorColumn.getChildren().add(instructorCard(instructor[0], instructor[1]));
        }

        HBox columns = new HBox(24, studentColumn, instructorColumn);
        columns.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(studentColumn, Priority.ALWAYS);
        HBox.setHgrow(instructorColumn, Priority.ALWAYS);

        VBox content = new VBox(12, sectionHead("Credits"), columns);
        content.setMaxWidth(Double.MAX_VALUE);
        content.setPadding(new Insets(18, 22, 20, 22));

        return card(content);
    }

    private static VBox instructorCard(String name, String designation) {
        Label nameLabel = new Label(name);
        nameLabel.getStyleClass().add("inst-name");

        Label role = new Label(designation);
        role.getStyleClass().add("role-pill");
        role.setMaxWidth(Region.USE_PREF_SIZE);

        Label where = new Label(INSTITUTE);
        where.getStyleClass().add("inst-where");
        where.setWrapText(true);
        where.setMaxWidth(Double.MAX_VALUE);

        VBox box = new VBox(5, nameLabel, role, where);
        box.getStyleClass().add("inst-card");
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    /** Technologies Used (7 cards) + Project Information tiles. */
    private static VBox technologiesCard() {
        GridPane technologyGrid = new GridPane();
        technologyGrid.setHgap(12);
        technologyGrid.setVgap(12);
        technologyGrid.setMaxWidth(Double.MAX_VALUE);

        for (int i = 0; i < TECHNOLOGIES.length; i++) {
            Node card = technologyCard(TECHNOLOGIES[i][0], TECHNOLOGIES[i][1]);
            technologyGrid.add(card, i % 3, i / 3);
            GridPane.setHgrow(card, Priority.ALWAYS);
            if (i == TECHNOLOGIES.length - 1) {
                GridPane.setColumnSpan(card, 3); // the long one spans the row
            }
        }

        Region divider = new Region();
        divider.getStyleClass().add("divider-line");
        divider.setMaxWidth(Double.MAX_VALUE);

        FlowPane tiles = new FlowPane(12, 12);
        tiles.setMaxWidth(Double.MAX_VALUE);
        for (String[] fact : PROJECT_FACTS) {
            tiles.getChildren().add(infoTile(fact[0], fact[1]));
        }

        VBox content = new VBox(12,
                sectionHead("Technologies Used"), technologyGrid,
                divider,
                sectionHead("Project Information"), tiles);
        content.setMaxWidth(Double.MAX_VALUE);
        content.setPadding(new Insets(18, 22, 20, 22));
        VBox.setVgrow(tiles, Priority.NEVER);

        return card(content);
    }

    private static HBox technologyCard(String name, String purpose) {
        Region dot = new Region();
        dot.getStyleClass().add("tech-dot");
        dot.setMaxWidth(11);
        dot.setMaxHeight(11);

        Region ring = new Region();
        ring.getStyleClass().add("tech-ring");
        ring.setMaxWidth(20);
        ring.setMaxHeight(20);

        StackPane icon = new StackPane(ring, dot);

        Label nameLabel = new Label(name);
        nameLabel.getStyleClass().add("tech-name");

        Label sub = new Label(purpose);
        sub.getStyleClass().add("tech-sub");
        sub.setWrapText(true);
        sub.setMaxWidth(Double.MAX_VALUE);

        VBox text = new VBox(nameLabel, sub);
        text.setMaxWidth(Double.MAX_VALUE);

        HBox card = new HBox(icon, text);
        card.getStyleClass().add("tech-card");
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    private static VBox infoTile(String label, String value) {
        Label key = new Label(label);
        key.getStyleClass().add("info-label");

        Label val = new Label(value);
        val.getStyleClass().add("info-value");
        val.setWrapText(true);
        val.setMaxWidth(Double.MAX_VALUE);

        VBox body = new VBox(key, val);
        body.getStyleClass().add("info-tile-body");
        body.setMaxWidth(Double.MAX_VALUE);

        Region bar = new Region();
        bar.getStyleClass().add("info-tile-bar");
        bar.setMaxWidth(Double.MAX_VALUE);

        VBox tile = new VBox(bar, body);
        tile.getStyleClass().add("info-tile");
        return tile;
    }

    // ------------------------------------------------------------------
    // shared building blocks (also used by UserGuide)
    // ------------------------------------------------------------------

    /** White card with the 4px gradient edge flush on its top. */
    public static VBox card(Node content) {
        Region edge = new Region();
        edge.getStyleClass().add("card-edge");
        edge.setMaxWidth(Double.MAX_VALUE);

        VBox card = new VBox(edge, content);
        card.getStyleClass().add("login-card");
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    /** Section heading: small gradient bar + bold title. */
    public static HBox sectionHead(String text) {
        Region bar = new Region();
        bar.getStyleClass().add("section-bar");

        Label label = new Label(text);
        label.getStyleClass().add("section-title");

        HBox head = new HBox(9, bar, label);
        head.getStyleClass().add("section-head");
        return head;
    }

    /** The standard Close button every menu page ends with. */
    public static Button closeButton(Runnable onClose) {
        Button close = new Button("Close");
        close.getStyleClass().add("btn-secondary");
        close.setDefaultButton(true);
        close.setOnAction(event -> onClose.run());
        return close;
    }

    /** Right-aligned button row (used by the pages that have two buttons). */
    public static HBox buttonRow(Node... buttons) {
        HBox row = new HBox(12, buttons);
        row.setAlignment(Pos.CENTER_RIGHT);
        return row;
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /** Two-column "key : value" facts used in Credits. */
    private static GridPane facts(String[][] rows) {
        GridPane grid = new GridPane();
        grid.setHgap(18);
        grid.setVgap(7);

        for (int i = 0; i < rows.length; i++) {
            Label key = new Label(rows[i][0]);
            key.getStyleClass().add("fact-key");

            Label value = new Label(rows[i][1]);
            value.getStyleClass().add("fact-value");
            value.setMaxWidth(Double.MAX_VALUE);

            grid.add(key, 0, i);
            grid.add(value, 1, i);
            GridPane.setHgrow(value, Priority.ALWAYS);
        }
        grid.setMaxWidth(Double.MAX_VALUE);
        return grid;
    }

    private static Stage asStage(Window owner) {
        if (owner instanceof Stage stage) {
            return stage;
        }
        if (owner != null && owner.getScene() != null
                && owner.getScene().getWindow() instanceof Stage stage) {
            return stage;
        }
        return null;
    }
}
