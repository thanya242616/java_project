# KEC Academic Records Manager

A menu-driven Java 17/JDBC application for student and course records, cumulative attendance, phased CIA/ESE marks, and semester reports. SQLite is used as the embedded database; the application creates its database and schema on first run.

## Run

Requirements: JDK 17 or later and Maven 3.8 or later.

```powershell
mvn clean package
mvn exec:java -Dexec.mainClass=edu.kec.MainApp
```

Set `KEC_DB_PATH` to change the SQLite file location; the default is `kec_academic.db` in the current directory. For example:

```powershell
$env:KEC_DB_PATH = "D:\kec-data\semester.db"
```

The database schema is in [kec_schema.sql](src/main/resources/kec_schema.sql). Tables enforce primary/foreign keys, valid course types, credit and semester ranges, mark ranges, attendance counts, and absence consistency.

## Menu and entry workflow

1. Add the student and course.
2. Enroll the student in the course for the semester, then enter cumulative classes held/attended and medical-condonation approval. Choose the same enrollment option later to update attendance without resetting existing marks.
3. Enter or update any CIA component at each assessment phase. Answer `n` for components not being entered; existing marks are preserved.
4. After attendance eligibility is met, enter the ESE mark or record absence.
5. Print a semester mark sheet, attendance-defaulter report, or department summary.

### Attendance updates

Attendance is cumulative and must satisfy `0 <= classes attended <= classes held`. Re-entering an existing enrollment leaves its attendance and marks intact until the cumulative attendance values are updated.

## KEC R2024 rule interpretation

- Attendance of at least 75% permits ESE. Attendance from 65% up to (but not including) 75% requires approved medical condonation. Below 65% is SA and blocks ESE entry.
- THEORY uses a CIA/ESE split of 40/60 and requires at least 27 weighted ESE marks out of 60. PRACTICAL uses 60/40 and a 20/40 ESE minimum. EMBEDDED uses 50/50 and a 22.5/50 minimum. Passing also requires a total of at least 50/100.
- CIA is the arithmetic mean of marks entered for CIA Tests 1 and 2, assignment, quiz, and lab evaluation. Components not yet entered are excluded from the mean; the report labels a course in progress until both a CIA mark and an ESE result exist.
- Grades are assigned to passing totals using the specified ranges: O, A+, A, B+, B. A failed ESE threshold or total receives RA; ESE absence receives AB; attendance below 65% receives SA. All non-passing, SA, AB, condonation-pending, and in-progress rows currently contribute zero grade points to SGPA.
- SGPA uses all enrolled course credits in the selected semester. Equivalent percentage is SGPA multiplied by 10. Department summary counts anything not passed (including incomplete results) as fail and includes those students in the average SGPA.

These calculations implement the rules stated in the project requirements; verify local academic-office interpretations before using results as official records.

## Screenshot evidence

No screenshots are fabricated or included. To capture genuine proof, run the application in a terminal and save screenshots showing:

1. Successful student and course creation.
2. Incremental CIA entry, followed by ESE entry (the mark sheet can show each saved phase).
3. The final mark sheet showing weighted total, grade, SGPA, and equivalent percentage.

Use the console menu, and capture the terminal using Windows Snipping Tool (`Win+Shift+S`). Keep the screenshots with the submission and avoid including real student data unless authorized.
