package com.cristoree.rocco.work;

import com.cristoree.rocco.RoccoMod;
import com.cristoree.rocco.config.RoccoCommonConfig;
import com.cristoree.rocco.entity.RoccoEntity;
import com.cristoree.rocco.entity.RoccoTask;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Ordena los cofres: junta lo repartido, cada cosa va al cofre donde ya habia mas de eso, se
// completan los stacks y adentro de cada cofre queda todo en orden. Pasa por cada cofre y el
// cambio se hace de una sola vez al final, contando que no se pierda ni se duplique nada: si la
// cuenta no da, no toca nada.
public final class SortErrand extends Errand
{
    private static final int MAX_CHESTS = 16;

    private record Key(String id, String tag) {}

    @Nullable
    private final BlockPos onlyChest;
    private List<BlockPos> chests;
    private boolean queued;
    private boolean sorted;

    public SortErrand(RoccoEntity rocco, @Nullable Player requester, @Nullable BlockPos onlyChest)
    {
        super(rocco, requester);
        this.onlyChest = onlyChest;
    }

    @Override
    protected Status step()
    {
        if (this.queued)
        {
            if (!this.sorted)
            {
                say(jobFailed() ? "fetch_unreachable" : "sort_failed");
                return Status.FAILED;
            }
            say(this.chests.size() == 1 ? "sort_done_one" : "sort_done", "count", String.valueOf(this.chests.size()));
            return Status.DONE;
        }

        this.chests = this.onlyChest != null ? List.of(this.onlyChest) : sortable();
        if (this.chests.isEmpty())
        {
            say("sort_none");
            return Status.FAILED;
        }

        // Abre cada cofre como si revisara; en el ultimo deja todo ordenado.
        for (int i = 0; i < this.chests.size(); i++)
        {
            BlockPos chest = this.chests.get(i);
            boolean last = i == this.chests.size() - 1;
            work(RoccoEntity.Job.custom(RoccoTask.INTERACTING, chest, ItemStack.EMPTY, level ->
            {
                Storage.animate(level, chest, true);
                this.rocco.schedule(14, () -> Storage.animate(level, chest, false));
                if (last)
                {
                    this.sorted = sortAll(level);
                }
            }, null).reaching(3.0D));
        }
        this.queued = true;
        return Status.RUNNING;
    }

    // Cofres y barriles de alrededor (nunca cajas de shulker: no aceptan todo).
    private List<BlockPos> sortable()
    {
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos pos : Storage.find(this.level, this.rocco.blockPosition(), RoccoCommonConfig.WORK_SEARCH_RADIUS.get()))
        {
            BlockEntity entity = this.level.getBlockEntity(pos);
            if (entity instanceof ChestBlockEntity || entity instanceof BarrelBlockEntity)
            {
                out.add(pos);
            }
            if (out.size() >= MAX_CHESTS)
            {
                break;
            }
        }
        return out;
    }

    private boolean sortAll(ServerLevel level)
    {
        List<Container> containers = new ArrayList<>();
        for (BlockPos pos : this.chests)
        {
            Container container = Storage.open(level, pos, requester());
            if (container == null)
            {
                return false;
            }
            containers.add(container);
        }

        // Cuanto hay de cada cosa, y en que cofre habia mas.
        Map<Key, ItemStack> sample = new LinkedHashMap<>();
        Map<Key, Integer> totals = new HashMap<>();
        Map<Key, int[]> perChest = new HashMap<>();
        for (int c = 0; c < containers.size(); c++)
        {
            for (ItemStack stack : Inv.contents(containers.get(c)))
            {
                Key key = keyOf(stack);
                sample.putIfAbsent(key, stack.copyWithCount(1));
                totals.merge(key, stack.getCount(), Integer::sum);
                perChest.computeIfAbsent(key, k -> new int[containers.size()])[c] += stack.getCount();
            }
        }

        // Lo que mas hay se acomoda primero, en su cofre "de siempre".
        List<Key> order = new ArrayList<>(totals.keySet());
        order.sort(Comparator.comparingInt((Key key) -> -totals.get(key)));

        List<List<ItemStack>> plan = new ArrayList<>();
        for (int c = 0; c < containers.size(); c++)
        {
            plan.add(new ArrayList<>());
        }

        for (Key key : order)
        {
            int[] counts = perChest.get(key);
            int home = 0;
            for (int c = 1; c < counts.length; c++)
            {
                if (counts[c] > counts[home])
                {
                    home = c;
                }
            }

            ItemStack template = sample.get(key);
            int left = totals.get(key);
            int max = Math.max(1, template.getMaxStackSize());
            while (left > 0)
            {
                int amount = Math.min(max, left);
                int target = home;
                if (plan.get(target).size() >= containers.get(target).getContainerSize())
                {
                    target = -1;
                    for (int c = 0; c < containers.size(); c++)
                    {
                        if (plan.get(c).size() < containers.get(c).getContainerSize())
                        {
                            target = c;
                            break;
                        }
                    }
                }
                if (target < 0)
                {
                    // No deberia pasar (juntar stacks solo ocupa menos lugar), pero si pasa no toca nada.
                    RoccoMod.LOGGER.warn("[Rocco] Ordenar cofres: no entraba todo, no toque nada");
                    return false;
                }
                plan.get(target).add(template.copyWithCount(amount));
                left -= amount;
            }
        }

        // Adentro de cada cofre, en orden alfabetico por id (bloques y herramientas quedan juntos).
        for (List<ItemStack> stacks : plan)
        {
            stacks.sort(Comparator.comparing((ItemStack stack) -> Inv.id(stack)).thenComparingInt(stack -> -stack.getCount()));
        }

        // La cuenta tiene que dar exacta antes de tocar un solo cofre.
        Map<Key, Integer> after = new HashMap<>();
        for (List<ItemStack> stacks : plan)
        {
            for (ItemStack stack : stacks)
            {
                after.merge(keyOf(stack), stack.getCount(), Integer::sum);
            }
        }
        if (!after.equals(totals))
        {
            RoccoMod.LOGGER.warn("[Rocco] Ordenar cofres: la cuenta no daba, no toque nada");
            return false;
        }

        for (int c = 0; c < containers.size(); c++)
        {
            Container container = containers.get(c);
            List<ItemStack> stacks = plan.get(c);
            for (int slot = 0; slot < container.getContainerSize(); slot++)
            {
                container.setItem(slot, slot < stacks.size() ? stacks.get(slot) : ItemStack.EMPTY);
            }
            container.setChanged();
        }
        return true;
    }

    private static Key keyOf(ItemStack stack)
    {
        CompoundTag tag = stack.getTag();
        return new Key(Inv.id(stack), tag == null ? "" : tag.toString());
    }

    @Override
    public String describe()
    {
        return "ordenando los cofres";
    }
}
