package com.smartlibrary.data;

import com.smartlibrary.database.SQLiteConnection;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Abstract base class implementing {@link LibraryRepository} with the
 * Template Method pattern: the whole "open connection, run SELECT, close
 * everything" sequence is written once and marked final, while the two
 * steps that differ per table are left abstract:
 *
 *   - selectAllSql()  which rows to read   (subclasses may also override it)
 *   - mapRow(rs)      how to build the object
 *
 * Subclasses therefore contain only their own SQL and their own mapping -
 * no connection handling is ever duplicated, and no subclass can break it.
 *
 * @param <T> the domain object this repository produces
 */
public abstract class JdbcRepository<T> implements LibraryRepository<T> {

    /** Which rows this repository reads - decided by the subclass. */
    protected abstract String selectAllSql();

    /** Turns a single ResultSet row into a domain object - decided by the subclass. */
    protected abstract T mapRow(ResultSet rs) throws SQLException;

    /**
     * Final: subclasses inherit the connection handling as-is. This is the
     * polymorphic entry point - a LibraryRepository reference calls whichever
     * subclass's mapRow()/selectAllSql() is really behind it.
     */
    @Override
    public final List<T> findAll() throws SQLException {
        List<T> rows = new ArrayList<>();

        try (Connection conn = SQLiteConnection.connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(selectAllSql())) {

            while (rs.next()) {
                rows.add(mapRow(rs));
            }
        }
        return rows;
    }
}
