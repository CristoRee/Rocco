package com.cristoree.rocco.entity;

import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animation.RawAnimation;

// Las animaciones de las escenas largas (el asado, las tortas fritas, el truco y la botella rota).
// El servidor decide cual va y cuando termina cada una; el cliente solo la muestra. Las que no son
// bucle quedan quietas en su ultimo cuadro hasta que el servidor pide la siguiente, asi nunca se ve
// un salto aunque el paquete llegue un tick tarde.
public enum RoccoScene
{
    NONE(null, 0, false),

    BOTTLE_SMASH("bottle_smash", 39, false),

    ASADO_START("asado_start", 120, false),
    ASADO_A1("asado_cycle_a1", 100, false),
    ASADO_A2("asado_cycle_a2", 100, false),
    ASADO_A3("asado_cycle_a3", 100, false),
    ASADO_A4("asado_cycle_a4", 100, false),
    ASADO_B1("asado_cycle_b1", 100, false),
    ASADO_B2("asado_cycle_b2", 100, false),
    ASADO_B3("asado_cycle_b3", 100, false),
    ASADO_B4("asado_cycle_b4", 100, false),
    ASADO_END("asado_end", 100, false),

    TORTAS_START("tortas_start", 130, false),
    TORTAS_A("tortas_cycle_a", 348, false),
    TORTAS_B("tortas_cycle_b", 348, false),
    TORTAS_END("tortas_end", 117, false),

    TRUCO_SUMMON("truco_summon", 60, false),
    TRUCO_SETTLE("truco_settle", 20, false),
    TRUCO_IDLE("truco_idle", 120, true),
    TRUCO_IDLE_NOCARDS("truco_idle_nocards", 120, true),
    TRUCO_THINK("truco_think", 80, true),
    TRUCO_PLAY_1("truco_play_1", 22, false),
    TRUCO_PLAY_2("truco_play_2", 22, false),
    TRUCO_PLAY_3("truco_play_3", 22, false),
    TRUCO_CALL_TRUCO("truco_call_truco", 32, false),
    TRUCO_CALL_ENVIDO("truco_call_envido", 28, false),
    TRUCO_CALL_FLOR("truco_call_flor", 40, false),
    TRUCO_QUIERO("truco_quiero", 20, false),
    TRUCO_NO_QUIERO("truco_no_quiero", 22, false),
    TRUCO_WIN_HAND("truco_win_hand", 32, false),
    TRUCO_LOSE_HAND("truco_lose_hand", 32, false),
    TRUCO_MAZO("truco_mazo", 24, false),
    TRUCO_DEAL("truco_deal", 72, false),
    TRUCO_MATEAR("truco_matear", 104, false),
    TRUCO_WIN_GAME("truco_win_game", 70, false),
    TRUCO_LOSE_GAME("truco_lose_game", 70, false),
    TRUCO_CEBAR_IZQ("truco_cebar_izq", 92, false),
    TRUCO_CEBAR_FRENTE("truco_cebar_frente", 92, false),
    TRUCO_CEBAR_DER("truco_cebar_der", 92, false),
    TRUCO_MATE_WAIT_IZQ("truco_mate_wait_izq", 48, true),
    TRUCO_MATE_WAIT_FRENTE("truco_mate_wait_frente", 48, true),
    TRUCO_MATE_WAIT_DER("truco_mate_wait_der", 48, true),
    TRUCO_MATE_BACK_IZQ("truco_mate_back_izq", 52, false),
    TRUCO_MATE_BACK_FRENTE("truco_mate_back_frente", 52, false),
    TRUCO_MATE_BACK_DER("truco_mate_back_der", 52, false);

    private static final RoccoScene[] VALUES = values();

    public final String animationName;
    public final int ticks;
    public final boolean loops;
    @Nullable
    private final RawAnimation animation;

    RoccoScene(@Nullable String name, int ticks, boolean loops)
    {
        this.animationName = name == null ? "" : "animation.rocco." + name;
        this.ticks = ticks;
        this.loops = loops;
        this.animation = name == null ? null : loops
                ? RawAnimation.begin().thenLoop(this.animationName)
                : RawAnimation.begin().thenPlayAndHold(this.animationName);
    }

    @Nullable
    public RawAnimation animation()
    {
        return this.animation;
    }

    public boolean asado()
    {
        return this.ordinal() >= ASADO_START.ordinal() && this.ordinal() <= ASADO_END.ordinal();
    }

    public boolean tortas()
    {
        return this.ordinal() >= TORTAS_START.ordinal() && this.ordinal() <= TORTAS_END.ordinal();
    }

    public boolean truco()
    {
        return this.ordinal() >= TRUCO_SUMMON.ordinal();
    }

    // Las escenas de cocina dejan a Rocco plantado con sus cosas delante: la caja de dibujo se agranda.
    public boolean cooking()
    {
        return asado() || tortas();
    }

    public static RoccoScene byId(int id)
    {
        return id >= 0 && id < VALUES.length ? VALUES[id] : NONE;
    }

    // ---------- los huesos que maneja el codigo, empaquetados en un entero ----------
    // bits 0-1: la carne en la parrilla (0 ninguna, 1 vaca, 2 cerdo, 3 oveja)
    // bits 2-3: la proxima cruda del estante (igual)
    // bits 4-11: las cuatro piezas hechas del costado, dos bits cada una
    // bits 12-13: el tamaño de la masa (0 nada, 1 grande, 2 mediana, 3 chica)
    // bits 14-16: los niveles del pote de tortas (0 a 4)
    // bits 17-18: las cartas del abanico (0 a 3)
    public static final int MEAT_NONE = 0;
    public static final int MEAT_BEEF = 1;
    public static final int MEAT_PORK = 2;
    public static final int MEAT_LAMB = 3;

    public static int cookMeat(int bits)
    {
        return bits & 3;
    }

    public static int nextMeat(int bits)
    {
        return (bits >> 2) & 3;
    }

    public static int warmMeat(int bits, int slot)
    {
        return (bits >> (4 + slot * 2)) & 3;
    }

    public static int dough(int bits)
    {
        return (bits >> 12) & 3;
    }

    public static int bowl(int bits)
    {
        return (bits >> 14) & 7;
    }

    public static int fan(int bits)
    {
        return (bits >> 17) & 3;
    }

    public static int asadoBits(int cooking, int next, int[] warm, int warmCount)
    {
        int bits = (cooking & 3) | ((next & 3) << 2);
        for (int slot = 0; slot < Math.min(4, warmCount); slot++)
        {
            bits |= (warm[slot] & 3) << (4 + slot * 2);
        }
        return bits;
    }

    public static int tortasBits(int dough, int bowl)
    {
        return ((dough & 3) << 12) | ((Math.min(4, bowl) & 7) << 14);
    }

    public static int trucoBits(int fan)
    {
        return (Math.max(0, Math.min(3, fan)) & 3) << 17;
    }
}
