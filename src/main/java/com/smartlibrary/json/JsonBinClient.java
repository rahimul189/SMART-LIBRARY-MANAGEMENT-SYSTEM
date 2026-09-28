package com.smartlibrary.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Thin HTTP client for JSONBin.io's v3 REST API, built on
 * {@link java.net.http.HttpClient} (Java 11+) with {@link ObjectMapper}
 * from Jackson doing all JSON work:
 *
 *   JavaFX -> HttpRequest -> JSONBin.io -> HttpResponse
 *          -> Jackson -> Java objects ({@link AuthDocument})
 *
 * and, for a registration update:
 *
 *   Java objects -> Jackson -> JSON body -> HttpRequest -> JSONBin.io
 *
 *   GET  {base}/v3/b/{binId}/latest   read the bin (X-Master-Key header)
 *   PUT  {base}/v3/b/{binId}          replace the bin with the given document
 *
 * Every failure - unreachable host, non-2xx status, malformed JSON - is
 * reported as a {@link JsonBinException} with a message that is safe to show
 * in the UI; nothing here ever throws raw IOExceptions at the controllers.
 */
public class JsonBinClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

    /** Longest error body echoed back to the user - enough to see what went wrong. */
    private static final int MAX_ERROR_BODY = 300;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient;

    public JsonBinClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * Fetches the authentication document from JSONBin.io and parses the
     * response into Java objects.
     */
    public AuthDocument fetchAuthDocument() throws JsonBinException {
        JsonBinConfig config = requireConfiguration();

        HttpRequest request = HttpRequest.newBuilder(
                        URI.create(config.baseUrl() + "/v3/b/" + config.binId() + "/latest"))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("X-Master-Key", config.apiKey())
                .GET()
                .build();

        String body = send(request, "GET");
        JsonNode root = parse(body, "GET");

        // JSONBin wraps the stored document in { "record": ..., "metadata": ... }.
        JsonNode record = root != null && root.has("record") ? root.get("record") : root;
        if (record == null || !record.isObject()) {
            throw new JsonBinException("JSONBin.io answered with a response that has no JSON object record.");
        }

        try {
            AuthDocument document = mapper.treeToValue(record, AuthDocument.class);
            document.normalize();
            return document;
        } catch (JsonProcessingException e) {
            throw new JsonBinException("JSONBin.io returned JSON that does not match the "
                    + "authentication structure: " + e.getOriginalMessage(), e);
        }
    }

    /**
     * Serializes the given document with Jackson and writes it back to
     * JSONBin.io (registration / any update of the online JSON).
     */
    public void saveAuthDocument(AuthDocument document) throws JsonBinException {
        JsonBinConfig config = requireConfiguration();

        String payload;
        try {
            payload = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(document);
        } catch (JsonProcessingException e) {
            throw new JsonBinException("Could not serialize the authentication data to JSON: "
                    + e.getOriginalMessage(), e);
        }

        HttpRequest request = HttpRequest.newBuilder(
                        URI.create(config.baseUrl() + "/v3/b/" + config.binId()))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("X-Master-Key", config.apiKey())
                .PUT(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();

        String body = send(request, "PUT");

        // JSONBin answers PUT with { "success": true, "meta": {...} }.
        if (body != null && !body.isBlank()) {
            JsonNode root = parse(body, "PUT");
            if (root != null && root.has("success") && !root.get("success").asBoolean()) {
                throw new JsonBinException("JSONBin.io rejected the update: " + abbreviate(body));
            }
        }
    }

    // ---- internals ----

    private JsonBinConfig requireConfiguration() throws JsonBinException {
        JsonBinConfig config = JsonBinConfig.load();
        if (!config.isConfigured()) {
            throw new JsonBinException(config.notConfiguredMessage());
        }
        return config;
    }

    /** Runs one request and returns the body, or throws a user-readable error. */
    private String send(HttpRequest request, String method) throws JsonBinException {
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new JsonBinException("Cannot reach JSONBin.io ("
                    + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName())
                    + "). Check the internet connection and try again.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new JsonBinException("The request to JSONBin.io was interrupted.", e);
        }

        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            String hint;
            if (status == 401 || status == 403) {
                hint = " - check api.key in jsonbin.properties";
            } else if (status == 404) {
                hint = " - check bin.id in jsonbin.properties";
            } else {
                hint = "";
            }
            throw new JsonBinException(method + " JSONBin.io failed: HTTP " + status + hint
                    + (response.body() == null || response.body().isBlank()
                    ? "" : " - " + abbreviate(response.body())));
        }
        return response.body();
    }

    /** Parses a body, turning Jackson's syntax error into a readable message. */
    private JsonNode parse(String body, String method) throws JsonBinException {
        try {
            return mapper.readTree(body);
        } catch (JsonProcessingException e) {
            throw new JsonBinException(method + " JSONBin.io returned invalid JSON: "
                    + e.getOriginalMessage(), e);
        }
    }

    private static String abbreviate(String text) {
        String singleLine = text.replaceAll("\\s+", " ").trim();
        return singleLine.length() <= MAX_ERROR_BODY
                ? singleLine
                : singleLine.substring(0, MAX_ERROR_BODY) + "...";
    }
}
