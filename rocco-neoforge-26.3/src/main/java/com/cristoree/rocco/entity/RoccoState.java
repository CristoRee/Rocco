package com.cristoree.rocco.entity;

// El orden viaja por red y queda guardado en el mundo: los nuevos van siempre al final.
public enum RoccoState
{
    PLANT,
    AWAKENING,
    ACTIVE,
    // Se murio y esta haciendo la animacion de la caida (death): quieto e invulnerable.
    DYING,
    // Enterradito en su tumba, al lado de donde reaparece el dueño, esperando que lo desentierren.
    BURIED,
    // Saliendo de la tumba (grave_rise_*): despues vuelve a ACTIVE.
    RISING;

    private static final RoccoState[] VALUES = values();

    public static RoccoState byId(int id)
    {
        return id >= 0 && id < VALUES.length ? VALUES[id] : PLANT;
    }

    public boolean isPlantVisible()
    {
        return this == PLANT || this == AWAKENING;
    }

    // En la tumba o saliendo de ella: se ve el monticulo, no hay IA ni se lo puede lastimar.
    public boolean isGrave()
    {
        return this == BURIED || this == RISING;
    }

    // Cualquier estado en el que no se mueve por su cuenta ni se lo puede lastimar.
    public boolean isInert()
    {
        return this != ACTIVE;
    }
}
