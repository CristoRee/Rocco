package com.cristoree.rocco.work;

import com.cristoree.rocco.entity.RoccoEntity;
import com.cristoree.rocco.entity.RoccoTask;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// Tala el arbol natural mas cercano entero (con hacha si tiene) y planta otro en su lugar,
// con el poder que le dio la Madre Naturaleza. Nunca toca troncos de construcciones: solo los
// que tienen hojas naturales pegadas.
public final class ChopErrand extends Errand
{
    private static final int RADIUS = 14;
    private static final int MAX_LOGS = 64;

    private enum Phase { FIND, CHOPPING, REPLANT, DONE }

    private final boolean quiet;
    private Phase phase = Phase.FIND;
    private BlockPos base;
    private Item sapling = Items.AIR;
    private int logs;

    public ChopErrand(RoccoEntity rocco, @Nullable Player requester, boolean quiet)
    {
        super(rocco, requester);
        this.quiet = quiet;
    }

    @Override
    protected Status step()
    {
        switch (this.phase)
        {
            case FIND ->
            {
                List<BlockPos> tree = findTree();
                if (tree.isEmpty())
                {
                    say("chop_no_tree");
                    return Status.FAILED;
                }

                this.base = tree.get(0);
                this.sapling = saplingFor(this.level.getBlockState(this.base));

                for (int i = 0; i < tree.size(); i++)
                {
                    BlockPos log = tree.get(i);
                    BlockState state = this.level.getBlockState(log);
                    Tools.Choice choice = Tools.best(this.rocco.inventory(), state, this.level, log);
                    if (choice == null)
                    {
                        continue;
                    }
                    // Al primero tiene que llegar caminando; el resto lo baja desde ahi.
                    mine(log, choice, i == 0 ? 3.0D : 10.0D, drops -> this.logs++);
                }
                this.phase = Phase.CHOPPING;
                return Status.RUNNING;
            }
            case CHOPPING ->
            {
                if (this.logs == 0)
                {
                    jobFailed();
                    say("chop_cant_reach");
                    return Status.FAILED;
                }
                this.phase = Phase.REPLANT;
                Crops.Plantable plant = this.sapling == Items.AIR ? null : Crops.plantableFor(this.sapling);
                if (plant != null && Crops.fits(this.level, this.base, plant))
                {
                    work(RoccoTask.PLANTING, this.base, new ItemStack(this.sapling), level -> Crops.plant(level, this.base, plant));
                }
                return Status.RUNNING;
            }
            case REPLANT ->
            {
                this.phase = Phase.DONE;
                if (!this.quiet)
                {
                    say("chop_done", "count", String.valueOf(this.logs));
                }
                return Status.DONE;
            }
            default ->
            {
                return Status.DONE;
            }
        }
    }

    // Los troncos del arbol mas cercano, de abajo hacia arriba.
    private List<BlockPos> findTree()
    {
        BlockPos center = this.rocco.blockPosition();
        List<BlockPos> candidates = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-RADIUS, -4, -RADIUS), center.offset(RADIUS, 6, RADIUS)))
        {
            if (this.level.getBlockState(pos).is(BlockTags.LOGS) && !this.level.getBlockState(pos.below()).is(BlockTags.LOGS))
            {
                candidates.add(pos.immutable());
            }
        }
        candidates.sort(Comparator.comparingDouble(pos -> pos.distSqr(center)));

        Set<BlockPos> checked = new HashSet<>();
        for (BlockPos start : candidates)
        {
            if (checked.contains(start))
            {
                continue;
            }
            List<BlockPos> tree = connectedLogs(start);
            checked.addAll(tree);
            if (isNatural(tree))
            {
                tree.sort(Comparator.<BlockPos>comparingInt(BlockPos::getY).thenComparingDouble(pos -> pos.distSqr(start)));
                return tree;
            }
        }
        return List.of();
    }

    private List<BlockPos> connectedLogs(BlockPos start)
    {
        List<BlockPos> out = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(start);

        while (!queue.isEmpty() && out.size() < MAX_LOGS)
        {
            BlockPos pos = queue.poll();
            out.add(pos);
            for (BlockPos near : BlockPos.betweenClosed(pos.offset(-1, 0, -1), pos.offset(1, 1, 1)))
            {
                BlockPos next = near.immutable();
                if (Math.abs(next.getX() - start.getX()) > 6 || Math.abs(next.getZ() - start.getZ()) > 6)
                {
                    continue;
                }
                if (seen.add(next) && this.level.getBlockState(next).is(BlockTags.LOGS))
                {
                    queue.add(next);
                }
            }
        }
        return out;
    }

    // Un arbol de verdad tiene hojas que no puso nadie (las puestas a mano son "persistentes").
    private boolean isNatural(List<BlockPos> logs)
    {
        for (BlockPos log : logs)
        {
            for (BlockPos near : BlockPos.betweenClosed(log.offset(-1, -1, -1), log.offset(1, 1, 1)))
            {
                BlockState state = this.level.getBlockState(near);
                if (state.getBlock() instanceof LeavesBlock && !state.getValue(LeavesBlock.PERSISTENT))
                {
                    return true;
                }
            }
        }
        return false;
    }

    // De roble sale roble; el mangle da propagulo.
    private static Item saplingFor(BlockState log)
    {
        ResourceLocation key = ForgeRegistries.BLOCKS.getKey(log.getBlock());
        if (key == null)
        {
            return Items.AIR;
        }
        String path = key.getPath().replace("stripped_", "");
        if (path.startsWith("mangrove"))
        {
            return Items.MANGROVE_PROPAGULE;
        }
        String sapling = path.replace("_log", "_sapling").replace("_wood", "_sapling");
        Item item = Inv.item(key.getNamespace() + ":" + sapling);
        return item == Items.AIR ? Items.OAK_SAPLING : item;
    }

    @Override
    public String describe()
    {
        return "talando un arbol";
    }
}
