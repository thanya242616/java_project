package edu.kec;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class StudentDAO {
    public void addStudent(String rollNumber, String name, String department, int semester) throws SQLException {
        String sql = """
                INSERT INTO students (roll_number, name, department, current_semester)
                VALUES (?, ?, ?, ?)
                """;
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, rollNumber.trim().toUpperCase());
            statement.setString(2, name.trim());
            statement.setString(3, department.trim().toUpperCase());
            statement.setInt(4, semester);
            statement.executeUpdate();
        }
    }

    public void addCourse(String courseCode, String title, int credits, String courseType) throws SQLException {
        String sql = """
                INSERT INTO courses (course_code, title, credits, course_type)
                VALUES (?, ?, ?, ?)
                """;
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, courseCode.trim().toUpperCase());
            statement.setString(2, title.trim());
            statement.setInt(3, credits);
            statement.setString(4, courseType.trim().toUpperCase());
            statement.executeUpdate();
        }
    }
}
