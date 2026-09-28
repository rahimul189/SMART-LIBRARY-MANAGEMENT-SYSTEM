package com.smartlibrary;

import com.smartlibrary.concurrency.AppExecutors;
import com.smartlibrary.concurrency.RefreshQueue;
import com.smartlibrary.database.DataSeeder;
import com.smartlibrary.database.SQLiteConnection;
import com.smartlibrary.json.JsonAuthService;
import com.smartlibrary.util.ProfileImageService;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Main extends Application {

    /** Default window size of both dashboards - "Reset View" returns here. */
    public static final int DASHBOARD_WIDTH = 960;
    public static final int DASHBOARD_HEIGHT = 580;

    private static Stage primaryStage;

    @Override
    public void start(Stage stage) {
        primaryStage = stage;
        primaryStage.setTitle("SmartLibrary");

        // One-time setup: create the DB/tables if needed (never deleted or
        // recreated), seed sample data, and check the online JSONBin.io auth
        // configuration. All of it is idempotent - safe on every start.
        SQLiteConnection.initializeDatabase();
        DataSeeder.seedIfEmpty();
        JsonAuthService.initialize();
        ProfileImageService.initializeIfMissing();

        showLogin();
    }

    /**
     * Called by JavaFX when the window closes. Lab 2's graceful-shutdown
     * pattern: stop accepting new work first and let the accepted tasks
     * finish, so no database read is cut in half while the app exits.
     */
    @Override
    public void stop() {
        AppExecutors.shutdown();
        RefreshQueue.shutdown();
    }

    public static void showLogin() {
        loadScene("/com/smartlibrary/fxml/login.fxml", 760, 560, true);
    }

    public static void showAdminDashboard() {
        loadScene("/com/smartlibrary/fxml/admin-dashboard.fxml",
                DASHBOARD_WIDTH, DASHBOARD_HEIGHT, true);
    }

    public static void showStudentDashboard() {
        loadScene("/com/smartlibrary/fxml/student-dashboard.fxml",
                DASHBOARD_WIDTH, DASHBOARD_HEIGHT, true);
    }

    private static void loadScene(String fxmlPath, int width, int height, boolean resizable) {
        try {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource(fxmlPath));
            Parent root = loader.load();
            Scene scene = new Scene(root, width, height);
            scene.getStylesheets().add(Main.class.getResource("/com/smartlibrary/css/style.css").toExternalForm());
            primaryStage.setScene(scene);
            primaryStage.setResizable(resizable);
            // Smallest window in which every screen still lays out without
            // clipping: the admin sidebar needs ~460px of height (avatar,
            // welcome block, five nav buttons, pinned logout) and the Issue
            // Book card ~485px. Scene height = window height - decorations,
            // so 560 leaves comfortable slack for both.
            primaryStage.setMinWidth(resizable ? 720 : width);
            primaryStage.setMinHeight(resizable ? 560 : height);
            primaryStage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
