package com.example.titles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.titles.api.TitlePriority;
import com.example.titles.api.TitlesSettings;
import org.junit.jupiter.api.Test;

class TitlePriorityTest {
    private static final long MIN = 60_000L;
    private static final long T0 = 1_000_000_000L;

    @Test
    void protectionWearsOffLinearlyOverThirtyMinutes() {
        assertEquals(80, TitlePriority.effective(80, T0, T0, 30, true));
        assertEquals(40, TitlePriority.effective(80, T0, T0 + 15 * MIN, 30, true));
        assertEquals(3, TitlePriority.effective(80, T0, T0 + 29 * MIN, 30, true));
        assertEquals(0, TitlePriority.effective(80, T0, T0 + 30 * MIN, 30, true));
        assertEquals(0, TitlePriority.effective(80, T0, T0 + 600 * MIN, 30, true));
    }

    @Test
    void creeperMagnetNoLongerLocksOutFallDeathsForever() {
        // Priority 80 title; a fall death (20) is blocked at first but gets through after ~23 min.
        assertFalse(TitlePriority.canReplace(20, TitlePriority.effective(80, T0, T0 + 10 * MIN, 30, true)));
        assertTrue(TitlePriority.canReplace(20, TitlePriority.effective(80, T0, T0 + 23 * MIN, 30, true)));
    }

    @Test
    void adminTitlesAndDisabledDecayKeepFullProtection() {
        assertEquals(80, TitlePriority.effective(80, T0, T0 + 999 * MIN, 30, false));
        assertEquals(80, TitlePriority.effective(80, T0, T0 + 999 * MIN, -1, true));
        assertEquals(-1, TitlePriority.minutesUntilOpen(T0, T0, 30, false));
    }

    @Test
    void legacyTitlesWithoutTimestampAreReplaceable() {
        assertEquals(0, TitlePriority.effective(95, 0, T0, 30, true));
        assertEquals(0, TitlePriority.minutesUntilOpen(0, T0, 30, true));
    }

    @Test
    void minutesUntilOpenRoundsUp() {
        assertEquals(30, TitlePriority.minutesUntilOpen(T0, T0, 30, true));
        assertEquals(1, TitlePriority.minutesUntilOpen(T0, T0 + 29 * MIN + 1, 30, true));
        assertEquals(0, TitlePriority.minutesUntilOpen(T0, T0 + 31 * MIN, 30, true));
    }

    @Test
    void defaultIsThirtyMinutes() {
        assertEquals(30, TitlesSettings.defaults().protectionDecayMinutes());
    }
}
