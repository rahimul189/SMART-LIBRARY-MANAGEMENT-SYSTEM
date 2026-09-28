package com.smartlibrary.json;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The whole online authentication document stored in the JSONBin.io bin -
 * the Java object the JSON response is parsed into and the object that is
 * serialized back when the bin is updated (student registration).
 *
 * Shape of the bin record:
 * {
 *   "admins":   [ { "username": "...", "password": "..." }, ... ],
 *   "students": [ { "studentId": "...", "email": "...", "password": "..." }, ... ]
 * }
 *
 * The field names match the project's original authentication JSON, so
 * existing contents can be copied into a bin as-is.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AuthDocument {

    private List<AdminAccount> admins;
    private List<StudentAccount> students;

    /**
     * Anything else the bin carries (fields this app does not know about).
     * They are read back and written out again on update, so registering a
     * student never silently deletes data that was already in the bin.
     */
    private final Map<String, JsonNode> unknownFields = new HashMap<>();

    public AuthDocument() {
        // needed by Jackson
    }

    @JsonAnySetter
    public void setUnknownField(String name, JsonNode value) {
        unknownFields.put(name, value);
    }

    @JsonAnyGetter
    public Map<String, JsonNode> getUnknownFields() {
        return unknownFields;
    }

    public List<AdminAccount> getAdmins() {
        return admins;
    }

    public void setAdmins(List<AdminAccount> admins) {
        this.admins = admins;
    }

    public List<StudentAccount> getStudents() {
        return students;
    }

    public void setStudents(List<StudentAccount> students) {
        this.students = students;
    }

    /**
     * Turns null collections into empty ones, so callers can iterate without
     * null checks even when the bin holds a partial document.
     */
    public void normalize() {
        if (admins == null) {
            admins = new ArrayList<>();
        }
        if (students == null) {
            students = new ArrayList<>();
        }
    }
}
