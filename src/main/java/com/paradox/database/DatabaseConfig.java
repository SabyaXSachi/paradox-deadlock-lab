package com.paradox.database;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Reads MySQL connection settings for Paradox.
 *
 * Sources, in order of precedence:
 *   1. environment variables  PARADOX_DB_URL, PARADOX_DB_USER, PARADOX_DB_PASSWORD
 *   2. config/database.properties  (gitignored; see database.properties.example)
 *   3. sane localhost defaults
 *
 * Passwords are never hardcoded in source code.
 */
public final class DatabaseConfig {

    private static final String DEFAULT_URL =
            "jdbc:mysql://localhost:3306/paradox_db";

    private final String url;
    private final String user;
    private final String password;

    public DatabaseConfig() {
        Properties props = loadPropertiesFile();
        this.url = envOr("PARADOX_DB_URL", props.getProperty("db.url", DEFAULT_URL));
        this.user = envOr("PARADOX_DB_USER", props.getProperty("db.user", "root"));
        this.password = envOr("PARADOX_DB_PASSWORD", props.getProperty("db.password", ""));
    }

    public DatabaseConfig(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    private static Properties loadPropertiesFile() {
        Properties props = new Properties();
        // The application is launched from the project root, either through
        // "mvn javafx:run" or "java -jar", so a relative path is stable.
        Path configFile = Path.of("config", "database.properties");
        if (Files.exists(configFile)) {
            try (InputStream in = Files.newInputStream(configFile)) {
                props.load(in);
            } catch (IOException e) {
                throw new DatabaseException(
                        "Could not read " + configFile.toAbsolutePath(), e);
            }
        }
        return props;
    }

    private static String envOr(String envVar, String fallback) {
        String value = System.getenv(envVar);
        return (value == null || value.isBlank()) ? fallback : value;
    }

    public String getUrl() {
        return url;
    }

    public String getUser() {
        return user;
    }

    public String getPassword() {
        return password;
    }
}
