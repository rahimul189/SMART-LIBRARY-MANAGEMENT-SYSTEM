package com.smartlibrary.ui;

import com.smartlibrary.concurrency.AppExecutors;
import com.smartlibrary.json.JsonAuthService;
import com.smartlibrary.json.JsonBinException;
import com.smartlibrary.util.Session;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Account -&gt; Change Password (student menu only), shown as a normal center
 * scene instead of a dialog window.
 *
 * Three fields - current password, new password, repeat - and one rule that
 * matters: nothing is written until the current password matches the one that
 * is stored online. The check itself is a GET -&gt; compare -&gt; PUT against
 * JSONBin.io inside JsonAuthService's AUTH_LOCK, run on the shared thread pool
 * so the page never freezes (the same threading rule as the login screen).
 *
 * Every outcome - validation problem, wrong current password, success or a
 * network failure - is written into the label under the fields: the form stays
 * exactly where it is, no alert window is ever raised, and the page keeps
 * showing until the user presses Close, which restores the previous screen.
 */
public final class PasswordChangePage {

    private PasswordChangePage() {
    }

    /**
     * Builds the whole center page.
     *
     * @param onClose called by the page's Close button (back to previous screen)
     */
    public static VBox pane(Runnable onClose) {
        String studentId = Session.getCurrentStudentId();

        VBox page = new VBox(16);
        page.setPadding(new Insets(30));
        page.getStyleClass().add("content-pane");

        Label title = new Label("Change Password");
        title.getStyleClass().add("page-title");

        Label subtitle = new Label("Enter the current password first, then choose the new one.");
        subtitle.getStyleClass().add("page-subtitle");
        subtitle.setWrapText(true);

        // ------------------------------------------------------------------
        // The form itself
        // ------------------------------------------------------------------

        PasswordField currentField = new PasswordField();
        currentField.setPromptText("Current password");
        currentField.setMaxWidth(Double.MAX_VALUE);

        PasswordField newField = new PasswordField();
        newField.setPromptText("New password");
        newField.setMaxWidth(Double.MAX_VALUE);

        PasswordField confirmField = new PasswordField();
        confirmField.setPromptText("Repeat new password");
        confirmField.setMaxWidth(Double.MAX_VALUE);

        // Success and failure share this one label: same scene, no dialog.
        Label message = new Label();
        message.setWrapText(true);
        message.setMaxWidth(Double.MAX_VALUE);
        message.setMinHeight(Label.USE_PREF_SIZE); // empty label keeps its slot

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(9);
        addRow(grid, 0, "Student ID", new Label(studentId == null ? "-" : studentId));
        addRow(grid, 1, "Current password", currentField);
        addRow(grid, 2, "New password", newField);
        addRow(grid, 3, "Repeat new password", confirmField);
        grid.add(message, 0, 4, 2, 1);
        GridPane.setMargin(message, new Insets(4, 0, 0, 0));

        Button changeButton = new Button("Change Password");
        changeButton.getStyleClass().add("btn-primary");
        changeButton.setDefaultButton(true);
        changeButton.setDisable(studentId == null); // nothing to change without a login

        Button closeButton = AppMenus.closeButton(onClose);
        HBox actions = AppMenus.buttonRow(changeButton, closeButton);

        VBox card = new VBox(14, grid, actions);
        card.getStyleClass().add("login-card");
        card.setMaxWidth(540);
        card.setPadding(new Insets(22, 26, 22, 26));

        if (studentId == null) {
            // Session cleared behind our back (logout elsewhere): say it here,
            // in the page, rather than opening a warning window.
            showMessage(message, "No student is logged in, so there is no password to change.", false);
        }

        changeButton.setOnAction(event ->
                attempt(studentId, changeButton, currentField, newField, confirmField, message));

        page.getChildren().addAll(title, subtitle, card);
        return page;
    }

    // ------------------------------------------------------------------

    /** Validates, then runs the online change off the JavaFX thread. */
    private static void attempt(String studentId, Button changeButton,
                                PasswordField currentField, PasswordField newField,
                                PasswordField confirmField, Label message) {

        String current = currentField.getText();
        String next = newField.getText();
        String repeat = confirmField.getText();

        if (current.isBlank()) {
            showMessage(message, "Your current password is required.", false);
            return;
        }
        if (next.isBlank()) {
            showMessage(message, "The new password cannot be empty.", false);
            return;
        }
        if (!next.equals(repeat)) {
            showMessage(message, "The new password and its repeat do not match.", false);
            return;
        }
        if (next.equals(current)) {
            showMessage(message, "The new password must be different from the current one.", false);
            return;
        }

        message.setText("");
        changeButton.setDisable(true); // one request at a time

        AppExecutors.execute(() -> {
            JsonAuthService.PasswordChange outcome = null;
            String failure = null;
            try {
                outcome = JsonAuthService.changeStudentPassword(studentId, current, next);
            } catch (JsonBinException e) {
                e.printStackTrace();
                failure = e.getMessage();
            }

            final JsonAuthService.PasswordChange result = outcome;
            final String text = failure;

            AppExecutors.runFx(() -> {
                changeButton.setDisable(false);

                if (text != null) {
                    showMessage(message,
                            "Could not reach the online account service: " + text, false);
                    return;
                }
                if (result == JsonAuthService.PasswordChange.SUCCESS) {
                    // Stay on this page and say it here - no popup.
                    currentField.clear();
                    newField.clear();
                    confirmField.clear();
                    showMessage(message,
                            "Password changed. Use the new password the next time you log in.", true);
                    return;
                }
                if (result == JsonAuthService.PasswordChange.WRONG_PASSWORD) {
                    showMessage(message,
                            "The current password is incorrect - nothing was changed.", false);
                    currentField.clear();
                    currentField.requestFocus();
                    return;
                }
                showMessage(message,
                        "No online account was found for student ID " + studentId + ".", false);
            });
        });
    }

    /** Puts a result on the page: green for success, red for a problem. */
    private static void showMessage(Label message, String text, boolean success) {
        message.getStyleClass().removeAll("error-text", "success-text");
        message.getStyleClass().add(success ? "success-text" : "error-text");
        message.setText(text);
    }

    /** Adds one label + control line to the form grid. */
    private static void addRow(GridPane grid, int row, String labelText, Node control) {
        Label label = new Label(labelText);
        label.getStyleClass().add("section-title");
        label.setWrapText(true);

        grid.add(label, 0, row);
        grid.add(control, 1, row);
        GridPane.setHgrow(control, Priority.ALWAYS); // the fields fill the row
    }
}
