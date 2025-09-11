package org.dreambot.scripts.roguesden;

import org.dreambot.api.methods.Calculations;
import org.dreambot.api.methods.tabs.Tab;
import org.dreambot.api.script.AbstractScript;
import org.dreambot.api.utilities.impl.ABCUtil;
import org.dreambot.api.utilities.sleep.Sleep;
import org.dreambot.api.wrappers.interactive.GameObject;

public class AntiBan {
    // Next time (in ms since epoch) to take a break
    private static long nextBreakTime = System.currentTimeMillis() + randomInterval();

    private static long randomInterval() {
        // Random interval between 10 and 20 minutes
        return Calculations.random(600_000, 1_200_000);
    }

    private static void scheduleNextBreak() {
        nextBreakTime = System.currentTimeMillis() + randomInterval();
    }

    private static void handleBreak(AbstractScript script) {
        if (System.currentTimeMillis() < nextBreakTime) {
            return;
        }

        long breakLength = Calculations.random(60_000, 180_000); // 1-3 minutes
        boolean logout = Calculations.random(0, 2) == 0;
        script.log("Taking a break for " + (breakLength / 1000) + "s " + (logout ? "(logging out)" : "(idling)"));
        if (logout) {
            script.getTabs().logout();
        }
        Sleep.sleep(breakLength);
        scheduleNextBreak();
    }

    public static void permute(AbstractScript script, ABCUtil abc, RoguesDenScript.Config config) {
        if (!config.antiban) return;

        handleBreak(script);

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
