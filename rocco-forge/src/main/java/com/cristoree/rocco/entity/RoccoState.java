package com.cristoree.rocco.entity;

public enum RoccoState
{
    PLANT,
    AWAKENING,
    ACTIVE;

    private static final RoccoState[] VALUES = values();

    public static RoccoState byId(int id)
    {
        return id >= 0 && id < VALUES.length ? VALUES[id] : PLANT;
    }

    public boolean isPlantVisible()
    {
        return this == PLANT || this == AWAKENING;
    }
}
