package com.cristoree.rocco.work;

import com.cristoree.rocco.entity.RoccoEntity;
import com.cristoree.rocco.entity.RoccoTask;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

// Siembra donde haya lugar, una semilla por planta. Si no le alcanzan las que tiene, primero
// pasa por los cofres de alrededor a buscar las que falten.
public final class PlantErrand extends Errand
{
    private static final int RADIUS = 8;

    private final Crops.Plantable plant;
    private final int count;
    // Donde estaba al recibir la orden: despues de ir al cofre por semillas, vuelve a sembrar ahi.
    private final BlockPos center;
    private boolean fetched;
    private boolean tillTried;
    private boolean queued;
    private int planted;
    private int missed;

    public PlantErrand(RoccoEntity rocco, @Nullable Player requester, Crops.Plantable plant, int count)
    {
        super(rocco, requester);
        this.plant = plant;
        this.count = count;
        this.center = rocco.blockPosition().immutable();
    }

    @Override
    protected Status step()
    {
        if (this.queued)
        {
            if (this.missed > 0 && this.planted > 0)
            {
                say("plant_seeds_ran_out", "item", Names.name(this.plant.shown()));
            }
            return this.planted > 0 ? Status.DONE : Status.FAILED;
        }

        List<BlockPos> spots = Crops.freeSpots(this.level, this.center, this.plant, RADIUS, this.count);
        if (spots.isEmpty())
        {
            // Sin tierra arada pero con azada: la prepara el y despues siembra.
            if (this.plant.soil() == Crops.Soil.FARMLAND && Tools.findHoe(this.rocco.inventory()) >= 0)
            {
                if (this.tillTried)
                {
                    return Status.FAILED;
                }
                this.tillTried = true;
                delegate(new TillErrand(this.rocco, requester(), this.count));
                return Status.RUNNING;
            }
            say(switch (this.plant.soil())
            {
                case FARMLAND -> "plant_no_farmland";
                case SOUL_SAND -> "plant_no_soul_sand";
                case GROUND -> "plant_no_ground";
            });
            return Status.FAILED;
        }

        // Le faltan semillas: va a buscarlas a los cofres (una sola vez) y despues siembra lo que pueda.
        int have = Crops.seeds(this.rocco, this.plant);
        if (have < spots.size() && !this.fetched)
        {
            this.fetched = true;
            FetchErrand fetch = FetchErrand.plan(this.rocco, requester(), stack -> stack.is(this.plant.shown()),
                    Names.name(this.plant.shown()), spots.size() - have, null, false, true);
            if (fetch != null)
            {
                delegate(fetch);
                return Status.RUNNING;
            }
        }
        if (have == 0)
        {
            say("plant_no_seeds", "item", Names.name(this.plant.shown()));
            return Status.FAILED;
        }

        for (BlockPos spot : spots.subList(0, Math.min(spots.size(), have)))
        {
            work(RoccoTask.PLANTING, spot, new ItemStack(this.plant.shown()), level ->
            {
                if (Crops.plant(level, spot, this.plant, this.rocco))
                {
                    this.planted++;
                }
            });
        }
        this.missed = spots.size() - Math.min(spots.size(), have);
        this.queued = true;
        return Status.RUNNING;
    }

    // Si no encontro semillas en los cofres, siembra igual con las que ya tenia.
    @Override
    protected boolean childDone(Errand child, boolean success)
    {
        return true;
    }

    @Override
    public String describe()
    {
        return "plantando " + Names.name(this.plant.shown());
    }
}
