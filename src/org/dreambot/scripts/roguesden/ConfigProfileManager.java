package org.dreambot.scripts.roguesden;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Handles persistence and loading of configuration profiles so the script can
 * start headlessly with sane defaults.
 */
public class ConfigProfileManager {

    private static final String DEFAULT_PROFILE_RESOURCE = "/org/dreambot/scripts/roguesden/config/default_config.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path configPath;
    private final Consumer<String> logger;

    public ConfigProfileManager() {
        this(defaultConfigPath(), null);
    }

    public ConfigProfileManager(Path configPath, Consumer<String> logger) {
        this.configPath = configPath != null ? configPath : defaultConfigPath();
        this.logger = logger != null ? logger : message -> { };
    }

    public LoadResult loadConfig(boolean ironman) {
        RoguesDenScript.Config saved = sanitize(loadSavedConfig());
        if (isValid(saved)) {
            return new LoadResult(saved, Source.SAVED, ironman);
        }
        if (saved != null) {
            log("Saved configuration was invalid; falling back to defaults.");
        }

        RoguesDenScript.Config fallback = sanitize(loadDefaultProfile(ironman));
        if (isValid(fallback)) {
            return new LoadResult(fallback, Source.DEFAULT, ironman);
        }

        log("Unable to load a valid configuration profile.");
        return new LoadResult(null, Source.NONE, ironman);
    }

    public boolean hasSavedConfiguration() {
        return Files.isRegularFile(configPath);
    }

    public void saveConfig(RoguesDenScript.Config config) {
        if (config == null) {
            return;
        }

        Path parent = configPath.getParent();
        try {
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (Writer writer = Files.newBufferedWriter(configPath, StandardCharsets.UTF_8)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException ex) {
            log("Failed to save configuration to " + configPath + ": " + ex.getMessage());
        }
    }

    private RoguesDenScript.Config loadSavedConfig() {
        if (!hasSavedConfiguration()) {
            return null;
        }

        try (Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, RoguesDenScript.Config.class);
        } catch (IOException | JsonParseException ex) {
            log("Failed to read saved configuration: " + ex.getMessage());
            return null;
        }
    }

    private RoguesDenScript.Config loadDefaultProfile(boolean ironman) {
        try (InputStream stream = getClass().getResourceAsStream(DEFAULT_PROFILE_RESOURCE)) {
            if (stream == null) {
                log("Default profile resource missing; using code defaults instead.");
                return createPreset(ironman);
            }
            ProfileBundle bundle = GSON.fromJson(
                new InputStreamReader(stream, StandardCharsets.UTF_8),
                ProfileBundle.class
            );
            if (bundle == null) {
                return createPreset(ironman);
            }
            return ironman ? bundle.ironman : bundle.standard;
        } catch (Exception ex) {
            log("Failed to load default profile: " + ex.getMessage());
            return createPreset(ironman);
        }
    }

    private RoguesDenScript.Config sanitize(RoguesDenScript.Config config) {
        if (config == null) {
            return null;
        }

        if (config.preferredFoodItem == null) {
            config.preferredFoodItem = "";
        }
        if (config.rewardTarget == null) {
            config.rewardTarget = RoguesDenScript.Config.RewardTarget.ROGUE_EQUIPMENT;
        }
        if (config.shortcutMode == null) {
            config.shortcutMode = RoguesDenScript.Config.ShortcutMode.AUTO;
        }

        return config;
    }

    private boolean isValid(RoguesDenScript.Config config) {
        if (config == null) {
            return false;
        }
        String error = ConfigValidator.validate(
            config.idleMin,
            config.idleMax,
            config.runThreshold,
            config.runRestore,
            config.breakIntervalMin,
            config.breakIntervalMax,
            config.breakLengthMin,
            config.breakLengthMax,
            config.staminaDoseTarget,
            config.staminaDoseThreshold,
            config.minimumHealthPercent
        );
        return error == null;
    }

    private void log(String message) {
        logger.accept(message);
    }

    private static Path defaultConfigPath() {
        return Paths.get(System.getProperty("user.home", "."), ".dreambot", "rogues_den", "config.json");
    }

    private static RoguesDenScript.Config createPreset(boolean ironman) {
        RoguesDenScript.Config config = new RoguesDenScript.Config();
        if (ironman) {
            config.staminaDoseTarget = 10;
            config.staminaDoseThreshold = 3;
            config.minimumHealthPercent = 35;
            config.preferredFoodItem = "Swordfish";
            config.runThreshold = 25;
            config.runRestore = 45;
        } else {
            config.staminaDoseTarget = 12;
            config.staminaDoseThreshold = 4;
            config.minimumHealthPercent = 0;
            config.preferredFoodItem = "";
            config.runThreshold = 20;
            config.runRestore = 40;
        }
        return config;
    }

    private static class ProfileBundle {
        RoguesDenScript.Config standard;
        RoguesDenScript.Config ironman;
    }

    public enum Source {
        SAVED,
        DEFAULT,
        NONE;

        String describe(boolean ironman) {
            switch (this) {
                case SAVED:
                    return "saved settings";
                case DEFAULT:
                    return ironman ? "default ironman profile" : "default profile";
                case NONE:
                default:
                    return "no configuration";
            }
        }
    }

    public static class LoadResult {
        private final RoguesDenScript.Config config;
        private final Source source;
        private final boolean ironman;

        LoadResult(RoguesDenScript.Config config, Source source, boolean ironman) {
            this.config = config;
            this.source = Objects.requireNonNull(source);
            this.ironman = ironman;
        }

        public RoguesDenScript.Config getConfig() {
            return config;
        }

        public Source getSource() {
            return source;
        }

        public boolean isIronmanProfile() {
            return ironman;
        }
    }
}
