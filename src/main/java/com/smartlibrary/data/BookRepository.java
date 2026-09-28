package com.smartlibrary.data;

import com.smartlibrary.model.Book;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Reads the "books" table (every book, ordered by title) into Book objects.
 * Only the two abstract steps of {@link JdbcRepository} are supplied here.
 */
public class BookRepository extends JdbcRepository<Book> {

    @Override
    protected String selectAllSql() {
        return "SELECT id, title, author, category, isbn, description, cover_path, status "
                + "FROM books ORDER BY title";
    }

    @Override
    protected Book mapRow(ResultSet rs) throws SQLException {
        return new Book(
                rs.getInt("id"),
                rs.getString("title"),
                rs.getString("author"),
                rs.getString("category"),
                rs.getString("isbn"),
                rs.getString("description"),
                rs.getString("cover_path"),
                rs.getString("status")
        );
    }
}
