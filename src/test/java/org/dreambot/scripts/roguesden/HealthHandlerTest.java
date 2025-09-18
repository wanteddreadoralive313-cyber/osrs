package org.dreambot.scripts.roguesden;

import org.dreambot.api.utilities.impl.ABCUtil;
import org.dreambot.api.utilities.sleep.Sleep;
import org.dreambot.api.wrappers.items.Item;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class HealthHandlerTest {

    @Test
    void handleLowHealthEatsFood() {
        RoguesDenScript.Config config = new RoguesDenScript.Config();
        config.hpEatThreshold = 50;
        config.foodQuantity = 10;
        config.foodName = "Lobster";
        RoguesDenScript script = new RoguesDenScript(mock(ABCUtil.class), config);

        AtomicInteger calls = new AtomicInteger();
        IntSupplier hpSupplier = () -> calls.getAndIncrement() == 0 ? 40 : 60;

        Item food = mock(Item.class);
        when(food.interact("Eat")).thenReturn(true);
        AtomicBoolean provided = new AtomicBoolean(false);
        Supplier<Item> foodSupplier = () -> provided.getAndSet(true) ? null : food;
        Runnable outOfFood = mock(Runnable.class);

        try (MockedStatic<Sleep> sleep = mockStatic(Sleep.class)) {
            sleep.when(() -> Sleep.sleepUntil(any(BooleanSupplier.class), anyLong()))
                .thenAnswer(invocation -> {
                    BooleanSupplier condition = invocation.getArgument(0);
                    return condition.getAsBoolean();
                });

            boolean result = script.handleLowHealth(hpSupplier, foodSupplier, outOfFood);
            assertTrue(result);
        }

        verify(food).interact("Eat");
        verifyNoInteractions(outOfFood);
    }

    @Test
    void handleLowHealthSignalsOutOfFood() {
        RoguesDenScript.Config config = new RoguesDenScript.Config();
        config.hpEatThreshold = 60;
        config.foodQuantity = 5;
        config.foodName = "Shark";
        RoguesDenScript script = new RoguesDenScript(mock(ABCUtil.class), config);

        IntSupplier hpSupplier = () -> 30;
        Supplier<Item> foodSupplier = () -> null;
        Runnable outOfFood = mock(Runnable.class);

        try (MockedStatic<Sleep> sleep = mockStatic(Sleep.class)) {
            sleep.when(() -> Sleep.sleepUntil(any(BooleanSupplier.class), anyLong())).thenReturn(false);
            boolean result = script.handleLowHealth(hpSupplier, foodSupplier, outOfFood);
            assertFalse(result);
        }

        verify(outOfFood).run();
    }
}
