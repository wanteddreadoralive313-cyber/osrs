package org.dreambot.scripts.roguesden;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ConfigProfileManagerTest {

    private static final Gson GSON = new Gson();

    @Test
    void usesSavedProfileWhenPresent(@TempDir Path tempDir) throws IOException {
        Path configPath = tempDir.resolve("saved.json");
        RoguesDenScript.Config saved = new RoguesDenScript.Config();
        saved.runThreshold = 15;
        saved.runRestore = 45;
        saved.staminaDoseTarget = 8;
        saved.staminaDoseThreshold = 2;
        Files.write(configPath, GSON.toJson(saved).getBytes(StandardCharsets.UTF_8));

        ConfigProfileManager manager = new ConfigProfileManager(configPath, message -> { });
        ConfigProfileManager.LoadResult result = manager.loadConfig(false);

        assertNotNull(result.getConfig());
        assertEquals(ConfigProfileManager.Source.SAVED, result.getSource());
        assertEquals(15, result.getConfig().runThreshold);
        assertEquals(8, result.getConfig().staminaDoseTarget);
    }

    @Test
    void fallsBackToDefaultWhenNoSavedProfile(@TempDir Path tempDir) {
        Path missingPath = tempDir.resolve("missing.json");
        ConfigProfileManager manager = new ConfigProfileManager(missingPath, message -> { });

        ConfigProfileManager.LoadResult result = manager.loadConfig(false);

        assertEquals(ConfigProfileManager.Source.DEFAULT, result.getSource());
        assertEquals(12, result.getConfig().staminaDoseTarget);
        assertEquals(0, result.getConfig().minimumHealthPercent);
    }

    @Test
    void loadsIronmanDefaults(@TempDir Path tempDir) {
        Path configPath = tempDir.resolve("iron.json");
        ConfigProfileManager manager = new ConfigProfileManager(configPath, message -> { });

        ConfigProfileManager.LoadResult result = manager.loadConfig(true);

        assertEquals(ConfigProfileManager.Source.DEFAULT, result.getSource());
        assertEquals(35, result.getConfig().minimumHealthPercent);
        assertEquals("Swordfish", result.getConfig().preferredFoodItem);
        assertEquals(3, result.getConfig().staminaDoseThreshold);
    }

    @Test
    void invalidSavedProfileFallsBackToDefault(@TempDir Path tempDir) throws IOException {
        Path configPath = tempDir.resolve("invalid.json");
        RoguesDenScript.Config invalid = new RoguesDenScript.Config();
        invalid.runThreshold = 60;
        invalid.runRestore = 20; // invalid threshold
        Files.write(configPath, GSON.toJson(invalid).getBytes(StandardCharsets.UTF_8));

        ConfigProfileManager manager = new ConfigProfileManager(configPath, message -> { });
        ConfigProfileManager.LoadResult result = manager.loadConfig(false);

        assertEquals(ConfigProfileManager.Source.DEFAULT, result.getSource());
        assertTrue(result.getConfig().runRestore > result.getConfig().runThreshold);
    }
}
