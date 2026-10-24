package edu.kec;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AcademicDAO {
    public void enroll(String rollNumber, String courseCode, int semester) throws SQLException {
        String sql = """
                INSERT INTO enrollments (roll_number, course_code, semester)
                VALUES (?, ?, ?)
                ON CONFLICT (roll_number, course_code, semester) DO NOTHING
                """;
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, rollNumber.trim().toUpperCase());
            statement.setString(2, courseCode.trim().toUpperCase());
            statement.setInt(3, semester);
            statement.executeUpdate();
        }
    }

    public void updateAttendance(String rollNumber, String courseCode, int semester,
                                 int classesHeld, int classesAttended, boolean condonationApproved)
            throws SQLException {
        String sql = """
                UPDATE enrollments
                SET classes_held = ?, classes_attended = ?, medical_condonation_approved = ?
                WHERE roll_number = ? AND course_code = ? AND semester = ?
                """;
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, classesHeld);
            statement.setInt(2, classesAttended);
            statement.setInt(3, condonationApproved ? 1 : 0);
            statement.setString(4, rollNumber.trim().toUpperCase());
            statement.setString(5, courseCode.trim().toUpperCase());
            statement.setInt(6, semester);
            requireUpdated(statement.executeUpdate(), "Enrollment not found.");
        }
    }

    public void recordCiaMark(String rollNumber, String courseCode, int semester,
                              String component, double mark) throws SQLException {
        if (!List.of("cia_test_1", "cia_test_2", "assignment_mark", "quiz_mark", "lab_evaluation_mark")
                .contains(component)) {
            throw new IllegalArgumentException("Unsupported CIA component.");
        }
        String sql = """
                INSERT INTO assessment_marks (roll_number, course_code, semester, %s)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (roll_number, course_code, semester)
                DO UPDATE SET %s = excluded.%s
                """.formatted(component, component, component);
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, rollNumber.trim().toUpperCase());
            statement.setString(2, courseCode.trim().toUpperCase());
            statement.setInt(3, semester);
            statement.setDouble(4, mark);
            statement.executeUpdate();
        }
    }

    public void recordEseMark(String rollNumber, String courseCode, int semester,
                              Double mark, boolean absent) throws SQLException {
        if (absent && mark != null) {
            throw new IllegalArgumentException("An absent student cannot have an ESE mark.");
        }
        if (!absent && (mark == null || mark < 0 || mark > 100)) {
            throw new IllegalArgumentException("ESE mark must be between 0 and 100.");
        }
        AttendanceStatus attendance = attendanceStatus(rollNumber, courseCode, semester);
        if (!attendance.eligibleForEse()) {
            throw new IllegalStateException("Student is not eligible to sit the ESE: " + attendance.message());
        }
        String sql = """
                INSERT INTO assessment_marks (roll_number, course_code, semester, ese_mark, ese_absent)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (roll_number, course_code, semester)
                DO UPDATE SET ese_mark = excluded.ese_mark, ese_absent = excluded.ese_absent
                """;
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, rollNumber.trim().toUpperCase());
            statement.setString(2, courseCode.trim().toUpperCase());
            statement.setInt(3, semester);
            if (absent) {
                statement.setNull(4, java.sql.Types.REAL);
            } else {
                statement.setDouble(4, mark);
            }
            statement.setInt(5, absent ? 1 : 0);
            statement.executeUpdate();
        }
    }

    public AttendanceStatus attendanceStatus(String rollNumber, String courseCode, int semester)
            throws SQLException {
        String sql = """
                SELECT classes_held, classes_attended, medical_condonation_approved
                FROM enrollments
                WHERE roll_number = ? AND course_code = ? AND semester = ?
                """;
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindEnrollment(statement, rollNumber, courseCode, semester);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalArgumentException("Enrollment not found.");
                }
                int held = result.getInt("classes_held");
                int attended = result.getInt("classes_attended");
                boolean approved = result.getInt("medical_condonation_approved") == 1;
                double percentage = held == 0 ? 0.0 : attended * 100.0 / held;
                boolean eligible = percentage >= 75.0 || (percentage >= 65.0 && approved);
                String message;
                if (percentage < 65.0) {
                    message = "SA: attendance below 65%; barred from ESE";
                } else if (percentage < 75.0 && !approved) {
                    message = "Medical condonation approval required";
                } else if (percentage < 75.0) {
                    message = "Eligible with approved medical condonation";
                } else {
                    message = "Eligible";
                }
                return new AttendanceStatus(percentage, eligible, percentage < 65.0, message);
            }
        }
    }

    public String markSheet(String rollNumber, int semester) throws SQLException {
        String sql = """
                SELECT s.name, s.department, e.course_code, c.title, c.credits, c.course_type,
                       e.classes_held, e.classes_attended, e.medical_condonation_approved,
                       m.cia_test_1, m.cia_test_2, m.assignment_mark, m.quiz_mark,
                       m.lab_evaluation_mark, m.ese_mark, m.ese_absent
                FROM enrollments e
                JOIN students s ON s.roll_number = e.roll_number
                JOIN courses c ON c.course_code = e.course_code
                LEFT JOIN assessment_marks m
                    ON m.roll_number = e.roll_number AND m.course_code = e.course_code AND m.semester = e.semester
                WHERE e.roll_number = ? AND e.semester = ?
                ORDER BY e.course_code
                """;
        List<MarkSheetRow> rows = new ArrayList<>();
        String studentName = null;
        String department = null;
        double weightedGradePoints = 0;
        int totalCredits = 0;
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, rollNumber.trim().toUpperCase());
            statement.setInt(2, semester);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    studentName = result.getString("name");
                    department = result.getString("department");
                    MarkSheetRow row = toMarkSheetRow(result);
                    rows.add(row);
                    weightedGradePoints += row.credits() * row.gradePoint();
                    totalCredits += row.credits();
                }
            }
        }
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("No enrollments found for that roll number and semester.");
        }
        double sgpa = totalCredits == 0 ? 0 : weightedGradePoints / totalCredits;
        StringBuilder report = new StringBuilder();
        report.append("KONGU ENGINEERING COLLEGE (AUTONOMOUS) - REGULATION 2024\n")
                .append("STUDENT SEMESTER MARK SHEET\n")
                .append("Roll Number: ").append(rollNumber.trim().toUpperCase())
                .append("    Name: ").append(studentName)
                .append("    Department: ").append(department)
                .append("    Semester: ").append(semester).append('\n')
                .append("Course | Title | Credits | Attendance | CIA avg/100 | ESE/100 | Total/100 | Grade | Status\n");
        for (MarkSheetRow row : rows) {
            report.append(row.courseCode()).append(" | ").append(row.title()).append(" | ")
                    .append(row.credits()).append(" | ").append(format(row.attendancePercentage())).append("% | ")
                    .append(formatNullable(row.ciaAverage())).append(" | ")
                    .append(row.absent() ? "AB" : formatNullable(row.eseMark())).append(" | ")
                    .append(formatNullable(row.total())).append(" | ").append(row.grade()).append(" | ")
                    .append(row.status()).append('\n');
        }
        return report.append("SGPA: ").append(format(sgpa))
                .append("    Equivalent Percentage: ").append(format(sgpa * 10)).append("%\n").toString();
    }

    public String attendanceDefaulters(int semester) throws SQLException {
        String sql = """
                SELECT s.roll_number, s.name, s.department, e.course_code, e.classes_held,
                       e.classes_attended
                FROM enrollments e
                JOIN students s ON s.roll_number = e.roll_number
                WHERE e.semester = ?
                      AND CASE WHEN e.classes_held = 0 THEN 0
                               ELSE e.classes_attended * 100.0 / e.classes_held END < 65
                ORDER BY s.department, s.roll_number, e.course_code
                """;
        StringBuilder report = new StringBuilder("ATTENDANCE DEFAULTERS (<65%) - SEMESTER ")
                .append(semester).append("\nRoll Number | Name | Department | Course | Attendance | Grade\n");
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, semester);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    int held = result.getInt("classes_held");
                    double percentage = held == 0 ? 0.0 : result.getInt("classes_attended") * 100.0 / held;
                    report.append(result.getString("roll_number")).append(" | ")
                            .append(result.getString("name")).append(" | ")
                            .append(result.getString("department")).append(" | ")
                            .append(result.getString("course_code")).append(" | ")
                            .append(format(percentage)).append("% | SA\n");
                }
            }
        }
        return report.toString();
    }

    public String classSummary(int semester) throws SQLException {
        String sql = """
                SELECT s.department, s.roll_number
                FROM students s
                WHERE EXISTS (
                    SELECT 1 FROM enrollments e WHERE e.roll_number = s.roll_number AND e.semester = ?
                )
                ORDER BY s.department, s.roll_number
                """;
        StringBuilder report = new StringBuilder("DEPARTMENT CLASS PERFORMANCE - SEMESTER ")
                .append(semester).append("\nDepartment | Students | Pass | Fail/SA | Average SGPA\n");
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, semester);
            try (ResultSet result = statement.executeQuery()) {
                String department = null;
                int students = 0;
                int passed = 0;
                int failed = 0;
                double sumSgpa = 0;
                while (result.next()) {
                    String nextDepartment = result.getString("department");
                    if (department != null && !department.equals(nextDepartment)) {
                        appendSummaryRow(report, department, students, passed, failed, sumSgpa);
                        students = 0;
                        passed = 0;
                        failed = 0;
                        sumSgpa = 0;
                    }
                    department = nextDepartment;
                    String rollNumber = result.getString("roll_number");
                    SummaryStats stats = studentSummary(connection, rollNumber, semester);
                    students++;
                    passed += stats.passed() ? 1 : 0;
                    failed += stats.passed() ? 0 : 1;
                    sumSgpa += stats.sgpa();
                }
                if (department != null) {
                    appendSummaryRow(report, department, students, passed, failed, sumSgpa);
                }
            }
        }
        return report.toString();
    }

    private SummaryStats studentSummary(Connection connection, String rollNumber, int semester) throws SQLException {
        String sql = """
                SELECT e.course_code, c.title, c.credits, c.course_type, e.classes_held, e.classes_attended,
                       e.medical_condonation_approved, m.cia_test_1, m.cia_test_2,
                       m.assignment_mark, m.quiz_mark, m.lab_evaluation_mark, m.ese_mark, m.ese_absent
                FROM enrollments e
                JOIN courses c ON c.course_code = e.course_code
                LEFT JOIN assessment_marks m
                    ON m.roll_number = e.roll_number AND m.course_code = e.course_code AND m.semester = e.semester
                WHERE e.roll_number = ? AND e.semester = ?
                """;
        double weightedPoints = 0;
        int credits = 0;
        boolean passedAll = true;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, rollNumber);
            statement.setInt(2, semester);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    MarkSheetRow row = toMarkSheetRow(result);
                    weightedPoints += row.credits() * row.gradePoint();
                    credits += row.credits();
                    passedAll &= row.status().equals("PASS");
                }
            }
        }
        return new SummaryStats(credits == 0 ? 0 : weightedPoints / credits, passedAll);
    }

    private MarkSheetRow toMarkSheetRow(ResultSet result) throws SQLException {
        double attendance = result.getInt("classes_held") == 0 ? 0.0
                : result.getInt("classes_attended") * 100.0 / result.getInt("classes_held");
        boolean condonationApproved = result.getInt("medical_condonation_approved") == 1;
        boolean attendanceEligible = attendance >= 75 || (attendance >= 65 && condonationApproved);
        boolean shortAttendance = attendance < 65;
        String type = result.getString("course_type");
        double ciaWeight = switch (type) {
            case "THEORY" -> 0.40;
            case "PRACTICAL" -> 0.60;
            case "EMBEDDED" -> 0.50;
            default -> throw new SQLException("Unknown course type in database: " + type);
        };
        double eseWeight = 1 - ciaWeight;
        Double[] ciaMarks = {
                nullableDouble(result, "cia_test_1"), nullableDouble(result, "cia_test_2"),
                nullableDouble(result, "assignment_mark"), nullableDouble(result, "quiz_mark"),
                nullableDouble(result, "lab_evaluation_mark")
        };
        double ciaSum = 0;
        int ciaCount = 0;
        for (Double ciaMark : ciaMarks) {
            if (ciaMark != null) {
                ciaSum += ciaMark;
                ciaCount++;
            }
        }
        Double ciaAverage = ciaCount == 0 ? null : ciaSum / ciaCount;
        Double eseMark = nullableDouble(result, "ese_mark");
        boolean absent = result.getInt("ese_absent") == 1;
        Double total = null;
        String grade;
        String status;
        int gradePoint;
        if (shortAttendance) {
            grade = "SA";
            status = "FAIL - SHORT ATTENDANCE";
            gradePoint = 0;
        } else if (!attendanceEligible) {
            grade = "PENDING";
            status = "CONDONATION REQUIRED";
            gradePoint = 0;
        } else if (absent) {
            grade = "AB";
            status = "FAIL - ABSENT";
            gradePoint = 0;
        } else if (eseMark == null || ciaAverage == null) {
            grade = "PENDING";
            status = "IN PROGRESS";
            gradePoint = 0;
        } else {
            total = ciaAverage * ciaWeight + eseMark * eseWeight;
            double eseThreshold = switch (type) {
                case "THEORY" -> 27.0;
                case "PRACTICAL" -> 20.0;
                case "EMBEDDED" -> 22.5;
                default -> throw new SQLException("Unknown course type in database: " + type);
            };
            if (eseMark * eseWeight < eseThreshold || total < 50.0) {
                grade = "RA";
                status = "FAIL";
                gradePoint = 0;
            } else {
                gradePoint = gradePoint(total);
                grade = letterGrade(total);
                status = "PASS";
            }
        }
        return new MarkSheetRow(result.getString("course_code"), result.getString("title"),
                result.getInt("credits"), attendance, ciaAverage, eseMark, absent, total, grade, status, gradePoint);
    }

    private static int gradePoint(double marks) {
        if (marks >= 91) return 10;
        if (marks >= 81) return 9;
        if (marks >= 71) return 8;
        if (marks >= 61) return 7;
        if (marks >= 50) return 6;
        return 0;
    }

    private static String letterGrade(double marks) {
        if (marks >= 91) return "O";
        if (marks >= 81) return "A+";
        if (marks >= 71) return "A";
        if (marks >= 61) return "B+";
        if (marks >= 50) return "B";
        return "RA";
    }

    private static Double nullableDouble(ResultSet result, String column) throws SQLException {
        double value = result.getDouble(column);
        return result.wasNull() ? null : value;
    }

    private static String formatNullable(Double value) {
        return value == null ? "-" : format(value);
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static void appendSummaryRow(StringBuilder report, String department, int students,
                                         int passed, int failed, double sumSgpa) {
        report.append(department).append(" | ").append(students).append(" | ")
                .append(passed).append(" | ").append(failed).append(" | ")
                .append(format(students == 0 ? 0 : sumSgpa / students)).append('\n');
    }

    private static void bindEnrollment(PreparedStatement statement, String rollNumber,
                                       String courseCode, int semester) throws SQLException {
        statement.setString(1, rollNumber.trim().toUpperCase());
        statement.setString(2, courseCode.trim().toUpperCase());
        statement.setInt(3, semester);
    }

    private static void requireUpdated(int updated, String message) {
        if (updated == 0) {
            throw new IllegalArgumentException(message);
        }
    }

    public record AttendanceStatus(double percentage, boolean eligibleForEse,
                                   boolean shortAttendance, String message) {
    }

    private record MarkSheetRow(String courseCode, String title, int credits, double attendancePercentage,
                                Double ciaAverage, Double eseMark, boolean absent, Double total,
                                String grade, String status, int gradePoint) {
    }

    private record SummaryStats(double sgpa, boolean passed) {
    }
}
