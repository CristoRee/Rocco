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
    CELEBRATING(52, 52),
    // cola_drink dura 14,2 s: solo caras y sonidos, sin efecto.
    COLA(284, 284),
    // equip_proud dura 2 s: se mira la armadura nueva.
    EQUIP_PROUD(40, 40),
    // ender_absorb (3 s) y ender_awaken (4,2 s). El poder se activa en el tick 41 de awaken, con la
    // explosion de luz (ver RoccoEntity: si se corta antes, se activa igual).
    ENDER_ABSORB(60, 60),
    ENDER_AWAKEN(84, 41),
    // Comer de la mano (food_eat 2,6 s, food_eat_bad 3 s): la comida hace efecto al tragar, en el
    // tick 40. Tomar una pocion (potion_drink 2,5 s, potion_drink_bad 2,8 s): hace efecto en el 30.
    FOOD_EAT(52, 40),
    FOOD_EAT_BAD(60, 40),
    POTION_DRINK(50, 30),
    POTION_DRINK_BAD(56, 30),
    // Bailes en bucle de la v12 (8 s cada vuelta, 16 tiempos a 120 bpm): de a dos vueltas.
    DANCE_FIESTA(320, 320),
    DANCE_MENEITO(320, 320),
    // Cantando la musica que pone (bucles de 2,4 s, 1,25 s y 1,3 s): de a 16 segundos.
    SING_SOFT(320, 320),
    SING_GROOVE(320, 320),
    SING_RAP(320, 320),
    // Los regalos de la v13: lo entrega en el tick de impacto (3,55 s, 6,25 s y 2,05 s).
    GIVE_SEED(92, 71),
    GIVE_SEED_CHILL(92, 71),
    GIVE_FLOWER(164, 125),
    GIVE_FLOWER_CHILL(164, 125),
    GIVE_COLA(62, 41),
    // Reacciona al eructo (2,7 s) y se muestra con la mochila nueva (3,8 s): sin efecto.
    REACT_BURP(54, 54),
    BACKPACK_SHOW(76, 76);

    // Le da algo al jugador: lo que entrega se dibuja en el hueso held.
    public boolean gifting()
    {
        return this == GIVE_SEED || this == GIVE_SEED_CHILL || this == GIVE_FLOWER || this == GIVE_FLOWER_CHILL || this == GIVE_COLA;
    }

    // Con la tapa de flores (el modo chill de los regalos).
    public boolean chillGift()
    {
        return this == GIVE_SEED_CHILL || this == GIVE_FLOWER_CHILL;
    }

    // Animaciones de la v13 con sus caras y sonidos: se hacen ahi donde esta, mirando al jugador.
    public boolean scripted()
    {
        return gifting() || this == REACT_BURP || this == BACKPACK_SHOW;
    }

    // Lo que tiene en las patas va en el hueso held: la comida, la pocion y los regalos.
    public boolean usesHeld()
    {
        return consuming() || gifting();
    }

    // El nombre de la animacion (solo las de la v13).
    public String scriptedAnimation()
    {
        return switch (this)
        {
            case GIVE_SEED -> "give_seed";
            case GIVE_SEED_CHILL -> "give_seed_chill";
            case GIVE_FLOWER -> "give_flower";
            case GIVE_FLOWER_CHILL -> "give_flower_chill";
            case GIVE_COLA -> "give_cola";
            case REACT_BURP -> "react_burp";
            default -> "backpack_show";
        };
    }

    // Las cuatro de comer y tomar con la comida o la pocion en la mano.
    public boolean consuming()
    {
        return this == FOOD_EAT || this == FOOD_EAT_BAD || this == POTION_DRINK || this == POTION_DRINK_BAD;
    }

    // Los bailes y el canto de la musica: animaciones en bucle que siguen mientras suene algo.
    public boolean musical()
    {
        return this == DANCE_FIESTA || this == DANCE_MENEITO || this == SING_SOFT || this == SING_GROOVE || this == SING_RAP;
    }

    public boolean singing()
    {
        return this == SING_SOFT || this == SING_GROOVE || this == SING_RAP;
    }

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
