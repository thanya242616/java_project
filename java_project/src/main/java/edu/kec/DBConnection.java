package edu.kec;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.stream.Collectors;

public final class DBConnection {
    private static final String SCHEMA_RESOURCE = "/kec_schema.sql";
    private static volatile boolean initialized;

    private DBConnection() {
    }

    public static Connection getConnection() throws SQLException {
        initialize();
        Connection connection = DriverManager.getConnection(databaseUrl());
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
        } catch (SQLException exception) {
            connection.close();
            throw exception;
        }
        return connection;
    }

    private static void initialize() throws SQLException {
        if (initialized) {
            return;
        }
        synchronized (DBConnection.class) {
            if (initialized) {
                return;
            }
            String configuredPath = System.getenv("KEC_DB_PATH");
            Path databasePath = Path.of(configuredPath == null || configuredPath.isBlank()
                    ? "kec_academic.db" : configuredPath).toAbsolutePath();
            Path parent = databasePath.getParent();
            try {
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Class.forName("org.sqlite.JDBC");
                try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
                     Statement statement = connection.createStatement()) {
                    statement.execute("PRAGMA foreign_keys = ON");
                    for (String sql : loadSchema().split(";")) {
                        if (!sql.isBlank()) {
                            statement.execute(sql);
                        }
                    }
                }
                initialized = true;
            } catch (IOException | ClassNotFoundException exception) {
                throw new SQLException("Could not initialize the SQLite database.", exception);
            }
        }
    }

    private static String databaseUrl() {
        String configuredPath = System.getenv("KEC_DB_PATH");
        Path databasePath = Path.of(configuredPath == null || configuredPath.isBlank()
                ? "kec_academic.db" : configuredPath).toAbsolutePath();
        return "jdbc:sqlite:" + databasePath;
    }

    private static String loadSchema() throws IOException {
        try (InputStream input = DBConnection.class.getResourceAsStream(SCHEMA_RESOURCE)) {
            if (input == null) {
                throw new IOException("Schema resource not found: " + SCHEMA_RESOURCE);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(input))) {
                return reader.lines().collect(Collectors.joining(System.lineSeparator()));
            }
        }
    }
}
