package com.cristoree.rocco;

import net.minecraft.world.level.Level;

// La hora del mundo. Desde 26.1 el tiempo lo lleva un reloj: la hora del dia es la del reloj del mundo normal.
public final class Days
{
    public static long dayTime(Level level)
    {
        return level.getOverworldClockTime();
    }

    // De dia: desde que amanece hasta que empieza a oscurecer (como el juego cuenta para dormir).
    public static boolean isDay(Level level)
    {
        long time = Math.floorMod(dayTime(level), 24000L);
        return time < 12542L || time >= 23460L;
    }

    public static boolean isNight(Level level)
    {
        return !isDay(level);
    }

    private Days() {}
}
