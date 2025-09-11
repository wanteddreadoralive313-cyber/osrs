package org.dreambot.scripts.roguesden;

import org.dreambot.api.methods.Calculations;
import org.dreambot.api.methods.tabs.Tab;
import org.dreambot.api.script.AbstractScript;
import org.dreambot.api.utilities.impl.ABCUtil;
import org.dreambot.api.utilities.sleep.Sleep;
import org.dreambot.api.wrappers.interactive.GameObject;

public class AntiBan {
    public static void permute(AbstractScript script, ABCUtil abc, RoguesDenScript.Config config) {
        if (!config.antiban) return;

        // Perform built-in timed actions
        abc.performTimedActions();

        if (abc.shouldCheckXP()) {
            script.getSkills().hoverSkill(org.dreambot.api.methods.skills.Skill.AGILITY);
        }

        if (abc.shouldOpenTab()) {
            script.getTabs().openWithMouse(Tab.INVENTORY);
        }

        // Hover a nearby entity if enabled
        if (config.hoverEntities && abc.shouldHover()) {
            GameObject g = script.getGameObjects().closest(o -> o != null);
            if (g != null) {
                g.hover();
            }
        }

        // Random right-click if enabled
        if (config.randomRightClick && abc.shouldOpenMenu()) {
            script.getMouse().click(false);
        }

        // Timed camera panning
        if (config.cameraPanning && abc.shouldRotateCamera()) {
            script.getCamera().rotateToYaw(Calculations.random(0, 2048));
            script.getCamera().rotateToPitch(Calculations.random(300, 400));
        }

        // Occasionally move mouse off screen
        if (Calculations.random(0, 40) == 0) {
            script.getMouse().moveMouseOutsideScreen();
        }

        // Optional idle delay
        if (config.idleMax > 0 && Calculations.random(0, 100) < 10) {
            Sleep.sleep(config.idleMin, config.idleMax);
        }
    }
}
