package com.smartlibrary.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Inserts some starter data into books and members so there is
 * something to see on screen once we build the UI. Safe to call
 * every time the app starts - it only inserts if the tables are
 * currently empty, so you won't get duplicates on the next run.
 */
public class DataSeeder {

    public static void seedIfEmpty() {
        seedBooksIfEmpty();
        seedMembersIfEmpty();
    }

    private static void seedBooksIfEmpty() {
        String countSql = "SELECT COUNT(*) FROM books";
        String insertSql = """
                INSERT INTO books (title, author, category, isbn, description, cover_path, status)
                VALUES (?, ?, ?, ?, ?, ?, 'AVAILABLE')
                """;

        try (Connection conn = SQLiteConnection.connect();
             Statement countStmt = conn.createStatement();
             ResultSet rs = countStmt.executeQuery(countSql)) {

            if (rs.next() && rs.getInt(1) == 0) {
                Object[][] sampleBooks = {
                        {"Programming Language", "Herbert Schildt", "Programming", "ISBN-0001",
                                "A beginner-friendly introduction to programming concepts.", null},
                        {"JavaFX Tutorial", "Oracle Docs Team", "Programming", "ISBN-0002",
                                "Covers building desktop UIs with JavaFX and FXML.", null},
                        {"C# Tutorial", "Andrew Troelsen", "Programming", "ISBN-0003",
                                "A practical guide to the C# language and .NET basics.", null},
                        {"Python Tutorial", "Guido van Rossum", "Programming", "ISBN-0004",
                                "An introduction to Python syntax and standard library.", null},
                        {"Database System Concepts", "Silberschatz, Korth, Sudarshan", "Database", "ISBN-0005",
                                "Fundamentals of relational databases and SQL.", null}
                };

                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    for (Object[] book : sampleBooks) {
                        ps.setString(1, (String) book[0]);
                        ps.setString(2, (String) book[1]);
                        ps.setString(3, (String) book[2]);
                        ps.setString(4, (String) book[3]);
                        ps.setString(5, (String) book[4]);
                        ps.setString(6, (String) book[5]);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                System.out.println("Seeded " + sampleBooks.length + " sample books.");
            }

        } catch (SQLException e) {
            System.err.println("Failed to seed books:");
            e.printStackTrace();
        }
    }

    private static void seedMembersIfEmpty() {
        String countSql = "SELECT COUNT(*) FROM members";
        String insertSql = """
                INSERT INTO members (student_id, name, email, department, phone, gender)
                VALUES (?, ?, ?, ?, ?, ?)
                """;

        try (Connection conn = SQLiteConnection.connect();
             Statement countStmt = conn.createStatement();
             ResultSet rs = countStmt.executeQuery(countSql)) {

            if (rs.next() && rs.getInt(1) == 0) {
                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    ps.setString(1, "2025001");
                    ps.setString(2, "Rahim Uddin");
                    ps.setString(3, "rahim@gmail.com");
                    ps.setString(4, "CSE");
                    ps.setString(5, "017XXXXXXXX");
                    ps.setString(6, "Male");
                    ps.executeUpdate();
                }
                System.out.println("Seeded 1 sample member.");
            }

        } catch (SQLException e) {
            System.err.println("Failed to seed members:");
            e.printStackTrace();
        }
    }
}
