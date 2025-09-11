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
private final Tile START_TILE = new Tile(3047,4975,1);

// Simple waypoint list (kept for compatibility with existing pathing code)
private final Tile[] MAZE_STEPS = {
    new Tile(3047,4973,1),
    new Tile(3048,4970,1),
    new Tile(3050,4970,1),
    new Tile(3052,4970,1)
};

// Reward/gear handling constants
private static final String TOKEN_NAME = "Rogue's reward token";
private static final String REWARD_NPC = "Rogue";
private static final String[] GEAR_ITEMS = {
    "Rogue mask", "Rogue top", "Rogue trousers", "Rogue gloves", "Rogue boots"
};

// Rich interaction model for the maze (new functionality)
private enum Interaction {
    OPEN, CLIMB, SQUEEZE, SEARCH, DISARM
}

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

private final MazeStep[] MAZE_PLAN = {
    new MazeStep(new Tile(3047,4973,1), Interaction.OPEN, "Door"),
    new MazeStep(new Tile(3048,4970,1), Interaction.CLIMB, "Rubble"),
    new MazeStep(new Tile(3050,4970,1), Interaction.SQUEEZE, "Gap"),
    new MazeStep(new Tile(3052,4968,1), Interaction.DISARM, "Trap"),
    new MazeStep(new Tile(3054,4968,1), Interaction.SEARCH, "Crate")
};

    };
    private int step = 0;
    private Config config = new Config();
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
            step = 0;
            return;
        }

        MazeStep current = MAZE_STEPS[step];
        Tile target = current.tile;
        if (getLocalPlayer().distance(target) > 2) {
            getWalking().walk(target);
            Sleep.sleepUntil(() -> getLocalPlayer().distance(target) <= 2, 5000);
            return;
        }

        switch (current.interaction) {
            case OPEN:
                handleOpen(current);
                break;
            case CLIMB:
                handleClimb(current);
                break;
            case SQUEEZE:
                handleSqueeze(current);
                break;
            case SEARCH:
                handleSearch(current);
                break;
            case DISARM:
                handleDisarm(current);
                break;
            case 3:
                handleChest();
                break;
            default:
                step = 0;
        }
    }

    private void handleOpen(MazeStep s) {
        GameObject obj = GameObjects.closest(o -> o != null && s.obstacle.equals(o.getName()));
        if (obj != null && obj.interact("Open")) {
            Sleep.sleepUntil(() -> getLocalPlayer().isMoving() || getLocalPlayer().isAnimating(), 3000);
            step++;
            AntiBan.sleepReaction(abc);
        }
    }

    private void handleClimb(MazeStep s) {
        GameObject obj = GameObjects.closest(o -> o != null && s.obstacle.equals(o.getName()));
        if (obj != null && obj.interact("Climb")) {
            Sleep.sleepUntil(() -> getLocalPlayer().isMoving() || getLocalPlayer().isAnimating(), 3000);
            step++;
            AntiBan.sleepReaction(abc);
        }
    }

    private void handleSqueeze(MazeStep s) {
        GameObject obj = GameObjects.closest(o -> o != null && s.obstacle.equals(o.getName()));
        if (obj != null && obj.interact("Squeeze")) {
            Sleep.sleepUntil(() -> getLocalPlayer().isMoving() || getLocalPlayer().isAnimating(), 3000);
            step++;
            AntiBan.sleepReaction(abc);
        }
    }

private void handleChest() {
    int attempts = 0;
    while (attempts < 3 && !Inventory.contains(TOKEN_NAME)) {
        GameObject chest = GameObjects.closest(o -> o != null && o.getName() != null && o.getName().contains("Chest"));
        if (chest != null && chest.interact("Search")) {
            // Wait until the token is in inventory or the player begins the search animation
            Sleep.sleepUntil(() -> Inventory.contains(TOKEN_NAME) || getLocalPlayer().isAnimating(), 5000);
        } else {
            log("Failed to interact with reward chest.");
        }
        attempts++;
        Sleep.sleep(300, 600);
    }
    if (Inventory.contains(TOKEN_NAME)) {
        step++;
    } else {
        log("Failed to obtain token from chest after multiple attempts.");
    }
}

private void handleSearch(MazeStep s) {
    GameObject obj = GameObjects.closest(o -> o != null && s.obstacle.equals(o.getName()));
    if (obj != null && obj.interact("Search")) {
        Sleep.sleepUntil(() -> getLocalPlayer().isAnimating(), 3000);
        step++;
    }
}

/** Open the loot chest at the end of the maze and collect the token. */
private boolean handleChest() {
    GameObject chest = GameObjects.closest(g -> g != null && "Chest".equals(g.getName()));
    if (chest == null) return false;

    if (!chest.isOnScreen()) {
        getWalking().walk(chest);
        Sleep.sleepUntil(chest::isOnScreen, Calculations.random(600, 1200));
    }

    if (chest.interact("Open")) {
        Sleep.sleepUntil(
            () -> Inventory.contains(TOKEN_NAME) || !chest.exists(),
            Calculations.random(1500, 3000)
        );
        return true;
    }
    return false;
}

/** Trade Rogue's reward token(s) to the Rogue NPC to obtain equipment pieces. */
private boolean cashInTokens() {
    if (!Inventory.contains(TOKEN_NAME)) return false;

    NPC rogue = NPCs.closest(n -> n != null && REWARD_NPC.equals(n.getName()));
    if (rogue == null) return false;

    if (!rogue.isOnScreen()) {
        getWalking().walk(rogue);
        Sleep.sleepUntil(rogue::isOnScreen, Calculations.random(600, 1200));
    }

    if (rogue.interact("Trade") || rogue.interact("Talk-to")) {
        Sleep.sleepUntil(() -> getDialogues().inDialogue() || getWidgets().isOpen(),
                         Calculations.random(1200, 2500));

        // Lightweight dialogue handling to consume token(s) and claim gear
        long end = System.currentTimeMillis() + Calculations.random(4000, 7000);
        while (System.currentTimeMillis() < end
                && (getDialogues().inDialogue() || getDialogues().canContinue())) {
            if (getDialogues().canContinue()) {
                getDialogues().spaceToContinue();
                Sleep.sleep(200, 400);
                continue;
            }
            if (getDialogues().chooseOption(opt -> {
                String o = opt.toLowerCase();
                return o.contains("token") || o.contains("equipment") || o.contains("rogue");
            })) {
                Sleep.sleep(300, 600);
            } else {
                break;
            }
        }

        // Wait for token to be spent or a new gear piece to appear
        Sleep.sleepUntil(() -> !Inventory.contains(TOKEN_NAME) || hasFullRogueSet(),
                         Calculations.random(2000, 4000));
        return true;
    }
    return false;
}

/** True if all rogue pieces are (now) in inventory. */
private boolean hasFullRogueSet() {
    int have = 0;
    for (String item : GEAR_ITEMS) {
        if (Inventory.contains(item)) {
            have++;
        }
    }
    return have == GEAR_ITEMS.length;
}

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
