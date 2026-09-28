package com.smartlibrary.database;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * A single database transaction, closed by try-with-resources.
 *
 * Issue Book and Return Book both change two tables at once (the borrow record
 * and the book's status) and must either apply completely or not at all. That
 * was previously written out by hand in each controller, with a
 * {@code rollbackQuietly} and a {@code closeQuietly} helper duplicated
 * between the two of them.
 *
 * Using it:
 * <pre>{@code
 * try (Transactions tx = Transactions.begin()) {
 *     tx.run("UPDATE books SET status = ? WHERE id = ?", "AVAILABLE", bookId);
 *     tx.run("UPDATE borrow_records SET status = 'RETURNED' WHERE id = ?", recordId);
 *     tx.commit();
 * } // anything not committed is rolled back here
 * }</pre>
 *
 * Any SQLException thrown inside the block rolls the transaction back, so a
 * half-finished issue can never leave a book marked BORROWED with no borrow
 * record pointing at it.
 */
public final class Transactions implements AutoCloseable {

    private final Connection connection;
    private boolean committed;

    private Transactions(Connection connection) {
        this.connection = connection;
    }

    /**
     * Opens a connection and turns off auto-commit, so the statements below
     * only become visible together on {@link #commit()}.
     */
    public static Transactions begin() throws SQLException {
        Connection connection = SQLiteConnection.connect();
        connection.setAutoCommit(false);
        return new Transactions(connection);
    }

    /**
     * Runs one statement that changes nothing.
     *
     * @return the first column of the first row, or 0 when there is no row
     */
    public int queryInt(String sql, Object... parameters) throws SQLException {
        try (var ps = connection.prepareStatement(sql)) {
            bind(ps, parameters);
            try (var rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /**
     * Runs one statement that changes rows.
     *
     * @return the number of affected rows
     */
    public int update(String sql, Object... parameters) throws SQLException {
        try (var ps = connection.prepareStatement(sql)) {
            bind(ps, parameters);
            return ps.executeUpdate();
        }
    }

    /** Makes every statement so far permanent. */
    public void commit() throws SQLException {
        connection.commit();
        committed = true;
    }

    /** Makes the changes permanent and releases the connection early. */
    public void closeAndCommit() throws SQLException {
        commit();
        close();
    }

    @Override
    public void close() {
        if (!committed) {
            try {
                connection.rollback();
            } catch (SQLException e) {
                // The block already failed; the original error is the one that
                // matters, so this is deliberately not rethrown.
                System.err.println("Rollback failed: " + e.getMessage());
            }
        }
        try {
            connection.setAutoCommit(true);
            connection.close();
        } catch (SQLException e) {
            // best effort - the connection is being discarded anyway
            System.err.println("Failed to close the transaction connection: " + e.getMessage());
        }
    }

    /** Fills the ? placeholders of a prepared statement. */
    private static void bind(java.sql.PreparedStatement ps, Object[] parameters)
            throws SQLException {
        if (parameters == null) {
            return;
        }
        for (int i = 0; i < parameters.length; i++) {
            ps.setObject(i + 1, parameters[i]);
        }
    }
}
