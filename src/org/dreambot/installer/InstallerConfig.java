package org.dreambot.installer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Represents runtime configuration for the installer. The configuration can be customised by providing
 * command line arguments or environment variables, but sensible defaults are provided for one-click use
 * on Windows systems.
 */
final class InstallerConfig {

    private static final String CHANNEL_SOURCE_ARG_PREFIX = "--channel-source=";
    private static final String CHANNEL_SOURCE_ENV = "ROGUES_DEN_CHANNEL_SOURCES";
    private static final String DREAMBOT_DIR_ENV = "ROGUES_DEN_DREAMBOT_DIR";

    private final Path configDirectory;
    private final Path dreamBotDirectory;
    private final List<String> additionalChannelSources;

    private InstallerConfig(Path configDirectory, Path dreamBotDirectory, List<String> additionalChannelSources) {
        this.configDirectory = configDirectory;
        this.dreamBotDirectory = dreamBotDirectory;
        this.additionalChannelSources = Collections.unmodifiableList(new ArrayList<>(additionalChannelSources));
    }

    static InstallerConfig load(String[] args) {
        Path configDir = resolveConfigDirectory();
        Path dreamBotDir = resolveDreamBotDirectory();
        List<String> extraSources = new ArrayList<>();

        String envSources = System.getenv(CHANNEL_SOURCE_ENV);
        if (envSources != null && !envSources.trim().isEmpty()) {
            for (String token : envSources.split(";")) {
                if (!token.trim().isEmpty()) {
                    extraSources.add(token.trim());
                }
            }
        }

        if (args != null) {
            for (String arg : args) {
                if (arg != null && arg.startsWith(CHANNEL_SOURCE_ARG_PREFIX)) {
                    String value = arg.substring(CHANNEL_SOURCE_ARG_PREFIX.length()).trim();
                    if (!value.isEmpty()) {
                        extraSources.add(value);
                    }
                }
            }
        }

        return new InstallerConfig(configDir, dreamBotDir, extraSources);
    }

    Path getConfigDirectory() {
        return configDirectory;
    }

    Path getChannelManifestPath() {
        return configDirectory.resolve("channels.json");
    }

    List<String> getAdditionalChannelSources() {
        return additionalChannelSources;
    }

    Path getDreamBotDirectory() {
        return dreamBotDirectory;
    }

    Path getScriptsDirectory() {
        return dreamBotDirectory.resolve("Scripts");
    }

    Path getTargetScriptPath(String fileName) {
        return getScriptsDirectory().resolve(fileName);
    }

    private static Path resolveConfigDirectory() {
        Path baseDir;
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (osName.contains("win")) {
            String appData = System.getenv("LOCALAPPDATA");
            if (appData != null && !appData.isEmpty()) {
                baseDir = Paths.get(appData, "RoguesDenInstaller");
            } else {
                baseDir = Paths.get(System.getProperty("user.home"), "AppData", "Local", "RoguesDenInstaller");
            }
        } else {
            baseDir = Paths.get(System.getProperty("user.home"), ".rogues-den", "installer");
        }
        ensureDirectory(baseDir);
        return baseDir;
    }

    private static Path resolveDreamBotDirectory() {
        String override = System.getenv(DREAMBOT_DIR_ENV);
        if (override != null && !override.trim().isEmpty()) {
            Path path = Paths.get(override.trim());
            ensureDirectory(path);
            return path;
        }

        String userHome = System.getProperty("user.home");
        Path defaultPath = Paths.get(userHome, "DreamBot");
        ensureDirectory(defaultPath);
        return defaultPath;
    }

    private static void ensureDirectory(Path path) {
        try {
            Files.createDirectories(path);
        } catch (Exception e) {
            throw new InstallerException("Unable to create directory " + path + ": " + e.getMessage(), e);
        }
    }
}
