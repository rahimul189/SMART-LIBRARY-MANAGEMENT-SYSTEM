package com.smartlibrary.json;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("JsonBinConfig - where the credentials come from")
class JsonBinConfigTest {

    private static final String KEY = "jsonbin.api.key";
    private static final String BIN = "jsonbin.bin.id";
    private static final String URL = "jsonbin.base.url";

    @AfterEach
    void clearSystemProperties() {
        System.clearProperty(KEY);
        System.clearProperty(BIN);
        System.clearProperty(URL);
    }

    @Test
    @DisplayName("system properties are the highest precedence")
    void systemPropertiesWin() {
        System.setProperty(KEY, "from-system");
        System.setProperty(BIN, "bin-from-system");
        System.setProperty(URL, "https://system.example");

        JsonBinConfig config = JsonBinConfig.load();

        assertEquals("from-system", config.apiKey());
        assertEquals("bin-from-system", config.binId());
        assertEquals("https://system.example", config.baseUrl());
    }

    @Test
    @DisplayName("a real key and bin id count as configured")
    void realValuesAreConfigured() {
        System.setProperty(KEY, "a-real-master-key");
        System.setProperty(BIN, "a-real-bin-id");

        assertTrue(JsonBinConfig.load().isConfigured());
    }

    @Test
    @DisplayName("the shipped placeholders are never treated as a real credential")
    void placeholdersAreNotRealValues() {
        // The exact text the example file ships with. If this ever passed,
        // the app would send YOUR_JSONBIN_MASTER_KEY to JSONBin.io.
        System.setProperty(KEY, "$YOUR_JSONBIN_MASTER_KEY");
        System.setProperty(BIN, "$YOUR_BIN_ID");

        JsonBinConfig config = JsonBinConfig.load();

        assertFalse(config.isConfigured());
        assertTrue(config.notConfiguredMessage().contains("jsonbin.properties"));
    }

    @Test
    @DisplayName("a placeholder for either value is enough to be unconfigured")
    void onePlaceholderDisablesTheConfig() {
        System.setProperty(KEY, "$YOUR_JSONBIN_MASTER_KEY");
        System.setProperty(BIN, "a-real-bin-id");
        assertFalse(JsonBinConfig.load().isConfigured());

        System.clearProperty(KEY);
        System.setProperty(BIN, "$YOUR_BIN_ID");
        assertFalse(JsonBinConfig.load().isConfigured());
    }

    @Test
    @DisplayName("missing values leave the config unconfigured rather than null")
    void missingValuesAreSimplyUnconfigured() {
        JsonBinConfig config = JsonBinConfig.load();

        // The properties file may or may not exist in the working directory,
        // so only the outcome is asserted: the config is never half-usable.
        if (config.apiKey() == null || config.binId() == null) {
            assertFalse(config.isConfigured());
        } else {
            assertTrue(config.isConfigured());
        }
    }

    @Test
    @DisplayName("blank and whitespace-only values are ignored in favour of the next source")
    void blankValuesFallThrough() {
        System.setProperty(KEY, "   ");
        System.setProperty(BIN, "");

        JsonBinConfig config = JsonBinConfig.load();

        // A blank property must not win over a real value from a lower
        // precedence source, so it is treated as absent.
        if (config.apiKey() != null) {
            assertFalse(config.apiKey().isBlank());
        }
        if (config.binId() != null) {
            assertFalse(config.binId().isBlank());
        }
    }

    @Test
    @DisplayName("surrounding whitespace is trimmed off every value")
    void valuesAreTrimmed() {
        System.setProperty(KEY, "  spaced-key  ");
        System.setProperty(BIN, "  spaced-bin  ");

        JsonBinConfig config = JsonBinConfig.load();

        assertEquals("spaced-key", config.apiKey());
        assertEquals("spaced-bin", config.binId());
    }

    @Test
    @DisplayName("trailing slashes are stripped from the base URL")
    void trailingSlashesAreStripped() {
        System.setProperty(URL, "https://api.jsonbin.io///");

        assertEquals("https://api.jsonbin.io", JsonBinConfig.load().baseUrl());
    }

    @Test
    @DisplayName("the default base URL is the real JSONBin.io API")
    void defaultBaseUrl() {
        // No URL set anywhere: the default must point at the live API.
        assertEquals("https://api.jsonbin.io", JsonBinConfig.load().baseUrl());
    }

    @Test
    @DisplayName("the placeholder check ignores case and surrounding space")
    void placeholderCheckIsForgiving() {
        System.setProperty(KEY, "  your_jsonbin_master_key  ");

        assertFalse(JsonBinConfig.load().isConfigured(),
                "the check should not depend on the exact casing of the placeholder");
    }

    @Test
    @DisplayName("the unconfigured message names both accepted sources")
    void notConfiguredMessageIsActionable() {
        String message = JsonBinConfig.load().notConfiguredMessage();

        assertNotNull(message);
        assertTrue(message.contains("api.key"));
        assertTrue(message.contains("bin.id"));
    }

    @Test
    @DisplayName("a null message check does not blow up on a loaded config")
    void loadIsAlwaysSafe() {
        JsonBinConfig config = JsonBinConfig.load();

        assertNotNull(config);
        assertNotNull(config.baseUrl(), "baseUrl always has the default, never null");
    }

    @Test
    @DisplayName("isConfigured needs both halves, not just the key")
    void bothHalvesAreRequired() {
        System.setProperty(KEY, "a-real-master-key");
        System.clearProperty(BIN);

        JsonBinConfig config = JsonBinConfig.load();

        if (config.binId() == null) {
            assertFalse(config.isConfigured(), "a key without a bin id is not usable");
        }
    }

    @Test
    @DisplayName("only the literal shipped placeholder is rejected")
    void onlyTheLiteralPlaceholderIsRejected() {
        System.setProperty(KEY, "prefix-$YOUR_JSONBIN_MASTER_KEY-suffix");
        System.setProperty(BIN, "a-real-bin-id");

        // The guard is a startsWith check on the placeholder, so a key that
        // merely contains that text elsewhere is still the user's own value.
        assertTrue(JsonBinConfig.load().isConfigured());
    }

    @Test
    @DisplayName("both placeholder styles of both values are recognised")
    void bothPlaceholderStylesAreRecognised() {
        // jsonbin.properties.example ships "$YOUR_JSONBIN_MASTER_KEY" and
        // "$YOUR_BIN_ID", while the older local file used the bare forms.
        // Neither may be accepted as a real credential.
        String[][] placeholders = {
                {"$YOUR_JSONBIN_MASTER_KEY", "$YOUR_BIN_ID"},
                {"YOUR_JSONBIN_MASTER_KEY", "YOUR_BIN_ID"},
                {"$YOUR_JSONBIN_MASTER_KEY", "YOUR_BIN_ID"},
                {"YOUR_JSONBIN_MASTER_KEY", "$YOUR_BIN_ID"}
        };

        for (String[] pair : placeholders) {
            System.setProperty(KEY, pair[0]);
            System.setProperty(BIN, pair[1]);

            assertFalse(JsonBinConfig.load().isConfigured(),
                    "placeholders " + pair[0] + " / " + pair[1] + " must not count as real");
        }
    }
}
