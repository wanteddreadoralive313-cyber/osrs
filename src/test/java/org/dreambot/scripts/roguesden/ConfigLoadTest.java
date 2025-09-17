package org.dreambot.scripts.roguesden;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ConfigLoadTest {

    private static final String[] PROPERTY_KEYS = {
        "roguesden.configFile",
        "roguesden.config",
        "roguesden.useStamina",
        "roguesden.antiban",
        "roguesden.hoverEntities",
        "roguesden.randomRightClick",
        "roguesden.cameraPanning",
        "roguesden.idleMin",
        "roguesden.idleMax",
        "roguesden.runThreshold",
        "roguesden.runRestore",
        "roguesden.breakIntervalMin",
        "roguesden.breakIntervalMax",
        "roguesden.breakLengthMin",
        "roguesden.breakLengthMax"
    };

    @AfterEach
    void clearProperties() {
        for (String key : PROPERTY_KEYS) {
            System.clearProperty(key);
        }
    }

    @Test
    void emptyWhenNoExternalConfigProvided() {
        Optional<RoguesDenScript.Config> config = RoguesDenScript.Config.loadFromFileOrSystem();
        assertTrue(config.isEmpty());
    }

    @Test
    void loadsFromSystemProperties() {
        System.setProperty("roguesden.useStamina", "false");
        System.setProperty("roguesden.idleMin", "500");
        System.setProperty("roguesden.idleMax", "600");
        System.setProperty("roguesden.runThreshold", "15");
        System.setProperty("roguesden.runRestore", "35");

        Optional<RoguesDenScript.Config> configOpt = RoguesDenScript.Config.loadFromFileOrSystem();
        assertTrue(configOpt.isPresent());

        RoguesDenScript.Config config = configOpt.get();
        assertFalse(config.useStamina);
        assertEquals(500, config.idleMin);
        assertEquals(600, config.idleMax);
        assertEquals(15, config.runThreshold);
        assertEquals(35, config.runRestore);
    }

    @Test
    void loadsFromFileWithSystemOverride(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("config.properties");
        Files.writeString(file, String.join(System.lineSeparator(),
            "useStamina=false",
            "idleMin=250",
            "idleMax=750",
            "breakIntervalMin=10",
            "breakIntervalMax=20"
        ));

        System.setProperty("roguesden.configFile", file.toString());
        System.setProperty("roguesden.idleMax", "800");

        Optional<RoguesDenScript.Config> configOpt = RoguesDenScript.Config.loadFromFileOrSystem();
        assertTrue(configOpt.isPresent());

        RoguesDenScript.Config config = configOpt.get();
        assertFalse(config.useStamina);
        assertEquals(250, config.idleMin);
        assertEquals(800, config.idleMax);
        assertEquals(10, config.breakIntervalMin);
        assertEquals(20, config.breakIntervalMax);
    }

    @Test
    void invalidIntegerThrows() {
        System.setProperty("roguesden.idleMin", "abc");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            RoguesDenScript.Config::loadFromFileOrSystem);

        assertTrue(exception.getMessage().contains("idleMin"));
    }
}
