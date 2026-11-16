package com.cristoree.rocco.work;

import com.cristoree.rocco.entity.RoccoEntity;
import com.cristoree.rocco.entity.RoccoTask;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// Pone antorchas en lo mas oscuro de alrededor, separadas, hasta que no quede donde aparezcan
// bichos. Si no tiene antorchas pero si carbon y palos, se las fabrica.
public final class LightErrand extends Errand
{
    private static final int RADIUS = 9;
    private static final int DARK = 3;
    private static final int SPACING = 5;

    private final int max;
    private int placed;
    private boolean craftTried;
    private boolean placing;
    @Nullable
    private BlockPos lastSpot;
    private final Set<BlockPos> skipped = new HashSet<>();
    private final List<BlockPos> mine = new ArrayList<>();

    public LightErrand(RoccoEntity rocco, @Nullable Player requester, int max)
    {
        super(rocco, requester);
        this.max = max > 0 ? Math.min(max, 32) : 6;
    }

    @Override
    protected Status step()
    {
        if (this.placing)
        {
            this.placing = false;
            if (jobFailed() && this.lastSpot != null)
            {
                this.skipped.add(this.lastSpot);
            }
        }

        if (this.placed >= this.max)
        {
            say("light_done", "count", String.valueOf(this.placed));
            return Status.DONE;
        }

        Item torch = torchItem();
        if (torch == null)
        {
            if (!this.craftTried && this.placed == 0)
            {
                this.craftTried = true;
                delegate(new CraftErrand(this.rocco, requester(), List.of(Items.TORCH), Math.max(4, this.max), true, true));
                return Status.RUNNING;
            }
            say(this.placed > 0 ? "light_ran_out" : "light_no_torches", "count", String.valueOf(this.placed));
            return this.placed > 0 ? Status.DONE : Status.FAILED;
        }

        BlockPos spot = darkestSpot();
        if (spot == null)
        {
            say(this.placed == 0 ? "light_already_lit" : "light_done", "count", String.valueOf(this.placed));
            return Status.DONE;
        }

        this.lastSpot = spot;
        this.placing = true;
        BlockHitResult floor = new BlockHitResult(Vec3.atCenterOf(spot.below()).add(0.0D, 0.5D, 0.0D),
                Direction.UP, spot.below(), false);
        work(RoccoTask.INTERACTING, spot, new ItemStack(torch), level ->
        {
            if (placeTorch(torch, floor))
            {
                this.placed++;
                this.mine.add(spot);
            }
            else
            {
                this.skipped.add(spot);
            }
        });
        return Status.RUNNING;
    }

    @Override
    protected boolean childDone(Errand child, boolean success)
    {
        return true;
    }

    @Nullable
    private Item torchItem()
    {
        if (Inv.count(this.rocco.inventory(), Items.TORCH) > 0)
        {
            return Items.TORCH;
        }
        if (Inv.count(this.rocco.inventory(), Items.SOUL_TORCH) > 0)
        {
            return Items.SOUL_TORCH;
        }
        return null;
    }

    private boolean placeTorch(Item torch, BlockHitResult floor)
    {
        for (int slot = 0; slot < this.rocco.inventory().getContainerSize(); slot++)
        {
            ItemStack stack = this.rocco.inventory().getItem(slot);
            if (stack.is(torch))
            {
                var context = new net.minecraft.world.item.context.BlockPlaceContext(
                        this.level, null, net.minecraft.world.InteractionHand.MAIN_HAND, stack, floor);
                if (!context.canPlace())
                {
                    return false;
                }
                // Antorcha de pie: se pone directo, sin preguntarle nada a un jugador que no existe.
                BlockState state = (torch == Items.SOUL_TORCH ? Blocks.SOUL_TORCH : Blocks.TORCH).defaultBlockState();
                BlockPos pos = context.getClickedPos();
                if (!state.canSurvive(this.level, pos))
                {
                    return false;
                }
                this.level.setBlock(pos, state, net.minecraft.world.level.block.Block.UPDATE_ALL);
                this.level.playSound(null, pos, net.minecraft.sounds.SoundEvents.WOOD_PLACE,
                        net.minecraft.sounds.SoundSource.BLOCKS, 0.8F, 1.0F);
                stack.shrink(1);
                if (stack.isEmpty())
                {
                    this.rocco.inventory().setItem(slot, ItemStack.EMPTY);
                }
                this.rocco.inventory().setChanged();
                return true;
            }
        }
        return false;
    }

    // Lo mas oscuro primero; separado de las que ya puso para no gastar de mas.
    @Nullable
    private BlockPos darkestSpot()
    {
        BlockPos center = this.rocco.blockPosition();
        BlockPos best = null;
        int bestLight = Integer.MAX_VALUE;
        double bestDistance = Double.MAX_VALUE;

        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-RADIUS, -2, -RADIUS), center.offset(RADIUS, 2, RADIUS)))
        {
            if (this.skipped.contains(pos) || !this.level.getBlockState(pos).isAir())
            {
                continue;
            }

            BlockPos below = pos.below();
            BlockState ground = this.level.getBlockState(below);
            if (!ground.isFaceSturdy(this.level, below, Direction.UP)
                    || ground.is(Blocks.FARMLAND) || ground.is(BlockTags.LEAVES) || ground.is(Blocks.WATER))
            {
                continue;
            }

            int light = this.level.getBrightness(LightLayer.BLOCK, pos);
            if (light > DARK || tooCloseToOthers(pos))
            {
                continue;
            }

            double distance = pos.distSqr(center);
            if (light < bestLight || (light == bestLight && distance < bestDistance))
            {
                best = pos.immutable();
                bestLight = light;
                bestDistance = distance;
            }
        }
        return best;
    }

    private boolean tooCloseToOthers(BlockPos pos)
    {
        for (BlockPos other : this.mine)
        {
            if (other.distManhattan(pos) < SPACING)
            {
                return true;
            }
        }
        return false;
    }

    @Override
    public String describe()
    {
        return "poniendo antorchas";
    }
}
