package com.cristoree.rocco.work;

import com.cristoree.rocco.entity.RoccoEntity;
import com.cristoree.rocco.entity.RoccoTask;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

// La huerta: cosecha lo maduro, lo deja replantado (sin gastar semillas, con su poder) y guarda
// la cosecha en el cofre que le marcaron o en uno que ya tenga eso. En modo "cuidar" se queda
// ahi haciendolo solo, rellena lo que quede vacio y vuelve a revisar cada tanto.
public final class FarmErrand extends Errand
{
    private enum Phase { HARVEST, FILL, DEPOSIT, AFTER, IDLE }

    private static final int RADIUS = 8;
    private static final int IDLE_TICKS = 200;

    private final boolean tend;
    private final BlockPos center;

    private Phase phase = Phase.HARVEST;
    private int harvested;
    private int sinceDeposit;
    private int idle;
    private boolean warnedNoChest;

    public FarmErrand(RoccoEntity rocco, @Nullable Player requester, boolean tend, BlockPos center)
    {
        super(rocco, requester);
        this.tend = tend;
        this.center = center.immutable();
    }

    public BlockPos center()
    {
        return this.center;
    }

    @Override
    public boolean persistent()
    {
        return this.tend;
    }

    @Override
    protected Status step()
    {
        switch (this.phase)
        {
            case HARVEST ->
            {
                List<BlockPos> ripe = Crops.findRipe(this.level, this.center, RADIUS, 16);
                if (ripe.isEmpty())
                {
                    this.phase = this.tend ? Phase.FILL : Phase.DEPOSIT;
                    return Status.RUNNING;
                }
                for (BlockPos pos : ripe)
                {
                    work(RoccoTask.HARVESTING, pos, ItemStack.EMPTY, level ->
                    {
                        List<ItemStack> drops = Crops.harvest(level, pos, this.rocco);
                        for (ItemStack drop : drops)
                        {
                            this.rocco.harvestTypes().add(drop.getItem());
                            this.harvested += drop.getCount();
                            this.sinceDeposit += drop.getCount();
                            this.rocco.keep(drop);
                        }
                        if (!drops.isEmpty())
                        {
                            this.rocco.showInHand(drops.get(0));
                        }
                    });
                }
                return Status.RUNNING;
            }
            case FILL ->
            {
                // Lo que quedo vacio se replanta con lo que mas hay en la huerta.
                Crops.Plantable plant = Crops.mostCommon(this.level, this.center, RADIUS);
                if (plant != null)
                {
                    for (BlockPos spot : Crops.freeSpots(this.level, this.center, plant, RADIUS, 16))
                    {
                        work(RoccoTask.PLANTING, spot, new ItemStack(plant.shown()), level -> Crops.plant(level, spot, plant));
                    }
                }
                this.phase = Phase.DEPOSIT;
                return Status.RUNNING;
            }
            case DEPOSIT ->
            {
                this.phase = Phase.AFTER;
                if (this.sinceDeposit > 0)
                {
                    this.sinceDeposit = 0;
                    delegate(new DepositErrand(this.rocco, requester(), DepositErrand.Mode.HARVEST, null, null, this.tend));
                }
                return Status.RUNNING;
            }
            case AFTER ->
            {
                if (!this.tend)
                {
                    if (this.harvested == 0)
                    {
                        say("harvest_none");
                        return Status.FAILED;
                    }
                    return Status.DONE;
                }
                if (emptySlots() == 0)
                {
                    say("farm_full");
                    return Status.DONE;
                }
                this.phase = Phase.IDLE;
                this.idle = 0;
                return Status.RUNNING;
            }
            case IDLE ->
            {
                // Se queda cerca de la huerta, sin pasear lejos.
                if (++this.idle % 20 == 0 && this.rocco.blockPosition().distSqr(this.center) > 36)
                {
                    this.rocco.getNavigation().moveTo(this.center.getX() + 0.5D, this.center.getY(), this.center.getZ() + 0.5D, 0.9D);
                }
                if (this.idle >= IDLE_TICKS)
                {
                    this.phase = Phase.HARVEST;
                }
                return Status.RUNNING;
            }
        }
        return Status.RUNNING;
    }

    // Que no haya donde guardar no corta la huerta: avisa una vez y sigue.
    @Override
    protected boolean childDone(Errand child, boolean success)
    {
        // Cosechando una vez, el propio encargo de guardar ya explico por que no pudo.
        if (!success && this.tend && child instanceof DepositErrand && !this.warnedNoChest)
        {
            this.warnedNoChest = true;
            say("harvest_kept");
        }
        return true;
    }

    private int emptySlots()
    {
        int free = 0;
        for (int slot = 0; slot < this.rocco.inventory().getContainerSize(); slot++)
        {
            if (this.rocco.inventory().getItem(slot).isEmpty())
            {
                free++;
            }
        }
        return free;
    }

    @Override
    public String describe()
    {
        return this.tend ? "cuidando la huerta" : "cosechando";
    }
}
