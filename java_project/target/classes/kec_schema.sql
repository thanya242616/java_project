CREATE TABLE IF NOT EXISTS students (
    roll_number TEXT PRIMARY KEY COLLATE NOCASE,
    name TEXT NOT NULL CHECK (length(trim(name)) > 0),
    department TEXT NOT NULL CHECK (length(trim(department)) > 0),
    current_semester INTEGER NOT NULL CHECK (current_semester BETWEEN 1 AND 8)
);

CREATE TABLE IF NOT EXISTS courses (
    course_code TEXT PRIMARY KEY COLLATE NOCASE,
    title TEXT NOT NULL CHECK (length(trim(title)) > 0),
    credits INTEGER NOT NULL CHECK (credits > 0),
    course_type TEXT NOT NULL CHECK (course_type IN ('THEORY', 'PRACTICAL', 'EMBEDDED'))
);

CREATE TABLE IF NOT EXISTS enrollments (
    roll_number TEXT NOT NULL COLLATE NOCASE,
    course_code TEXT NOT NULL COLLATE NOCASE,
    semester INTEGER NOT NULL CHECK (semester BETWEEN 1 AND 8),
    classes_held INTEGER NOT NULL DEFAULT 0 CHECK (classes_held >= 0),
    classes_attended INTEGER NOT NULL DEFAULT 0 CHECK (classes_attended >= 0 AND classes_attended <= classes_held),
    medical_condonation_approved INTEGER NOT NULL DEFAULT 0 CHECK (medical_condonation_approved IN (0, 1)),
    PRIMARY KEY (roll_number, course_code, semester),
    FOREIGN KEY (roll_number) REFERENCES students(roll_number) ON DELETE CASCADE,
    FOREIGN KEY (course_code) REFERENCES courses(course_code) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS assessment_marks (
    roll_number TEXT NOT NULL COLLATE NOCASE,
    course_code TEXT NOT NULL COLLATE NOCASE,
    semester INTEGER NOT NULL,
    cia_test_1 REAL CHECK (cia_test_1 BETWEEN 0 AND 100),
    cia_test_2 REAL CHECK (cia_test_2 BETWEEN 0 AND 100),
    assignment_mark REAL CHECK (assignment_mark BETWEEN 0 AND 100),
    quiz_mark REAL CHECK (quiz_mark BETWEEN 0 AND 100),
    lab_evaluation_mark REAL CHECK (lab_evaluation_mark BETWEEN 0 AND 100),
    ese_mark REAL CHECK (ese_mark BETWEEN 0 AND 100),
    ese_absent INTEGER NOT NULL DEFAULT 0 CHECK (ese_absent IN (0, 1)),
    PRIMARY KEY (roll_number, course_code, semester),
    FOREIGN KEY (roll_number, course_code, semester)
        REFERENCES enrollments(roll_number, course_code, semester) ON DELETE CASCADE,
    CHECK (ese_absent = 0 OR ese_mark IS NULL)
);

CREATE INDEX IF NOT EXISTS idx_enrollments_semester ON enrollments(semester);
CREATE INDEX IF NOT EXISTS idx_students_department ON students(department);
