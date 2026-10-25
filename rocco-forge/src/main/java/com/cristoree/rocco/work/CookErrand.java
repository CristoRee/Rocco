package com.cristoree.rocco.work;

import com.cristoree.rocco.config.RoccoCommonConfig;
import com.cristoree.rocco.entity.RoccoEntity;
import com.cristoree.rocco.entity.RoccoTask;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlastFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SmokerBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeHooks;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

// Cocina en un horno de verdad: elige el mejor que haya (ahumador para comida, alto horno para
// minerales), le pone lo que hay que cocinar y el combustible justo, espera, y lo saca.
// Si no hay horno pero tiene uno (o con que hacerlo), lo pone. Mientras se cocina puede
// seguir al jugador; cuando esta listo vuelve a buscarlo si no quedo muy lejos.
public final class CookErrand extends Errand
{
    private enum Phase { SELECT, FURNACE, LOAD, COOKING, COLLECT, GIVE, END }

    private static final int SLOT_INPUT = 0;
    private static final int SLOT_FUEL = 1;
    private static final int SLOT_RESULT = 2;

    @Nullable
    private final Names.Query query;
    private final int wanted;

    private Phase phase = Phase.SELECT;
    private Item input = Items.AIR;
    private int amount;
    @Nullable
    private BlockPos furnace;
    private int cookTime = 200;
    private boolean fetchTried;
    private boolean fuelFetchTried;
    private boolean placeTried;
    private boolean craftTried;
    private int loaded;
    private int stalled;
    private int collected;
    private Item cooked = Items.AIR;

    public CookErrand(RoccoEntity rocco, @Nullable Player requester, @Nullable Names.Query query, int wanted)
    {
        super(rocco, requester);
        this.query = query != null && query.found() ? query : null;
        this.wanted = wanted;
    }

    @Override
    protected Status step()
    {
        return switch (this.phase)
        {
            case SELECT -> select();
            case FURNACE -> furnace();
            case LOAD -> load();
            case COOKING -> cooking();
            case COLLECT -> collect();
            case GIVE -> give();
            case END -> Status.DONE;
        };
    }

    @Override
    protected boolean childDone(Errand child, boolean success)
    {
        return true;
    }

    // ---------- que cocinar ----------

    private Status select()
    {
        Predicate<ItemStack> filter = this.query != null
                ? this.query.filter().and(this::cookable)
                : stack -> cookable(stack) && Recipes.cookedResult(this.level, stack).isEdible();

        ItemStack first = Inv.contents(this.rocco.inventory()).stream().filter(filter).findFirst().orElse(ItemStack.EMPTY);
        if (first.isEmpty())
        {
            if (this.query != null && !this.fetchTried)
            {
                this.fetchTried = true;
                FetchErrand fetch = FetchErrand.plan(this.rocco, requester(), filter, Names.name(this.query.first()),
                        this.wanted > 0 ? this.wanted : 64, null, false, true);
                if (fetch != null)
                {
                    delegate(fetch);
                    return Status.RUNNING;
                }
            }

            if (this.query != null && !Recipes.cookedResult(this.level, new ItemStack(this.query.first())).isEmpty())
            {
                say("cook_dont_have", "item", Names.name(this.query.first()));
            }
            else if (this.query != null)
            {
                say("cook_not_cookable", "item", Names.name(this.query.first()));
            }
            else
            {
                say("cook_nothing");
            }
            return Status.FAILED;
        }

        this.input = first.getItem();
        int have = Inv.count(this.rocco.inventory(), this.input);
        this.amount = Math.min(64, this.wanted > 0 ? Math.min(this.wanted, have) : have);
        this.cooked = Recipes.cookedResult(this.level, first).getItem();
        this.phase = Phase.FURNACE;
        return Status.RUNNING;
    }

    private boolean cookable(ItemStack stack)
    {
        return !stack.isEmpty() && !Recipes.cookedResult(this.level, stack).isEmpty();
    }

    // ---------- donde ----------

    private Status furnace()
    {
        ItemStack probe = new ItemStack(this.input);
        boolean food = this.cooked.isEdible();

        List<BlockPos> furnaces = Storage.findBlockEntities(this.level, this.rocco.blockPosition(),
                RoccoCommonConfig.WORK_SEARCH_RADIUS.get(), entity -> accepts(entity, probe));

        // Mas rapido primero: ahumador para comida, alto horno para minerales; despues el comun.
        Optional<BlockPos> best = furnaces.stream().min(Comparator.comparingInt(pos ->
        {
            BlockEntity entity = this.level.getBlockEntity(pos);
            boolean fast = entity instanceof SmokerBlockEntity || entity instanceof BlastFurnaceBlockEntity;
            return fast ? 0 : 1;
        }));

        if (best.isPresent())
        {
            this.furnace = best.get();
            BlockEntity entity = this.level.getBlockEntity(this.furnace);
            this.cookTime = cookTimeFor(entity, probe);
            this.phase = Phase.LOAD;
            return Status.RUNNING;
        }

        Item own = food && Inv.count(this.rocco.inventory(), Items.SMOKER) > 0 ? Items.SMOKER : Items.FURNACE;
        if (!this.placeTried && Inv.count(this.rocco.inventory(), own) > 0)
        {
            this.placeTried = true;
            say("cook_placing_furnace");
            delegate(new PlaceErrand(this.rocco, requester(), own, null));
            return Status.RUNNING;
        }
        if (!this.craftTried)
        {
            this.craftTried = true;
            this.placeTried = false;
            say("cook_making_furnace");
            delegate(new CraftErrand(this.rocco, requester(), List.of(Items.FURNACE), 1, true, true));
            return Status.RUNNING;
        }

        say("cook_no_furnace");
        return Status.FAILED;
    }

    private boolean accepts(BlockEntity entity, ItemStack probe)
    {
        if (!(entity instanceof AbstractFurnaceBlockEntity furnace))
        {
            return false;
        }
        ItemStack current = furnace.getItem(SLOT_INPUT);
        if (!current.isEmpty() && (!ItemStack.isSameItemSameTags(current, probe) || current.getCount() >= current.getMaxStackSize()))
        {
            return false;
        }
        // El resultado tiene que poder juntarse con lo que haya salido antes.
        ItemStack result = furnace.getItem(SLOT_RESULT);
        if (!result.isEmpty() && !result.is(this.cooked))
        {
            return false;
        }
        return Recipes.cooking(this.level, typeOf(entity), probe).isPresent();
    }

    private static RecipeType<? extends AbstractCookingRecipe> typeOf(BlockEntity entity)
    {
        if (entity instanceof SmokerBlockEntity)
        {
            return RecipeType.SMOKING;
        }
        if (entity instanceof BlastFurnaceBlockEntity)
        {
            return RecipeType.BLASTING;
        }
        return RecipeType.SMELTING;
    }

    private int cookTimeFor(@Nullable BlockEntity entity, ItemStack probe)
    {
        if (entity == null)
        {
            return 200;
        }
        return Recipes.cooking(this.level, typeOf(entity), probe).map(AbstractCookingRecipe::getCookingTime).orElse(200);
    }

    // ---------- cargar ----------

    private Status load()
    {
        AbstractFurnaceBlockEntity entity = furnaceEntity();
        if (entity == null)
        {
            this.phase = Phase.FURNACE;
            return Status.RUNNING;
        }

        int ticksNeeded = this.amount * this.cookTime - fuelTicks(entity.getItem(SLOT_FUEL));
        boolean burning = this.level.getBlockState(this.furnace).getValue(AbstractFurnaceBlock.LIT);

        if (ticksNeeded > 0 && pickFuel(entity) == null && !burning && entity.getItem(SLOT_FUEL).isEmpty())
        {
            if (!this.fuelFetchTried)
            {
                this.fuelFetchTried = true;
                FetchErrand fetch = FetchErrand.plan(this.rocco, requester(), this::goodFuel, "combustible",
                        Math.max(1, (this.amount + 7) / 8), null, false, true);
                if (fetch != null)
                {
                    delegate(fetch);
                    return Status.RUNNING;
                }
            }
            say("cook_no_fuel");
            return Status.FAILED;
        }

        BlockPos where = this.furnace;
        work(RoccoTask.INTERACTING, where, new ItemStack(this.input), level -> fill(where));
        this.phase = Phase.COOKING;
        return Status.RUNNING;
    }

    private void fill(BlockPos where)
    {
        AbstractFurnaceBlockEntity entity = furnaceEntity();
        if (entity == null)
        {
            return;
        }

        // Lo que se cocina.
        ItemStack current = entity.getItem(SLOT_INPUT);
        int room = current.isEmpty() ? new ItemStack(this.input).getMaxStackSize() : current.getMaxStackSize() - current.getCount();
        int move = Math.min(room, this.amount);
        List<ItemStack> taken = Inv.take(this.rocco.inventory(), stack -> stack.is(this.input), move);
        int moved = 0;
        for (ItemStack stack : taken)
        {
            ItemStack inSlot = entity.getItem(SLOT_INPUT);
            if (inSlot.isEmpty())
            {
                entity.setItem(SLOT_INPUT, stack);
            }
            else
            {
                inSlot.grow(stack.getCount());
            }
            moved += stack.getCount();
        }
        this.loaded = moved;

        // El combustible justo para lo que puso (mas lo que ya tuviera).
        int ticksNeeded = moved * this.cookTime - fuelTicks(entity.getItem(SLOT_FUEL));
        while (ticksNeeded > 0)
        {
            ItemStack picked = pickFuel(entity);
            if (picked == null)
            {
                break;
            }
            // Copia: el stack del inventario se va vaciando mientras se saca.
            ItemStack fuel = picked.copy();
            int burn = ForgeHooks.getBurnTime(fuel, RecipeType.SMELTING);
            int units = Math.max(1, (ticksNeeded + burn - 1) / burn);
            ItemStack inSlot = entity.getItem(SLOT_FUEL);
            int space = inSlot.isEmpty() ? fuel.getMaxStackSize() : inSlot.getMaxStackSize() - inSlot.getCount();
            units = Math.min(units, space);
            if (units <= 0)
            {
                break;
            }

            List<ItemStack> fuelTaken = Inv.take(this.rocco.inventory(), stack -> ItemStack.isSameItemSameTags(stack, fuel), units);
            int added = 0;
            for (ItemStack stack : fuelTaken)
            {
                ItemStack slot = entity.getItem(SLOT_FUEL);
                if (slot.isEmpty())
                {
                    entity.setItem(SLOT_FUEL, stack);
                }
                else
                {
                    slot.grow(stack.getCount());
                }
                added += stack.getCount();
            }
            if (added == 0)
            {
                break;
            }
            ticksNeeded -= added * burn;
        }

        entity.setChanged();
        this.level.playSound(null, where, net.minecraft.sounds.SoundEvents.STONE_HIT,
                net.minecraft.sounds.SoundSource.BLOCKS, 0.6F, 1.0F);
    }

    private int fuelTicks(ItemStack fuel)
    {
        return fuel.isEmpty() ? 0 : ForgeHooks.getBurnTime(fuel, RecipeType.SMELTING) * fuel.getCount();
    }

    // Carbon primero; nunca lo que se esta cocinando ni herramientas.
    @Nullable
    private ItemStack pickFuel(AbstractFurnaceBlockEntity entity)
    {
        ItemStack inSlot = entity.getItem(SLOT_FUEL);
        List<ItemStack> options = new ArrayList<>();
        for (ItemStack stack : Inv.contents(this.rocco.inventory()))
        {
            if (!goodFuel(stack))
            {
                continue;
            }
            // Solo se puede sumar al mismo combustible que ya tiene puesto.
            if (!inSlot.isEmpty() && !ItemStack.isSameItemSameTags(inSlot, stack))
            {
                continue;
            }
            options.add(stack);
        }

        return options.stream()
                .min(Comparator.comparingInt((ItemStack stack) -> preference(stack))
                        .thenComparingInt(stack -> -ForgeHooks.getBurnTime(stack, RecipeType.SMELTING)))
                .orElse(null);
    }

    private boolean goodFuel(ItemStack stack)
    {
        return !stack.isEmpty()
                && !stack.is(this.input)
                && !Tools.isTool(stack)
                && !stack.hasTag()
                && stack.getItem().getCraftingRemainingItem(stack).isEmpty()
                && ForgeHooks.getBurnTime(stack, RecipeType.SMELTING) > 0;
    }

    private static int preference(ItemStack stack)
    {
        if (stack.is(Items.COAL) || stack.is(Items.CHARCOAL))
        {
            return 0;
        }
        if (stack.is(Items.COAL_BLOCK) || stack.is(Items.DRIED_KELP_BLOCK) || stack.is(Items.BLAZE_ROD))
        {
            return 1;
        }
        return 2;
    }

    // ---------- esperar ----------

    private Status cooking()
    {
        if (jobFailed())
        {
            say("cook_cant_reach");
            return Status.FAILED;
        }
        if (this.loaded == 0)
        {
            say("cook_furnace_busy");
            return Status.FAILED;
        }
        if (this.age % 20 != 0)
        {
            return Status.RUNNING;
        }

        AbstractFurnaceBlockEntity entity = furnaceEntity();
        if (entity == null)
        {
            say("cook_furnace_gone");
            return Status.FAILED;
        }

        ItemStack input = entity.getItem(SLOT_INPUT);
        ItemStack output = entity.getItem(SLOT_RESULT);
        boolean done = (input.isEmpty() || !input.is(this.input)) && !output.isEmpty();
        boolean lit = this.level.getBlockState(this.furnace).getValue(AbstractFurnaceBlock.LIT);

        if (!done && !lit && entity.getItem(SLOT_FUEL).isEmpty())
        {
            // Se apago con cosas sin cocinar: se quedo sin combustible.
            if (++this.stalled >= 3)
            {
                say("cook_out_of_fuel");
                this.phase = output.isEmpty() ? Phase.END : Phase.COLLECT;
                return output.isEmpty() ? Status.FAILED : Status.RUNNING;
            }
        }
        else
        {
            this.stalled = 0;
        }

        if (!done)
        {
            return Status.RUNNING;
        }

        // Si se alejo siguiendo al jugador, no vuelve desde la otra punta: avisa donde quedo.
        if (this.rocco.distanceToSqr(Vec3.atCenterOf(this.furnace)) > 32 * 32)
        {
            say("cook_ready_far", "pos", this.furnace.getX() + ", " + this.furnace.getY() + ", " + this.furnace.getZ(),
                    "item", Names.name(this.cooked));
            return Status.DONE;
        }
        this.phase = Phase.COLLECT;
        return Status.RUNNING;
    }

    @Override
    protected boolean waitingPassively()
    {
        return this.phase == Phase.COOKING && this.loaded > 0;
    }

    // ---------- sacar ----------

    private Status collect()
    {
        BlockPos where = this.furnace;
        work(RoccoTask.INTERACTING, where, ItemStack.EMPTY, level ->
        {
            AbstractFurnaceBlockEntity entity = furnaceEntity();
            if (entity == null)
            {
                return;
            }
            ItemStack output = entity.removeItemNoUpdate(SLOT_RESULT);
            if (!output.isEmpty())
            {
                this.collected = output.getCount();
                this.cooked = output.getItem();
                this.rocco.showInHand(output);
                this.rocco.keep(output);
            }
            // La experiencia del horno sale ahi mismo, para el jugador.
            entity.getRecipesToAwardAndPopExperience(level, Vec3.atCenterOf(where));
            entity.setChanged();
        });
        this.phase = Phase.GIVE;
        return Status.RUNNING;
    }

    private Status give()
    {
        if (this.collected == 0)
        {
            if (jobFailed())
            {
                say("cook_cant_reach");
            }
            return Status.FAILED;
        }

        say("cook_done", "items", this.collected + " de " + Names.name(this.cooked));
        this.phase = Phase.END;
        if (RoccoCommonConfig.WORK_GIVE_RESULTS.get() && requester() != null)
        {
            Item result = this.cooked;
            delegate(new GiveErrand(this.rocco, requester(), stack -> stack.is(result), this.collected, false));
            return Status.RUNNING;
        }
        return Status.DONE;
    }

    @Nullable
    private AbstractFurnaceBlockEntity furnaceEntity()
    {
        if (this.furnace == null || !this.level.isLoaded(this.furnace))
        {
            return null;
        }
        return this.level.getBlockEntity(this.furnace) instanceof AbstractFurnaceBlockEntity entity ? entity : null;
    }

    @Override
    public String describe()
    {
        if (this.phase == Phase.COOKING)
        {
            return "esperando que se cocine " + Names.name(this.input);
        }
        return "cocinando " + (this.input == Items.AIR ? "algo" : Names.name(this.input));
    }
}
