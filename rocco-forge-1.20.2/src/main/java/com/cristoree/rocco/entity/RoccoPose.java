package com.cristoree.rocco.entity;

// Postura de Rocco. Las de transicion duran lo que dura su animacion y despues pasan solas
// a la postura estable que les toca. Los ticks salen del largo real de cada animacion.
public enum RoccoPose
{
    STANDING(0, -1),
    SITTING_DOWN(14, 2),
    SITTING(0, -1),
    STANDING_UP(14, 0),
    FALLING_ASLEEP(40, 5),
    SLEEPING(0, -1),
    WAKING_UP(37, 0);

    private static final RoccoPose[] VALUES = values();

    private final int ticks;
    private final int nextOrdinal;

    RoccoPose(int ticks, int nextOrdinal)
    {
        this.ticks = ticks;
        this.nextOrdinal = nextOrdinal;
    }

    public int ticks()
    {
        return this.ticks;
    }

    // La postura a la que pasa cuando termina la transicion, o null si ya es estable.
    public RoccoPose next()
    {
        return this.nextOrdinal < 0 ? null : VALUES[this.nextOrdinal];
    }

    public boolean isTransition()
    {
        return this.ticks > 0;
    }

    // Quieto: no camina, no sigue al jugador ni pasea.
    public boolean isResting()
    {
        return this != STANDING;
    }

    public boolean isAsleep()
    {
        return this == SLEEPING || this == FALLING_ASLEEP;
    }

    public static RoccoPose byId(int id)
    {
        return id >= 0 && id < VALUES.length ? VALUES[id] : STANDING;
    }
}
