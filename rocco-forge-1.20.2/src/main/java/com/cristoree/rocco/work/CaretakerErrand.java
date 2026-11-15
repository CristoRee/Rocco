package com.cristoree.rocco.work;

import com.cristoree.rocco.entity.RoccoEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// "Quedate cuidando la casa": Rocco se queda ahi, aunque te vayas lejos, haciendo vueltas: la
// huerta (cosecha, replanta y guarda), los animales (esquila, los hace criar hasta el tope, saca
// los que sobran, junta lo que dejan y lo guarda) y un rato de descanso cerca del centro. Si un
// bicho lo ataca se defiende y sigue. Cuando volves te cuenta lo que hizo mientras no estabas.
// Dura hasta que le pidas otra cosa ("seguime", "pará"), y sobrevive a cerrar el mundo.
public final class CaretakerErrand extends Errand
{
    public enum Duty
    {
        CROPS, ANIMALS, HOME;

        boolean crops()
        {
            return this != ANIMALS;
        }

        boolean animals()
        {
            return this != CROPS;
        }

        public static Duty parse(String name)
        {
            try
            {
                return valueOf(name.toUpperCase(Locale.ROOT));
            }
            catch (IllegalArgumentException e)
            {
                return CROPS;
            }
        }
    }

    private enum Phase { CROPS, ANIMALS, REST }

    // Entre vuelta y vuelta. Los animales tardan 5 minutos en poder volver a criar, asi que no
    // hace falta mas seguido.
    private static final int REST_TICKS = 20 * 30;
    // Que tan lejos tiene que estar el jugador (o cuanto tiempo) para contar que "no estaba".
    private static final double AWAY_DISTANCE = 48.0D;
    private static final long AWAY_TICKS = 20 * 120;
    private static final int FEED_RESERVE = 16;

    private final Duty duty;
    private final BlockPos center;

    private Phase phase = Phase.CROPS;
    private int rest;
    @Nullable
    private FarmErrand farm;
    @Nullable
    private RanchErrand ranch;

    private boolean warnedShears;
    private boolean warnedCull;
    // Lo que hizo desde la ultima vez que te vio cerca, para contartelo.
    private int harvested;
    private int pairs;
    private int sheared;
    private int culled;
    private long lastNear = -1L;
    private boolean ownerAway;

    public CaretakerErrand(RoccoEntity rocco, @Nullable Player requester, Duty duty, BlockPos center)
    {
        super(rocco, requester);
        this.duty = duty;
        this.center = center.immutable();
    }

    public Duty duty()
    {
        return this.duty;
    }

    public BlockPos center()
    {
        return this.center;
    }

    @Override
    public boolean persistent()
    {
        return true;
    }

    // Cuida la casa horas: si lo atacan, deja lo que esta haciendo, se defiende y despues sigue.
    @Override
    public boolean allowsFighting()
    {
        return true;
    }

    @Override
    protected Status step()
    {
        watchOwner();
        switch (this.phase)
        {
            case CROPS ->
            {
                this.phase = Phase.ANIMALS;
                if (this.duty.crops())
                {
                    this.farm = new FarmErrand(this.rocco, requester(), true, this.center, false);
                    if (this.duty.animals())
                    {
                        // Se queda con un poco de trigo y zanahorias para darles a los animales.
                        this.farm.keepingAlso(RanchErrand.FEED, FEED_RESERVE);
                    }
                    delegate(this.farm);
                }
            }
            case ANIMALS ->
            {
                this.phase = Phase.REST;
                this.rest = 0;
                if (this.duty.animals())
                {
                    this.ranch = new RanchErrand(this.rocco, requester(), this.center, RanchErrand.ALL, !this.warnedCull);
                    delegate(this.ranch);
                }
            }
            case REST ->
            {
                // Descansa cerca del centro, sin irse lejos.
                if (++this.rest % 20 == 0 && this.rocco.blockPosition().distSqr(this.center) > 36)
                {
                    this.rocco.getNavigation().moveTo(this.center.getX() + 0.5D, this.center.getY(), this.center.getZ() + 0.5D, 0.9D);
                }
                if (this.rest >= REST_TICKS)
                {
                    this.phase = Phase.CROPS;
                }
            }
        }
        return Status.RUNNING;
    }

    // Nada de lo de una vuelta la corta: lo que no salio, sale en la proxima.
    @Override
    protected boolean childDone(Errand child, boolean success)
    {
        if (child == this.farm)
        {
            this.harvested += this.farm.harvested();
            this.farm = null;
        }
        else if (child == this.ranch)
        {
            this.pairs += this.ranch.pairs();
            this.sheared += this.ranch.sheared();
            this.culled += this.ranch.culled();
            this.warnedCull |= this.ranch.warnedCull();
            if (this.ranch.missingShears() && !this.warnedShears)
            {
                this.warnedShears = true;
                say("home_need_shears");
            }
            this.ranch = null;
        }
        return true;
    }

    // Si te fuiste un buen rato y volviste, te cuenta que hizo. Si no hizo nada, no dice nada.
    private void watchOwner()
    {
        long now = this.level.getGameTime();
        ServerPlayer owner = this.rocco.trueOwner();
        boolean near = owner != null && owner.level() == this.level
                && owner.distanceToSqr(this.center.getCenter()) < AWAY_DISTANCE * AWAY_DISTANCE;
        if (!near)
        {
            if (this.lastNear >= 0L && now - this.lastNear > AWAY_TICKS)
            {
                this.ownerAway = true;
            }
            return;
        }
        if (this.ownerAway)
        {
            this.ownerAway = false;
            report();
        }
        this.lastNear = now;
        // Mientras estas cerca ya lo ves: el resumen cuenta desde que te vas.
        this.harvested = 0;
        this.pairs = 0;
        this.sheared = 0;
        this.culled = 0;
    }

    private void report()
    {
        List<String> parts = new ArrayList<>();
        if (this.harvested > 0)
        {
            parts.add("coseche " + this.harvested + (this.harvested == 1 ? " cosa" : " cosas"));
        }
        if (this.pairs > 0)
        {
            parts.add(this.pairs == 1 ? "nacio una cria" : "nacieron " + this.pairs + " crias");
        }
        if (this.sheared > 0)
        {
            parts.add("esquile " + this.sheared + (this.sheared == 1 ? " oveja" : " ovejas"));
        }
        if (this.culled > 0)
        {
            parts.add("saque " + this.culled + (this.culled == 1 ? " animal que sobraba" : " animales que sobraban"));
        }
        if (!parts.isEmpty())
        {
            say("home_report", "done", Names.joinList(parts));
        }
    }

    @Override
    public String describe()
    {
        return switch (this.duty)
        {
            case CROPS -> "cuidando la huerta";
            case ANIMALS -> "cuidando los animales";
            case HOME -> "cuidando la casa (la huerta y los animales)";
        };
    }
}
