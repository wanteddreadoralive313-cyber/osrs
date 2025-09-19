package org.dreambot.scripts.roguesden;

import org.dreambot.api.wrappers.items.Item;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

class RoguesDenScriptTest {

    @Test
    void lowHpEatsFoodWhenAvailable() {
        RoguesDenScript script = createScriptWithFood();
        Item foodItem = Mockito.mock(Item.class);
        Mockito.when(foodItem.interact("Eat")).thenReturn(true);

        RoguesDenScript.HealthCheckResult result = script.handleHealthMaintenance(
            () -> 30,
            () -> true,
            () -> foodItem
        );

        assertEquals(RoguesDenScript.HealthCheckResult.CONSUMED_FOOD, result);
        Mockito.verify(foodItem).interact("Eat");
    }

    @Test
    void lowHpWithoutFoodMarksRestock() throws Exception {
        RoguesDenScript script = createScriptWithFood();
        setSuppliesReady(script, true);

        RoguesDenScript.HealthCheckResult result = script.handleHealthMaintenance(
            () -> 30,
            () -> false,
            () -> null
        );

        assertEquals(RoguesDenScript.HealthCheckResult.NEEDS_RESTOCK, result);
        assertFalse(isSuppliesReady(script));
    }

    @Test
    void healthyPlayerSkipsEating() {
        RoguesDenScript script = createScriptWithFood();
        Item foodItem = Mockito.mock(Item.class);

        RoguesDenScript.HealthCheckResult result = script.handleHealthMaintenance(
            () -> 90,
            () -> true,
            () -> foodItem
        );

        assertEquals(RoguesDenScript.HealthCheckResult.NO_ACTION, result);
        Mockito.verifyNoInteractions(foodItem);
    }

    private static RoguesDenScript createScriptWithFood() {
        RoguesDenScript.Config config = new RoguesDenScript.Config();
        config.minimumHealthPercent = 50;
        config.preferredFoodItem = "Lobster";
        return new RoguesDenScript(config);
    }

    private static void setSuppliesReady(RoguesDenScript script, boolean value) throws Exception {
        Field field = RoguesDenScript.class.getDeclaredField("suppliesReady");
        field.setAccessible(true);
        field.setBoolean(script, value);
    }

    private static boolean isSuppliesReady(RoguesDenScript script) throws Exception {
        Field field = RoguesDenScript.class.getDeclaredField("suppliesReady");
        field.setAccessible(true);
        return field.getBoolean(script);
    }
}
