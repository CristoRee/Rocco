package com.cristoree.rocco.work;

import com.cristoree.rocco.config.RoccoCommonConfig;
import com.cristoree.rocco.entity.RoccoEntity;
import com.cristoree.rocco.entity.RoccoTask;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

// Saca un bloque (la mesa, el horno, un cofre, el cofre de ender...) con la herramienta que
// corresponde y se queda con lo que suelta. Si es un cofre con cosas, primero las guarda el.
public final class BreakErrand extends Errand
{
    private final BlockPos target;
    private boolean queued;
    private final List<ItemStack> got = new ArrayList<>();

    public BreakErrand(RoccoEntity rocco, @Nullable Player requester, BlockPos target)
    {
        super(rocco, requester);
        this.target = target.immutable();
    }

    @Override
    protected Status step()
    {
        if (this.queued)
        {
            if (jobFailed())
            {
                say("break_cant_reach");
                return Status.FAILED;
            }
            if (this.got.isEmpty())
            {
                say("break_nothing");
                return Status.DONE;
            }
            say("break_done", "items", Names.stacks(this.got));
            return Status.DONE;
        }

        BlockState state = this.level.getBlockState(this.target);
        if (state.isAir() || state.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock)
        {
            say("break_not_found", "item", "eso");
            return Status.FAILED;
        }
        if (state.getDestroySpeed(this.level, this.target) < 0.0F)
        {
            say("break_unbreakable");
            return Status.FAILED;
        }

        Tools.Choice choice;
        if (state.is(Blocks.ENDER_CHEST))
        {
            // Sin Toque de seda se hace obsidiana: no lo rompe.
            choice = Tools.bestWithSilkTouch(this.rocco.inventory(), state, this.level, this.target);
            if (choice == null)
            {
                say("break_ender_needs_silk");
                return Status.FAILED;
            }
        }
        else
        {
            choice = Tools.best(this.rocco.inventory(), state, this.level, this.target);
            if (choice == null)
            {
                say("break_unbreakable");
                return Status.FAILED;
            }
            if (!choice.dropsSomething())
            {
                say("break_need_tool", "tool", Tools.neededFor(state));
                return Status.FAILED;
            }
        }

        if (choice.ticks() > RoccoCommonConfig.WORK_MAX_MINING_SECONDS.get() * 20)
        {
            say("break_too_slow");
            return Status.FAILED;
        }

        // Un cofre (o un horno) con cosas: se las guarda antes, para que no queden tiradas.
        BlockEntity entity = this.level.getBlockEntity(this.target);
        if (entity instanceof Container container && !(entity instanceof ShulkerBoxBlockEntity) && !Inv.isEmpty(container)
                && !Inv.fitsAll(this.rocco.inventory(), Inv.contents(container)))
        {
            say("break_container_full");
            return Status.FAILED;
        }

        Tools.Choice chosen = choice;
        ItemStack shown = chosen.byHand() ? ItemStack.EMPTY : chosen.tool().copy();
        work(RoccoEntity.Job.custom(RoccoTask.MINING, this.target, shown, level ->
        {
            if (level.getBlockEntity(this.target) instanceof Container container
                    && !(level.getBlockEntity(this.target) instanceof ShulkerBoxBlockEntity))
            {
                for (int slot = 0; slot < container.getContainerSize(); slot++)
                {
                    ItemStack inside = container.removeItemNoUpdate(slot);
                    if (!inside.isEmpty())
                    {
                        this.rocco.keep(inside);
                    }
                }
                container.setChanged();
            }

            for (ItemStack drop : Tools.breakBlock(this.rocco, level, this.target, chosen))
            {
                this.got.add(drop.copy());
                this.rocco.keep(drop);
            }
        }, null).lasting(Math.max(6, chosen.ticks())).reaching(4.0D));

        this.queued = true;
        return Status.RUNNING;
    }

    // El bloque de ese tipo mas cercano. null si no hay ninguno a mano.
    @Nullable
    public static BlockPos nearest(RoccoEntity rocco, Names.Query query)
    {
        return Storage.findBlock((net.minecraft.server.level.ServerLevel) rocco.level(), rocco.blockPosition(), 10, 4,
                state -> !state.isAir() && query.items().contains(state.getBlock().asItem()));
    }

    @Override
    public String describe()
    {
        return "rompiendo " + Names.name(this.level.getBlockState(this.target).getBlock().asItem());
    }
}
