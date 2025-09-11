package org.dreambot.scripts.roguesden;

import org.dreambot.api.methods.Calculations;
import org.dreambot.api.methods.input.Mouse;
import org.dreambot.api.methods.tabs.Tab;
import org.dreambot.api.script.AbstractScript;
import org.dreambot.api.utilities.impl.ABCUtil;

public class AntiBan {
    public static void permute(AbstractScript script, ABCUtil abc, boolean enabled) {
        if (!enabled) return;
        abc.performTimedActions();
        if (abc.shouldCheckXP()) script.getSkills().hoverSkill(org.dreambot.api.methods.skills.Skill.AGILITY);
        if (abc.shouldOpenTab()) script.getTabs().openWithMouse(Tab.INVENTORY);
        if (Calculations.random(0,40) == 0) script.getMouse().moveMouseOutsideScreen();
        if (Calculations.random(0,50) == 0) script.getCamera().rotateToPitch(Calculations.random(300,400));
    }
}
