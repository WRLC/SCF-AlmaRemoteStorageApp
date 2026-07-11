package com.exlibris.version;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AppVersionInfo {

    private static final String UNKNOWN = "unknown";
        private static final Pattern PROJECT_VERSION_PATTERN = Pattern
            .compile("(?s)<project\\b.*?<version>\\s*([^<\\s]+)\\s*</version>");
        private static final Pattern PROJECT_ARTIFACT_PATTERN = Pattern
            .compile("(?s)<project\\b.*?<artifactId>\\s*([^<\\s]+)\\s*</artifactId>");
    private static final AppVersionInfo INSTANCE = new AppVersionInfo();

    private final String appName;
    private final String version;
    private final String buildTime;
    private final String gitCommit;
    private final String gitBranch;

    private AppVersionInfo() {
        Properties appProps = loadProperties("app-version.properties");
        Properties gitProps = loadProperties("git.properties");
        Properties pomProps = loadLocalPomProperties();

        appName = getPreferredValue(getValue(pomProps, "app.name"), getValue(appProps, "app.name"));
        version = getPreferredValue(getValue(pomProps, "app.version"), getValue(appProps, "app.version"));
        buildTime = getValue(appProps, "app.build.time");
        gitCommit = getValue(gitProps, "git.commit.id.abbrev");
        gitBranch = getValue(gitProps, "git.branch");
    }

    public static AppVersionInfo getInstance() {
        return INSTANCE;
    }

    public String getAppName() {
        return appName;
    }

    public String getVersion() {
        return version;
    }

    public String getBuildTime() {
        return buildTime;
    }

    public String getGitCommit() {
        return gitCommit;
    }

    public String getGitBranch() {
        return gitBranch;
    }

    public String toLogLine() {
        return "APP_START app=" + appName
                + " version=" + version
                + " buildTime=" + buildTime
                + " gitBranch=" + gitBranch
                + " gitCommit=" + gitCommit;
    }

    private static Properties loadProperties(String fileName) {
        Properties properties = new Properties();
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = AppVersionInfo.class.getClassLoader();
        }

        if (classLoader == null) {
            return properties;
        }

        try (InputStream input = classLoader.getResourceAsStream(fileName)) {
            if (input != null) {
                properties.load(input);
            }
        } catch (IOException ignored) {
            // Keep defaults when metadata files are missing/unreadable.
        }

        return properties;
    }

    private static String getValue(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            return UNKNOWN;
        }

        String trimmed = value.trim();
        if (trimmed.startsWith("${") && trimmed.endsWith("}")) {
            return UNKNOWN;
        }

        return trimmed;
    }

    private static String getPreferredValue(String preferred, String fallback) {
        if (!UNKNOWN.equals(preferred)) {
            return preferred;
        }

        return fallback;
    }

    private static Properties loadLocalPomProperties() {
        Properties properties = new Properties();
        Path pomPath = resolveLocalPomPath();
        if (pomPath == null || !Files.exists(pomPath)) {
            return properties;
        }

        try {
            String pomContent = Files.readString(pomPath);
            putRegexValue(properties, "app.name", pomContent, PROJECT_ARTIFACT_PATTERN);
            putRegexValue(properties, "app.version", pomContent, PROJECT_VERSION_PATTERN);
        } catch (IOException ignored) {
            // Ignore local pom parsing in non-dev deployments.
        }

        return properties;
    }

    private static void putRegexValue(Properties properties, String key, String content, Pattern pattern) {
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            properties.setProperty(key, matcher.group(1).trim());
        }
    }

    private static Path resolveLocalPomPath() {
        try {
            URL location = AppVersionInfo.class.getProtectionDomain().getCodeSource().getLocation();
            if (location == null) {
                return null;
            }

            Path codePath = Paths.get(location.toURI()).normalize();
            Path path = Files.isDirectory(codePath) ? codePath : codePath.getParent();
            if (path == null) {
                return null;
            }

            if (path.endsWith(Paths.get("target", "classes"))) {
                return path.getParent().getParent().resolve("pom.xml");
            }
        } catch (URISyntaxException ignored) {
            return null;
        }

        return null;
    }
}
