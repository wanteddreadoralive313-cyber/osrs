package org.dreambot.scripts.roguesden;

import org.dreambot.api.methods.container.impl.Inventory;
import org.dreambot.api.methods.interactive.GameObjects;
import org.dreambot.api.methods.interactive.NPCs;
import org.dreambot.api.methods.map.Tile;
import org.dreambot.api.utilities.sleep.Sleep;
import org.dreambot.api.utilities.impl.ABCUtil;
import org.dreambot.api.wrappers.interactive.GameObject;
import org.dreambot.api.wrappers.interactive.NPC;
import org.dreambot.api.wrappers.items.GroundItem;
import org.dreambot.api.wrappers.items.Item;

/**
 * Handles the navigation logic for progressing through the Rogues' Den maze.
 */
class MazeNavigator {

    private static final int FAILURE_THRESHOLD = 3;

    private enum InstructionType {
        HINT,
        MOVE,
        INTERACT,
        GROUND_ITEM,
        STUN_GUARD,
        SKIP
    }

    private static class MazeInstruction {
        final Tile tile;
        final String label;
        final InstructionType type;
        final String data;

        MazeInstruction(Tile tile, String label, InstructionType type, String data) {
            this.tile = tile;
            this.label = label;
            this.type = type;
            this.data = data;
        }
    }

    private final RoguesDenScript script;
    private final RoguesDenScript.Config config;
    private final ABCUtil abc;
    private final SupplyManager supplyManager;
    private final Tile startTile;

    private int step;
    private int failureCount;
    private Tile lastSafeTile;

    private final MazeInstruction[] mazePath = new MazeInstruction[]{
        new MazeInstruction(new Tile(3056, 4991, 1), "(Drink potion)", InstructionType.HINT, null),
        new MazeInstruction(new Tile(3004, 5003, 1), "Run", InstructionType.MOVE, null),
        new MazeInstruction(new Tile(2994, 5004, 1), "Climb", InstructionType.INTERACT, "Climb"),
        new MazeInstruction(new Tile(2969, 5018, 1), "Stand", InstructionType.MOVE, null),
        new MazeInstruction(new Tile(2958, 5031, 1), "Cross", InstructionType.INTERACT, "Cross"),
        new MazeInstruction(new Tile(2962, 5050, 1), "Stand", InstructionType.MOVE, null),
        new MazeInstruction(new Tile(3000, 5034, 1), "Run", InstructionType.MOVE, null),
        new MazeInstruction(new Tile(2992, 5045, 1), "Stand", InstructionType.MOVE, null),
        new MazeInstruction(new Tile(2992, 5053, 1), "Run", InstructionType.MOVE, null),
        new MazeInstruction(new Tile(2980, 5044, 1), "(Go east)", InstructionType.MOVE, null),
        new MazeInstruction(new Tile(2963, 5056, 1), "Run", InstructionType.MOVE, null),
        new MazeInstruction(new Tile(2957, 5068, 1), "Enter", InstructionType.INTERACT, "Enter"),
        new MazeInstruction(new Tile(2957, 5074, 1), "Stand", InstructionType.MOVE, null),
        new MazeInstruction(new Tile(2955, 5094, 1), "Enter", InstructionType.INTERACT, "Enter"),
        new MazeInstruction(new Tile(2972, 5098, 1), "Enter", InstructionType.INTERACT, "Enter"),
        new MazeInstruction(new Tile(2972, 5094, 1), "Open", InstructionType.INTERACT, "Open"),
        new MazeInstruction(new Tile(2976, 5087, 1), "Click", InstructionType.INTERACT, "Search"),
        new MazeInstruction(new Tile(2982, 5087, 1), "Climb", InstructionType.INTERACT, "Climb"),
        new MazeInstruction(new Tile(2993, 5088, 1), "Search", InstructionType.INTERACT, "Search"),
        new MazeInstruction(new Tile(2997, 5088, 1), "Run", InstructionType.MOVE, null),
        new MazeInstruction(new Tile(3006, 5088, 1), "Run", InstructionType.MOVE, null),
        new MazeInstruction(new Tile(2989, 5057, 1), "Open", InstructionType.INTERACT, "Open"),
        new MazeInstruction(new Tile(2992, 5058, 1), "(Go north)", InstructionType.MOVE, null),
        new MazeInstruction(new Tile(2992, 5067, 1), "Stand", InstructionType.MOVE, null),
        new MazeInstruction(new Tile(2992, 5075, 1), "Run", InstructionType.MOVE, null),
        new MazeInstruction(new Tile(2974, 5061, 1), "Enter", InstructionType.INTERACT, "Enter"),
        new MazeInstruction(new Tile(3050, 4997, 1), "Enter", InstructionType.INTERACT, "Enter"),
        new MazeInstruction(new Tile(3039, 4999, 1), "Stand", InstructionType.MOVE, null),
        new MazeInstruction(new Tile(3029, 5003, 1), "Run", InstructionType.MOVE, null),
        new MazeInstruction(new Tile(3024, 5001, 1), "Open", InstructionType.INTERACT, "Open"),
        new MazeInstruction(new Tile(3011, 5005, 1), "Run", InstructionType.MOVE, null),
        new MazeInstruction(new Tile(3028, 5033, 1), "Stand", InstructionType.MOVE, null),
        new MazeInstruction(new Tile(3024, 5033, 1), "Run", InstructionType.MOVE, null),
        new MazeInstruction(new Tile(3015, 5033, 1), "Open", InstructionType.INTERACT, "Open"),
        new MazeInstruction(new Tile(3010, 5033, 1), "Run/Open", InstructionType.INTERACT, "Open"),
        new MazeInstruction(new Tile(3009, 5063, 1), "Take", InstructionType.GROUND_ITEM, "Flash powder"),
        new MazeInstruction(new Tile(3014, 5063, 1), "(Stun NPC)", InstructionType.STUN_GUARD, null),
        new MazeInstruction(new Tile(3028, 5056, 1), "Run", InstructionType.MOVE, null),
        new MazeInstruction(new Tile(3028, 5047, 1), "Walk", InstructionType.INTERACT, "Walk-across"),
        new MazeInstruction(new Tile(3039, 5043, 1), "(Go south-west)", InstructionType.MOVE, null),
        new MazeInstruction(new Tile(3018, 5047, 1), "Crack", InstructionType.INTERACT, "Crack")
    };

    MazeNavigator(RoguesDenScript script, RoguesDenScript.Config config, ABCUtil abc, SupplyManager supplyManager, Tile startTile) {
        this.script = script;
        this.config = config;
        this.abc = abc;
        this.supplyManager = supplyManager;
        this.startTile = startTile;
        this.lastSafeTile = startTile;
    }

    void handleMaze() {
        if (script.getLocalPlayer().distance(startTile) <= 1 && step > 0) {
            recoverMaze();
            return;
        }

        if (step >= mazePath.length) {
            handleChest();
            return;
        }

        MazeInstruction current = mazePath[step];
        switch (current.type) {
            case SKIP:
                step++;
                break;
            case HINT:
                handleHint(current);
                step++;
                break;
            case MOVE:
                handleMoveInstruction(current);
                break;
            case INTERACT:
                handleInteractInstruction(current);
                break;
            case GROUND_ITEM:
                handleGroundItemInstruction(current);
                break;
            case STUN_GUARD:
                handleGuardInstruction(current);
                break;
        }
    }

    int getStep() {
        return step;
    }

    int getTotalSteps() {
        return mazePath.length;
    }

    void incrementStep() {
        step++;
    }

    private boolean isAtTile(Tile tile) {
        return tile != null && script.getLocalPlayer().distance(tile) <= 1;
    }

    private void markStepComplete() {
        step++;
        lastSafeTile = script.getLocalPlayer().getTile();
        failureCount = 0;
        AntiBan.sleepReaction(abc, config);
    }

    private void instructionFailed(String label, String reason) {
        failureCount++;
        script.log("Instruction " + label + " failed: " + reason);
        if (failureCount > FAILURE_THRESHOLD) {
            script.log("Failure threshold exceeded, returning to last safe tile");
            if (lastSafeTile != null) {
                script.getWalking().walk(lastSafeTile);
                Sleep.sleepUntil(() -> isAtTile(lastSafeTile) || !script.getLocalPlayer().isMoving(), 6000);
            }
            failureCount = 0;
        }
    }

    private boolean hasAnyAction(GameObject obj, String[] actions) {
        if (obj == null || actions == null) {
            return false;
        }
        for (String action : actions) {
            if (action != null && obj.hasAction(action)) {
                return true;
            }
        }
        return false;
    }

    private String[] expandActions(MazeInstruction instruction) {
        if (instruction.data == null) {
            return new String[0];
        }
        switch (instruction.data) {
            case "Climb":
                return new String[]{"Climb", "Climb-over", "Climb-up", "Climb-down"};
            case "Cross":
                return new String[]{"Cross", "Cross-quickly", "Walk-across", "Walk-quickly", "Balance", "Jump-across"};
            case "Walk-across":
                return new String[]{"Walk-across", "Walk-quickly", "Cross"};
            case "Open":
                return new String[]{"Open", "Push-open"};
            case "Enter":
                return new String[]{"Enter", "Go-through", "Pass", "Walk-through"};
            case "Search":
                return new String[]{"Search", "Inspect"};
            case "Crack":
                return new String[]{"Crack", "Open"};
            default:
                return new String[]{instruction.data};
        }
    }

    private void handleHint(MazeInstruction instruction) {
        if ("(Drink potion)".equals(instruction.label) && config.useStamina) {
            Item stamina = supplyManager.getStaminaPotion();
            if (stamina != null && stamina.interact("Drink")) {
                Sleep.sleepUntil(() -> script.getWalking().getRunEnergy() > config.runRestore, 3000);
            }
        }
    }

    private void handleMoveInstruction(MazeInstruction instruction) {
        if (isAtTile(instruction.tile)) {
            markStepComplete();
            return;
        }

        if (!script.getLocalPlayer().isMoving()) {
            if (!script.getWalking().walk(instruction.tile)) {
                instructionFailed(instruction.label, "could not path to tile");
                return;
            }
        }

        Sleep.sleepUntil(() -> isAtTile(instruction.tile) || !script.getLocalPlayer().isMoving(), 6000);
        if (isAtTile(instruction.tile)) {
            markStepComplete();
        }
    }

    private void handleInteractInstruction(MazeInstruction instruction) {
        String[] actions = expandActions(instruction);
        GameObject obj = GameObjects.closest(o ->
            o != null
                && o.getTile() != null
                && instruction.tile != null
                && o.getTile().distance(instruction.tile) <= 3
                && hasAnyAction(o, actions)
        );

        if (obj == null) {
            instructionFailed(instruction.label, "object not found");
            return;
        }

        boolean interacted = false;
        for (String action : actions) {
            if (action == null) {
                continue;
            }
            if (obj.hasAction(action) && obj.interact(action)) {
                interacted = true;
                break;
            }
        }

        if (!interacted) {
            instructionFailed(instruction.label, "interaction failed");
            return;
        }

        boolean started = Sleep.sleepUntil(
            () -> script.getLocalPlayer().isMoving() || script.getLocalPlayer().isAnimating() || isAtTile(instruction.tile),
            3000
        );
        if (!started) {
            instructionFailed(instruction.label, "no movement/animation");
            return;
        }

        Sleep.sleepUntil(() -> !script.getLocalPlayer().isMoving() && !script.getLocalPlayer().isAnimating(), 5000);
        markStepComplete();
    }

    private void handleGroundItemInstruction(MazeInstruction instruction) {
        if (instruction.data == null) {
            markStepComplete();
            return;
        }

        if (Inventory.contains(instruction.data)) {
            markStepComplete();
            return;
        }

        GroundItem item = script.getGroundItems().closest(g ->
            g != null
                && g.getTile() != null
                && instruction.tile != null
                && g.getTile().distance(instruction.tile) <= 4
                && instruction.data.equalsIgnoreCase(g.getName())
        );

        if (item == null) {
            instructionFailed(instruction.label, instruction.data + " not found");
            return;
        }

        if (!item.interact("Take")) {
            instructionFailed(instruction.label, "take failed");
            return;
        }

        if (!Sleep.sleepUntil(() -> Inventory.contains(instruction.data), 3000)) {
            instructionFailed(instruction.label, "item not picked up");
            return;
        }

        markStepComplete();
    }

    private void handleGuardInstruction(MazeInstruction instruction) {
        Item powder = Inventory.get(i -> i != null && "Flash powder".equalsIgnoreCase(i.getName()));
        if (powder == null) {
            instructionFailed(instruction.label, "missing flash powder");
            return;
        }

        NPC guard = NPCs.closest(n ->
            n != null
                && n.getName() != null
                && n.getName().equalsIgnoreCase("Rogue guard")
                && n.getTile() != null
                && instruction.tile != null
                && n.getTile().distance(instruction.tile) <= 5
        );

        if (guard == null) {
            instructionFailed(instruction.label, "guard not found");
            return;
        }

        if (!powder.useOn(guard)) {
            instructionFailed(instruction.label, "failed to use flash powder");
            return;
        }

        Sleep.sleep(600, 900);
        markStepComplete();
    }

    private void handleChest() {
        if (Inventory.isFull()) {
            script.log("Inventory full, cannot loot reward chest.");
            step = 0;
            return;
        }

        GameObject chest = GameObjects.closest(o -> o != null && "Chest".equals(o.getName()));
        if (chest == null) {
            script.log("Reward chest not found.");
            step = 0;
            return;
        }

        int before = Inventory.count(supplyManager.getTokenName());
        if (!chest.interact("Open")) {
            script.log("Failed to open reward chest.");
            step = 0;
            return;
        }

        if (Sleep.sleepUntil(() -> Inventory.count(supplyManager.getTokenName()) > before, 5000)) {
            step = 0;
            lastSafeTile = script.getLocalPlayer().getTile();
            failureCount = 0;
        } else {
            script.log("No token received from chest.");
            step = 0;
        }
    }

    private void recoverMaze() {
        script.log("Recovering maze...");
        script.getWalking().walk(startTile);
        Sleep.sleepUntil(() -> script.getLocalPlayer().distance(startTile) <= 2, 6000);
        step = 0;
        failureCount = 0;
        lastSafeTile = startTile;
    }
}
