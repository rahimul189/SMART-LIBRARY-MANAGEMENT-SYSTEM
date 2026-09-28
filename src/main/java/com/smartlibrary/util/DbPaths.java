package com.smartlibrary.util;

import java.nio.file.Path;

/**
 * Where library.db lives.
 *
 * The application always uses the plain relative name, which puts the file next
 * to the pom.xml. The indirection exists so the tests can point the connection
 * at a temporary file: without it, running the test suite would open and
 * migrate the developer's real library.db.
 *
 * <p>Production code never calls the override.
 */
public final class DbPaths {

    /** The database file, relative to the working directory. */
    public static final String DEFAULT_DATABASE = "library.db";

    private static volatile String override;

    private DbPaths() {
        // static helper
    }

    /**
     * The JDBC URL for the database. Returns the test override when one is set,
     * otherwise the normal relative file.
     */
    public static String jdbcUrl() {
        String custom = override;
        return custom != null ? "jdbc:sqlite:" + custom : "jdbc:sqlite:" + DEFAULT_DATABASE;
    }

    /**
     * Points the application at a different database file, or restores the
     * default when given null.
     *
     * <p>Only the test suite calls this - it deliberately has no guard against
     * accidental use in application code, because hiding a legitimate override
     * would be worse than a clear call site.
     *
     * @param path the file to use, or null to go back to library.db
     */
    public static void useForTests(Path path) {
        override = path == null ? null : path.toString();
    }
}
