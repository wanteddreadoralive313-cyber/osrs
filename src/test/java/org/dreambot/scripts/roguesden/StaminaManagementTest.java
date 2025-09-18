package org.dreambot.scripts.roguesden;

import org.dreambot.api.utilities.impl.ABCUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class StaminaManagementTest {

    @Test
    void calculatesWithdrawalsAndRestockThresholds() {
        RoguesDenScript.Config config = new RoguesDenScript.Config();
        config.staminaDoseTarget = 12;
        config.staminaDoseThreshold = 4;

        RoguesDenScript script = new RoguesDenScript(mock(ABCUtil.class), config);

        assertEquals(3, script.getRequiredStaminaPotions(0));
        assertEquals(1, script.getRequiredStaminaPotions(9));
        assertTrue(script.shouldRestockStamina(0));
        assertTrue(script.shouldRestockStamina(3));
        assertFalse(script.shouldRestockStamina(4));
    }
}
