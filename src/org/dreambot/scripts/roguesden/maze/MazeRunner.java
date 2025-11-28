package org.dreambot.scripts.roguesden.maze;

import org.dreambot.api.methods.container.impl.Inventory;
import org.dreambot.api.methods.interactive.GameObjects;
import org.dreambot.api.methods.interactive.NPCs;
import org.dreambot.api.methods.map.Tile;
import org.dreambot.api.methods.skills.Skill;
import org.dreambot.api.utilities.impl.ABCUtil;
import org.dreambot.api.utilities.sleep.Sleep;
import org.dreambot.api.wrappers.interactive.GameObject;
import org.dreambot.api.wrappers.interactive.NPC;
import org.dreambot.api.wrappers.interactive.Player;
import org.dreambot.api.wrappers.items.GroundItem;
import org.dreambot.api.wrappers.items.Item;
import org.dreambot.scripts.roguesden.AntiBan;
import org.dreambot.scripts.roguesden.RoguesDenScript;
import org.dreambot.scripts.roguesden.bank.BankManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class MazeRunner {

    private final RoguesDenScript script;
    private final RoguesDenScript.Config config;
    private final ABCUtil abc;
    private final BankManager bankManager;
    private final Tile startTile;
    private final Tile chestTile;
    private final String rewardCrateName;
    private final String flashPowderName;
    private final String rogueGuardName;
    private final int failureThreshold;
    private final String[] gearItems;

    private int step;
    private int failureCount;
    private Tile lastSafeTile;
    private RoguesDenScript.MazeRoute activeMazeRoute = RoguesDenScript.MazeRoute.LONG;

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

    private static class MazePaths {
        final List<MazeInstruction> longRoute;
        final List<MazeInstruction> shortcutRoute;

        MazePaths(List<MazeInstruction> longRoute, List<MazeInstruction> shortcutRoute) {
            this.longRoute = Collections.unmodifiableList(new ArrayList<>(longRoute));
            this.shortcutRoute = Collections.unmodifiableList(new ArrayList<>(shortcutRoute));
        }
    }

    private static final MazePaths MAZE_PATHS = loadMazePaths();

    public MazeRunner(RoguesDenScript script,
                      RoguesDenScript.Config config,
                      ABCUtil abc,
                      BankManager bankManager,
                      Tile startTile,
                      Tile chestTile,
                      String rewardCrateName,
                      String flashPowderName,
                      String rogueGuardName,
                      int failureThreshold,
                      String[] gearItems) {
        this.script = Objects.requireNonNull(script, "script");
        this.config = Objects.requireNonNull(config, "config");
        this.abc = Objects.requireNonNull(abc, "abc");
        this.bankManager = Objects.requireNonNull(bankManager, "bankManager");
        this.startTile = Objects.requireNonNull(startTile, "startTile");
        this.chestTile = Objects.requireNonNull(chestTile, "chestTile");
        this.rewardCrateName = Objects.requireNonNull(rewardCrateName, "rewardCrateName");
        this.flashPowderName = Objects.requireNonNull(flashPowderName, "flashPowderName");
        this.rogueGuardName = Objects.requireNonNull(rogueGuardName, "rogueGuardName");
        this.failureThreshold = failureThreshold;
        this.gearItems = Arrays.copyOf(gearItems, gearItems.length);
        this.lastSafeTile = startTile;
    }

    public void handleMaze(Player player) {
        if (player != null && player.distance(startTile) <= 1 && step > 0) {
            recoverMaze();
            return;
        }

        ensureActiveMazeRouteSelected();
        List<MazeInstruction> path = getActiveMazePath();

        if (step >= path.size()) {
            handleChest();
            return;
        }

        MazeInstruction current = path.get(step);
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

    public void resetProgress() {
        step = 0;
        failureCount = 0;
        lastSafeTile = startTile;
    }

    public int getStep() {
        return step;
    }

    public void incrementStep() {
        step++;
    }

    public int getMazeStepCount() {
        return getActiveMazePath().size();
    }

    public String getInstructionLabel(RoguesDenScript.MazeRoute route, int index) {
        List<MazeInstruction> path = getMazePath(route);
        if (index < 0 || index >= path.size()) {
            throw new IndexOutOfBoundsException("Instruction index out of range: " + index);
        }
        return path.get(index).label;
    }

    public RoguesDenScript.MazeRoute determineMazeRouteForLevel(int thievingLevel) {
        boolean useShortcut = config.shouldUseShortcut(thievingLevel);
        if (useShortcut && !MAZE_PATHS.shortcutRoute.isEmpty()) {
            return RoguesDenScript.MazeRoute.SHORTCUT;
        }
        return RoguesDenScript.MazeRoute.LONG;
    }

    public RoguesDenScript.MazeRoute getActiveMazeRoute() {
        return activeMazeRoute;
    }

    private void ensureActiveMazeRouteSelected() {
        if (step == 0) {
            activeMazeRoute = determineMazeRouteForLevel(script.getSkills().getRealLevel(Skill.THIEVING));
        }
    }

    private List<MazeInstruction> getActiveMazePath() {
        return getMazePath(activeMazeRoute);
    }

    private List<MazeInstruction> getMazePath(RoguesDenScript.MazeRoute route) {
        return route == RoguesDenScript.MazeRoute.SHORTCUT ? MAZE_PATHS.shortcutRoute : MAZE_PATHS.longRoute;
    }

    private void markStepComplete() {
        step++;
        Player player = script.getLocalPlayer();
        if (player != null) {
            lastSafeTile = player.getTile();
        }
        failureCount = 0;
        AntiBan.sleepReaction(script, abc, config);
    }

    private void instructionFailed(String label, String reason) {
        failureCount++;
        script.log("Instruction " + label + " failed: " + reason);
        if (failureCount > failureThreshold) {
            script.log("Failure threshold exceeded, returning to last safe tile");
            if (lastSafeTile != null) {
                script.getWalking().walk(lastSafeTile);
                Sleep.sleepUntil(() -> {
                    Player current = script.getLocalPlayer();
                    return current != null && (isAtTile(current, lastSafeTile) || !current.isMoving());
                }, 6000);
            }
            failureCount = 0;
        }
    }

    private boolean isAtTile(Player player, Tile tile) {
        return player != null && tile != null && player.distance(tile) <= 1;
    }

    private void handleHint(MazeInstruction instruction) {
        if ("(Drink potion)".equals(instruction.label) && config.useStamina) {
            Item stamina = bankManager.getStaminaPotion();
            if (stamina != null && stamina.interact("Drink")) {
                Sleep.sleepUntil(() -> script.getWalking().getRunEnergy() > config.runRestore, 3000);
            }
        }
    }

    private void handleMoveInstruction(MazeInstruction instruction) {
        Player player = script.getLocalPlayer();
        if (isAtTile(player, instruction.tile)) {
            markStepComplete();
            return;
        }

        if (player == null || !player.isMoving()) {
            if (!script.getWalking().walk(instruction.tile)) {
                instructionFailed(instruction.label, "could not path to tile");
                return;
            }
        }

        Sleep.sleepUntil(() -> {
            Player current = script.getLocalPlayer();
            return current != null && (isAtTile(current, instruction.tile) || !current.isMoving());
        }, 6000);
        player = script.getLocalPlayer();
        if (isAtTile(player, instruction.tile)) {
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
            if (action == null) continue;
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
            () -> {
                Player current = script.getLocalPlayer();
                return current != null
                    && (current.isMoving() || current.isAnimating() || isAtTile(current, instruction.tile));
            },
            3000
        );
        if (!started) {
            instructionFailed(instruction.label, "no movement/animation");
            return;
        }

        Sleep.sleepUntil(() -> {
            Player current = script.getLocalPlayer();
            return current != null && !current.isMoving() && !current.isAnimating();
        }, 5000);
        markStepComplete();
    }

    private void handleGroundItemInstruction(MazeInstruction instruction) {
        String targetName = instruction.data;
        boolean needsPickup = targetName != null && !Inventory.contains(targetName);

        if (Inventory.isFull() && needsPickup) {
            if (!ensureInventorySpaceForGroundItem(targetName)) {
                script.log("Inventory full and no safe item to drop for " + targetName + ". Aborting maze run.");
                recoverMaze();
                return;
            }
        }

        if (instruction.data == null) {
            markStepComplete();
            return;
        }

        if (Inventory.contains(instruction.data)) {
            markStepComplete();
            return;
        }

        if (Inventory.isFull()) {
            script.log("Inventory still full before taking " + instruction.data + ". Aborting maze run.");
            recoverMaze();
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
        if (!Inventory.contains(flashPowderName)) {
            instructionFailed(instruction.label, "Flash powder missing");
            return;
        }

        if (instruction.tile == null) {
            instructionFailed(instruction.label, "missing guard tile");
            return;
        }

        NPC guard = NPCs.closest(n ->
            n != null
                && rogueGuardName.equals(n.getName())
                && n.getTile() != null
                && n.getTile().distance(instruction.tile) <= 6
        );

        if (guard == null) {
            instructionFailed(instruction.label, "guard not found");
            return;
        }

        if (!org.dreambot.api.methods.tabs.Tabs.isOpen(org.dreambot.api.methods.tabs.Tab.INVENTORY)) {
            if (!org.dreambot.api.methods.tabs.Tabs.open(org.dreambot.api.methods.tabs.Tab.INVENTORY)
                || !Sleep.sleepUntil(() -> org.dreambot.api.methods.tabs.Tabs.isOpen(org.dreambot.api.methods.tabs.Tab.INVENTORY), 1200)) {
                instructionFailed(instruction.label, "failed to open inventory");
                return;
            }
        }

        Item powder = Inventory.get(flashPowderName);
        if (powder == null) {
            instructionFailed(instruction.label, "Flash powder missing");
            return;
        }

        int initialCount = Inventory.count(flashPowderName);
        int initialAnimation = guard.getAnimation();
        boolean actionInitiated = false;

        if (powder.hasAction("Throw") && powder.interact("Throw")) {
            boolean consumedOrSelected = Sleep.sleepUntil(
                () -> Inventory.count(flashPowderName) < initialCount || Inventory.isItemSelected(),
                1500
            );

            if (!consumedOrSelected) {
                actionInitiated = false;
            } else if (Inventory.count(flashPowderName) < initialCount) {
                actionInitiated = true;
            } else {
                String selectedName = Inventory.getSelectedItemName();
                if (selectedName != null && selectedName.equalsIgnoreCase(flashPowderName)) {
                    actionInitiated = guard.interact(action -> action != null && action.startsWith("Use"));
                }
            }
        }

        if (!actionInitiated) {
            if (Inventory.isItemSelected()) {
                Inventory.deselect();
            }

            powder = Inventory.get(flashPowderName);
            if (powder != null && powder.useOn(guard)) {
                actionInitiated = true;
            } else if (powder != null && powder.hasAction("Use") && powder.interact("Use")) {
                boolean selected = Sleep.sleepUntil(Inventory::isItemSelected, 1500);
                if (selected) {
                    String selectedName = Inventory.getSelectedItemName();
                    if (selectedName != null && selectedName.equalsIgnoreCase(flashPowderName)) {
                        actionInitiated = guard.interact(action -> action != null && action.startsWith("Use"));
                    }
                }
            }
        }

        if (!actionInitiated) {
            instructionFailed(instruction.label, "failed to use flash powder");
            return;
        }

        boolean effectObserved = Sleep.sleepUntil(
            () -> Inventory.count(flashPowderName) < initialCount || isGuardStunned(guard, initialAnimation),
            5000
        );

        if (!effectObserved) {
            instructionFailed(instruction.label, "guard not stunned");
            return;
        }

        markStepComplete();
    }

    private boolean isGuardStunned(NPC guard, int initialAnimation) {
        return guard != null
            && guard.exists()
            && guard.isAnimating()
            && guard.getAnimation() != initialAnimation;
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

    private void handleChest() {
        if (Inventory.isFull()) {
            if (Inventory.contains(rewardCrateName)) {
                script.log("Inventory full due to leftover rogue equipment crate; clearing it before looting chest.");
                if (!bankManager.clearRewardCrates() || Inventory.isFull()) {
                    script.log("Unable to clear rogue equipment crate before opening reward chest.");
                    resetProgress();
                    return;
                }
            } else {
                script.log("Inventory full, cannot loot reward chest.");
                resetProgress();
                return;
            }
        }

        GameObject chest = GameObjects.closest(o ->
            o != null
                && "Chest".equals(o.getName())
                && o.getTile() != null
                && o.getTile().equals(chestTile)
        );
        if (chest == null) {
            script.log("Reward chest not found.");
            resetProgress();
            return;
        }

        Tile chestTile = chest.getTile();
        if (chestTile == null) {
            script.log("Reward chest tile unknown.");
            resetProgress();
            return;
        }

        if (!script.getMap().canReach(chestTile)) {
            script.log("Reward chest is not reachable.");
            resetProgress();
            return;
        }

        int before = Inventory.count(rewardCrateName);
        boolean hadCrateBefore = before > 0 || Inventory.contains(rewardCrateName);
        if (!chest.interact("Open")) {
            script.log("Failed to open reward chest.");
            resetProgress();
            return;
        }

        boolean crateReceived = Sleep.sleepUntil(() ->
                Inventory.count(rewardCrateName) > before
                    || (!hadCrateBefore && Inventory.contains(rewardCrateName)),
            5000
        );

        if (crateReceived) {
            Player player = script.getLocalPlayer();
            if (player != null) {
                lastSafeTile = player.getTile();
            }
            failureCount = 0;
        } else {
            script.log("No rogue equipment crate received from chest.");
        }
        resetProgress();
    }

    private void recoverMaze() {
        script.log("Recovering maze...");
        script.getWalking().walk(startTile);
        Sleep.sleepUntil(() -> {
            Player player = script.getLocalPlayer();
            return player != null && player.distance(startTile) <= 2;
        }, 6000);
        resetProgress();
    }

    private boolean ensureInventorySpaceForGroundItem(String targetName) {
        if (!Inventory.isFull()) {
            return true;
        }

        while (Inventory.isFull()) {
            boolean droppedItem = false;
            int extraFlashPowder = Math.max(0, Inventory.count(flashPowderName) - 1);

            for (Item item : Inventory.all()) {
                if (item == null || item.getName() == null) {
                    continue;
                }

                String name = item.getName();

                if (name.isEmpty()) {
                    continue;
                }

                if (rewardCrateName.equalsIgnoreCase(name)) {
                    continue;
                }

                if (bankManager.isStaminaPotion(item)) {
                    continue;
                }

                if (isRogueGear(name)) {
                    continue;
                }

                if (targetName != null && targetName.equalsIgnoreCase(name)) {
                    continue;
                }

                boolean shouldDrop = false;

                if ("Vial".equalsIgnoreCase(name)) {
                    shouldDrop = true;
                } else if (name.contains("(0)")) {
                    shouldDrop = true;
                } else if (flashPowderName.equalsIgnoreCase(name) && extraFlashPowder > 0) {
                    shouldDrop = true;
                }

                if (!shouldDrop) {
                    continue;
                }

                if (!item.interact("Drop")) {
                    continue;
                }

                droppedItem = true;

                if (flashPowderName.equalsIgnoreCase(name) && extraFlashPowder > 0) {
                    extraFlashPowder--;
                }

                if (Sleep.sleepUntil(() -> !Inventory.isFull(), 1800)) {
                    return true;
                }

                break;
            }

            if (!droppedItem) {
                return false;
            }
        }

        return true;
    }

    private boolean isRogueGear(String name) {
        if (name == null) {
            return false;
        }
        for (String item : gearItems) {
            if (name.equalsIgnoreCase(item)) {
                return true;
            }
        }
        return false;
    }

    private static MazePaths loadMazePaths() {
        List<MazeInstruction> longRoute = new ArrayList<>();
        List<MazeInstruction> shortcutRoute = new ArrayList<>();
        List<MazeInstruction> target = longRoute;
        try (InputStream stream = MazeRunner.class.getResourceAsStream("/org/dreambot/scripts/roguesden/maze_path.csv")) {
            if (stream == null) {
                throw new IllegalStateException("Unable to locate maze_path.csv resource");
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                int lineNumber = 0;
                while ((line = reader.readLine()) != null) {
                    lineNumber++;
                    line = line.trim();
                    if (line.isEmpty()) {
                        continue;
                    }

                    if (line.startsWith("#")) {
                        String lower = line.toLowerCase(Locale.ROOT);
                        if (lower.startsWith("# route:")) {
                            String routeName = lower.substring("# route:".length()).trim();
                            if ("long".equals(routeName)) {
                                target = longRoute;
                            } else if ("shortcut".equals(routeName)) {
                                target = shortcutRoute;
                            } else {
                                throw new IllegalStateException("Unknown maze route '" + routeName + "' on line " + lineNumber);
                            }
                        }
                        continue;
                    }

                    String[] parts = line.split("\\|", -1);
                    if (parts.length < 3) {
                        throw new IllegalStateException("Invalid maze instruction on line " + lineNumber + ": " + line);
                    }

                    Tile tile = parseTile(parts[0].trim(), lineNumber);
                    String label = parts[1].trim();
                    InstructionType type = parseInstructionType(parts[2].trim(), lineNumber);
                    String data = parts.length > 3 ? parts[3].trim() : null;
                    if (data != null && data.isEmpty()) {
                        data = null;
                    }

                    target.add(new MazeInstruction(tile, label, type, data));
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load maze_path.csv", e);
        }

        if (longRoute.isEmpty()) {
            throw new IllegalStateException("No long route instructions loaded from maze_path.csv");
        }
        if (shortcutRoute.isEmpty()) {
            throw new IllegalStateException("No shortcut route instructions loaded from maze_path.csv");
        }

        return new MazePaths(longRoute, shortcutRoute);
    }

    private static Tile parseTile(String raw, int lineNumber) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }

        String[] coords = raw.split(",");
        if (coords.length != 3) {
            throw new IllegalStateException("Invalid tile coordinates on line " + lineNumber + ": " + raw);
        }

        try {
            int x = Integer.parseInt(coords[0].trim());
            int y = Integer.parseInt(coords[1].trim());
            int z = Integer.parseInt(coords[2].trim());
            return new Tile(x, y, z);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Non-numeric tile coordinate on line " + lineNumber + ": " + raw, e);
        }
    }

    private static InstructionType parseInstructionType(String raw, int lineNumber) {
        if (raw == null || raw.isEmpty()) {
            throw new IllegalStateException("Missing instruction type on line " + lineNumber);
        }

        try {
            return InstructionType.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Unknown instruction type '" + raw + "' on line " + lineNumber, ex);
        }
    }
}
