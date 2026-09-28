package com.smartlibrary.data;

/**
 * Only the books that are on the shelf.
 *
 * A third level of the hierarchy: it reuses BookRepository's mapping
 * unchanged and overrides just the SQL - inheritance + method overriding,
 * with the final findAll() dispatching to it at runtime (polymorphism).
 */
public class AvailableBookRepository extends BookRepository {

    @Override
    protected String selectAllSql() {
        return "SELECT id, title, author, category, isbn, description, cover_path, status "
                + "FROM books WHERE status = 'AVAILABLE' ORDER BY title";
    }
}
