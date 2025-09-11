package org.dreambot.scripts.roguesden;

import org.dreambot.api.methods.Calculations;
import org.dreambot.api.methods.container.impl.Inventory;
import org.dreambot.api.methods.interactive.GameObjects;
import org.dreambot.api.methods.interactive.NPCs;
import org.dreambot.api.methods.map.Area;
import org.dreambot.api.methods.map.Tile;
import org.dreambot.api.methods.skills.Skill;
import org.dreambot.api.script.AbstractScript;
import org.dreambot.api.script.Category;
import org.dreambot.api.script.ScriptManager;
import org.dreambot.api.script.ScriptManifest;
import org.dreambot.api.utilities.impl.ABCUtil;
import org.dreambot.api.utilities.sleep.Sleep;
import org.dreambot.api.wrappers.interactive.GameObject;
import org.dreambot.api.wrappers.interactive.NPC;
// --- merged, conflict-free section ---

import org.dreambot.api.methods.magic.Normal;
import javax.swing.SwingUtilities;
import org.dreambot.api.wrappers.items.Item; // kept from main

private static final String TOKEN_NAME = "Rogue's reward token";
private static final String REWARD_NPC = "Rogue";
private static final String[] GEAR_ITEMS = {
    "Rogue mask", "Rogue top", "Rogue trousers", "Rogue gloves", "Rogue boots"
};

// Optional enum for readability when defining steps
private enum Interaction { OPEN, CLIMB, SQUEEZE, SEARCH, DISARM }

// Unified MazeStep definition compatible with the rest of the codebase
private static class MazeStep {
    final org.dreambot.api.methods.map.Tile tile;
    final String name;   // target object name (e.g., "Door", "Gap")
    final String action; // interaction action (e.g., "Open", "Climb", "Squeeze-through", "Search", "Disarm")

    MazeStep(org.dreambot.api.methods.map.Tile tile, String name, String action) {
        this.tile = tile;
        this.name = name;
        this.action = action;
    }

    // Convenience constructor to support the Interaction enum style
    MazeStep(org.dreambot.api.methods.map.Tile tile, Interaction interaction, String obstacle) {
        this(tile, obstacle, toAction(interaction));
    }

    private static String toAction(Interaction i) {
        switch (i) {
            case OPEN:    return "Open";
            case CLIMB:   return "Climb";
            case SQUEEZE: return "Squeeze-through";
            case SEARCH:  return "Search";
            case DISARM:  return "Disarm";
            default:      return "Use";
        }
    }
}

// Use MAZE_STEPS (expected elsewhere in the code). Also expose MAZE_PLAN as an alias for compatibility.
private final MazeStep[] MAZE_STEPS = new MazeStep[] {
    new MazeStep(new org.dreambot.api.methods.map.Tile(3047, 4973, 1), Interaction.OPEN,   "Door"),
    new MazeStep(new org.dreambot.api.methods.map.Tile(3048, 4970, 1), Interaction.CLIMB,  "Rubble"),
    new MazeStep(new org.dreambot.api.methods.map.Tile(3050, 4970, 1), Interaction.SQUEEZE,"Gap"),
    new MazeStep(new org.dreambot.api.methods.map.Tile(3052, 4968, 1), Interaction.DISARM, "Trap"),
    new MazeStep(new org.dreambot.api.methods.map.Tile(3054, 4968, 1), Interaction.SEARCH, "Crate")
};
private final MazeStep[] MAZE_PLAN = MAZE_STEPS; // alias: keeps either name working

private int step = 0;
private final Config config = new Config();
private RoguesDenGUI gui;
private boolean ironman;
private boolean suppliesReady;

@Override
public void onStart() {
    log("Starting Rogues' Den script");
    if (!meetsRequirements()) {
        log("Account doesn't meet Rogues' Den requirements.");
        ScriptManager.getScriptManager().stop();
        return;
    }
    ironman = getClient().isIronMan();
    SwingUtilities.invokeLater(() -> {
        gui = new RoguesDenGUI(config, guiDone);
        gui.setVisible(true);
    });
}

private boolean meetsRequirements() {
    return getSkills().getRealLevel(Skill.THIEVING) >= 50
        && getSkills().getRealLevel(Skill.AGILITY) >= 50;
}
// --- end merged section ---


import javax.swing.SwingUtilities;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

@ScriptManifest(category = Category.AGILITY, name = "RoguesDen", author = "Assistant", version = 1.0)
public class RoguesDenScript extends AbstractScript {

    private static final String TOKEN_NAME = "Rogue's reward token";
    private static final String REWARD_NPC = "Rogue";
    private static final String[] GEAR_ITEMS = {
            "Rogue mask", "Rogue top", "Rogue trousers", "Rogue gloves", "Rogue boots"
    };

    private static class MazeStep {
        final Tile tile;
        final String name;
        final String action;

        MazeStep(Tile tile, String name, String action) {
            this.tile = tile;
            this.name = name;
            this.action = action;
        }
    }

    private final MazeStep[] MAZE_STEPS = new MazeStep[]{
            new MazeStep(new Tile(3047, 4973, 1), "Door", "Open"),
            new MazeStep(new Tile(3048, 4970, 1), "Rubble", "Climb"),
            new MazeStep(new Tile(3050, 4970, 1), "Gap", "Squeeze-through"),
            new MazeStep(new Tile(3052, 4968, 1), "Trap", "Disarm"),
            new MazeStep(new Tile(3054, 4968, 1), "Crate", "Search")
    };

    private final ABCUtil abc = new ABCUtil();
    private final AtomicBoolean guiDone = new AtomicBoolean(false);
    private final Area DEN_AREA = new Area(3040, 4970, 3050, 4980, 1);
    private final Tile START_TILE = new Tile(3047, 4975, 1);

    private int step = 0;
    private Config config = new Config();
    private RoguesDenGUI gui;
    private boolean ironman;
    private boolean suppliesReady;
    private int failureCount = 0;
    private Tile lastSafeTile = START_TILE;

    private enum State { TRAVEL, MAZE, REST }

    @Override
    public void onStart() {
        log("Starting Rogues' Den script");
        if (!meetsRequirements()) {
            log("Account doesn't meet Rogues' Den requirements.");
            ScriptManager.getScriptManager().stop();
            return;
        }
        ironman = getClient().isIronMan();
        SwingUtilities.invokeLater(() -> {
            gui = new RoguesDenGUI(config, guiDone);
            gui.setVisible(true);
            abc.generateTrackers();
        });
    }

    private boolean meetsRequirements() {
        return getSkills().getRealLevel(Skill.THIEVING) >= 50 &&
               getSkills().getRealLevel(Skill.AGILITY) >= 50;
    }

    @Override
    public int onLoop() {
        if (!guiDone.get()) return 600;

        if (!suppliesReady) {
            prepareSupplies();
            suppliesReady = true;
            return 600;
        }

        if (!getWalking().isRunEnabled() && getWalking().getRunEnergy() >= config.runRestore) {
            getWalking().toggleRun(true);
        }

        AntiBan.permute(this, abc, config);

        if (handleRewards()) {
            return Calculations.random(600, 900);
        }

        State state = getState();
        switch (state) {
            case TRAVEL:
                // merged: prefer robust travel method, with fallback to direct walk
                handleTravel();
                if (!DEN_AREA.contains(getLocalPlayer())) {
                    getWalking().walk(START_TILE);
                    Sleep.sleepUntil(() -> DEN_AREA.contains(getLocalPlayer()), 12000);
                }
                return Calculations.random(300, 600);

            case REST:
                handleRest();
                return Calculations.random(600, 900);
            case MAZE:
                if (!getLocalPlayer().isAnimating() && !getLocalPlayer().isMoving()) {
                    handleMaze();
                }
                return Calculations.random(200, 400);
        }
        return Calculations.random(200, 400);
    }

    private State getState() {
        if (!DEN_AREA.contains(getLocalPlayer()))
            return State.TRAVEL;
        if (needsRest())
            return State.REST;
        return State.MAZE;
    }

    private boolean needsRest() {
        return getWalking().getRunEnergy() < config.runThreshold;
    }

    private void handleTravel() {
        if (DEN_AREA.contains(getLocalPlayer())) {
            return;
        }

        int failures = 0;
        while (!DEN_AREA.contains(getLocalPlayer())) {
            // wait out any teleport animations
            if (getLocalPlayer().isAnimating()) {
                Sleep.sleepUntil(() -> !getLocalPlayer().isAnimating(), 15000);
                continue;
            }

            // after several failed path attempts try teleporting
            if (failures >= 3 && getMagic().canCast(Normal.HOME_TELEPORT)) {
                log("Teleporting closer to the Rogues' Den...");
                if (getMagic().castSpell(Normal.HOME_TELEPORT)) {
                    Sleep.sleepUntil(() -> !getLocalPlayer().isAnimating(), 30000);
                }
                failures = 0;
                continue;
            }

            if (!getWalking().walk(START_TILE)) {
                failures++;
                log("Failed to generate path to den (" + failures + ")");
                Sleep.sleep(600, 900);
            } else {
                Sleep.sleepUntil(() -> DEN_AREA.contains(getLocalPlayer()) || !getLocalPlayer().isMoving(), 15000);
            }
        }
    }

    private void handleRest() {
        log("Waiting for run energy...");
        if (config.useStamina && Inventory.contains(i -> {
            String n = i.getName();
            return n != null && n.contains("Stamina potion");
        })) {
            Item stamina = Inventory.get(i -> {
                String n = i.getName();
                return n != null && n.contains("Stamina potion");
            });
            if (stamina != null) {
                stamina.interact("Drink");
            }
            Sleep.sleepUntil(() -> getWalking().getRunEnergy() > config.runRestore, 3000);
        } else {
            Sleep.sleepUntil(() -> getWalking().getRunEnergy() > config.runRestore, 60000);
        }
    }

    private void handleMaze() {
        if (getLocalPlayer().distance(START_TILE) <= 1 && step > 0) {
            recoverMaze();
            return;
        }

        if (step >= MAZE_STEPS.length) {
            step = 0;
            return;
        }

        MazeStep current = MAZE_STEPS[step];
        if (getLocalPlayer().distance(current.tile) > 2) {
            getWalking().walk(current.tile);
            Sleep.sleepUntil(() -> getLocalPlayer().distance(current.tile) <= 2, 5000);
            return;
        }

        handleObstacle(current);
    }

    private void handleObstacle(MazeStep stepDef) {
        if (stepDef == null || stepDef.name == null || stepDef.action == null) {
            obstacleFailed(stepDef != null ? String.valueOf(stepDef.name) : "Unknown", "invalid MazeStep");
            return;
        }

        GameObject obj = GameObjects.closest(o ->
                o != null &&
                stepDef.name.equals(o.getName()) &&
                o.hasAction(stepDef.action)
        );

        if (obj == null) {
            obstacleFailed(stepDef.name, "object not found");
            return;
        }

        Tile before = getLocalPlayer().getTile();

        if (!obj.interact(stepDef.action)) {
            obstacleFailed(stepDef.name, "interaction failed");
            return;
        }

        if (!Sleep.sleepUntil(() -> getLocalPlayer().isMoving() || getLocalPlayer().isAnimating(), 3000)) {
            obstacleFailed(stepDef.name, "no " + stepDef.action.toLowerCase() + " animation/move");
            return;
        }

        Sleep.sleepUntil(() -> !getLocalPlayer().isMoving() && !getLocalPlayer().isAnimating(), 5000);

        if (getLocalPlayer().distance(before) <= 1) {
            obstacleFailed(stepDef.name, "position unchanged");
            return;
        }

        step++;
        lastSafeTile = getLocalPlayer().getTile();
        AntiBan.sleepReaction(abc);
    }

    private void obstacleFailed(String name, String reason) {
        log("Obstacle " + name + " failed: " + reason);
        failureCount++;
        getWalking().walk(lastSafeTile);
    }

    private void recoverMaze() {
        log("Recovering maze...");
        getWalking().walk(START_TILE);
        Sleep.sleepUntil(() -> getLocalPlayer().distance(START_TILE) <= 2, 6000);
        step = 0;
    }

    private boolean handleRewards() {
        if (Inventory.count(TOKEN_NAME) < 1) {
            return false;
        }
        int attempts = 0;
        while (Inventory.count(TOKEN_NAME) >= 1 && attempts < 3) {
            NPC npc = NPCs.closest(REWARD_NPC);
            if (npc != null && npc.interact("Claim")) {
                boolean success = Sleep.sleepUntil(() -> Inventory.contains(i -> isRogueGear(i.getName())), 5000);
                if (success) {
                    return true;
                } else {
                    log("No gear received, retrying...");
                }
            } else {
                log("Failed to locate reward NPC.");
            }
            attempts++;
            Sleep.sleep(600, 1200);
        }
        if (Inventory.count(TOKEN_NAME) >= 1) {
            log("Failed to obtain gear after multiple attempts.");
        }
        return true;
    }

    private boolean isRogueGear(String name) {
        return name != null && Arrays.asList(GEAR_ITEMS).contains(name);
    }

    private void prepareSupplies() {
        int attempts = 0;
        try {
            while (attempts < 3 && !getBank().isOpen()) {
                if (!getBank().openClosest()) {
                    log("Failed to open closest bank. Retrying...");
                    attempts++;
                    Sleep.sleep(600, 1200);
                    continue;
                }
                Sleep.sleepUntil(() -> getBank().isOpen(), 5000);
            }

            if (!getBank().isOpen()) {
                log("Unable to open bank after multiple attempts. Aborting supply preparation.");
                return;
            }

            if (!ironman && !Inventory.contains("Coins")) {
                if (getBank().withdrawAll("Coins")) {
                    Sleep.sleepUntil(() -> Inventory.contains("Coins"), 2000);
                    if (!Inventory.contains("Coins")) {
                        log("Failed to withdraw coins.");
                    }
                } else {
                    log("Bank failed to withdraw coins.");
                }
            } else if (ironman) {
                log("Ironman account detected, skipping coin withdrawal.");
            }

            if (config.useStamina && !Inventory.contains(i -> {
                String n = i.getName();
                return n != null && n.contains("Stamina potion");
            })) {
                boolean withdrew = getBank().withdrawAll(i -> {
                    String n = i.getName();
                    return n != null && n.contains("Stamina potion");
                });
                if (withdrew) {
                    Sleep.sleepUntil(() -> Inventory.contains(i -> {
                        String n = i.getName();
                        return n != null && n.contains("Stamina potion");
                    }), 2000);
                    if (!Inventory.contains(i -> {
                        String n = i.getName();
                        return n != null && n.contains("Stamina potion");
                    })) {
                        log("Failed to confirm stamina potions in inventory after withdrawal.");
                    }
                } else {
                    log("Bank failed to withdraw stamina potions.");
                }
            }
        } finally {
            if (getBank().isOpen()) {
                getBank().close();
                Sleep.sleepUntil(() -> !getBank().isOpen(), 2000);
            }
        }
    }

    @Override
    public void onExit() {
        if (gui != null) gui.dispose();
    }

    static class Config {
        boolean useStamina = true;
        boolean antiban = true;
        boolean hoverEntities = true;
        boolean randomRightClick = true;
        boolean cameraPanning = true;
        int idleMin = 200;
        int idleMax = 600;
        int runThreshold = 20;
        int runRestore = 40;
    }
}

