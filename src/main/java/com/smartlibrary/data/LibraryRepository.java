package com.smartlibrary.data;

import java.sql.SQLException;
import java.util.List;

/**
 * Advanced OOP layer: a generic contract for "something that can read rows
 * of one kind out of the database".
 *
 * The type parameter T is the domain object this repository returns
 * (Book, Member, ...), so callers get type-safe results without casting.
 *
 * Controllers hold references to this interface, never to a concrete class,
 * which is what makes them polymorphic: any LibraryRepository implementation
 * can be swapped in without touching the calling code.
 *
 * @param <T> the domain object this repository produces
 */
public interface LibraryRepository<T> {

    /**
     * Reads every row this repository is responsible for.
     *
     * @return the rows, already mapped to domain objects
     * @throws SQLException when the query cannot be executed
     */
    List<T> findAll() throws SQLException;
}
