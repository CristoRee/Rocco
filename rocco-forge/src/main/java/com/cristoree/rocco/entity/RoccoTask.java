package com.cristoree.rocco.entity;

// Los ticks de impacto salen de ROCCO-PLANTA-CARAS-Y-HABLA.md: son el momento en que el efecto
// real tiene que coincidir con el fotograma de la animacion.
public enum RoccoTask
{
    NONE(0, 0),
    PLANTING(36, 11),
    HARVESTING(40, 22),
    INTERACTING(18, 8),
    // eat dura 3,25 s y swallowterror 3,2 s. El tick de impacto es estimado: es cuando la
    // mandibula se cierra. Si no coincide con la animacion, se ajusta aca.
    EATING(65, 40),
    SWALLOWING(64, 40);

    private static final RoccoTask[] VALUES = values();

    private final int totalTicks;
    private final int impactTick;

    RoccoTask(int totalTicks, int impactTick)
    {
        this.totalTicks = totalTicks;
        this.impactTick = impactTick;
    }

    public int totalTicks()
    {
        return this.totalTicks;
    }

    public int impactTick()
    {
        return this.impactTick;
    }

    public static RoccoTask byId(int id)
    {
        return id >= 0 && id < VALUES.length ? VALUES[id] : NONE;
    }
}
