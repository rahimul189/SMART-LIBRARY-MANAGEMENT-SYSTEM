package com.smartlibrary.json;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

/**
 * Configuration for the JSONBin.io HTTP client.
 *
 * Read once per request from jsonbin.properties in the working directory
 * (next to library.db), with these overrides so credentials never have to
 * be hard-coded:
 *
 *   1. system property   -Djsonbin.api.key=... / -Djsonbin.bin.id=...
 *   2. environment var   JSONBIN_API_KEY      / JSONBIN_BIN_ID
 *   3. jsonbin.properties api.key             / bin.id
 *
 * The file only carries credentials - the authentication data itself is
 * always fetched from JSONBin.io over HTTP, never from a local file.
 */
public final class JsonBinConfig {

    private static final String PROPERTIES_FILE = "jsonbin.properties";

    private static final String DEFAULT_BASE_URL = "https://api.jsonbin.io";

    private final String apiKey;
    private final String binId;
    private final String baseUrl;

    private JsonBinConfig(String apiKey, String binId, String baseUrl) {
        this.apiKey = apiKey;
        this.binId = binId;
        this.baseUrl = baseUrl;
    }

    /** Reloads the configuration (system properties > environment > file). */
    public static JsonBinConfig load() {
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(Path.of(PROPERTIES_FILE))) {
            properties.load(in);
        } catch (IOException e) {
            // Missing file: the overrides below may still supply the values,
            // and isConfigured() reports the problem if they do not.
        }

        String apiKey = firstNonBlank(
                System.getProperty("jsonbin.api.key"),
                System.getenv("JSONBIN_API_KEY"),
                properties.getProperty("api.key"));
        String binId = firstNonBlank(
                System.getProperty("jsonbin.bin.id"),
                System.getenv("JSONBIN_BIN_ID"),
                properties.getProperty("bin.id"));
        String baseUrl = firstNonBlank(
                System.getProperty("jsonbin.base.url"),
                System.getenv("JSONBIN_BASE_URL"),
                properties.getProperty("base.url"),
                DEFAULT_BASE_URL);

        return new JsonBinConfig(apiKey, binId, stripTrailingSlash(baseUrl));
    }

    public String apiKey() {
        return apiKey;
    }

    public String binId() {
        return binId;
    }

    public String baseUrl() {
        return baseUrl;
    }

    /** True when a real key and bin id are present (not the shipped placeholders). */
    public boolean isConfigured() {
        return isRealValue(apiKey) && isRealValue(binId);
    }

    /** The message shown / logged when the app is still unconfigured. */
    public String notConfiguredMessage() {
        return "JSONBin.io is not configured: set api.key and bin.id in "
                + PROPERTIES_FILE + " (or in the JSONBIN_API_KEY / JSONBIN_BIN_ID "
                + "environment variables).";
    }

    // ---- helpers ----

    /**
     * The placeholders that {@link #isRealValue(String)} refuses to accept,
     * shortest first. Anything listed here is treated as "still unconfigured"
     * so it can never be sent to JSONBin.io as a credential.
     *
     * <p>Both the bare {@code YOUR_JSONBIN_...} form and the {@code $}-prefixed
     * form used by {@code jsonbin.properties.example} are covered - checking
     * only the bare form would have let the shipped {@code $YOUR_JSONBIN_...}
     * through as if it were a real key.
     */
    private static final String[] PLACEHOLDERS = {
            "YOUR_JSONBIN",
            "$YOUR_JSONBIN",
            "YOUR_BIN",
            "$YOUR_BIN",
            "YOUR_API",
            "$YOUR_API"
    };

    private static boolean isRealValue(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String upper = value.trim().toUpperCase(Locale.ROOT);
        for (String placeholder : PLACEHOLDERS) {
            if (upper.startsWith(placeholder)) {
                return false;
            }
        }
        return true;
    }

    private static String stripTrailingSlash(String url) {
        String trimmed = url.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}
