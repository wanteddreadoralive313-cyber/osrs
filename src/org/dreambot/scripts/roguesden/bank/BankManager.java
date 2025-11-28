package org.dreambot.scripts.roguesden.bank;

import org.dreambot.api.methods.container.impl.Inventory;
import org.dreambot.api.methods.interactive.GameObjects;
import org.dreambot.api.methods.magic.Normal;
import org.dreambot.api.methods.map.Area;
import org.dreambot.api.methods.map.Tile;
import org.dreambot.api.script.ScriptManager;
import org.dreambot.api.utilities.sleep.Sleep;
import org.dreambot.api.wrappers.interactive.GameObject;
import org.dreambot.api.wrappers.interactive.Player;
import org.dreambot.api.wrappers.items.Item;
import org.dreambot.scripts.roguesden.RoguesDenScript;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class BankManager {

    private final RoguesDenScript script;
    private final RoguesDenScript.Config config;
    private final String[] gearItems;
    private final String rewardCrateName;
    private final int bankInteractionRange;
    private final int staminaDosesPerPotion;

    private Tile cachedBankTile;
    private Tile bankTravelTarget;

    public BankManager(RoguesDenScript script,
                       RoguesDenScript.Config config,
                       String[] gearItems,
                       String rewardCrateName,
                       int bankInteractionRange,
                       int staminaDosesPerPotion) {
        this.script = Objects.requireNonNull(script, "script");
        this.config = Objects.requireNonNull(config, "config");
        this.gearItems = Arrays.copyOf(gearItems, gearItems.length);
        this.rewardCrateName = Objects.requireNonNull(rewardCrateName, "rewardCrateName");
        this.bankInteractionRange = bankInteractionRange;
        this.staminaDosesPerPotion = staminaDosesPerPotion;
    }

    public boolean prepareSupplies(boolean suppliesReady) {
        if (suppliesReady) {
            return true;
        }

        boolean bankOpened = false;

        if (!Inventory.isEmpty()) {
            if (!ensureBankOpen("deposit inventory")) {
                return false;
            }
            bankOpened = true;
            script.getBank().depositAllItems();
            if (!Sleep.sleepUntil(Inventory::isEmpty, 2000)) {
                script.log("Unable to fully clear inventory; continuing with remaining items.");
            }
        }

        for (String item : gearItems) {
            if (isGearEquipped(item) || Inventory.contains(item)) {
                continue;
            }

            if (!bankOpened && !ensureBankOpen("withdraw " + item)) {
                return false;
            }

            bankOpened = true;
            if (!script.getBank().contains(item)) {
                script.log("Missing Rogue gear piece: " + item);
                closeBank();
                return false;
            }

            script.getBank().withdraw(item, 1);
            if (!Sleep.sleepUntil(() -> Inventory.contains(item), 2000)) {
                script.log("Failed to withdraw " + item + ".");
                closeBank();
                return false;
            }
        }

        if (config.useStamina) {
            int currentDoses = getStaminaDoseCount();
            int potionsNeeded = getRequiredStaminaPotions(currentDoses);
            if (potionsNeeded > 0) {
                if (!bankOpened && !ensureBankOpen("withdraw stamina potions")) {
                    return false;
                }
                bankOpened = true;
                if (!withdrawStaminaPotions(potionsNeeded)) {
                    closeBank();
                    return false;
                }
            }
        }

        String foodName = getConfiguredFoodName();
        if (!foodName.isEmpty() && !Inventory.contains(foodName)) {
            if (!bankOpened && !ensureBankOpen("withdraw " + foodName)) {
                return false;
            }
            bankOpened = true;
            if (!script.getBank().contains(foodName)) {
                script.log("Missing preferred food item: " + foodName);
                closeBank();
                return false;
            }
            script.getBank().withdraw(foodName, 1);
            if (!Sleep.sleepUntil(() -> Inventory.contains(foodName), 2000)) {
                script.log("Failed to withdraw preferred food item " + foodName + ".");
                closeBank();
                return false;
            }
        }

        if (bankOpened) {
            if (Inventory.contains("Vial")) {
                script.getBank().depositAll("Vial");
                Sleep.sleepUntil(() -> !Inventory.contains("Vial"), 2000);
            }
            closeBank();
        }

        for (String item : gearItems) {
            if (isGearEquipped(item)) {
                continue;
            }

            Item gear = Inventory.get(item);
            if (gear == null) {
                script.log("Failed to locate " + item + " in inventory for equipping.");
                return false;
            }
            if (!gear.interact("Wear")) {
                script.log("Failed to equip " + item);
                return false;
            }
            if (!Sleep.sleepUntil(() -> isGearEquipped(item), 2000)) {
                script.log("Could not confirm " + item + " equipped.");
                return false;
            }
        }

        boolean ready = hasRequiredSupplies();
        if (!ready) {
            script.log("Supply verification failed, will retry.");
        }
        return ready;
    }

    public boolean ensureBankOpen(String context) {
        if (script.getBank().isOpen()) {
            bankTravelTarget = null;
            return true;
        }

        Tile bankTile = getNearestBankTile();
        if (bankTile == null) {
            return handleBankAccessIssue(
                "Unable to locate any reachable bank while attempting to " + context + ".",
                true
            );
        }

        Player player = script.getLocalPlayer();
        if (player == null) {
            return false;
        }

        double distance = player.distance(bankTile);
        if (!Double.isFinite(distance)) {
            return false;
        }

        if (distance > bankInteractionRange) {
            if (player.isMoving() && Objects.equals(bankTravelTarget, bankTile)) {
                return false;
            }

            if (!initiateBankTravel(bankTile)) {
                return handleBankAccessIssue(
                    "Failed to generate a path to the nearest bank while attempting to " + context + ".",
                    true
                );
            }

            return false;
        }

        bankTravelTarget = null;

        if (player.isMoving()) {
            return false;
        }

        if (!script.getBank().openClosest()) {
            return handleBankAccessIssue(
                "Failed to open the closest bank while attempting to " + context + ".",
                false
            );
        }

        if (!Sleep.sleepUntil(() -> script.getBank().isOpen(), 5000)) {
            return handleBankAccessIssue(
                "Timed out waiting for bank to open while attempting to " + context + ".",
                false
            );
        }

        cachedBankTile = bankTile;
        return true;
    }

    public boolean clearRewardCrates() {
        int crateCount = Inventory.count(rewardCrateName);
        if (crateCount < 1) {
            return true;
        }

        script.log("Clearing leftover rogue equipment crates: " + crateCount);

        boolean cleared = false;
        boolean openedBankHere = false;

        if (!script.getBank().isOpen()) {
            if (ensureBankOpen("deposit leftover reward crates")) {
                openedBankHere = true;
            }
        }

        if (script.getBank().isOpen()) {
            script.getBank().depositAll(rewardCrateName);
            cleared = Sleep.sleepUntil(() -> !Inventory.contains(rewardCrateName), 2000);
            if (!cleared) {
                script.log("Failed to deposit rogue equipment crates; attempting to drop them.");
            }
        }

        if (openedBankHere) {
            closeBank();
        }

        if (!cleared) {
            int attempts = 0;
            while (Inventory.contains(rewardCrateName) && attempts < 5) {
                Item crate = Inventory.get(rewardCrateName);
                if (crate == null || !crate.interact("Drop")) {
                    break;
                }
                Sleep.sleep(200, 400);
                Sleep.sleepUntil(() -> !Inventory.contains(rewardCrateName), 1200);
                attempts++;
            }
            cleared = !Inventory.contains(rewardCrateName);
        }

        if (!cleared) {
            script.log("Unable to remove rogue equipment crates.");
        }

        return cleared;
    }

    public boolean bankRogueRewards() {
        boolean openedHere = false;
        if (!script.getBank().isOpen()) {
            if (!ensureBankOpen("bank rogue rewards")) {
                return false;
            }
            openedHere = true;
        }

        boolean deposited = true;

        if (Inventory.contains(rewardCrateName)) {
            script.getBank().depositAll(rewardCrateName);
            boolean cleared = Sleep.sleepUntil(() -> !Inventory.contains(rewardCrateName), 2000);
            if (!cleared) {
                script.log("Failed to deposit rogue equipment crates while banking rewards.");
                deposited = false;
            }
        }

        for (String gear : gearItems) {
            if (!Inventory.contains(gear)) {
                continue;
            }

            if (!script.getBank().depositAll(gear)) {
                script.log("Failed to deposit " + gear + " while banking rewards.");
                deposited = false;
                continue;
            }

            boolean cleared = Sleep.sleepUntil(() -> !Inventory.contains(gear), 2000);
            if (!cleared) {
                script.log("Unable to confirm " + gear + " deposited while banking rewards.");
                deposited = false;
            }
        }

        if (openedHere) {
            closeBank();
        }

        return deposited;
    }

    public void depositDuplicateRogueGear() {
        boolean openedHere = false;
        List<String> depositPlan = new ArrayList<>();
        for (String item : gearItems) {
            int keep = isGearEquipped(item) ? 0 : 1;
            int count = Inventory.count(item);
            for (int i = 0; i < count - keep; i++) {
                depositPlan.add(item);
            }
        }

        if (depositPlan.isEmpty()) {
            return;
        }

        if (!script.getBank().isOpen()) {
            if (!ensureBankOpen("deposit duplicate rogue gear")) {
                return;
            }
            openedHere = true;
        }

        for (String item : depositPlan) {
            if (!script.getBank().deposit(item, 1)) {
                script.log("Failed to deposit duplicate " + item + ".");
                continue;
            }
            int keep = isGearEquipped(item) ? 0 : 1;
            Sleep.sleepUntil(() -> Inventory.count(item) <= keep, 2000);
        }

        if (openedHere) {
            closeBank();
        }
    }

    public boolean hasFullRogueSet() {
        List<String> missing = new ArrayList<>();
        for (String item : gearItems) {
            if (isGearEquipped(item) || Inventory.contains(item)) {
                continue;
            }
            missing.add(item);
        }
        if (missing.isEmpty()) {
            return true;
        }

        boolean openedHere = false;
        if (!script.getBank().isOpen()) {
            if (!script.getBank().openClosest()) {
                script.log("Could not open bank to verify rogue set.");
                return false;
            }
            if (!Sleep.sleepUntil(() -> script.getBank().isOpen(), 5000)) {
                script.log("Timed out waiting for bank to open while verifying rogue set.");
                return false;
            }
            openedHere = true;
        }

        boolean allPresent = missing.stream().allMatch(i -> script.getBank().contains(i));

        if (openedHere) {
            closeBank();
        }

        return allPresent;
    }

    public boolean hasRequiredSupplies() {
        if (!hasFullRogueSetEquipped()) {
            return false;
        }
        if (config.useStamina && shouldRestockStamina(getStaminaDoseCount())) {
            return false;
        }
        return !needsFoodRestock();
    }

    private boolean hasFullRogueSetEquipped() {
        for (String item : gearItems) {
            if (!isGearEquipped(item)) {
                return false;
            }
        }
        return true;
    }

    private boolean isGearEquipped(String item) {
        return script.getEquipment() != null && script.getEquipment().contains(item);
    }

    private boolean needsFoodRestock() {
        String foodName = getConfiguredFoodName();
        return !foodName.isEmpty() && !Inventory.contains(foodName);
    }

    private String getConfiguredFoodName() {
        String food = config.preferredFoodItem;
        return food == null ? "" : food.trim();
    }

    public Item getStaminaPotion() {
        return Inventory.get(this::isStaminaPotion);
    }

    public boolean isStaminaPotion(Item item) {
        return item != null && item.getName() != null && item.getName().contains("Stamina potion");
    }

    public int getStaminaDoseCount() {
        int total = 0;
        for (Item item : Inventory.all()) {
            if (!isStaminaPotion(item)) {
                continue;
            }
            total += getStaminaDoses(item);
        }
        return total;
    }

    private int getStaminaDoses(Item item) {
        if (item == null || item.getName() == null) {
            return 0;
        }
        String name = item.getName();
        int start = name.indexOf('(');
        int end = name.indexOf(')', start + 1);
        if (start == -1 || end == -1 || end <= start + 1) {
            return 0;
        }
        try {
            return Integer.parseInt(name.substring(start + 1, end));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public boolean withdrawStaminaPotions(int potionsToWithdraw) {
        if (potionsToWithdraw <= 0) {
            return true;
        }
        if (!script.getBank().contains(this::isStaminaPotion)) {
            script.log("No stamina potions available in bank.");
            return true;
        }

        for (int i = 0; i < potionsToWithdraw; i++) {
            if (!script.getBank().contains(this::isStaminaPotion)) {
                script.log("Insufficient stamina potions remaining in bank to reach configured target.");
                break;
            }
            int before = getStaminaDoseCount();
            script.getBank().withdraw(this::isStaminaPotion, 1);
            if (!Sleep.sleepUntil(() -> getStaminaDoseCount() > before, 2000)) {
                script.log("Failed to confirm stamina potion withdrawal.");
                return false;
            }
        }

        return true;
    }

    public int getRequiredStaminaPotions(int currentDoseCount) {
        if (!config.useStamina) {
            return 0;
        }
        int deficit = config.staminaDoseTarget - currentDoseCount;
        if (deficit <= 0) {
            return 0;
        }
        return (deficit + staminaDosesPerPotion - 1) / staminaDosesPerPotion;
    }

    public boolean shouldRestockStamina(int currentDoseCount) {
        if (!config.useStamina) {
            return false;
        }
        if (currentDoseCount <= 0) {
            return true;
        }
        return currentDoseCount < config.staminaDoseThreshold;
    }

    public boolean isBankInRange() {
        Player player = script.getLocalPlayer();
        Tile bankTile = getNearestBankTile();
        if (player == null || bankTile == null) {
            return false;
        }
        double distance = player.distance(bankTile);
        return Double.isFinite(distance) && distance <= bankInteractionRange;
    }

    private Tile getNearestBankTile() {
        Tile resolved = resolveNearestReachableBankTile();
        if (resolved != null) {
            cachedBankTile = resolved;
        }
        return cachedBankTile;
    }

    private Tile resolveNearestReachableBankTile() {
        Tile viaApi = resolveNearestBankViaApi();
        if (viaApi != null) {
            return viaApi;
        }

        GameObject bankObject = GameObjects.closest(obj -> obj != null && obj.hasAction("Bank"));
        return bankObject != null ? bankObject.getTile() : null;
    }

    private Tile resolveNearestBankViaApi() {
        Object bank = script.getBank();
        if (bank == null) {
            return null;
        }

        try {
            Object location = bank.getClass().getMethod("getClosestBankLocation").invoke(bank);
            Tile tile = resolveBankTileFromLocation(location);
            if (tile != null) {
                return tile;
            }
        } catch (ReflectiveOperationException | SecurityException ignored) {
        }

        try {
            Object closest = bank.getClass().getMethod("getClosestBank").invoke(bank);
            if (closest instanceof GameObject) {
                return ((GameObject) closest).getTile();
            }
        } catch (ReflectiveOperationException | SecurityException ignored) {
        }

        return null;
    }

    private Tile resolveBankTileFromLocation(Object location) {
        if (location == null) {
            return null;
        }

        String[] accessors = {"getCenter", "getTile", "getPosition"};
        for (String accessor : accessors) {
            try {
                Object result = location.getClass().getMethod(accessor).invoke(location);
                if (result instanceof Tile) {
                    return (Tile) result;
                }
            } catch (ReflectiveOperationException | SecurityException ignored) {
            }
        }

        try {
            Object area = location.getClass().getMethod("getArea").invoke(location);
            if (area instanceof Area) {
                return ((Area) area).getCenter();
            }
        } catch (ReflectiveOperationException | SecurityException ignored) {
        }

        return null;
    }

    private boolean initiateBankTravel(Tile bankTile) {
        if (bankTile == null) {
            return false;
        }

        if (script.getWalking() == null) {
            return false;
        }

        Player player = script.getLocalPlayer();
        if (player != null && player.isMoving() && Objects.equals(bankTravelTarget, bankTile)) {
            return true;
        }

        bankTravelTarget = bankTile;

        if (script.getWalking().walk(bankTile)) {
            waitForBankMovement(bankTile);
            return true;
        }

        if (attemptWebWalk(bankTile)) {
            return true;
        }

        bankTravelTarget = null;
        return false;
    }

    private boolean attemptWebWalk(Tile bankTile) {
        Object walking = script.getWalking();
        if (walking == null) {
            return false;
        }

        try {
            Object result = walking.getClass().getMethod("walk", Tile.class, boolean.class).invoke(walking, bankTile, true);
            if (!(result instanceof Boolean) || (Boolean) result) {
                waitForBankMovement(bankTile);
                return true;
            }
        } catch (ReflectiveOperationException | SecurityException ignored) {
        }

        try {
            Object result = walking.getClass().getMethod("webWalk", Tile.class).invoke(walking, bankTile);
            if (!(result instanceof Boolean) || (Boolean) result) {
                waitForBankMovement(bankTile);
                return true;
            }
        } catch (ReflectiveOperationException | SecurityException ignored) {
        }

        return false;
    }

    private void waitForBankMovement(Tile bankTile) {
        if (bankTile == null) {
            return;
        }

        Sleep.sleepUntil(() -> {
            Player player = script.getLocalPlayer();
            if (player == null) {
                return true;
            }
            double distance = player.distance(bankTile);
            return player.isMoving() || (Double.isFinite(distance) && distance <= bankInteractionRange);
        }, 3000);
    }

    public void closeBank() {
        if (!script.getBank().isOpen()) {
            return;
        }
        script.getBank().close();
        Sleep.sleepUntil(() -> !script.getBank().isOpen(), 2000);
    }

    private boolean handleBankAccessIssue(String message, boolean attemptRecovery) {
        script.log(message);
        if (attemptRecovery && attemptTeleportToSafety()) {
            script.log("Teleport initiated to recover bank access; will retry once relocated.");
            cachedBankTile = null;
            bankTravelTarget = null;
            return false;
        }

        if (attemptRecovery) {
            script.log("Teleport unavailable or failed; stopping script to avoid running without bank access.");
        } else {
            script.log("Stopping script to avoid running without bank access.");
        }
        ScriptManager.getScriptManager().stop();
        return false;
    }

    private boolean attemptTeleportToSafety() {
        if (!script.getMagic().canCast(Normal.HOME_TELEPORT)) {
            script.log("Home teleport is not available to recover bank access.");
            return false;
        }

        script.log("Attempting home teleport to recover bank access...");
        if (!script.getMagic().castSpell(Normal.HOME_TELEPORT)) {
            script.log("Failed to cast home teleport while trying to reach a bank.");
            return false;
        }

        boolean started = Sleep.sleepUntil(
            () -> script.getLocalPlayer() != null && script.getLocalPlayer().isAnimating(),
            3000
        );
        boolean finished = Sleep.sleepUntil(
            () -> script.getLocalPlayer() == null || !script.getLocalPlayer().isAnimating(),
            30000
        );
        if (started && finished) {
            script.log("Home teleport completed for bank recovery.");
        }
        return true;
    }
}
