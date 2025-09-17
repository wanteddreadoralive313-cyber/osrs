package org.dreambot.scripts.roguesden;

import org.dreambot.api.methods.container.impl.Inventory;
import org.dreambot.api.methods.interactive.NPCs;
import org.dreambot.api.utilities.sleep.Sleep;
import org.dreambot.api.wrappers.interactive.NPC;
import org.dreambot.api.wrappers.items.Item;
import org.dreambot.api.script.ScriptManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Handles supply preparation and bank management for the Rogues' Den script.
 */
class SupplyManager {

    private static final String[] GEAR_ITEMS = {
        "Rogue mask", "Rogue top", "Rogue trousers", "Rogue gloves", "Rogue boots"
    };

    private final RoguesDenScript script;
    private final RoguesDenScript.Config config;
    private final String tokenName;

    private boolean suppliesReady;

    SupplyManager(RoguesDenScript script, RoguesDenScript.Config config, String tokenName) {
        this.script = script;
        this.config = config;
        this.tokenName = tokenName;
    }

    String getTokenName() {
        return tokenName;
    }

    boolean areSuppliesReady() {
        return suppliesReady;
    }

    void invalidateSupplies() {
        suppliesReady = false;
    }

    boolean prepareSupplies() {
        if (suppliesReady) {
            return true;
        }

        boolean bankOpened = false;

        if (!Inventory.isEmpty()) {
            if (!openBank()) {
                return false;
            }
            bankOpened = true;
            script.getBank().depositAllItems();
            if (!Sleep.sleepUntil(Inventory::isEmpty, 2000)) {
                script.log("Unable to fully clear inventory; continuing with remaining items.");
            }
        }

        for (String item : GEAR_ITEMS) {
            if (isGearEquipped(item) || Inventory.contains(item)) {
                continue;
            }

            if (!bankOpened && !openBank()) {
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
            Item stamina = getStaminaPotion();
            if (stamina == null) {
                if (!bankOpened && !openBank()) {
                    return false;
                }
                bankOpened = true;
                if (script.getBank().contains(this::isStaminaPotion)) {
                    script.getBank().withdraw(this::isStaminaPotion, 1);
                    Sleep.sleepUntil(() -> Inventory.contains(this::isStaminaPotion), 2000);
                } else {
                    script.log("No stamina potions available in bank.");
                }
            }
        }

        if (bankOpened) {
            if (Inventory.contains("Vial")) {
                script.getBank().depositAll("Vial");
                Sleep.sleepUntil(() -> !Inventory.contains("Vial"), 2000);
            }
            closeBank();
        }

        for (String item : GEAR_ITEMS) {
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

        suppliesReady = hasRequiredSupplies();
        if (!suppliesReady) {
            script.log("Supply verification failed, will retry.");
        }
        return suppliesReady;
    }

    boolean hasRequiredSupplies() {
        if (!hasFullRogueSetEquipped()) {
            return false;
        }
        if (config.useStamina && !hasStaminaPotion()) {
            return false;
        }
        return true;
    }

    boolean handleRewards() {
        if (Inventory.count(tokenName) < 1) {
            return false;
        }
        int attempts = 0;
        while (Inventory.count(tokenName) >= 1 && attempts < 3) {
            NPC npc = NPCs.closest("Rogue");
            if (npc != null && npc.interact("Claim")) {
                boolean success = Sleep.sleepUntil(
                    () -> Inventory.contains(i -> i != null && i.getName() != null && isRogueGear(i.getName())),
                    5000
                );
                if (success) {
                    if (hasFullRogueSet()) {
                        script.log("Full rogue set obtained. Stopping script.");
                        ScriptManager.getScriptManager().stop();
                    }
                    return true;
                } else {
                    script.log("No gear received, retrying...");
                }
            } else {
                script.log("Failed to locate reward NPC.");
            }
            attempts++;
            Sleep.sleep(600, 1200);
        }
        if (Inventory.count(tokenName) >= 1) {
            script.log("Failed to obtain gear after multiple attempts.");
        }
        return true;
    }

    Item getStaminaPotion() {
        return Inventory.get(this::isStaminaPotion);
    }

    private boolean hasFullRogueSetEquipped() {
        for (String item : GEAR_ITEMS) {
            if (!isGearEquipped(item)) {
                return false;
            }
        }
        return true;
    }

    private boolean hasFullRogueSet() {
        List<String> missing = new ArrayList<>();
        for (String item : GEAR_ITEMS) {
            if (!Inventory.contains(item)) {
                missing.add(item);
            }
        }
        if (missing.isEmpty()) {
            return true;
        }

        boolean opened = false;
        if (!script.getBank().isOpen()) {
            if (!script.getBank().openClosest()) {
                script.log("Could not open bank to verify rogue set.");
                return false;
            }
            Sleep.sleepUntil(() -> script.getBank().isOpen(), 5000);
            opened = true;
        }

        boolean allPresent = missing.stream().allMatch(i -> script.getBank().contains(i));

        if (opened) {
            script.getBank().close();
            Sleep.sleepUntil(() -> !script.getBank().isOpen(), 2000);
        }

        return allPresent;
    }

    private boolean hasStaminaPotion() {
        return Inventory.contains(this::isStaminaPotion);
    }

    private boolean isStaminaPotion(Item item) {
        return item != null && item.getName() != null && item.getName().contains("Stamina potion");
    }

    private boolean isRogueGear(String name) {
        return name != null && Arrays.asList(GEAR_ITEMS).contains(name);
    }

    private boolean isGearEquipped(String item) {
        return script.getEquipment() != null && script.getEquipment().contains(item);
    }

    private boolean openBank() {
        if (script.getBank().isOpen()) {
            return true;
        }
        if (!script.getBank().openClosest()) {
            script.log("Could not open bank to withdraw supplies.");
            return false;
        }
        if (!Sleep.sleepUntil(() -> script.getBank().isOpen(), 5000)) {
            script.log("Timed out waiting for bank to open.");
            return false;
        }
        return true;
    }

    private void closeBank() {
        if (!script.getBank().isOpen()) {
            return;
        }
        script.getBank().close();
        Sleep.sleepUntil(() -> !script.getBank().isOpen(), 2000);
    }
}
