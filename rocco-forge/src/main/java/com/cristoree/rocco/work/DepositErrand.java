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

    // Cuanto de cada cosa va a ese cofre (una cosa puede repartirse entre varios).
    private record Visit(BlockPos chest, Map<Item, Integer> items) {}

    // Si algo no entro donde pensaba (alguien toco el cofre) o no llego, reparte de nuevo lo que
    // le quedo entre los otros cofres, hasta estas veces.
    private static final int MAX_REPLANS = 3;

    private final Mode mode;
    @Nullable
    private final Predicate<ItemStack> filter;
    @Nullable
    private final BlockPos chosenChest;
    private final boolean quiet;

    private Deque<Visit> visits;
    // Lo que se queda aunque entre en la seleccion: las semillas para replantar, por ejemplo.
    private final Map<Item, Integer> reserve = new java.util.HashMap<>();
    private final List<ItemStack> stored = new ArrayList<>();
    // Cofres a los que no pudo llegar: al repartir de nuevo ya no cuentan.
    private final Set<BlockPos> unreachable = new java.util.HashSet<>();
    @Nullable
    private Visit current;
    private boolean visiting;
    private boolean leftBehind;
    private boolean noChests;
    private int replans;

    public DepositErrand(RoccoEntity rocco, @Nullable Player requester, Mode mode, @Nullable Predicate<ItemStack> filter,
                         @Nullable BlockPos chosenChest, boolean quiet)
    {
        super(rocco, requester);
        this.mode = mode;
        this.filter = filter;
        this.chosenChest = chosenChest;
        this.quiet = quiet;
    }

    // Se queda con 'amount' de cada una de esas cosas y guarda el resto.
    public DepositErrand keeping(java.util.Collection<Item> items, int amount)
    {
        items.forEach(item -> this.reserve.put(item, amount));
        return this;
    }

    @Override
    protected Status step()
    {
        if (this.visits == null)
        {
            planVisits();
        }

        if (this.visiting)
        {
            this.visiting = false;
            if (jobFailed() && this.current != null)
            {
                // No llego a ese cofre: lo que iba ahi prueba en otro.
                this.unreachable.add(this.current.chest());
                this.leftBehind = true;
            }
        }

        Visit next = this.visits.poll();
        if (next == null && this.leftBehind && this.replans < MAX_REPLANS)
        {
            this.replans++;
            this.leftBehind = false;
            planVisits();
            next = this.visits.poll();
        }
        if (next != null)
        {
            Visit visit = next;
            this.current = visit;
            work(RoccoTask.INTERACTING, visit.chest(), ItemStack.EMPTY, level -> storeIn(visit));
            this.visiting = true;
            return Status.RUNNING;
        }

        List<Item> left = remaining();
        if (this.stored.isEmpty())
        {
            if (!this.quiet)
            {
                if (left.isEmpty())
                {
                    say("deposit_nothing");
                }
                else if (!this.unreachable.isEmpty())
                {
                    say("fetch_unreachable");
                }
                else
                {
                    // Sin ningun cofre cerca no puede decir que estan llenos (antes lo decia igual).
                    if (this.noChests)
                    {
                        Player player = requester();
                        com.cristoree.rocco.RoccoMod.LOGGER.info("[Rocco] Guardar: ningun cofre a {} bloques (ni al doble). Rocco en {}, jugador en {}",
                                RoccoCommonConfig.WORK_SEARCH_RADIUS.get(), this.rocco.blockPosition(),
                                player == null ? "-" : player.blockPosition());
                    }
                    say(this.noChests ? "deposit_no_chest" : "deposit_no_place", "items", names(left));
                }
            }
            return Status.FAILED;
        }

        if (!this.quiet)
        {
            say("deposit_done", "items", Names.stacks(this.stored));
            if (!left.isEmpty())
            {
                if (this.unreachable.isEmpty())
                {
                    say("deposit_some_kept", "items", names(left));
                }
                else
                {
                    say("fetch_unreachable");
                }
            }
        }
        return Status.DONE;
    }

    // Lo que quedo sin guardar de lo que tenia que guardar.
    private List<Item> remaining()
    {
        Set<Item> left = new LinkedHashSet<>();
        for (ItemStack stack : Inv.contents(this.rocco.inventory()))
        {
            if (selected(stack) && spare(stack.getItem()) > 0)
            {
                left.add(stack.getItem());
            }
        }
        return new ArrayList<>(left);
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
        items.removeIf(item -> spare(item) <= 0);
        if (items.isEmpty())
        {
            return;
        }

        // A donde va cada cosa, como lo haria una persona prolija (el cofre que le marcaron va
        // primero; los demas, del mas cercano al mas lejano):
        //  1. a un cofre que ya tenga eso mismo;
        //  2. a uno de su tipo (los minerales con los minerales, la comida con la comida);
        //  3. a un cofre vacio, que desde ahi queda para ese tipo;
        //  4. a cualquiera que tenga lugar.
        // Reparte sobre copias de los cofres: asi cada uno se va llenando en la cuenta igual que en
        // serio. Antes miraba si entraba UN item y mandaba todo: lo que no entraba volvia con el y
        // decia que el cofre estaba lleno, aunque al lado hubiera otros vacios.
        Map<BlockPos, Container> opened = new LinkedHashMap<>();
        BlockPos fixed = fixedChest();
        if (fixed != null && !this.unreachable.contains(fixed))
        {
            opened.put(fixed, simulated(Storage.open(this.level, fixed, requester())));
        }
        for (BlockPos chest : nearbyChests())
        {
            if (!opened.containsKey(chest) && !this.unreachable.contains(chest))
            {
                Container container = Storage.open(this.level, chest, requester());
                if (container != null)
                {
                    opened.put(chest, simulated(container));
                }
            }
        }
        this.noChests = opened.isEmpty() && this.unreachable.isEmpty();

        Map<BlockPos, ItemCategory> themes = new LinkedHashMap<>();
        opened.forEach((chest, container) ->
        {
            ItemCategory theme = themeOf(container);
            if (theme != null)
            {
                themes.put(chest, theme);
            }
        });

        Map<BlockPos, Map<Item, Integer>> plan = new LinkedHashMap<>();
        for (Item item : items)
        {
            ItemCategory category = ItemCategory.of(new ItemStack(item));
            Set<BlockPos> order = new LinkedHashSet<>();
            if (fixed != null && opened.containsKey(fixed))
            {
                order.add(fixed);
            }
            opened.forEach((chest, container) ->
            {
                if (Inv.count(container, item) > 0)
                {
                    order.add(chest);
                }
            });
            themes.forEach((chest, theme) ->
            {
                if (theme == category)
                {
                    order.add(chest);
                }
            });
            opened.forEach((chest, container) ->
            {
                if (Inv.isEmpty(container) && !themes.containsKey(chest))
                {
                    order.add(chest);
                }
            });
            order.addAll(opened.keySet());

            // Lo que no entre en ningun lado se queda con el (y lo avisa al final).
            for (ItemStack stack : toStore(item))
            {
                ItemStack rest = stack;
                for (BlockPos chest : order)
                {
                    Container container = opened.get(chest);
                    boolean wasEmpty = Inv.isEmpty(container);
                    ItemStack after = Inv.insert(container, rest);
                    int moved = rest.getCount() - after.getCount();
                    if (moved > 0)
                    {
                        plan.computeIfAbsent(chest, key -> new LinkedHashMap<>()).merge(item, moved, Integer::sum);
                        if (wasEmpty && !themes.containsKey(chest))
                        {
                            themes.put(chest, category);
                        }
                    }
                    rest = after;
                    if (rest.isEmpty())
                    {
                        break;
                    }
                }
            }
        }

        plan.forEach((chest, amounts) -> this.visits.add(new Visit(chest, amounts)));
    }

    // Copias de los stacks de eso que puede guardar, sin pasarse de lo que se queda.
    private List<ItemStack> toStore(Item item)
    {
        List<ItemStack> out = new ArrayList<>();
        int left = spare(item);
        for (ItemStack stack : Inv.contents(this.rocco.inventory()))
        {
            if (left <= 0)
            {
                break;
            }
            if (stack.is(item) && selected(stack))
            {
                int count = Math.min(left, stack.getCount());
                out.add(stack.copyWithCount(count));
                left -= count;
            }
        }
        return out;
    }

    private List<BlockPos> nearbyChests()
    {
        return Storage.findAround(this.level, this.rocco.blockPosition(), requester(), RoccoCommonConfig.WORK_SEARCH_RADIUS.get());
    }

    // Una copia del cofre para hacer la cuenta (respeta lo que no se puede meter, como una caja de
    // shulker adentro de otra).
    private static Container simulated(Container real)
    {
        net.minecraft.world.SimpleContainer copy = new net.minecraft.world.SimpleContainer(real.getContainerSize())
        {
            @Override
            public boolean canPlaceItem(int slot, ItemStack stack)
            {
                return real.canPlaceItem(slot, stack);
            }

            @Override
            public int getMaxStackSize()
            {
                return real.getMaxStackSize();
            }
        };
        for (int slot = 0; slot < real.getContainerSize(); slot++)
        {
            copy.setItem(slot, real.getItem(slot).copy());
        }
        return copy;
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
            // Lo rompieron mientras venia.
            this.unreachable.add(visit.chest());
            this.leftBehind = true;
            return;
        }

        Storage.animate(this.level, visit.chest(), true);
        BlockPos chest = visit.chest();
        this.rocco.schedule(16, () -> Storage.animate(this.level, chest, false));

        for (Map.Entry<Item, Integer> entry : visit.items().entrySet())
        {
            Item item = entry.getKey();
            int amount = Math.min(entry.getValue(), spare(item));
            if (amount <= 0)
            {
                continue;
            }
            for (ItemStack stack : Inv.take(this.rocco.inventory(), candidate -> candidate.is(item) && selected(candidate), amount))
            {
                int before = stack.getCount();
                ItemStack leftover = Inv.insert(container, stack);
                if (leftover.getCount() < before)
                {
                    this.stored.add(stack.copyWithCount(before - leftover.getCount()));
                }
                if (!leftover.isEmpty())
                {
                    // Alguien lleno el cofre mientras venia: lo que sobra lo reparte de nuevo.
                    this.leftBehind = true;
                    this.rocco.keep(leftover);
                }
            }
        }
    }

    // Cuanto de eso puede guardar sin tocar lo que se queda.
    private int spare(Item item)
    {
        int have = Inv.count(this.rocco.inventory(), candidate -> candidate.is(item) && selected(candidate));
        return have - this.reserve.getOrDefault(item, 0);
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
