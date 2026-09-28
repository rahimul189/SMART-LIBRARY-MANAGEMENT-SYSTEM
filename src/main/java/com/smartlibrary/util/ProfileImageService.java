package com.smartlibrary.util;

import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Stores the profile pictures picked with the sidebar's "Choose File" button
 * (Student panel and Admin panel).
 *
 * It deliberately follows the two patterns this project already uses:
 *  - like BookController's cover images, the chosen file is copied into a
 *    folder inside the project (avatars/) and only the path is remembered;
 *  - like the rest of the project's local state, those paths live in a small
 *    JSON file (profile-images.json), so no database change is needed.
 *    (Authentication is different: it lives online in JSONBin.io - only the
 *    avatar paths stay local.)
 *
 * Shape of the file:
 * {
 *   "admin": "avatars/1700000000000.png",
 *   "student-2025001": "avatars/1700000000001.jpg"
 * }
 */
public final class ProfileImageService {

    /** Key used for the (single) admin account. */
    public static final String ADMIN_KEY = "admin";

    private static final String FILE_PATH = "profile-images.json";
    private static final String AVATARS_DIR = "avatars";

    /**
     * Critical-section locks. Choosing a picture is a slow disk job, so it
     * runs on the AppExecutors pool while the JavaFX thread keeps painting -
     * which means these two pieces of shared state really can be hit by two
     * threads at once (Lab 2 Task 5/6: shared mutable data).
     *
     * FILE_LOCK guards the read-modify-write of profile-images.json, so two
     * updates can never overwrite each other. COPY_LOCK only serialises the
     * copy into avatars/ so two stores cannot pick the same timestamped
     * file name; it stays separate on purpose - a slow copy must never make
     * the JavaFX thread wait for FILE_LOCK.
     */
    private static final Object FILE_LOCK = new Object();
    private static final Object COPY_LOCK = new Object();
    /** Longest edge kept in memory - far more than the 72px circle needs. */
    private static final double MAX_IMAGE_DIMENSION = 1024;

    private ProfileImageService() {
    }

    /** Key used for a student account, e.g. "student-2025001". */
    public static String studentKey(String studentId) {
        return "student-" + studentId;
    }

    /** Creates profile-images.json if it does not exist yet. Safe to call on every start. */
    public static void initializeIfMissing() {
        if (!Files.exists(Path.of(FILE_PATH))) {
            save(new JSONObject());
        }
    }

    /** Copies the picked image into avatars/ and returns the path to store. */
    public static String store(File chosen) throws IOException {
        synchronized (COPY_LOCK) {
            Path dir = Path.of(AVATARS_DIR);
            Files.createDirectories(dir);

            String originalName = chosen.getName();
            String extension = "";
            int dot = originalName.lastIndexOf('.');
            if (dot >= 0) extension = originalName.substring(dot);

            Path target = dir.resolve(System.currentTimeMillis() + extension);
            Files.copy(chosen.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
            return target.toString();
        }
    }

    /** Returns the stored avatar path for the given key, or null if there is none. */
    public static String getAvatarPath(String key) {
        synchronized (FILE_LOCK) {
            return load().optString(key, null);
        }
    }

    /** Remembers the avatar path for the given key (admin or a student). */
    public static void saveAvatarPath(String key, String path) {
        synchronized (FILE_LOCK) {
            JSONObject root = load();
            root.put(key, path);
            save(root);
        }
    }

    /**
     * Loads the stored image, downscaled and center-cropped to a square so it
     * fills the circular profile area without distortion. Returns null when
     * there is no usable image, so callers can keep the placeholder circle.
     */
    public static Image loadSquareImage(String path) {
        if (path == null || path.isBlank()) return null;

        File file = new File(path);
        if (!file.exists()) return null;

        try {
            Image image = new Image(file.toURI().toString(),
                    MAX_IMAGE_DIMENSION, MAX_IMAGE_DIMENSION, true, false);
            return toSquare(image);
        } catch (Exception e) {
            System.err.println("Failed to load profile image " + path + ": " + e.getMessage());
            return null;
        }
    }

    /** Center-crops any image to a square (no scaling, no distortion). */
    private static Image toSquare(Image image) {
        double width = image.getWidth();
        double height = image.getHeight();
        PixelReader reader = image.getPixelReader();
        if (reader == null || width < 1 || height < 1) return image;

        int side = (int) Math.min(width, height);
        int x = (int) ((width - side) / 2);
        int y = (int) ((height - side) / 2);
        return new WritableImage(reader, x, y, side, side);
    }

    // ---- internal helpers (same shape as JsonAuthService) ----

    private static JSONObject load() {
        try {
            String content = Files.readString(Path.of(FILE_PATH));
            return new JSONObject(content);
        } catch (Exception e) {
            // Missing or unreadable file - start from an empty structure so callers don't crash.
            return new JSONObject();
        }
    }

    private static void save(JSONObject root) {
        try (FileWriter writer = new FileWriter(FILE_PATH)) {
            writer.write(root.toString(2)); // pretty-printed with 2-space indent
        } catch (IOException e) {
            System.err.println("Failed to write profile-images.json:");
            e.printStackTrace();
        }
    }
}
