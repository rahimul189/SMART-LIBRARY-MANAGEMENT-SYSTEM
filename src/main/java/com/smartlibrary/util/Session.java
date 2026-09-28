package com.smartlibrary.util;

/**
 * Holds simple "who is logged in right now" state so that after
 * switching from the Login screen to a dashboard, the dashboard
 * controller knows which student (or that an admin) is logged in.
 * Cleared on logout.
 */
public class Session {

    private static String currentStudentId;

    public static void loginAsStudent(String studentId) {
        currentStudentId = studentId;
    }

    public static void loginAsAdmin() {
        currentStudentId = null;
    }

    public static String getCurrentStudentId() {
        return currentStudentId;
    }

    public static void clear() {
        currentStudentId = null;
    }
}
