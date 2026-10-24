package edu.kec;

import java.sql.SQLException;
import java.util.Locale;
import java.util.Scanner;

public class MainApp {
    private final Scanner scanner = new Scanner(System.in);
    private final StudentDAO studentDAO = new StudentDAO();
    private final AcademicDAO academicDAO = new AcademicDAO();

    public static void main(String[] args) {
        try {
            DBConnection.getConnection().close();
            new MainApp().run();
        } catch (Exception exception) {
            System.err.println("Application error: " + exception.getMessage());
            exception.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private void run() {
        System.out.println("KEC Academic Records - Regulation 2024");
        while (true) {
            System.out.println("""

                    1. Add student
                    2. Add course
                    3. Enroll student / update attendance
                    4. Enter CIA marks
                    5. Enter ESE mark / mark absent
                    6. Print student semester mark sheet
                    7. Print attendance defaulters (<65%)
                    8. Print department class summary
                    0. Exit
                    """);
            try {
                switch (readInt("Choice: ")) {
                    case 1 -> addStudent();
                    case 2 -> addCourse();
                    case 3 -> enrollAndUpdateAttendance();
                    case 4 -> enterCiaMarks();
                    case 5 -> enterEseMark();
                    case 6 -> printMarkSheet();
                    case 7 -> System.out.println(academicDAO.attendanceDefaulters(readSemester()));
                    case 8 -> System.out.println(academicDAO.classSummary(readSemester()));
                    case 0 -> {
                        System.out.println("Goodbye.");
                        return;
                    }
                    default -> System.out.println("Choose a listed menu option.");
                }
            } catch (SQLException | IllegalArgumentException | IllegalStateException exception) {
                System.err.println("Operation failed: " + exception.getMessage());
            }
        }
    }

    private void addStudent() throws SQLException {
        studentDAO.addStudent(readText("Roll number: "), readText("Student name: "),
                readText("Department: "), readSemester());
        System.out.println("Student added.");
    }

    private void addCourse() throws SQLException {
        String type = readText("Course type (THEORY/PRACTICAL/EMBEDDED): ").toUpperCase(Locale.ROOT);
        studentDAO.addCourse(readText("Course code: "), readText("Course title: "),
                readInt("Credits: "), type);
        System.out.println("Course added.");
    }

    private void enrollAndUpdateAttendance() throws SQLException {
        String roll = readText("Roll number: ");
        String course = readText("Course code: ");
        int semester = readSemester();
        academicDAO.enroll(roll, course, semester);
        System.out.println("Enrollment added. Record the current cumulative attendance.");
        updateAttendance(roll, course, semester);
    }

    private void updateAttendance(String roll, String course, int semester) throws SQLException {
        int held = readInt("Total classes held: ");
        int attended = readInt("Classes attended: ");
        boolean approved = readYesNo("Medical condonation approved? (y/n): ");
        academicDAO.updateAttendance(roll, course, semester, held, attended, approved);
        AcademicDAO.AttendanceStatus status = academicDAO.attendanceStatus(roll, course, semester);
        System.out.printf(Locale.ROOT, "Attendance: %.2f%% - %s%n", status.percentage(), status.message());
    }

    private void enterCiaMarks() throws SQLException {
        String roll = readText("Roll number: ");
        String course = readText("Course code: ");
        int semester = readSemester();
        boolean enterTest1 = readYesNo("Enter/update CIA Test 1? (y/n): ");
        if (enterTest1) {
            academicDAO.recordCiaMark(roll, course, semester, "cia_test_1", readMark("CIA Test 1 (0-100): "));
        }
        boolean enterTest2 = readYesNo("Enter/update CIA Test 2? (y/n): ");
        if (enterTest2) {
            academicDAO.recordCiaMark(roll, course, semester, "cia_test_2", readMark("CIA Test 2 (0-100): "));
        }
        boolean enterAssignment = readYesNo("Enter/update assignment mark? (y/n): ");
        if (enterAssignment) {
            academicDAO.recordCiaMark(roll, course, semester, "assignment_mark",
                    readMark("Assignment (0-100): "));
        }
        boolean enterQuiz = readYesNo("Enter/update quiz mark? (y/n): ");
        if (enterQuiz) {
            academicDAO.recordCiaMark(roll, course, semester, "quiz_mark", readMark("Quiz (0-100): "));
        }
        boolean enterLab = readYesNo("Enter/update lab evaluation mark? (y/n): ");
        if (enterLab) {
            academicDAO.recordCiaMark(roll, course, semester, "lab_evaluation_mark",
                    readMark("Lab evaluation (0-100): "));
        }
        System.out.println("CIA marks saved; omitted components were left unchanged.");
    }

    private void enterEseMark() throws SQLException {
        String roll = readText("Roll number: ");
        String course = readText("Course code: ");
        int semester = readSemester();
        AcademicDAO.AttendanceStatus status = academicDAO.attendanceStatus(roll, course, semester);
        System.out.printf(Locale.ROOT, "Attendance: %.2f%% - %s%n", status.percentage(), status.message());
        if (!status.eligibleForEse()) {
            System.out.println("ESE entry blocked by the attendance rule.");
            return;
        }
        boolean absent = readYesNo("Student absent in ESE? (y/n): ");
        Double mark = absent ? null : readMark("ESE mark (0-100): ");
        academicDAO.recordEseMark(roll, course, semester, mark, absent);
        System.out.println(absent ? "Absence recorded as AB." : "ESE mark saved.");
    }

    private void printMarkSheet() throws SQLException {
        System.out.println(academicDAO.markSheet(readText("Roll number: "), readSemester()));
    }

    private int readSemester() {
        return readInt("Semester (1-8): ");
    }

    private double readMark(String prompt) {
        while (true) {
            double mark = readDouble(prompt);
            if (mark >= 0 && mark <= 100) {
                return mark;
            }
            System.out.println("Enter a mark from 0 through 100.");
        }
    }

    private int readInt(String prompt) {
        while (true) {
            try {
                return Integer.parseInt(readText(prompt));
            } catch (NumberFormatException exception) {
                System.out.println("Enter a whole number.");
            }
        }
    }

    private double readDouble(String prompt) {
        while (true) {
            try {
                return Double.parseDouble(readText(prompt));
            } catch (NumberFormatException exception) {
                System.out.println("Enter a number.");
            }
        }
    }

    private boolean readYesNo(String prompt) {
        while (true) {
            String response = readText(prompt);
            if (response.equalsIgnoreCase("y") || response.equalsIgnoreCase("yes")) return true;
            if (response.equalsIgnoreCase("n") || response.equalsIgnoreCase("no")) return false;
            System.out.println("Enter y or n.");
        }
    }

    private String readText(String prompt) {
        while (true) {
            System.out.print(prompt);
            String value = scanner.nextLine().trim();
            if (!value.isEmpty()) {
                return value;
            }
            System.out.println("A value is required.");
        }
    }
}
