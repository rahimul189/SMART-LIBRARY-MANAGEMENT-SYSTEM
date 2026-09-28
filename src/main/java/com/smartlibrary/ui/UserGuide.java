package com.smartlibrary.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextFlow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Help -&gt; User Guide, shown as a normal center scene (no dialog window).
 *
 * Layout: a gradient banner on top, a TreeView of the section grouped into
 * categories on the left, and a detail card on the right. Selecting a node
 * rewrites the detail card - "select this, see that". Each answer is made of
 * short labelled blocks (WHAT YOU WILL SEE / HOW TO USE IT / GOOD TO KNOW and,
 * for the password form, numbered steps) written in a friendly, second-person
 * style, with the bold screen names the user reads in the interface.
 *
 * The content is role specific - a student is guided through the student
 * section, an admin through the admin section - so the same class serves both
 * dashboards. The Close button restores whatever screen was open before.
 */
public final class UserGuide {

    private UserGuide() {
    }

    /** One labelled paragraph of an answer: normal, tip, or numbered steps. */
    private record Block(String label, String body, boolean tip, List<String> steps) {

        static Block text(String label, String body) {
            return new Block(label, body, false, List.of());
        }

        static Block tip(String label, String body) {
            return new Block(label, body, true, List.of());
        }

        static Block steps(String label, String... steps) {
            return new Block(label, "", false, List.of(steps));
        }
    }

    /** One topic of the guide: a leaf of the tree, answered in the detail card. */
    private record Topic(String name, String summary, List<Block> blocks) {
    }

    /** One category: a group of the tree holding its topics. */
    private record Group(String name, String summary, List<Topic> topics) {
    }

    /**
     * Builds the guide page for the given section.
     *
     * @param student true for the student section, false for the admin section
     * @param onClose called by the page's Close button
     */
    public static BorderPane pane(boolean student, Runnable onClose) {
        List<Group> groups = student ? studentGroups() : adminGroups();
        String rootLabel = student ? "STUDENT SECTION" : "ADMIN SECTION";
        String intro = student
                ? "Choose a topic on the left to read what that part of the student section "
                        + "does. Every answer appears in this pane, and Close takes you back "
                        + "to the screen you came from."
                : "Choose a topic on the left to read what that part of the admin section "
                        + "does. Every answer appears in this pane, and Close takes you back "
                        + "to the screen you came from.";

        // ---- the tree -------------------------------------------------
        Map<String, String> summaries = new LinkedHashMap<>();
        Map<String, Topic> topics = new LinkedHashMap<>();
        Map<String, Group> groupOfTopic = new HashMap<>();

        TreeItem<String> root = new TreeItem<>(rootLabel);
        root.setExpanded(true);
        summaries.put(rootLabel, intro);

        for (Group group : groups) {
            String groupLabel = group.name().toUpperCase();
            TreeItem<String> groupItem = new TreeItem<>(groupLabel);
            groupItem.setExpanded(true);
            summaries.put(groupLabel, group.summary());

            for (Topic topic : group.topics()) {
                groupItem.getChildren().add(new TreeItem<>(topic.name()));
                topics.put(topic.name(), topic);
                groupOfTopic.put(topic.name(), group);
            }
            root.getChildren().add(groupItem);
        }

        TreeView<String> tree = new TreeView<>(root);
        tree.setShowRoot(true);
        tree.setPrefWidth(270);
        tree.setMinWidth(200);
        tree.setMaxWidth(340);
        tree.setMinHeight(0);
        tree.setCellFactory(view -> new TreeCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("guide-root", "guide-group");
                if (empty || item == null) {
                    setText(null);
                    return;
                }
                setText(item);
                TreeItem<String> node = getTreeItem();
                if (node.getParent() == null) {
                    getStyleClass().add("guide-root");
                } else if (node.getParent().getParent() == null) {
                    getStyleClass().add("guide-group");
                }
            }
        });

        // ---- the detail card ------------------------------------------
        VBox detail = new VBox(6);
        detail.getStyleClass().addAll("login-card", "guide-detail");
        detail.setPadding(new Insets(20, 24, 22, 24));
        detail.setMinWidth(0);      // let fitToWidth shrink it, then wrap
        detail.setMaxWidth(Double.MAX_VALUE);

        ScrollPane scroll = new ScrollPane(detail);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("guide-scroll");
        scroll.setPannable(true);
        scroll.setMinHeight(0);
        HBox.setHgrow(scroll, Priority.ALWAYS);

        tree.getSelectionModel().selectedItemProperty().addListener((obs, oldNode, node) -> {
            if (node == null) {
                return;
            }
            if (node.getParent() == null) {
                showWelcome(detail, student ? "Student section" : "Admin section", intro);
            } else if (node.getParent().getParent() == null) {
                showGroup(detail, node.getValue(), summaries.get(node.getValue()), groups);
            } else {
                Topic topic = topics.get(node.getValue());
                if (topic != null) {
                    showTopic(detail, groupOfTopic.get(node.getValue()).name(),
                            topic.name(), topic.summary(), topic.blocks());
                }
            }
        });
        tree.getSelectionModel().select(root);

        HBox body = new HBox(16, tree, scroll);
        body.setAlignment(Pos.TOP_LEFT);
        // The middle row takes exactly what the window leaves between the
        // banner and the footer. Both panels follow the row's height (and the
        // row itself has no preferred height), so the page never grows past
        // the window - the panels scroll inside instead, and the sidebar's
        // Logout stays where it is.
        body.setPrefHeight(0);
        tree.prefHeightProperty().bind(body.heightProperty());
        scroll.prefHeightProperty().bind(body.heightProperty());

        BorderPane page = new BorderPane();
        page.setPadding(new Insets(30));
        page.getStyleClass().add("content-pane");
        page.setTop(AppMenus.banner("User Guide",
                student
                        ? "Student section \u00b7 what each screen shows"
                        : "Admin section \u00b7 what each screen does"));
        page.setCenter(body);

        HBox footer = AppMenus.buttonRow(AppMenus.closeButton(onClose));
        footer.setPadding(new Insets(14, 0, 0, 0));
        page.setBottom(footer);

        BorderPane.setMargin(body, new Insets(16, 0, 0, 0));
        return page;
    }

    // ------------------------------------------------------------------
    // detail card rendering
    // ------------------------------------------------------------------

    /** The starting answer: how to read the guide. */
    private static void showWelcome(VBox detail, String title, String summary) {
        render(detail, "USER GUIDE", title, summary, List.of(
                Block.text("HOW TO USE THIS GUIDE",
                        "Select a category to fold it open, then pick a topic. The category "
                                + "chip at the top of each answer tells you where you are, and "
                                + "the blocks below it always read in the same order: what the "
                                + "screen shows, how to use it, then one tip.")));
    }

    /** The answer for a whole category: what the group contains. */
    private static void showGroup(VBox detail, String groupLabel, String summary,
                                  List<Group> groups) {
        String plainName = null;
        StringBuilder members = new StringBuilder();
        for (Group group : groups) {
            if (group.name().toUpperCase().equals(groupLabel)) {
                plainName = group.name();
                for (Topic topic : group.topics()) {
                    if (!members.isEmpty()) {
                        members.append(", ");
                    }
                    members.append("<b>").append(topic.name()).append("</b>");
                }
                break;
            }
        }
        render(detail, "CATEGORY", plainName == null ? groupLabel : plainName, summary,
                List.of(Block.text("WHAT IS INSIDE",
                        members.isEmpty() ? "Nothing to show here." : members + ".")));
    }

    private static void showTopic(VBox detail, String category, String name,
                                  String summary, List<Block> blocks) {
        render(detail, category.toUpperCase(), name, summary, blocks);
    }

    /** Rebuilds the detail card: chip, title, summary and the blocks. */
    private static void render(VBox detail, String crumb, String title,
                               String summary, List<Block> blocks) {
        detail.getChildren().clear();

        Label chip = new Label(crumb);
        chip.getStyleClass().add("guide-crumb");
        chip.setMaxWidth(Region.USE_PREF_SIZE);

        Label heading = new Label(title);
        heading.getStyleClass().add("guide-detail-title");

        Label lead = new Label(summary);
        lead.getStyleClass().add("guide-summary");
        lead.setWrapText(true);
        lead.setMaxWidth(Double.MAX_VALUE);

        detail.getChildren().addAll(chip, heading, lead);

        for (Block block : blocks) {
            detail.getChildren().add(blockView(block));
        }
    }

    /** One labelled block: a heading plus a paragraph, steps or a tip box. */
    private static VBox blockView(Block block) {
        Label label = new Label(block.label());
        label.getStyleClass().add("guide-block-label");

        VBox box = new VBox(5);
        box.getStyleClass().add(block.tip() ? "guide-tip" : "guide-block");
        box.setMaxWidth(Double.MAX_VALUE);
        box.getChildren().add(label);

        if (!block.steps().isEmpty()) {
            int number = 1;
            for (String step : block.steps()) {
                Label no = new Label(number + ".");
                no.getStyleClass().add("guide-step-no");

                TextFlow body = RichText.flow(step);
                HBox row = new HBox(no, body);
                row.getStyleClass().add("guide-step-row");
                row.setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(body, Priority.ALWAYS);

                box.getChildren().add(row);
                number++;
            }
        } else {
            box.getChildren().add(RichText.flow(block.body()));
        }
        return box;
    }

    // ------------------------------------------------------------------
    // content: the student section
    // ------------------------------------------------------------------

    private static List<Group> studentGroups() {
        List<Group> groups = new ArrayList<>();

        groups.add(new Group("Reading Books",
                "What the library holds for you right now, and what you have borrowed.",
                List.of(
                        new Topic("Available Books",
                                "See every book you can borrow right now.",
                                List.of(
                                        Block.text("WHAT YOU WILL SEE",
                                                "A clean table of all books that are ready to be "
                                                        + "borrowed - their Title, Author, Category "
                                                        + "and ISBN."),
                                        Block.text("HOW TO USE IT",
                                                "Scroll through the list and pick the book you "
                                                        + "want. Then simply ask at the library "
                                                        + "counter: the admin records the loan from "
                                                        + "the Issue Book screen and the copy is "
                                                        + "reserved in your name."),
                                        Block.tip("GOOD TO KNOW",
                                                "The list only contains books marked AVAILABLE, "
                                                        + "and it refreshes automatically every "
                                                        + "time you open the screen."))),
                        new Topic("My Borrowed Books",
                                "The books that are in your hands at this moment.",
                                List.of(
                                        Block.text("WHAT YOU WILL SEE",
                                                "Your current loans, newest first, each with the "
                                                        + "date it was issued."),
                                        Block.text("HOW TO USE IT",
                                                "Open this page whenever you want to check what "
                                                        + "you have out, or before visiting the "
                                                        + "counter so you know exactly which titles "
                                                        + "to return."),
                                        Block.tip("GOOD TO KNOW",
                                                "When a book is returned it leaves this list and "
                                                        + "moves to Borrowing History with its "
                                                        + "return date."))),
                        new Topic("Borrowing History",
                                "Your complete reading record, kept permanently.",
                                List.of(
                                        Block.text("WHAT YOU WILL SEE",
                                                "Every loan you have ever made: the book title, "
                                                        + "the issue date, and either the return "
                                                        + "date or the note \"still borrowed\"."),
                                        Block.text("HOW TO USE IT",
                                                "Use it as your personal record - to remember "
                                                        + "which books you read last term, or to "
                                                        + "check that a return was registered "
                                                        + "correctly."),
                                        Block.tip("GOOD TO KNOW",
                                                "History is never deleted, so earlier loans stay "
                                                        + "visible even after the book has been "
                                                        + "returned."))))));

        groups.add(new Group("My Account",
                "Your record and your login - all of it opens in the same window.",
                List.of(
                        new Topic("My Profile",
                                "Your record as the library keeps it.",
                                List.of(
                                        Block.text("WHAT YOU WILL SEE",
                                                "Your photo at the top, followed by the six details "
                                                        + "of your account: Student ID, Name, Email, "
                                                        + "Department, Phone and Gender."),
                                        Block.text("HOW TO USE IT",
                                                "Open it any time to confirm your information. The "
                                                        + "page is read-only - if something needs "
                                                        + "correcting, the admin updates it in "
                                                        + "Manage Members."),
                                        Block.tip("GOOD TO KNOW",
                                                "To change your photo, use Choose File in the "
                                                        + "sidebar, just above the Welcome message. "
                                                        + "The same picture then appears on this "
                                                        + "page."))),
                        new Topic("Change Password",
                                "Update your login in four short steps.",
                                List.of(
                                        Block.steps("HOW TO USE IT",
                                                "Open Account > Change Password - the form opens in "
                                                        + "the middle of the window.",
                                                "Type your current password first - the one you "
                                                        + "logged in with.",
                                                "Type the new password, then repeat it in the "
                                                        + "third field.",
                                                "Press Change Password and read the message on the "
                                                        + "form."),
                                        Block.text("WHAT HAPPENS NEXT",
                                                "A green message confirms the change; a red message "
                                                        + "explains what went wrong - for example a "
                                                        + "wrong current password. The message "
                                                        + "always appears on the same page, never "
                                                        + "in a popup."),
                                        Block.tip("GOOD TO KNOW",
                                                "Nothing is saved until the current password is "
                                                        + "correct, and Close always brings you back "
                                                        + "to the screen you came from."))))));

        groups.add(new Group("Menus",
                "The bar at the top of every screen.",
                List.of(
                        new Topic("File Menu",
                                "One command: leave the application.",
                                List.of(
                                        Block.text("WHAT IT OFFERS",
                                                "Exit closes Smart Library immediately, without any "
                                                        + "extra window."))),
                        new Topic("View Menu",
                                "Fit the window to what you are doing.",
                                List.of(
                                        Block.text("WHAT IT OFFERS",
                                                "Full Screen fills the whole screen with the "
                                                        + "application - useful for demos or a small "
                                                        + "laptop display. Reset View brings the "
                                                        + "window back to its normal size, leaves "
                                                        + "full screen or maximised mode, and "
                                                        + "scrolls the page back to the top."))),
                        new Topic("Help Menu",
                                "Guidance and project information.",
                                List.of(
                                        Block.text("WHAT IT OFFERS",
                                                "User Guide opens this page. About shows the "
                                                        + "project details: what the application "
                                                        + "does, who built it, and which "
                                                        + "technologies it uses."))))));

        return groups;
    }

    // ------------------------------------------------------------------
    // content: the admin section
    // ------------------------------------------------------------------

    private static List<Group> adminGroups() {
        List<Group> groups = new ArrayList<>();

        groups.add(new Group("Overview",
                "A quick picture of the library.",
                List.of(
                        new Topic("Dashboard",
                                "The health of your library at a glance.",
                                List.of(
                                        Block.text("WHAT YOU WILL SEE",
                                                "Four counters - Total Books, Available Books, "
                                                        + "Total Members and Issued Books - a "
                                                        + "department selector, a category "
                                                        + "selector, and the Recent Borrowing "
                                                        + "Activity slider."),
                                        Block.text("HOW TO USE IT",
                                                "Open the Dashboard whenever you need a quick "
                                                        + "picture of the library. Pick a department "
                                                        + "to see how many members belong to it, or "
                                                        + "a category to see how many books it "
                                                        + "holds, and drag the slider to review "
                                                        + "loans from the last few days."),
                                        Block.tip("GOOD TO KNOW",
                                                "Every figure is read fresh when the Dashboard "
                                                        + "opens, so the numbers are never out of "
                                                        + "date."))))));

        groups.add(new Group("Library Records",
                "What the library owns and who may borrow it.",
                List.of(
                        new Topic("Manage Books",
                                "Add, correct and remove the collection.",
                                List.of(
                                        Block.text("WHAT YOU WILL SEE",
                                                "A form with Title, Author, Category, ISBN, "
                                                        + "Description and an optional cover image, "
                                                        + "next to the full list of books with their "
                                                        + "AVAILABLE / BORROWED status."),
                                        Block.text("HOW TO USE IT",
                                                "Press the add button to register a new book, "
                                                        + "select a row to edit its details, and use "
                                                        + "delete when a title leaves the library."),
                                        Block.tip("GOOD TO KNOW",
                                                "A book that already appears in the borrowing "
                                                        + "history cannot be deleted - the record of "
                                                        + "the loan has to stay intact."))),
                        new Topic("Manage Members",
                                "Register borrowers and keep their data correct.",
                                List.of(
                                        Block.text("WHAT YOU WILL SEE",
                                                "The registration form - Student ID, Name, Email, "
                                                        + "Department, Phone and Gender - next to "
                                                        + "the list of every registered member."),
                                        Block.text("HOW TO USE IT",
                                                "Registering a member writes the profile into the "
                                                        + "library and creates the online login in "
                                                        + "one step. Select a row to edit - the "
                                                        + "Student ID stays fixed, and the password "
                                                        + "fields stay hidden while editing."),
                                        Block.tip("GOOD TO KNOW",
                                                "A member with borrowing history cannot be "
                                                        + "deleted, so no loan ever loses its "
                                                        + "borrower."))))));

        groups.add(new Group("Loans",
                "Handing books out and taking them back.",
                List.of(
                        new Topic("Issue Book",
                                "Hand a copy to a student.",
                                List.of(
                                        Block.text("WHAT YOU WILL SEE",
                                                "Three choices: the student, the book, and the "
                                                        + "issue date."),
                                        Block.text("HOW TO USE IT",
                                                "Select the student, pick a book from the available "
                                                        + "list, confirm the date and press Issue. A "
                                                        + "borrow record is created and the book "
                                                        + "immediately becomes BORROWED."),
                                        Block.tip("GOOD TO KNOW",
                                                "Books that are already out on loan are not offered "
                                                        + "in the list, so a copy can never be issued "
                                                        + "twice."))),
                        new Topic("Return Book",
                                "Take a book back into the collection.",
                                List.of(
                                        Block.text("WHAT YOU WILL SEE",
                                                "Only the loans that are still open, one row each."),
                                        Block.text("HOW TO USE IT",
                                                "Select the loan, confirm the return date and press "
                                                        + "Return. The record becomes RETURNED and "
                                                        + "the book goes back to AVAILABLE, ready "
                                                        + "for the next borrower."),
                                        Block.tip("GOOD TO KNOW",
                                                "Returned loans stay visible in the student's "
                                                        + "Borrowing History with their return "
                                                        + "date."))))));

        groups.add(new Group("Menus",
                "The bar at the top of every admin screen. There is no Account menu here - "
                        + "admin credentials live in the online account record.",
                List.of(
                        new Topic("File Menu",
                                "One command: leave the application.",
                                List.of(
                                        Block.text("WHAT IT OFFERS",
                                                "Exit closes Smart Library immediately, without any "
                                                        + "extra window."))),
                        new Topic("View Menu",
                                "Fit the window to what you are doing.",
                                List.of(
                                        Block.text("WHAT IT OFFERS",
                                                "Full Screen fills the whole screen with the "
                                                        + "application - useful for demos or a small "
                                                        + "laptop display. Reset View brings the "
                                                        + "window back to its normal size, leaves "
                                                        + "full screen or maximised mode, and "
                                                        + "scrolls the page back to the top."))),
                        new Topic("Help Menu",
                                "Guidance and project information.",
                                List.of(
                                        Block.text("WHAT IT OFFERS",
                                                "User Guide opens this page. About shows the "
                                                        + "project details: what the application "
                                                        + "does, who built it, and which "
                                                        + "technologies it uses."))))));

        return groups;
    }
}
