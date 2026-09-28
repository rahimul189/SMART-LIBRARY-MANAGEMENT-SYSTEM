package com.smartlibrary.data;

import com.smartlibrary.model.Member;

import java.sql.ResultSet;
import java.sql.SQLException;

/** Reads the "members" table (ordered by name) into Member objects. */
public class MemberRepository extends JdbcRepository<Member> {

    @Override
    protected String selectAllSql() {
        return "SELECT id, student_id, name, email, department, phone, gender "
                + "FROM members ORDER BY name";
    }

    @Override
    protected Member mapRow(ResultSet rs) throws SQLException {
        return new Member(
                rs.getInt("id"),
                rs.getString("student_id"),
                rs.getString("name"),
                rs.getString("email"),
                rs.getString("department"),
                rs.getString("phone"),
                rs.getString("gender")
        );
    }
}
