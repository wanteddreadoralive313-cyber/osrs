package org.dreambot.scripts.roguesden;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MazeStepProgressionTest {

    @Test
    void stepsIncrementUntilAllCompleted() {
        RoguesDenScript script = new RoguesDenScript();
        int total = script.getMazeStepCount();
        for (int i = 0; i < total; i++) {
            assertEquals(i, script.getStep());
            script.incrementStep();
        }
        assertEquals(total, script.getStep());
    }
}
