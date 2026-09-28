package com.smartlibrary.json;

/**
 * Handles the "online" authentication data for both Admin and Student
 * logins against JSONBin.io.
 *
 * Everything is fetched over HTTP - there is no local authentication file
 * any more:
 *
 *   JavaFX -> HttpRequest -> JSONBin.io -> HttpResponse -> Jackson -> objects
 *
 * verifyAdmin()/verifyStudent() GET the bin and compare the credentials;
 * registerStudent() GETs the bin, adds the student and PUTs the whole
 * document back so the online JSON stays the single source of truth;
 * changeStudentPassword() GETs it, checks the current password and PUTs the
 * document back with the new one - the same read-modify-write, guarded by
 * AUTH_LOCK so a concurrent registration or password change cannot be lost.
 *
 * The studentId returned by verifyStudent() is the same string stored in
 * members.student_id in SQLite - that shared, application-level identifier
 * is what links an online login to the local member profile (no SQLite
 * foreign key points at anything online).
 *
 * The bin holds this document (the shape the project used before, so
 * existing contents can be pasted straight into a bin):
 * {
 *   "admins":   [ { "username": "...", "password": "..." }, ... ],
 *   "students": [ { "studentId": "...", "email": "...", "password": "..." }, ... ]
 * }
 *
 * Connection details live in jsonbin.properties next to library.db.
 */
public final class JsonAuthService {

    private static final JsonBinClient CLIENT = new JsonBinClient();

    /**
     * Guards the read-modify-write of the bin. registerStudent() and
     * changeStudentPassword() both read the whole document, change one entry
     * and write it back - if two of those ran at once, one change could
     * silently disappear (lost update). One monitor makes each
     * check-and-change a single critical section.
     */
    private static final Object AUTH_LOCK = new Object();

    private JsonAuthService() {
    }

    /**
     * Startup check: reports whether jsonbin.properties carries real
     * credentials. Deliberately does no network I/O, so opening the app is
     * never blocked - the first real request happens when somebody logs in.
     */
    public static void initialize() {
        JsonBinConfig config = JsonBinConfig.load();
        if (config.isConfigured()) {
            System.out.println("Authentication service: JSONBin.io bin " + config.binId()
                    + " (" + config.baseUrl() + ").");
        } else {
            System.err.println(config.notConfiguredMessage());
            System.err.println("Until then, logins will report that the online authentication "
                    + "service cannot be reached.");
        }
    }

    /**
     * Returns true if the given username/password matches an admin entry in
     * the online JSON.
     *
     * @throws JsonBinException when JSONBin.io cannot be reached or answered
     *                          with something that is not valid JSON
     */
    public static boolean verifyAdmin(String username, String password) throws JsonBinException {
        if (username == null || password == null) {
            return false;
        }

        AuthDocument document = CLIENT.fetchAuthDocument();
        for (AdminAccount admin : document.getAdmins()) {
            if (username.equals(admin.getUsername()) && password.equals(admin.getPassword())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks a student's email/password against the online JSON.
     * Returns the matching studentId on success, or null if there's no match.
     * The caller then uses that studentId to look up the full profile in SQLite.
     *
     * @throws JsonBinException when JSONBin.io cannot be reached or answered
     *                          with something that is not valid JSON
     */
    public static String verifyStudent(String email, String password) throws JsonBinException {
        if (email == null || password == null) {
            return null;
        }

        AuthDocument document = CLIENT.fetchAuthDocument();
        for (StudentAccount student : document.getStudents()) {
            if (email.equals(student.getEmail()) && password.equals(student.getPassword())) {
                return student.getStudentId();
            }
        }
        return null;
    }

    /**
     * Adds a new student's login credentials to the online JSON with a
     * JSONBin.io update request (GET -&gt; append -&gt; PUT, the body serialized
     * by Jackson).
     *
     * Returns false (and updates nothing) if the studentId or email is
     * already registered.
     *
     * @throws JsonBinException when JSONBin.io cannot be reached, the update
     *                          is rejected, or the response is not valid JSON
     */
    public static boolean registerStudent(String studentId, String email, String password)
            throws JsonBinException {
        synchronized (AUTH_LOCK) {
            AuthDocument document = CLIENT.fetchAuthDocument();
            for (StudentAccount student : document.getStudents()) {
                if (studentId.equals(student.getStudentId()) || email.equals(student.getEmail())) {
                    return false; // already registered
                }
            }

            document.getStudents().add(new StudentAccount(studentId, email, password));
            CLIENT.saveAuthDocument(document);
            return true;
        }
    }

    /** Why a password change did or did not reach the online JSON. */
    public enum PasswordChange {
        /** The new password was checked and stored (a PUT was issued). */
        SUCCESS,
        /** The student exists but the current password does not match - nothing written. */
        WRONG_PASSWORD,
        /** No student with that studentId is in the online document - nothing written. */
        NO_ACCOUNT
    }

    /**
     * Replaces a student's password in the online JSON, but only after the
     * current one has been checked against the stored value:
     *
     *   GET the document -> find studentId -> compare current password
     *   -> setPassword(new) -> PUT the whole document back
     *
     * The whole sequence runs inside AUTH_LOCK (see above), so a registration
     * or a second password change happening at the same time cannot overwrite
     * this update, and this update cannot drop theirs.
     *
     * Nothing is written on WRONG_PASSWORD or NO_ACCOUNT - the caller uses
     * that to keep the dialog open with an explanation.
     *
     * @throws JsonBinException when JSONBin.io cannot be reached, the update
     *                          is rejected, or the response is not valid JSON
     */
    public static PasswordChange changeStudentPassword(String studentId, String currentPassword,
                                                       String newPassword) throws JsonBinException {
        if (studentId == null) {
            return PasswordChange.NO_ACCOUNT; // nothing to look up
        }
        if (currentPassword == null || newPassword == null || newPassword.isEmpty()) {
            return PasswordChange.WRONG_PASSWORD; // incomplete input, nothing written
        }

        synchronized (AUTH_LOCK) {
            AuthDocument document = CLIENT.fetchAuthDocument();
            for (StudentAccount student : document.getStudents()) {
                if (studentId.equals(student.getStudentId())) {
                    if (!currentPassword.equals(student.getPassword())) {
                        return PasswordChange.WRONG_PASSWORD;
                    }
                    student.setPassword(newPassword);
                    CLIENT.saveAuthDocument(document);
                    return PasswordChange.SUCCESS;
                }
            }
            return PasswordChange.NO_ACCOUNT;
        }
    }
}
