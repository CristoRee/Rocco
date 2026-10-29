package com.cristoree.rocco.work;

import com.cristoree.rocco.config.RoccoCommonConfig;
import com.cristoree.rocco.entity.RoccoEntity;
import com.cristoree.rocco.entity.RoccoTask;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

// Guarda cosas suyas en cofres. A donde: el cofre que le marcaron (el que mira el jugador, o el
// de la cosecha) y si no, uno que ya tenga eso mismo, que es como ordena la gente.
public final class DepositErrand extends Errand
{
    public enum Mode { ALL, HARVEST, MATCHING }

    private record Visit(BlockPos chest, List<Item> items) {}

    private final Mode mode;
    @Nullable
    private final Predicate<ItemStack> filter;
    @Nullable
    private final BlockPos chosenChest;
    private final boolean quiet;

    private Deque<Visit> visits;
    private final List<Item> homeless = new ArrayList<>();
    private final List<ItemStack> stored = new ArrayList<>();
    private boolean visiting;
    private boolean chestFull;

    public DepositErrand(RoccoEntity rocco, @Nullable Player requester, Mode mode, @Nullable Predicate<ItemStack> filter,
                         @Nullable BlockPos chosenChest, boolean quiet)
    {
        super(rocco, requester);
        this.mode = mode;
        this.filter = filter;
        this.chosenChest = chosenChest;
        this.quiet = quiet;
    }

    @Override
    protected Status step()
    {
        if (this.visits == null)
        {
            planVisits();
            if (this.visits.isEmpty())
            {
                if (this.homeless.isEmpty())
                {
                    if (!this.quiet)
                    {
                        say("deposit_nothing");
                    }
                    return Status.FAILED;
                }
                if (!this.quiet)
                {
                    say("deposit_no_place", "items", names(this.homeless));
                }
                return Status.FAILED;
            }
        }

        if (this.visiting)
        {
            this.visiting = false;
            if (jobFailed() && !this.quiet)
            {
                say("fetch_unreachable");
            }
        }

        Visit next = this.visits.poll();
        if (next != null)
        {
            work(RoccoTask.INTERACTING, next.chest(), ItemStack.EMPTY, level -> storeIn(next));
            this.visiting = true;
            return Status.RUNNING;
        }

        if (this.stored.isEmpty())
        {
            if (!this.quiet)
            {
                say(this.chestFull ? "deposit_full" : "deposit_nothing");
            }
            return Status.FAILED;
        }

        if (!this.quiet)
        {
            say("deposit_done", "items", Names.stacks(this.stored));
        }
        if (this.chestFull && !this.quiet)
        {
            say("deposit_full");
        }
        else if (!this.homeless.isEmpty() && !this.quiet)
        {
            say("deposit_some_kept", "items", names(this.homeless));
        }
        return Status.DONE;
    }

    private void planVisits()
    {
        this.visits = new ArrayDeque<>();

        Set<Item> items = new LinkedHashSet<>();
        for (ItemStack stack : Inv.contents(this.rocco.inventory()))
        {
            if (selected(stack))
            {
                items.add(stack.getItem());
            }
        }
        if (items.isEmpty())
        {
            return;
        }

        BlockPos fixed = fixedChest();
        if (fixed != null)
        {
            this.visits.add(new Visit(fixed, new ArrayList<>(items)));
            return;
        }

        // A donde va cada cosa, como lo haria una persona prolija (los cofres van del mas cercano al
        // mas lejano):
        //  1. a un cofre que ya tenga eso mismo;
        //  2. a uno de su tipo (los minerales con los minerales, la comida con la comida);
        //  3. a un cofre vacio, que desde ahi queda para ese tipo;
        //  4. a cualquiera que tenga lugar.
        List<BlockPos> chests = Storage.find(this.level, this.rocco.blockPosition(), RoccoCommonConfig.WORK_SEARCH_RADIUS.get());
        Map<BlockPos, Container> opened = new LinkedHashMap<>();
        Map<BlockPos, ItemCategory> themes = new LinkedHashMap<>();
        for (BlockPos chest : chests)
        {
            Container container = Storage.open(this.level, chest, requester());
            if (container != null)
            {
                opened.put(chest, container);
                ItemCategory theme = themeOf(container);
                if (theme != null)
                {
                    themes.put(chest, theme);
                }
            }
        }
        Map<BlockPos, List<Item>> plan = new LinkedHashMap<>();

        for (Item item : items)
        {
            ItemStack probe = new ItemStack(item);
            ItemCategory category = ItemCategory.of(probe);
            BlockPos home = null;

            for (Map.Entry<BlockPos, Container> chest : opened.entrySet())
            {
                if (Inv.count(chest.getValue(), item) > 0 && Inv.room(chest.getValue(), probe) > 0)
                {
                    home = chest.getKey();
                    break;
                }
            }
            if (home == null)
            {
                for (Map.Entry<BlockPos, Container> chest : opened.entrySet())
                {
                    if (themes.get(chest.getKey()) == category && Inv.room(chest.getValue(), probe) > 0)
                    {
                        home = chest.getKey();
                        break;
                    }
                }
            }
            if (home == null)
            {
                for (Map.Entry<BlockPos, Container> chest : opened.entrySet())
                {
                    if (Inv.isEmpty(chest.getValue()) && !themes.containsKey(chest.getKey()) && !plan.containsKey(chest.getKey()))
                    {
                        home = chest.getKey();
                        themes.put(home, category);
                        break;
                    }
                }
            }
            if (home == null)
            {
                for (Map.Entry<BlockPos, Container> chest : opened.entrySet())
                {
                    if (Inv.room(chest.getValue(), probe) > 0)
                    {
                        home = chest.getKey();
                        break;
                    }
                }
            }

            if (home == null)
            {
                this.homeless.add(item);
            }
            else
            {
                plan.computeIfAbsent(home, key -> new ArrayList<>()).add(item);
            }
        }

        plan.forEach((chest, list) -> this.visits.add(new Visit(chest, list)));
    }

    // El tipo de cosas que guarda un cofre: el que ocupa al menos la mitad de lo que tiene.
    @Nullable
    static ItemCategory themeOf(Container container)
    {
        Map<ItemCategory, Integer> counts = new java.util.EnumMap<>(ItemCategory.class);
        int total = 0;
        for (ItemStack stack : Inv.contents(container))
        {
            counts.merge(ItemCategory.of(stack), stack.getCount(), Integer::sum);
            total += stack.getCount();
        }
        for (Map.Entry<ItemCategory, Integer> entry : counts.entrySet())
        {
            if (total > 0 && entry.getValue() * 2 >= total)
            {
                return entry.getKey();
            }
        }
        return null;
    }

    @Nullable
    private BlockPos fixedChest()
    {
        if (this.chosenChest != null && Storage.open(this.level, this.chosenChest, requester()) != null)
        {
            return this.chosenChest;
        }

        BlockPos harvest = this.rocco.getHarvestChest();
        if (this.mode == Mode.HARVEST && harvest != null)
        {
            if (Storage.open(this.level, harvest, requester()) != null)
            {
                return harvest;
            }
            // Lo rompieron o ya no esta: se olvida para no ir a un lugar vacio.
            this.rocco.setHarvestChest(null);
        }
        return null;
    }

    private boolean selected(ItemStack stack)
    {
        return switch (this.mode)
        {
            // Las herramientas se las queda: son con las que trabaja.
            case ALL -> !Tools.isTool(stack);
            case HARVEST -> this.rocco.harvestTypes().contains(stack.getItem());
            case MATCHING -> this.filter != null && this.filter.test(stack);
        };
    }

    private void storeIn(Visit visit)
    {
        Container container = Storage.open(this.level, visit.chest(), requester());
        if (container == null)
        {
            return;
        }

        Storage.animate(this.level, visit.chest(), true);
        BlockPos chest = visit.chest();
        this.rocco.schedule(16, () -> Storage.animate(this.level, chest, false));

        for (Item item : visit.items())
        {
            for (ItemStack stack : Inv.take(this.rocco.inventory(), candidate -> candidate.is(item) && selected(candidate), Integer.MAX_VALUE))
            {
                int before = stack.getCount();
                ItemStack leftover = Inv.insert(container, stack);
                if (leftover.getCount() < before)
                {
                    this.stored.add(stack.copyWithCount(before - leftover.getCount()));
                }
                if (!leftover.isEmpty())
                {
                    this.chestFull = true;
                    this.rocco.keep(leftover);
                }
            }
        }
    }

    private static String names(List<Item> items)
    {
        return Names.joinList(items.stream().map(Names::name).distinct().toList());
    }

    @Override
    public String describe()
    {
        return "guardando cosas en un cofre";
    }
}
