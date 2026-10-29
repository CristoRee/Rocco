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
    SWALLOWING(64, 40),
    // tool_swing es un bucle de 12 ticks con el golpe en el 5. Picar dura lo que diga el bloque
    // (el trabajo trae su duracion) y el efecto cae al final, cuando el bloque se rompe.
    MINING(12, 12),
    // animation.rocco.mate dura 30,6 s y no tiene "impacto": todo pasa en los ticks de caras y
    // sonidos. Siempre al final: el ordinal viaja por red.
    MATE(612, 612),
    // animation.rocco.enchant dura 5,6 s; el encantamiento se aplica en el empujon del tick 89.
    ENCHANTING(112, 89),
    // dance dura 6,6 s, clap 1,4 s y celebrate 2,6 s. Son festejos: no tienen efecto, solo la
    // animacion con sus caras y sonidos.
    DANCING(132, 132),
    CLAPPING(28, 28),
    CELEBRATING(52, 52);

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
