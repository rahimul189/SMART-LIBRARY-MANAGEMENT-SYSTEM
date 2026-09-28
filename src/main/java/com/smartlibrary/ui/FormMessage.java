package com.smartlibrary.ui;

import javafx.scene.control.Label;

/**
 * The result line that sits directly under a form's buttons - the place a
 * professional form reports instead of opening a popup window.
 *
 * The wording stays the same as before ("Book added.", "Member updated.",
 * "Failed to delete book: ..."); only the place and the colour are new:
 *
 * <ul>
 *   <li>the action worked - the line is green ({@code success-text}),</li>
 *   <li>it did not - the line is red ({@code error-text}).</li>
 * </ul>
 *
 * One line at a time: writing a new message switches the colour, so an error
 * right after a success never stays green.
 */
public final class FormMessage {

    private static final String SUCCESS_STYLE = "success-text";
    private static final String FAILURE_STYLE = "error-text";

    private FormMessage() {
        // static helper
    }

    /** Green line: the action completed. */
    public static void success(Label label, String message) {
        show(label, message, true);
    }

    /** Red line: validation or the action failed. */
    public static void error(Label label, String message) {
        show(label, message, false);
    }

    /** Empties the line; it keeps its default (red) styling for next time. */
    public static void clear(Label label) {
        if (label != null) {
            label.setText("");
        }
    }

    private static void show(Label label, String message, boolean success) {
        if (label == null) {
            return;
        }
        label.setText(message == null ? "" : message);

        String wanted = success ? SUCCESS_STYLE : FAILURE_STYLE;
        String other = success ? FAILURE_STYLE : SUCCESS_STYLE;
        label.getStyleClass().remove(other);
        if (!label.getStyleClass().contains(wanted)) {
            label.getStyleClass().add(wanted);
        }
    }
}
