package com.cristoree.rocco.work;

import com.cristoree.rocco.entity.RoccoEntity;
import com.cristoree.rocco.entity.RoccoTask;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// Ara con azada la tierra que tiene agua cerca (si no, la tierra de cultivo se seca).
public final class TillErrand extends Errand
{
    private static final int RADIUS = 6;

    private final int max;
    private boolean queued;
    private int tilled;

    public TillErrand(RoccoEntity rocco, @Nullable Player requester, int max)
    {
        super(rocco, requester);
        this.max = max > 0 ? Math.min(max, 48) : 16;
    }

    @Override
    protected Status step()
    {
        if (this.queued)
        {
            if (this.tilled == 0)
            {
                return Status.FAILED;
            }
            say("till_done", "count", String.valueOf(this.tilled));
            return Status.DONE;
        }

        int hoe = Tools.findHoe(this.rocco.inventory());
        if (hoe < 0)
        {
            say("till_need_hoe");
            return Status.FAILED;
        }

        List<BlockPos> spots = findSpots();
        if (spots.isEmpty())
        {
            say("till_no_spot");
            return Status.FAILED;
        }

        ItemStack shown = this.rocco.inventory().getItem(hoe).copy();
        for (BlockPos spot : spots)
        {
            work(RoccoTask.INTERACTING, spot, shown, level ->
            {
                int slot = Tools.findHoe(this.rocco.inventory());
                if (slot < 0 || !tillable(spot))
                {
                    return;
                }
                level.setBlock(spot, Blocks.FARMLAND.defaultBlockState(), Block.UPDATE_ALL);
                level.playSound(null, spot, SoundEvents.HOE_TILL, SoundSource.BLOCKS, 1.0F, 1.0F);
                Tools.wear(this.rocco, slot);
                this.tilled++;
            });
        }
        this.queued = true;
        return Status.RUNNING;
    }

    private List<BlockPos> findSpots()
    {
        BlockPos center = this.rocco.blockPosition();
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-RADIUS, -2, -RADIUS), center.offset(RADIUS, 1, RADIUS)))
        {
            if (tillable(pos) && nearWater(pos) && !standingOn(pos))
            {
                out.add(pos.immutable());
            }
        }
        out.sort(Comparator.comparingDouble(pos -> pos.distSqr(center)));
        return out.size() > this.max ? out.subList(0, this.max) : out;
    }

    private boolean tillable(BlockPos pos)
    {
        BlockState state = this.level.getBlockState(pos);
        return (state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT_PATH))
                && this.level.getBlockState(pos.above()).isAir();
    }

    // Igual que el juego: agua a 4 bloques o menos, a la misma altura o uno arriba.
    private boolean nearWater(BlockPos pos)
    {
        for (BlockPos near : BlockPos.betweenClosed(pos.offset(-4, 0, -4), pos.offset(4, 1, 4)))
        {
            if (this.level.getFluidState(near).is(FluidTags.WATER))
            {
                return true;
            }
        }
        return false;
    }

    private boolean standingOn(BlockPos pos)
    {
        if (pos.equals(this.rocco.blockPosition().below()))
        {
            return true;
        }
        return this.level.players().stream().anyMatch(player -> player.blockPosition().below().equals(pos));
    }

    @Override
    public String describe()
    {
        return "arando la tierra";
    }
}
