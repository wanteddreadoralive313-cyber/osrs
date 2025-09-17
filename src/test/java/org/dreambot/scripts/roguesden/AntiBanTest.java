package org.dreambot.scripts.roguesden;

import org.dreambot.api.methods.tabs.Tab;
import org.dreambot.api.methods.tabs.Tabs;
import org.dreambot.api.script.AbstractScript;
import org.dreambot.api.input.Mouse;
import org.dreambot.api.utilities.impl.ABCUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.Point;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
    void permuteHandlesMissingTabsDuringBreak() throws Exception {
        RoguesDenScript.Config cfg = baseConfig();
        ABCUtil abc = mockABC();
        AbstractScript script = mockScript();
        when(script.getTabs()).thenReturn(null);

        nextBreakField.setLong(null, 0L);
        breakEndField.setLong(null, -1L);

        AntiBan.permute(script, abc, cfg);

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
    void permuteOpensInventoryTabWhenAvailable() throws Exception {
        RoguesDenScript.Config cfg = baseConfig();
        ABCUtil abc = mockABC();
        when(abc.shouldOpenTab()).thenReturn(true);

        AbstractScript script = mockScript();
        Tabs tabs = mock(Tabs.class);
        when(script.getTabs()).thenReturn(tabs);

        nextBreakField.setLong(null, Long.MAX_VALUE);
        breakEndField.setLong(null, -1L);

        AntiBan.permute(script, abc, cfg);

        verify(tabs).openWithMouse(Tab.INVENTORY);
    }

    @Test
    void permuteSkipsInventoryTabWhenUnavailable() throws Exception {
        RoguesDenScript.Config cfg = baseConfig();
        ABCUtil abc = mockABC();
        when(abc.shouldOpenTab()).thenReturn(true);

        AbstractScript script = mockScript();
        when(script.getTabs()).thenReturn(null);

        nextBreakField.setLong(null, Long.MAX_VALUE);
        breakEndField.setLong(null, -1L);

        AntiBan.permute(script, abc, cfg);

        verify(abc).generateTrackers();
    }

    @Test
    void permuteSkipsHoverWhenGameObjectsMissing() throws Exception {
        RoguesDenScript.Config cfg = baseConfig();
        cfg.hoverEntities = true;

        ABCUtil abc = mockABC();
        when(abc.shouldHover()).thenReturn(true);

        AbstractScript script = mockScript();
        when(script.getGameObjects()).thenReturn(null);

        nextBreakField.setLong(null, Long.MAX_VALUE);
        breakEndField.setLong(null, -1L);

        AntiBan.permute(script, abc, cfg);

        verify(abc).generateTrackers();
    }

    @Test
    void permuteSkipsRandomRightClickWhenMouseMissing() throws Exception {
        RoguesDenScript.Config cfg = baseConfig();
        cfg.randomRightClick = true;

        ABCUtil abc = mockABC();
        when(abc.shouldOpenMenu()).thenReturn(true);

        AbstractScript script = mockScript();
        when(script.getMouse()).thenReturn(null);

        nextBreakField.setLong(null, Long.MAX_VALUE);
        breakEndField.setLong(null, -1L);

        AntiBan.permute(script, abc, cfg);

        verify(abc).generateTrackers();
    }

    @Test
    void permuteSkipsCameraActionsWhenCameraMissing() throws Exception {
        RoguesDenScript.Config cfg = baseConfig();
        cfg.cameraPanning = true;

        ABCUtil abc = mockABC();
        when(abc.shouldRotateCamera()).thenReturn(true);

        AbstractScript script = mockScript();
        when(script.getCamera()).thenReturn(null);

        nextBreakField.setLong(null, Long.MAX_VALUE);
        breakEndField.setLong(null, -1L);

        AntiBan.permute(script, abc, cfg);

        verify(abc).generateTrackers();
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
}

