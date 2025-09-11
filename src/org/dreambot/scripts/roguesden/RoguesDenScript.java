package org.dreambot.scripts.roguesden;

import org.dreambot.api.methods.Calculations;
import org.dreambot.api.methods.container.impl.Inventory;
import org.dreambot.api.methods.interactive.GameObjects;
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
// --- merged, conflict-free section ---
import org.dreambot.api.wrappers.interactive.NPC;
import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.List;
import org.dreambot.api.wrappers.items.Item; // from main branch

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
    new MazeStep(new org.dreambot.api.methods.map.Tile(3047, 4973, 1), Interaction.OPEN,    "Door"),
    new MazeStep(new org.dreambot.api.methods.map.Tile(3048, 4970, 1), Interaction.CLIMB,   "Rubble"),
    new MazeStep(new org.dreambot.api.methods.map.Tile(3050, 4970, 1), Interaction.SQUEEZE, "Gap"),
    new MazeStep(new org.dreambot.api.methods.map.Tile(3052, 4968, 1), Interaction.DISARM,  "Trap"),
    new MazeStep(new org.dreambot.api.methods.map.Tile(3054, 4968, 1), Interaction.SEARCH,  "Crate")
};
private final MazeStep[] MAZE_PLAN = MAZE_STEPS; // alias: either name works

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


// --- end merged section ---


import javax.swing.SwingUtilities;
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

// --- merged, conflict-free section ---
private final ABCUtil abc = new ABCUtil();
private final AtomicBoolean guiDone = new AtomicBoolean(false);
private final Area DEN_AREA = new Area(3040, 4970, 3050, 4980, 1); // approximate
private final Tile START_TILE = new Tile(3047, 4975, 1);

/**
 * Represents a single obstacle step within the maze.
 * successTile/animationId are optional and may be unused by handlers.
 */
private static class MazeStep {
    final Tile tile;
    final String name;       // target object name (e.g., "Door", "Gap")
    final String action;     // interaction action (e.g., "Open", "Climb")
    final Tile successTile;  // optional: expected tile after success
    final int animationId;   // optional: expected animation id during action

    MazeStep(Tile tile, String name, String action) {
        this(tile, name, action, null, -1);
    }

    MazeStep(Tile tile, String name, String action, Tile successTile, int animationId) {
        this.tile = tile;
        this.name = name;
        this.action = action;
        this.successTile = successTile;
        this.animationId = animationId;
    }
}

/**
 * Obstacle path through the maze (names/actions match handlers elsewhere).
 */
private final MazeStep[] MAZE_STEPS = new MazeStep[] {
    new MazeStep(new Tile(3047, 4973, 1), "Door",   "Open"),
    new MazeStep(new Tile(3048, 4970, 1), "Rubble", "Climb"),
    new MazeStep(new Tile(3050, 4970, 1), "Gap",    "Squeeze-through"),
    new MazeStep(new Tile(3052, 4968, 1), "Trap",   "Disarm"),
    new MazeStep(new Tile(3054, 4968, 1), "Crate",  "Search")
};
// --- end merged section ---

    };

    private final ABCUtil abc = new ABCUtil();
    private final AtomicBoolean guiDone = new AtomicBoolean(false);
    private final Area DEN_AREA = new Area(3040, 4970, 3050, 4980, 1);
    private final Tile START_TILE = new Tile(3047, 4975, 1);

    private int step = 0;
    private Config config = new Config();
    private RoguesDenGUI gui;
    private boolean ironman;
private static final int FAILURE_THRESHOLD = 3;

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
        });

        new Thread(() -> {
            while (!guiDone.get()) {
                Sleep.sleep(100);
            }
            prepareSupplies();
        }).start();
    }

    private boolean meetsRequirements() {
        return getSkills().getRealLevel(Skill.THIEVING) >= 50 &&
               getSkills().getRealLevel(Skill.AGILITY) >= 50;
    }

    @Override
    public int onLoop() {
        if (!guiDone.get()) return 600;

if (!suppliesReady) {
    suppliesReady = prepareSupplies();
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
                getWalking().walk(START_TILE);
                Sleep.sleepUntil(() -> DEN_AREA.contains(getLocalPlayer()), 12000);
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

    private void handleRest() {
        log("Waiting for run energy...");
        if (config.useStamina && Inventory.contains(i -> i.getName().contains("Stamina potion"))) {
            Inventory.get(i -> i.getName().contains("Stamina potion")).interact("Drink");
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

    // Unified, instrumented obstacle handler (merging both branches)
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

        // Start condition: movement, animation, or expected animation id (if provided)
        boolean started = Sleep.sleepUntil(
            () -> getLocalPlayer().isMoving()
               || getLocalPlayer().isAnimating()
               || (stepDef.animationId != -1 && getLocalPlayer().getAnimation() == stepDef.animationId),
            3000
        );
        if (!started) {
            obstacleFailed(stepDef.name, "no " + stepDef.action.toLowerCase() + " animation/move");
            return;
        }

        // Wait until action completes
        Sleep.sleepUntil(() -> !getLocalPlayer().isMoving() && !getLocalPlayer().isAnimating(), 5000);

        boolean moved = getLocalPlayer().distance(before) > 1;
        boolean atSuccessTile = stepDef.successTile != null && getLocalPlayer().distance(stepDef.successTile) <= 1;

        if (!moved && !atSuccessTile) {
            obstacleFailed(stepDef.name, "position unchanged");
            return;
        }

        // Success
        step++;
        lastSafeTile = getLocalPlayer().getTile();
        failureCount = 0;
        AntiBan.sleepReaction(abc);
    }

    // Specialized handler for Squeeze/Squeeze-through gaps
    private void handleSqueeze(MazeStep s) {
        GameObject obj = GameObjects.closest(o ->
            o != null
            && s != null
            && s.name.equals(o.getName())
            && (o.hasAction("Squeeze") || o.hasAction("Squeeze-through"))
        );

        boolean ok = obj != null && (
            obj.hasAction("Squeeze") ? obj.interact("Squeeze")
                                     : obj.interact("Squeeze-through")
        );

        if (!ok) {
            obstacleFailed(s != null ? s.name : "SQUEEZE", "interaction failed");
            return;
        }

        Tile before = getLocalPlayer().getTile();

        if (!Sleep.sleepUntil(() -> getLocalPlayer().isMoving() || getLocalPlayer().isAnimating(), 3000)) {
            obstacleFailed(s != null ? s.name : "SQUEEZE", "no squeeze movement");
            return;
        }

        Sleep.sleepUntil(() -> !getLocalPlayer().isMoving() && !getLocalPlayer().isAnimating(), 5000);

        if (getLocalPlayer().distance(before) <= 1) {
            obstacleFailed(s != null ? s.name : "SQUEEZE", "position unchanged");
            return;
        }

        step++;
        lastSafeTile = getLocalPlayer().getTile();
        failureCount = 0;
        AntiBan.sleepReaction(abc);
    }

    // Specialized handler for Search-only interactions (may not move the player)
    private void handleSearch(MazeStep s) {
        GameObject obj = GameObjects.closest(o ->
            o != null
            && s != null
            && s.name.equals(o.getName())
            && o.hasAction("Search")
        );

        if (obj == null || !obj.interact("Search")) {
            obstacleFailed(s != null ? s.name : "SEARCH", "interaction failed");
            return;
        }

        if (!Sleep.sleepUntil(() -> getLocalPlayer().isAnimating(), 3000)) {
            obstacleFailed(s != null ? s.name : "SEARCH", "no search animation");
            return;
        }

        Sleep.sleepUntil(() -> !getLocalPlayer().isAnimating(), 5000);

        step++;
        lastSafeTile = getLocalPlayer().getTile();
        failureCount = 0;
        AntiBan.sleepReaction(abc);
    }

    // Failure handling with threshold/backtrack
    private void obstacleFailed(String obstacleName, String reason) {
        failureCount++;
        log("Obstacle " + obstacleName + " failed: " + reason);
        if (failureCount > FAILURE_THRESHOLD) {
            log("Failure threshold exceeded, returning to last safe tile");
            getWalking().walk(lastSafeTile);
            Sleep.sleepUntil(() -> getLocalPlayer().distance(lastSafeTile) <= 2, 6000);
            step = 0;
            failureCount = 0;
        }
    }

    // Compatibility shims for older, specialized handlers
    private void handleOpen(MazeStep s) {
        if (s != null && (s.action == null || s.action.isEmpty())) s.action = "Open";
        handleObstacle(s);
    }

    private void handleClimb(MazeStep s) {
        if (s != null && (s.action == null || s.action.isEmpty())) s.action = "Climb";
        handleObstacle(s);
    }

    private void handlePush(MazeStep s) {
        if (s != null && (s.action == null || s.action.isEmpty())) s.action = "Push";
        handleObstacle(s);
    }


    private void failObstacle(String name) {
        log("DEBUG: obstacle failed -> " + name);
        step = 0;
    }

    private void recoverMaze() {
        log("Recovering maze...");
        getWalking().walk(START_TILE);
        Sleep.sleepUntil(() -> getLocalPlayer().distance(START_TILE) <= 2, 6000);
        step = 0;
    }

// --- merged, conflict-free section ---

private boolean handleRewards() {
    if (Inventory.count(TOKEN_NAME) < 1) {
        return false;
    }
    int attempts = 0;
    while (Inventory.count(TOKEN_NAME) >= 1 && attempts < 3) {
        org.dreambot.api.wrappers.interactive.NPC npc = NPCs.closest(REWARD_NPC);
        if (npc != null && npc.interact("Claim")) {
            boolean success = Sleep.sleepUntil(
                () -> Inventory.contains(i -> i != null && i.getName() != null && isRogueGear(i.getName())),
                5000
            );
            if (success) {
                if (hasFullRogueSet()) {
                    log("Full rogue set obtained. Stopping script.");
                    ScriptManager.getScriptManager().stop();
                }
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
    return true; // non-blocking: continue script even if rewards failed for now
}

private boolean isRogueGear(String name) {
    return name != null && java.util.Arrays.asList(GEAR_ITEMS).contains(name);
}

private boolean hasFullRogueSet() {
    java.util.List<String> missing = new java.util.ArrayList<>();
    for (String item : GEAR_ITEMS) {
        if (!Inventory.contains(item)) {
            missing.add(item);
        }
    }
    if (missing.isEmpty()) {
        return true;
    }

    boolean opened = false;
    if (!getBank().isOpen()) {
        if (!getBank().openClosest()) {
            log("Could not open bank to verify rogue set.");
            return false;
        }
        Sleep.sleepUntil(() -> getBank().isOpen(), 5000);
        opened = true;
    }

    boolean allPresent = missing.stream().allMatch(i -> getBank().contains(i));

    if (opened) {
        getBank().close();
        Sleep.sleepUntil(() -> !getBank().isOpen(), 2000);
    }

    return allPresent;
}

/**
 * Ensures required supplies are available. Returns true if we're good to proceed.
 */
private boolean prepareSupplies() {
    if (suppliesReady) return true;

    // Ensure we have the full Rogue set either on us or available in bank
    boolean haveSetInInv = true;
    for (String item : GEAR_ITEMS) {
        if (!Inventory.contains(item)) { haveSetInInv = false; break; }
    }
    if (!haveSetInInv && !hasFullRogueSet()) {
        log("Missing Rogue gear pieces and not all found in bank.");
        return false;
    }

    // Make sure we have at least one stamina potion for run energy management
    org.dreambot.api.wrappers.items.Item stamina = Inventory.get(i -> {
        String n = (i == null) ? null : i.getName();
        return n != null && n.contains("Stamina potion");
    });

    boolean opened = false;
    if (stamina == null) {
        if (!getBank().isOpen()) {
            if (!getBank().openClosest()) {
                log("Could not open bank to withdraw supplies.");
                return false;
            }
            Sleep.sleepUntil(() -> getBank().isOpen(), 5000);
            opened = true;
        }

        if (getBank().contains(i -> i != null && i.getName() != null && i.getName().contains("Stamina potion"))) {
            // Withdraw one potion (any dose)
            getBank().withdraw(i -> i != null && i.getName() != null && i.getName().contains("Stamina potion"), 1);
            Sleep.sleepUntil(() ->
                Inventory.contains(i -> i != null && i.getName() != null && i.getName().contains("Stamina potion")),
                2000
            );
        } else {
            log("No stamina potions available in bank.");
        }
    }

    if (opened) {
        getBank().close();
        Sleep.sleepUntil(() -> !getBank().isOpen(), 2000);
    }

    suppliesReady = true;
    return true;
}

// --- end merged section ---

        int attempts = 0;
        boolean success = false;
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
                return false;
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

// Stamina potions (if configured)
if (config.useStamina && !Inventory.contains(i -> i != null && i.getName() != null && i.getName().contains("Stamina potion"))) {
    boolean withdrew = getBank().withdrawAll(i -> i != null && i.getName() != null && i.getName().contains("Stamina potion"));
}

                if (withdrew) {
                    Sleep.sleepUntil(() -> Inventory.contains(i -> i.getName().contains("Stamina potion")), 2000);
                    if (!Inventory.contains(i -> i.getName().contains("Stamina potion"))) {
                        log("Failed to confirm stamina potions in inventory after withdrawal.");
                    }
                } else {
                    log("Bank failed to withdraw stamina potions.");
                }
            }
            success = true;
        } finally {
            if (getBank().isOpen()) {
                getBank().close();
                Sleep.sleepUntil(() -> !getBank().isOpen(), 2000);
            }
        }
        return success;
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

