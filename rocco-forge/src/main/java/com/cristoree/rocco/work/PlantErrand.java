package com.cristoree.rocco.work;

import com.cristoree.rocco.entity.RoccoEntity;
import com.cristoree.rocco.entity.RoccoTask;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

// Siembra donde haya lugar. No gasta nada: Rocco lleva adentro el poder de las semillas.
public final class PlantErrand extends Errand
{
    private static final int RADIUS = 8;

    private final Crops.Plantable plant;
    private final int count;
    private boolean queued;
    private int planted;

    public PlantErrand(RoccoEntity rocco, @Nullable Player requester, Crops.Plantable plant, int count)
    {
        super(rocco, requester);
        this.plant = plant;
        this.count = count;
    }

    @Override
    protected Status step()
    {
        if (this.queued)
        {
            return this.planted > 0 ? Status.DONE : Status.FAILED;
        }

        List<BlockPos> spots = Crops.freeSpots(this.level, this.rocco.blockPosition(), this.plant, RADIUS, this.count);
        if (spots.isEmpty())
        {
            say(switch (this.plant.soil())
            {
                case FARMLAND -> "plant_no_farmland";
                case SOUL_SAND -> "plant_no_soul_sand";
                case GROUND -> "plant_no_ground";
            });
            return Status.FAILED;
        }

        for (BlockPos spot : spots)
        {
            work(RoccoTask.PLANTING, spot, new ItemStack(this.plant.shown()), level ->
            {
                if (Crops.fits(level, spot, this.plant))
                {
                    Crops.plant(level, spot, this.plant);
                    this.planted++;
                }
            });
        }
        this.queued = true;
        return Status.RUNNING;
    }

    @Override
    public String describe()
    {
        return "plantando " + Names.name(this.plant.shown());
    }
}
