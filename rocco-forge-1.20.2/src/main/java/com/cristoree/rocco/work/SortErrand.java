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

// Ordena los cofres: junta lo repartido, completa los stacks y deja cada cofre de un tema
// (minerales, comida, madera...), en el que ya tenia mas de ese tipo, con todo en orden adentro.
// Pasa por cada cofre y el cambio se hace de una sola vez al final, contando que no se pierda ni
// se duplique nada: si la cuenta no da, no toca nada.
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

        // Cada cofre queda de un tema: cada tipo de cosa (minerales, comida, madera...) va al cofre
        // donde ya habia mas de ese tipo, asi lo que el jugador ya tenia acomodado casi no se mueve.
        // Un tipo que no entra en su cofre sigue en uno libre, y recien despues en cualquiera.
        Map<Key, ItemCategory> categoryOf = new HashMap<>();
        Map<ItemCategory, int[]> categoryPerChest = new java.util.EnumMap<>(ItemCategory.class);
        Map<ItemCategory, Integer> categoryTotals = new java.util.EnumMap<>(ItemCategory.class);
        for (Map.Entry<Key, int[]> entry : perChest.entrySet())
        {
            ItemCategory category = ItemCategory.of(sample.get(entry.getKey()));
            categoryOf.put(entry.getKey(), category);
            int[] row = categoryPerChest.computeIfAbsent(category, k -> new int[containers.size()]);
            for (int c = 0; c < row.length; c++)
            {
                row[c] += entry.getValue()[c];
            }
            categoryTotals.merge(category, totals.get(entry.getKey()), Integer::sum);
        }

        List<ItemCategory> categoryOrder = new ArrayList<>(categoryTotals.keySet());
        categoryOrder.sort(Comparator.comparingInt((ItemCategory category) -> -categoryTotals.get(category)));

        boolean[] claimed = new boolean[containers.size()];
        Map<ItemCategory, List<Integer>> homes = new java.util.EnumMap<>(ItemCategory.class);
        for (ItemCategory category : categoryOrder)
        {
            int[] row = categoryPerChest.get(category);
            int best = -1;
            for (int c = 0; c < row.length; c++)
            {
                if (!claimed[c] && row[c] > 0 && (best < 0 || row[c] > row[best]))
                {
                    best = c;
                }
            }
            List<Integer> list = new ArrayList<>();
            if (best >= 0)
            {
                claimed[best] = true;
                list.add(best);
            }
            homes.put(category, list);
        }

        // Primero los tipos que mas hay; adentro de cada tipo, cada cosa junta.
        List<Key> order = new ArrayList<>(totals.keySet());
        order.sort(Comparator.comparingInt((Key key) -> categoryOrder.indexOf(categoryOf.get(key)))
                .thenComparing(Key::id).thenComparing(Key::tag));

        List<List<ItemStack>> plan = new ArrayList<>();
        for (int c = 0; c < containers.size(); c++)
        {
            plan.add(new ArrayList<>());
        }

        for (Key key : order)
        {
            ItemStack template = sample.get(key);
            List<Integer> mine = homes.get(categoryOf.get(key));
            int left = totals.get(key);
            int max = Math.max(1, template.getMaxStackSize());
            while (left > 0)
            {
                int amount = Math.min(max, left);
                int target = -1;
                for (int c : mine)
                {
                    if (plan.get(c).size() < containers.get(c).getContainerSize())
                    {
                        target = c;
                        break;
                    }
                }
                if (target < 0)
                {
                    // Su cofre se lleno: toma uno que no sea de nadie...
                    for (int c = 0; c < containers.size(); c++)
                    {
                        if (!claimed[c] && plan.get(c).size() < containers.get(c).getContainerSize())
                        {
                            claimed[c] = true;
                            mine.add(c);
                            target = c;
                            break;
                        }
                    }
                }
                if (target < 0)
                {
                    // ...y si no queda ninguno, el que tenga lugar.
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

        // Adentro de cada cofre: por tipo, despues por id (cada cosa junta), los stacks llenos primero.
        for (List<ItemStack> stacks : plan)
        {
            stacks.sort(Comparator.comparingInt((ItemStack stack) -> ItemCategory.of(stack).ordinal())
                    .thenComparing(Inv::id)
                    .thenComparingInt(stack -> -stack.getCount()));
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
