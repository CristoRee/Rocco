package com.cristoree.rocco.entity;

import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.core.animation.RawAnimation;

// Como acompaña Rocco cuando el jugador no va caminando: volando con el elytra, en el agua
// (flotador de patito o buceando con snorkel) o en su barquito. Cada valor es la animacion que
// tiene que verse; el servidor decide cual y el cliente solo la muestra. Las de una sola vez
// (despegar, aterrizar, meterse al agua...) duran lo que dura su animacion y despues el servidor
// pasa a la que sigue. Los ticks salen de ROCCO-VUELO-AGUA-BARCO.md.
// Siempre agregar al final: el ordinal viaja por red.
public enum RoccoMotion
{
    NONE(null, 0, Kind.GROUND),

    FLY_TAKEOFF("fly_takeoff", 18, Kind.AIR),
    FLY_GLIDE("fly_glide", 0, Kind.AIR),
    FLY_DIVE("fly_dive", 0, Kind.AIR),
    FLY_BOOST("fly_boost", 0, Kind.AIR),
    FLY_BOOST_END("fly_boost_end", 20, Kind.AIR),
    FLY_LAND("fly_land", 15, Kind.AIR),

    FLOAT_START("float_start", 14, Kind.SURFACE),
    FLOAT_IDLE("float_idle", 0, Kind.SURFACE),
    FLOAT_SWIM("float_swim", 0, Kind.SURFACE),
    FLOAT_END("float_end", 20, Kind.SURFACE),

    SWIM_DIVE("swim_dive", 22, Kind.UNDERWATER),
    SWIM_UNDER_IDLE("swim_under_idle", 0, Kind.UNDERWATER),
    SWIM_UNDER("swim_under", 0, Kind.UNDERWATER),
    SWIM_SURFACE("swim_surface", 20, Kind.UNDERWATER),

    BOAT_ENTER("boat_enter", 18, Kind.BOAT),
    BOAT_IDLE("boat_idle", 0, Kind.BOAT),
    BOAT_ROW("boat_row", 0, Kind.BOAT),
    BOAT_EXIT("boat_exit", 16, Kind.BOAT);

    public enum Kind { GROUND, AIR, SURFACE, UNDERWATER, BOAT }

    private static final RoccoMotion[] VALUES = values();

    @Nullable
    private final RawAnimation animation;
    private final int ticks;
    private final Kind kind;

    RoccoMotion(@Nullable String name, int ticks, Kind kind)
    {
        this.ticks = ticks;
        this.kind = kind;
        if (name == null)
        {
            this.animation = null;
        }
        else
        {
            // Las de una sola vez se sostienen en el ultimo fotograma: si el servidor tarda un
            // tick en pasar a la siguiente, no vuelve a la pose de reposo en el medio.
            String full = "animation.rocco." + name;
            this.animation = ticks > 0
                    ? RawAnimation.begin().thenPlayAndHold(full)
                    : RawAnimation.begin().thenLoop(full);
        }
    }

    @Nullable
    public RawAnimation animation()
    {
        return this.animation;
    }

    // 0 = bucle (dura lo que haga falta).
    public int ticks()
    {
        return this.ticks;
    }

    public boolean isTransition()
    {
        return this.ticks > 0;
    }

    public Kind kind()
    {
        return this.kind;
    }

    // Lo mueve el mod (sin la fisica de caminar): volando, buceando y en el barquito. En la
    // superficie flota con el agua de verdad y nada con la navegacion de siempre.
    public boolean steered()
    {
        return this.kind == Kind.AIR || this.kind == Kind.UNDERWATER || this.kind == Kind.BOAT;
    }

    // Las animaciones de vuelo ya lo acuestan; el renderer ademas lo inclina con el jugador.
    public boolean tiltsWithPlayer()
    {
        return this == FLY_GLIDE || this == FLY_DIVE || this == FLY_BOOST || this == FLY_BOOST_END;
    }

    // La cara la pone la animacion (el animo automatico no la pisa mientras dura).
    public boolean scriptedFace()
    {
        return isTransition() || this == FLY_BOOST;
    }

    public static RoccoMotion byId(int id)
    {
        return id >= 0 && id < VALUES.length ? VALUES[id] : NONE;
    }
}
