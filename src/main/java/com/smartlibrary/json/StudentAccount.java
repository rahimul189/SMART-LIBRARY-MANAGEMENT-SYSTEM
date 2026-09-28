package com.smartlibrary.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One student login inside {@link AuthDocument}.
 *
 * studentId is the application level identifier that must stay identical to
 * members.student_id in the local SQLite database - that shared value is how
 * a login on JSONBin.io is tied to a profile row in SQLite. It is deliberately
 * NOT a SQLite foreign key: SQLite cannot (and must not) reference online data.
 *
 *   { "studentId": "2025001", "email": "rahim@gmail.com", "password": "1234" }
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class StudentAccount {

    private String studentId;
    private String email;
    private String password;

    public StudentAccount() {
        // needed by Jackson
    }

    public StudentAccount(String studentId, String email, String password) {
        this.studentId = studentId;
        this.email = email;
        this.password = password;
    }

    public String getStudentId() {
        return studentId;
    }

    public void setStudentId(String studentId) {
        this.studentId = studentId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
