package org.dreambot.scripts.roguesden;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConfigValidatorTest {

    @Test
    void validConfigurationPasses() {
        String error = ConfigValidator.validate(100, 200, 20, 40, 30, 60, 1, 5);
        assertNull(error);
    }

    @Test
    void invalidRunEnergyFails() {
        String error = ConfigValidator.validate(100, 200, 50, 40, 30, 60, 1, 5);
        assertNotNull(error);
    }

    @Test
    void invalidIdleRangeFails() {
        String error = ConfigValidator.validate(-1, 200, 20, 40, 30, 60, 1, 5);
        assertNotNull(error);
    }
}
