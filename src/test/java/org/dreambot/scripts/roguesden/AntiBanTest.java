package org.dreambot.scripts.roguesden;

import org.dreambot.api.methods.Calculations;
import org.dreambot.api.methods.camera.Camera;
import org.dreambot.api.methods.tabs.Tabs;
import org.dreambot.api.input.Mouse;
import org.dreambot.api.script.AbstractScript;
import org.dreambot.api.utilities.impl.ABCUtil;
import org.dreambot.api.utilities.sleep.Sleep;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.awt.Point;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

class AntiBanTest {

    private Field nextBreakField;
    private Field breakEndField;

    @BeforeEach
    void resetScheduler() throws Exception {
        nextBreakField = AntiBan.class.getDeclaredField("nextBreak");
        breakEndField = AntiBan.class.getDeclaredField("breakEnd");
        nextBreakField.setAccessible(true);
        breakEndField.setAccessible(true);
        nextBreakField.setLong(null, -1L);
        breakEndField.setLong(null, -1L);
    }

    private RoguesDenScript.Config baseConfig() {
        RoguesDenScript.Config cfg = new RoguesDenScript.Config();
        cfg.antiban = true;
        cfg.hoverEntities = false;
        cfg.randomRightClick = false;
        cfg.cameraPanning = false;
        cfg.idleMin = 0;
        cfg.idleMax = 0;
        cfg.breakIntervalMin = 1;
        cfg.breakIntervalMax = 1;
        cfg.breakLengthMin = 1;
        cfg.breakLengthMax = 1;
        return cfg;
    }

    private ABCUtil mockABC() {
        ABCUtil abc = mock(ABCUtil.class);
        when(abc.shouldCheckXP()).thenReturn(false);
        when(abc.shouldOpenTab()).thenReturn(false);
        when(abc.shouldHover()).thenReturn(false);
        when(abc.shouldOpenMenu()).thenReturn(false);
        when(abc.shouldRotateCamera()).thenReturn(false);
        when(abc.generateReactionTime()).thenReturn(0);
        return abc;
    }

    private AbstractScript mockScript() {
        AbstractScript script = mock(AbstractScript.class);
        Mouse mouse = mock(Mouse.class);
        when(mouse.getPosition()).thenReturn(new Point(0, 0));
        when(script.getMouse()).thenReturn(mouse);
        doNothing().when(script).log(anyString());
        return script;
    }

    @Test
    void permuteSchedulesBreakWhenDue() throws Exception {
        RoguesDenScript.Config cfg = baseConfig();
        ABCUtil abc = mockABC();
        AbstractScript script = mockScript();
        Tabs tabs = mock(Tabs.class);
        when(script.getTabs()).thenReturn(tabs);

        nextBreakField.setLong(null, 0L); // force break
        breakEndField.setLong(null, -1L);

        AntiBan.permute(script, abc, cfg);

        verify(tabs).logout();
        long breakEnd = breakEndField.getLong(null);
        assertTrue(breakEnd > System.currentTimeMillis());
    }

    @Test
    void permuteResetsTrackersAfterPermutation() throws Exception {
        RoguesDenScript.Config cfg = baseConfig();
        ABCUtil abc = mockABC();
        AbstractScript script = mockScript();

        nextBreakField.setLong(null, Long.MAX_VALUE); // no break scheduled
        breakEndField.setLong(null, -1L);

        AntiBan.permute(script, abc, cfg);

        verify(abc).generateTrackers();
    }

    @Test
    void permuteSkipsBreakSchedulingWhenDisabled() throws Exception {
        RoguesDenScript.Config cfg = baseConfig();
        cfg.breakIntervalMin = 0;
        cfg.breakIntervalMax = 0;
        cfg.breakLengthMin = 0;
        cfg.breakLengthMax = 0;

        ABCUtil abc = mockABC();
        AbstractScript script = mockScript();
        Tabs tabs = mock(Tabs.class);
        when(script.getTabs()).thenReturn(tabs);

        nextBreakField.setLong(null, 0L);
        breakEndField.setLong(null, 0L);

        AntiBan.permute(script, abc, cfg);

        verify(tabs, never()).logout();
        assertEquals(-1L, nextBreakField.getLong(null));
        assertEquals(-1L, breakEndField.getLong(null));
        verify(abc).generateTrackers();
    }

    @Test
    void permuteResetsSchedulerWhenAntibanDisabled() throws Exception {
        RoguesDenScript.Config cfg = baseConfig();
        cfg.antiban = false;

        ABCUtil abc = mockABC();
        AbstractScript script = mockScript();

        nextBreakField.setLong(null, 123L);
        breakEndField.setLong(null, 456L);

        AntiBan.permute(script, abc, cfg);

        assertEquals(-1L, nextBreakField.getLong(null));
        assertEquals(-1L, breakEndField.getLong(null));
        verify(abc, never()).generateTrackers();
    }

    @Test
    void sleepReactionSkipsWhenAntiBanDisabled() {
        RoguesDenScript.Config cfg = baseConfig();
        cfg.antiban = false;

        ABCUtil abc = mock(ABCUtil.class);

        AntiBan.sleepReaction(abc, cfg);

        verify(abc, never()).generateReactionTime();
    }

    @Test
    void sleepReactionUsesReactionWhenEnabled() {
        RoguesDenScript.Config cfg = baseConfig();
        cfg.antiban = true;

        ABCUtil abc = mock(ABCUtil.class);
        when(abc.generateReactionTime()).thenReturn(0);

        AntiBan.sleepReaction(abc, cfg);

        verify(abc).generateReactionTime();
    }

    @Test
    void permutePerformsMisclickWhenRandomTriggers() throws Exception {
        RoguesDenScript.Config cfg = baseConfig();
        cfg.breakIntervalMin = 0;
        cfg.breakIntervalMax = 0;
        cfg.breakLengthMin = 0;
        cfg.breakLengthMax = 0;

        ABCUtil abc = mockABC();
        AbstractScript script = mockScript();
        Mouse mouse = script.getMouse();
        Point start = new Point(50, 50);
        when(mouse.getPosition()).thenReturn(start);

        try (MockedStatic<Calculations> calculations = mockStatic(Calculations.class);
             MockedStatic<Sleep> sleep = mockStatic(Sleep.class)) {

            AtomicInteger call = new AtomicInteger();
            calculations.when(() -> Calculations.random(anyInt(), anyInt()))
                .thenAnswer(invocation -> {
                    int max = invocation.getArgument(1);
                    switch (call.getAndIncrement()) {
                        case 0:
                            return 1; // Trigger misclick
                        case 1:
                            return 10; // X offset
                        case 2:
                            return -10; // Y offset
                        case 3:
                            return max; // Skip moveRandomly
                        case 4:
                            return max; // Skip moveMouseOutsideScreen
                        case 5:
                            return max; // Skip occasional off screen move
                        default:
                            return max;
                    }
                });

            sleep.when(() -> Sleep.sleep(anyInt(), anyInt())).thenAnswer(invocation -> null);
            sleep.when(() -> Sleep.sleep(anyInt())).thenAnswer(invocation -> null);

            AntiBan.permute(script, abc, cfg);
        }

        verify(mouse).move(60, 40);
        verify(mouse).click();
        verify(mouse).move(start);
    }

    @Test
    void permuteRotatesCameraWhenRandomTriggers() throws Exception {
        RoguesDenScript.Config cfg = baseConfig();
        cfg.cameraPanning = true;
        cfg.breakIntervalMin = 0;
        cfg.breakIntervalMax = 0;
        cfg.breakLengthMin = 0;
        cfg.breakLengthMax = 0;

        ABCUtil abc = mockABC();
        when(abc.shouldRotateCamera()).thenReturn(true);

        AbstractScript script = mockScript();
        Camera camera = mock(Camera.class);
        when(script.getCamera()).thenReturn(camera);

        try (MockedStatic<Calculations> calculations = mockStatic(Calculations.class);
             MockedStatic<Sleep> sleep = mockStatic(Sleep.class)) {

            AtomicInteger call = new AtomicInteger();
            calculations.when(() -> Calculations.random(anyInt(), anyInt()))
                .thenAnswer(invocation -> {
                    int max = invocation.getArgument(1);
                    switch (call.getAndIncrement()) {
                        case 0:
                            return 99; // Skip misclick
                        case 1:
                            return max; // Skip moveRandomly
                        case 2:
                            return 1; // Skip moveMouseOutsideScreen
                        case 3:
                            return 1500; // Camera yaw
                        case 4:
                            return 350; // Camera pitch
                        case 5:
                            return max; // Skip occasional off screen move
                        default:
                            return max;
                    }
                });

            sleep.when(() -> Sleep.sleep(anyInt(), anyInt())).thenAnswer(invocation -> null);
            sleep.when(() -> Sleep.sleep(anyInt())).thenAnswer(invocation -> null);

            AntiBan.permute(script, abc, cfg);
        }

        verify(camera).rotateToYaw(1500);
        verify(camera).rotateToPitch(350);
    }
}

