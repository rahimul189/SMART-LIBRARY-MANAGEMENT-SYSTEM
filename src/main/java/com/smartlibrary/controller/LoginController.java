package com.smartlibrary.controller;

import com.smartlibrary.Main;
import com.smartlibrary.concurrency.AppExecutors;
import com.smartlibrary.json.JsonAuthService;
import com.smartlibrary.json.JsonBinException;
import com.smartlibrary.util.Session;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.NumberExpression;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public class LoginController {

    /** Smallest / largest width the login card may take, in pixels. */
    private static final double MIN_CARD_WIDTH = 340;
    private static final double MAX_CARD_WIDTH = 560;
    private static final double CARD_WIDTH_SHARE = 0.86; // share of the window width

    @FXML private StackPane loginRoot;
    @FXML private VBox loginCard;

    @FXML private TextField adminUsernameField;
    @FXML private PasswordField adminPasswordField;
    @FXML private Label adminErrorLabel;

    @FXML private TextField studentEmailField;
    @FXML private PasswordField studentPasswordField;
    @FXML private Label studentErrorLabel;

    /**
     * True while a login request is in flight. Authentication now goes over
     * HTTP to JSONBin.io, so the check runs on a background thread - this
     * flag (only ever touched on the JavaFX thread) stops a second click
     * from firing a second request while the first one is still pending.
     */
    private boolean authRequestPending;

    /**
     * Layout responsiveness: the card's width is *bound* to the window width
     * instead of being a fixed number - 86% of the scene, clamped between
     * MIN_CARD_WIDTH and MAX_CARD_WIDTH with Bindings.min / Bindings.max.
     * Resizing the window therefore re-lays the form out automatically; the
     * title label has wrapText on, so it re-wraps instead of being clipped.
     */
    @FXML
    public void initialize() {
        loginRoot.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) {
                return; // window closing - nothing to bind to
            }
            NumberExpression windowWidth = newScene.widthProperty();
            loginCard.prefWidthProperty().bind(
                    Bindings.max(MIN_CARD_WIDTH,
                            Bindings.min(MAX_CARD_WIDTH, windowWidth.multiply(CARD_WIDTH_SHARE))));
        });
    }

    @FXML
    private void handleAdminLogin() {
        adminErrorLabel.setText("");
        String username = adminUsernameField.getText();
        String password = adminPasswordField.getText();

        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            adminErrorLabel.setText("Please enter both username and password.");
            return;
        }
        if (authRequestPending) {
            return; // a request is already on its way to JSONBin.io
        }

        authRequestPending = true;
        adminErrorLabel.setText("Verifying with the online authentication service...");

        // JavaFX -> HttpRequest -> JSONBin.io -> HttpResponse -> Jackson -> objects.
        // The network call must never run on the JavaFX thread, so the whole
        // check happens on the pool and only the outcome comes back here.
        AppExecutors.execute(() -> {
            final AuthAttempt attempt = AuthAttempt.admin(username, password);
            AppExecutors.runFx(() -> {
                authRequestPending = false;
                if (attempt.error() != null) {
                    adminErrorLabel.setText(attempt.error().getMessage());
                } else if (attempt.verified()) {
                    Session.loginAsAdmin();
                    Main.showAdminDashboard();
                } else {
                    adminErrorLabel.setText("Incorrect username or password.");
                }
            });
        });
    }

    @FXML
    private void handleStudentLogin() {
        studentErrorLabel.setText("");
        String email = studentEmailField.getText();
        String password = studentPasswordField.getText();

        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            studentErrorLabel.setText("Please enter both email and password.");
            return;
        }
        if (authRequestPending) {
            return;
        }

        authRequestPending = true;
        studentErrorLabel.setText("Verifying with the online authentication service...");

        // Same background pattern as the admin login: the studentId comes back
        // from the online JSON and is then used as the SQLite lookup key.
        AppExecutors.execute(() -> {
            final AuthAttempt attempt = AuthAttempt.student(email, password);
            AppExecutors.runFx(() -> {
                authRequestPending = false;
                if (attempt.error() != null) {
                    studentErrorLabel.setText(attempt.error().getMessage());
                } else if (attempt.studentId() != null) {
                    Session.loginAsStudent(attempt.studentId());
                    Main.showStudentDashboard();
                } else {
                    studentErrorLabel.setText("Incorrect email or password.");
                }
            });
        });
    }

    /**
     * Outcome of one background authentication attempt: either the credentials
     * matched (verified / studentId) or the request itself failed (error).
     * Built by the pool worker so the JavaFX callback only has to read it -
     * an immutable record is safe to hand from one thread to the other.
     */
    private record AuthAttempt(boolean verified, String studentId, JsonBinException error) {

        static AuthAttempt admin(String username, String password) {
            try {
                return new AuthAttempt(JsonAuthService.verifyAdmin(username, password), null, null);
            } catch (JsonBinException e) {
                return new AuthAttempt(false, null, e);
            }
        }

        static AuthAttempt student(String email, String password) {
            try {
                return new AuthAttempt(false, JsonAuthService.verifyStudent(email, password), null);
            } catch (JsonBinException e) {
                return new AuthAttempt(false, null, e);
            }
        }
    }
}
