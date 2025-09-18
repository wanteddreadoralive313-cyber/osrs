package org.dreambot.scripts.roguesden;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConfigValidatorTest {

    @Test
    void validConfigurationPasses() {
        String error = ConfigValidator.validate(100, 200, 20, 40, 30, 60, 1, 5, 50, 25, 10, "Lobster");
        assertNull(error);
    }

    @Test
    void invalidRunEnergyFails() {
        String error = ConfigValidator.validate(100, 200, 50, 40, 30, 60, 1, 5, 50, 25, 10, "Lobster");
        assertNotNull(error);
    }

    @Test
    void invalidIdleRangeFails() {
        String error = ConfigValidator.validate(-1, 200, 20, 40, 30, 60, 1, 5, 50, 25, 10, "Lobster");
        assertNotNull(error);
    }

    @Test
    void invalidBreakRangeFails() {
        String error = ConfigValidator.validate(100, 200, 20, 40, 60, 30, 1, 5, 50, 25, 10, "Lobster");
        assertNotNull(error);
    }

    @Test
    void invalidHpRangeFails() {
        String error = ConfigValidator.validate(100, 200, 20, 40, 30, 60, 1, 5, 30, 40, 10, "Lobster");
        assertNotNull(error);
    }

    @Test
    void missingFoodNameFailsWhenQuantityPositive() {
        String error = ConfigValidator.validate(100, 200, 20, 40, 30, 60, 1, 5, 50, 25, 10, "");
        assertNotNull(error);
    }

    @Test
    void zeroQuantityAllowedWhenFoodDisabled() {
        String error = ConfigValidator.validate(100, 200, 20, 40, 30, 60, 1, 5, 50, 25, 0, "");
        assertNull(error);
    }

    @Test
    void negativeFoodQuantityFails() {
        String error = ConfigValidator.validate(100, 200, 20, 40, 30, 60, 1, 5, 50, 25, -1, "");
        assertNotNull(error);
    }
}
