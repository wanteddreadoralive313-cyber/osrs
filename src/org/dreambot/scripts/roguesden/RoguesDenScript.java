package org.dreambot.scripts.roguesden;

import org.dreambot.api.script.Category;
import org.dreambot.api.script.ScriptManager;
import org.dreambot.api.script.ScriptManifest;
import org.dreambot.api.script.AbstractScript;
import org.dreambot.api.methods.Calculations;
import org.dreambot.api.methods.container.impl.Inventory;
import org.dreambot.api.methods.interactive.GameObjects;
import org.dreambot.api.methods.interactive.NPCs;
import org.dreambot.api.methods.map.Area;
import org.dreambot.api.methods.map.Tile;
import org.dreambot.api.methods.skills.Skill;
import org.dreambot.api.utilities.impl.ABCUtil;
import org.dreambot.api.utilities.sleep.Sleep;
import org.dreambot.api.wrappers.interactive.GameObject;
import org.dreambot.api.wrappers.interactive.NPC;
import javax.swing.SwingUtilities;

private static final String TOKEN_NAME = "Rogue's reward token";
private static final String REWARD_NPC = "Rogue";
private static final String[] GEAR_ITEMS = {
    "Rogue mask", "Rogue top", "Rogue trousers", "Rogue gloves", "Rogue boots"
};

private enum Interaction { OPEN, CLIMB, SQUEEZE, SEARCH, DISARM }

private static class MazeStep {
    final Tile tile;
    final Interaction interaction;
    final String obstacle;

    MazeStep(Tile tile, Interaction interaction, String obstacle) {
        this.tile = tile;
        this.interaction = interaction;
        this.obstacle = obstacle;
    }
}

private final MazeStep[] MAZE_PLAN = new MazeStep[] {
    new MazeStep(new Tile(3047, 4973, 1), Interaction.OPEN, "Door"),
    new MazeStep(new Tile(3048, 4970, 1), Interaction.CLIMB, "Rubble"),
    new MazeStep(new Tile(3050, 4970, 1), Interaction.SQUEEZE, "Gap"),
    new MazeStep(new Tile(3052, 4968, 1), Interaction.DISARM, "Trap"),
    new MazeStep(new Tile(3054, 4968, 1), Interaction.SEARCH, "Crate")
};

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

import javax.swing.SwingUtilities;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

@ScriptManifest(category = Category.AGILITY, name = "RoguesDen", author = "Assistant", version = 1.0)
public class RoguesDenScript extends AbstractScript {

    private enum State { TRAVEL, MAZE, REST }

    private final ABCUtil abc = new ABCUtil();
    private final AtomicBoolean guiDone = new AtomicBoolean(false);
    private final Area DEN_AREA = new Area(3040,4970,3050,4980,1); // approximate
    private final Tile START_TILE = new Tile(3047,4975,1);
// ==== BEGIN FIX (lines 87–166) ====
private static final class Obstacle {
    final Tile tile;
    final String name;
    final String action;
    final Tile successTile;
    final int animationId;

    Obstacle(final Tile tile, final String name, final String action, final Tile successTile, final int animationId) {
        this.tile = tile;
        this.name = name;
        this.action = action;
        this.successTile = successTile;
        this.animationId = animationId;
    }
}

private final Obstacle[] MAZE_PATH = new Obstacle[] {
    new Obstacle(new Tile(3047, 4973, 1), "Door", "Open",          new Tile(3047, 4972, 1), -1),
    new Obstacle(new Tile(3048, 4970, 1), "Climb", "Climb",        new Tile(3049, 4970, 1), -1),
    new Obstacle(new Tile(3050, 4970, 1), "Squeeze-through", "Squeeze", new Tile(3051, 4970, 1), -1),
    new Obstacle(new Tile(3052, 4970, 1), "Trap", "Jump-over",     new Tile(3053, 4970, 1), -1),
    new Obstacle(new Tile(3054, 4970, 1), "Token", "Take",         new Tile(3054, 4970, 1), -1),
    new Obstacle(new Tile(3055, 4970, 1), "Exit door", "Open",     new Tile(3056, 4970, 1), -1)
};
// ==== END FIX (lines 87–166) ====

    };

    private int step = 0;
    private Config config = new Config();
    private RoguesDenGUI gui;
    private boolean ironman;
private int failureCount = 0;
private Tile lastSafeTile = START_TILE;
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
            abc.generateTrackers();
        });
    }

    private boolean meetsRequirements() {
        return getSkills().getRealLevel(Skill.THIEVING) >= 50 && getSkills().getRealLevel(Skill.AGILITY) >= 50;
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
            return Calculations.random(600,900);
        }

        State state = getState();
        switch (state) {
            case TRAVEL:
                getWalking().walk(START_TILE);
                Sleep.sleepUntil(() -> DEN_AREA.contains(getLocalPlayer()), 12000);
                return Calculations.random(300,600);
            case REST:
                handleRest();
                return Calculations.random(600,900);
            case MAZE:
                if (!getLocalPlayer().isAnimating() && !getLocalPlayer().isMoving()) {
                    handleMaze();
                }
                return Calculations.random(200,400);
        }
        return Calculations.random(200,400);
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
    handleChest();
}

            step = 0;
            return;
        }

MazeStep current = MAZE_STEPS[step];
if (getLocalPlayer().distance(current.tile) > 2) {
    getWalking().walk(current.tile);
    Sleep.sleepUntil(() -> getLocalPlayer().distance(current.tile) <= 2, 5000);
    return;
}

// Approach obstacle for the current maze step
handleObstacle(current);
}

    private void handleChest() {
        if (Inventory.isFull()) {
            log("Inventory full, cannot loot reward chest.");
            return;
        }

        GameObject chest = GameObjects.closest(o -> o != null && "Chest".equals(o.getName()));
        if (chest == null) {
            log("Reward chest not found.");
            return;
        }

        int before = Inventory.count(TOKEN_NAME);
        if (!chest.interact("Open")) {
            log("Failed to open reward chest.");
            return;
        }

        if (Sleep.sleepUntil(() -> Inventory.count(TOKEN_NAME) > before, 5000)) {
            step = 0;
            lastSafeTile = getLocalPlayer().getTile();
        } else {
            log("No token received from chest.");
        }
    }

// Generic, instrumented obstacle handler (resolves the merge conflict)
private void handleObstacle(MazeStep stepDef) {
    // Defensive checks to avoid NPEs and preserve existing error logging behavior
    if (stepDef == null || stepDef.name == null || stepDef.action == null) {
        obstacleFailed(stepDef != null ? String.valueOf(stepDef.name) : "Unknown", "invalid MazeStep");
        return;
    }

    // Find the target object by name and required action
    GameObject obj = GameObjects.closest(o ->
        o != null
            && stepDef.name.equals(o.getName())
            && o.hasAction(stepDef.action)
    );

    if (obj == null) {
        obstacleFailed(stepDef.name, "object not found");
        return;
    }

    // Remember our position before interacting
    Tile before = getLocalPlayer().getTile();

    // Try to interact (e.g., "Open", "Climb", "Push", etc.)
    if (!obj.interact(stepDef.action)) {
        obstacleFailed(stepDef.name, "interaction failed");
        return;
    }

    // Wait until we start moving or animating as a result of the interaction
    if (!Sleep.sleepUntil(() -> getLocalPlayer().isMoving() || getLocalPlayer().isAnimating(), 3000)) {
        obstacleFailed(stepDef.name, "no " + stepDef.action.toLowerCase() + " animation/move");
        return;
    }

    }
}

    // Wait for the interaction to finish (stop moving/animating)
    Sleep.sleepUntil(() -> !getLocalPlayer().isMoving() && !getLocalPlayer().isAnimating(), 5000);

    // If we didn't meaningfully change position, treat as a failure
    if (getLocalPlayer().distance(before) <= 1) {
        obstacleFailed(stepDef.name, "position unchanged");
        return;
    }

    // Success: advance the maze step, update our last safe tile, and apply anti-ban reaction
    step++;
    lastSafeTile = getLocalPlayer().getTile();
    AntiBan.sleepReaction(abc);
}

        step++;
        lastSafeTile = getLocalPlayer().getTile();
    }

private void handleSqueeze(MazeStep s) {
    GameObject obj = GameObjects.closest(o -> o != null
            && s.obstacle.equals(o.getName())
            && (o.hasAction("Squeeze") || o.hasAction("Squeeze-through")));
    boolean ok = obj != null && (obj.hasAction("Squeeze") ? obj.interact("Squeeze") : obj.interact("Squeeze-through"));
    if (!ok) {
        obstacleFailed("SQUEEZE", "interaction failed");
        return;
    }
    Tile before = getLocalPlayer().getTile();
    if (!Sleep.sleepUntil(() -> getLocalPlayer().isMoving() || getLocalPlayer().isAnimating(), 3000)) {
        obstacleFailed("SQUEEZE", "no squeeze movement");
        return;
    }
    Sleep.sleepUntil(() -> !getLocalPlayer().isMoving() && !getLocalPlayer().isAnimating(), 5000);
    if (getLocalPlayer().distance(before) <= 1) {
        obstacleFailed("SQUEEZE", "position unchanged");
        return;
    }
    step++;
    lastSafeTile = getLocalPlayer().getTile();
    AntiBan.sleepReaction(abc);
}

private void handleSearch(MazeStep s) {
    GameObject obj = GameObjects.closest(o -> o != null && s.obstacle.equals(o.getName()) && o.hasAction("Search"));
    if (obj == null || !obj.interact("Search")) {
        obstacleFailed("SEARCH", "interaction failed");
        return;
    }
    if (!Sleep.sleepUntil(() -> getLocalPlayer().isAnimating(), 3000)) {
        obstacleFailed("SEARCH", "no search animation");
        return;
    }
    Sleep.sleepUntil(() -> !getLocalPlayer().isAnimating(), 5000);
    step++;
    lastSafeTile = getLocalPlayer().getTile();
    AntiBan.sleepReaction(abc);
}

    }
}

// Compatibility shims for any older, specialized handlers that may be referenced elsewhere.
// These delegate to the unified, instrumented handler above to preserve functionality
// while removing duplicate/conflicting implementations.

private void handleOpen(MazeStep s) {
    // Assume "Open" action if not explicitly provided
    if (s != null && (s.action == null || s.action.isEmpty())) {
        s.action = "Open";
    }
    handleObstacle(s);
}

private void handleClimb(MazeStep s) {
    if (s != null && (s.action == null || s.action.isEmpty())) {
        s.action = "Climb";
    }
    handleObstacle(s);
}

private void handlePush(MazeStep s) {
    if (s != null && (s.action == null || s.action.isEmpty())) {
        s.action = "Push";
    }
    handleObstacle(s);
}

private void handleSearch(MazeStep s) {
    if (s != null && (s.action == null || s.action.isEmpty())) {
        s.action = "Search";
    }
    handleObstacle(s);
}

// If your code uses differently named helpers (e.g., handleDoor, handleGate, etc.),
// add thin wrappers here that simply set a default action (if needed) and call handleObstacle.

}

    private void prepareSupplies() {
        int attempts = 0;
        try {
        // Robustly open the nearest bank (up to 3 attempts)
        while (attempts < 3 && !getBank().isOpen()) {
            if (!getBank().openClosest()) {
                log("Failed to open closest bank. Retrying...");
                attempts++;
                Sleep.sleep(600, 1200);
                continue;
            }
            // Bank opened; proceed with supply logic below...

            }
            attempts++;
        }
        if (Inventory.contains(TOKEN_NAME)) {
            step++;
        } else {
            log("Failed to obtain token from chest after multiple attempts.");
        }
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
            // Robustly open the nearest bank (up to 3 attempts)
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

            // Coins (skip on ironman)
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

            // Stamina potions (if configured), with null-safe name checks
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
            // Always try to close the bank
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
