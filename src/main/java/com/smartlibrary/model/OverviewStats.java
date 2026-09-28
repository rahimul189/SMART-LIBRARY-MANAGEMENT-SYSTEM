package com.smartlibrary.model;

import java.util.List;

/**
 * Immutable snapshot of everything the admin Dashboard shows.
 *
 * A Java record: the constructor, getters (totalBooks(), departments(), ...),
 * equals()/hashCode()/toString() and the field declarations all come from
 * the type itself, and the compiler guarantees the state cannot change after
 * the coordinator task has built it off the JavaFX thread.
 *
 * @param totalBooks      total books in the library
 * @param availableBooks  books whose status is AVAILABLE
 * @param totalMembers    registered members
 * @param issuedBooks     books currently borrowed
 * @param membersBorrowing members who have at least one book out right now
 * @param departments     department names for the filter combo box
 * @param categories      category names for the filter combo box
 */
public record OverviewStats(int totalBooks,
                            int availableBooks,
                            int totalMembers,
                            int issuedBooks,
                            int membersBorrowing,
                            List<String> departments,
                            List<String> categories) {
}
