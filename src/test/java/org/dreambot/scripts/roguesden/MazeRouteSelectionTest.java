package org.dreambot.scripts.roguesden;

import org.dreambot.api.utilities.impl.ABCUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class MazeRouteSelectionTest {

    @Test
    void autoModeSelectsShortcutAtEightyThieving() {
        RoguesDenScript.Config config = new RoguesDenScript.Config();
        config.shortcutMode = RoguesDenScript.Config.ShortcutMode.AUTO;

        RoguesDenScript script = new RoguesDenScript(mock(ABCUtil.class), config);
        RoguesDenScript.MazeRoute route = script.determineMazeRouteForLevel(80);

        assertEquals(RoguesDenScript.MazeRoute.SHORTCUT, route);
        assertEquals("Approach shortcut", script.getInstructionLabel(RoguesDenScript.MazeRoute.SHORTCUT, 11));
        assertNotEquals(
            script.getInstructionLabel(RoguesDenScript.MazeRoute.SHORTCUT, 11),
            script.getInstructionLabel(RoguesDenScript.MazeRoute.LONG, 11)
        );
    }
}
