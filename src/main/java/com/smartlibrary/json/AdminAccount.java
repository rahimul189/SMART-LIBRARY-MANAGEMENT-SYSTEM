package com.smartlibrary.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One admin account inside {@link AuthDocument}.
 *
 * Mirrors the "admins" entry that already existed in the project's
 * authentication JSON, so the same document can be pasted into a JSONBin.io
 * bin unchanged:
 *
 *   { "username": "admin", "password": "1234" }
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AdminAccount {

    private String username;
    private String password;

    public AdminAccount() {
        // needed by Jackson
    }

    public AdminAccount(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
