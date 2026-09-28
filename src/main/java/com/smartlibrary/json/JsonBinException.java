package com.smartlibrary.json;

/**
 * Raised whenever the online authentication store (JSONBin.io) cannot be
 * used: missing configuration, no network, a non-2xx HTTP status, or a
 * response body that is not valid JSON.
 *
 * Callers translate it into the message the user sees - a wrong password is
 * *not* an exception, it simply makes verifyAdmin()/verifyStudent() return
 * false/null.
 */
public class JsonBinException extends Exception {

    public JsonBinException(String message) {
        super(message);
    }

    public JsonBinException(String message, Throwable cause) {
        super(message, cause);
    }
}
