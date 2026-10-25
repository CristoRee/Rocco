package com.cristoree.rocco.work;

import com.cristoree.rocco.config.RoccoCommonConfig;
import com.cristoree.rocco.entity.RoccoEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.Tags;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

// "Conseguime X": primero lo que ya lleva, despues los cofres, y si falta, de la naturaleza
// (tala un arbol o pica lo que suelte eso). Solo toca terreno natural, nunca construcciones.
public final class GatherErrand extends Errand
{
    private static final int RADIUS = 10;
    private static final int MAX_TREES = 4;

    private final List<Item> items;
    private final Predicate<ItemStack> filter;
    private final int amount;
    private final boolean keep;
    private final boolean quiet;

    private boolean chestsTried;
    private boolean craftTried;
    private boolean handedOver;
    private int trees;
    private int mined;
    private final Set<BlockPos> skipped = new HashSet<>();
    private final Map<BlockState, Boolean> dropsWanted = new HashMap<>();
    @Nullable
    private BlockPos lastTarget;

    public GatherErrand(RoccoEntity rocco, @Nullable Player requester, List<Item> items, int amount, boolean keep, boolean quiet)
    {
        super(rocco, requester);
        this.items = items;
        this.filter = stack -> items.contains(stack.getItem());
        this.amount = Math.max(1, amount);
        this.keep = keep;
        this.quiet = quiet;
    }

    @Override
    protected Status step()
    {
        if (this.handedOver)
        {
            return Status.DONE;
        }

        if (this.lastTarget != null)
        {
            if (jobFailed())
            {
                this.skipped.add(this.lastTarget);
            }
            this.lastTarget = null;
        }

        int have = Inv.count(this.rocco.inventory(), this.filter);
        if (have >= this.amount)
        {
            return finish();
        }

        if (!this.chestsTried)
        {
            this.chestsTried = true;
            FetchErrand fetch = FetchErrand.plan(this.rocco, requester(), this.filter, what(),
                    this.amount - have, null, false, true);
            if (fetch != null)
            {
                delegate(fetch);
                return Status.RUNNING;
            }
        }

        // Las tablas y los palos no salen del suelo: se fabrican (y eso si sabe conseguir troncos).
        if (!this.craftTried && !isNatural() && !Recipes.recipesFor(this.level, Inv.id(this.items.get(0))).isEmpty())
        {
            this.craftTried = true;
            delegate(new CraftErrand(this.rocco, requester(), this.items, this.amount - have, true, true));
            return Status.RUNNING;
        }

        if (!RoccoCommonConfig.WORK_GATHER_FROM_NATURE.get())
        {
            return giveUp(have);
        }

        if (isLog())
        {
            if (this.trees >= MAX_TREES)
            {
                return giveUp(have);
            }
            this.trees++;
            delegate(new ChopErrand(this.rocco, requester(), true));
            return Status.RUNNING;
        }

        if (this.mined > this.amount * 3 + 16)
        {
            return giveUp(have);
        }

        BlockPos source = findSource();
        if (source == null)
        {
            return giveUp(have);
        }

        BlockState state = this.level.getBlockState(source);
        Tools.Choice choice = Tools.best(this.rocco.inventory(), state, this.level, source);
        if (choice == null || !choice.dropsSomething())
        {
            say("gather_need_tool", "tool", Tools.neededFor(state), "item", what());
            return Status.FAILED;
        }
        if (choice.ticks() > RoccoCommonConfig.WORK_MAX_MINING_SECONDS.get() * 20)
        {
            this.skipped.add(source);
            return Status.RUNNING;
        }

        this.mined++;
        this.lastTarget = source;
        mine(source, choice, 4.0D, null);
        return Status.RUNNING;
    }

    @Override
    protected boolean childDone(Errand child, boolean success)
    {
        return true;
    }

    private Status finish()
    {
        if (!this.keep && RoccoCommonConfig.WORK_GIVE_RESULTS.get() && requester() != null)
        {
            this.handedOver = true;
            delegate(new GiveErrand(this.rocco, requester(), this.filter, this.amount, false));
            return Status.RUNNING;
        }
        if (!this.quiet)
        {
            say("gather_done", "amount", this.amount + " de " + what());
        }
        return Status.DONE;
    }

    private Status giveUp(int have)
    {
        if (have > 0 && !this.keep && RoccoCommonConfig.WORK_GIVE_RESULTS.get() && requester() != null)
        {
            say("gather_partial", "got", String.valueOf(have), "item", what());
            this.handedOver = true;
            delegate(new GiveErrand(this.rocco, requester(), this.filter, have, false));
            return Status.RUNNING;
        }
        if (!this.quiet || have == 0)
        {
            say("gather_not_found", "item", what());
        }
        return have > 0 ? Status.DONE : Status.FAILED;
    }

    private boolean isLog()
    {
        return new ItemStack(this.items.get(0)).is(ItemTags.LOGS);
    }

    // Lo que sale picando el terreno (roca, tierra, arena, minerales...) o talando.
    private boolean isNatural()
    {
        if (isLog())
        {
            return true;
        }
        return findSource() != null;
    }

    @Nullable
    private BlockPos findSource()
    {
        BlockPos center = this.rocco.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;

        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-RADIUS, -4, -RADIUS), center.offset(RADIUS, 4, RADIUS)))
        {
            double distance = pos.distSqr(center);
            if (distance >= bestDistance || this.skipped.contains(pos))
            {
                continue;
            }
            BlockState state = this.level.getBlockState(pos);
            if (!isTerrain(state) || !exposed(pos) || standingOn(pos) || !dropsWhatWeWant(state, pos))
            {
                continue;
            }
            best = pos.immutable();
            bestDistance = distance;
        }
        return best;
    }

    private static boolean isTerrain(BlockState state)
    {
        return state.is(BlockTags.BASE_STONE_OVERWORLD)
                || state.is(BlockTags.BASE_STONE_NETHER)
                || state.is(BlockTags.DIRT)
                || state.is(BlockTags.SAND)
                || state.is(Tags.Blocks.GRAVEL)
                || state.is(Tags.Blocks.ORES)
                || state.is(Blocks.CLAY)
                || state.is(Blocks.SNOW_BLOCK);
    }

    private boolean exposed(BlockPos pos)
    {
        for (Direction direction : Direction.values())
        {
            BlockState side = this.level.getBlockState(pos.relative(direction));
            if (side.isAir())
            {
                return true;
            }
        }
        return false;
    }

    // No le saca el piso de abajo a nadie.
    private boolean standingOn(BlockPos pos)
    {
        if (pos.equals(this.rocco.blockPosition().below()))
        {
            return true;
        }
        for (Player player : this.level.players())
        {
            if (player.blockPosition().below().equals(pos))
            {
                return true;
            }
        }
        return false;
    }

    // Se simula lo que suelta con la mejor herramienta que tiene (una vez por tipo de bloque).
    private boolean dropsWhatWeWant(BlockState state, BlockPos pos)
    {
        return this.dropsWanted.computeIfAbsent(state, key ->
        {
            Tools.Choice choice = Tools.best(this.rocco.inventory(), key, this.level, pos);
            ItemStack tool = choice == null || choice.byHand() ? ItemStack.EMPTY : choice.tool();
            List<ItemStack> drops = net.minecraft.world.level.block.Block.getDrops(key, this.level, pos, null, this.rocco, tool);
            return drops.stream().anyMatch(this.filter);
        });
    }

    private String what()
    {
        return Names.name(this.items.get(0));
    }

    @Override
    public String describe()
    {
        return "consiguiendo " + this.amount + " de " + what();
    }
}
