package net.vibmc.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TimeSystemTest {
    @Test
    void normalizesTimeAndReportsMinecraftPhases() {
        TimeSystem time = new TimeSystem();

        time.setTimeOfDay(-1);
        assertEquals(23999, time.timeOfDay());
        assertEquals("sunrise", time.phase());

        time.setTimeOfDay(6000);
        assertEquals("day", time.phase());
        time.setTimeOfDay(12000);
        assertEquals("sunset", time.phase());
        time.setTimeOfDay(13000);
        assertEquals("night", time.phase());
    }
}
