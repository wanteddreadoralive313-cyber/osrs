package org.dreambot.scripts.roguesden;

import org.dreambot.api.utilities.impl.ABCUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class MazeStepProgressionTest {

    @Test
    void stepsIncrementUntilAllCompleted() {
        RoguesDenScript script = new RoguesDenScript(mock(ABCUtil.class), new RoguesDenScript.Config());
        int total = script.getMazeStepCount();
        for (int i = 0; i < total; i++) {
            assertEquals(i, script.getStep());
            script.incrementStep();
        }
        assertEquals(total, script.getStep());
    }
}
