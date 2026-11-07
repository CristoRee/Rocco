package com.cristoree.rocco.work;

import com.cristoree.rocco.config.RoccoCommonConfig;
import com.cristoree.rocco.entity.RoccoEntity;
import com.cristoree.rocco.entity.RoccoTask;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Una vuelta por los animales de la casa, como haria un granjero: esquila las ovejas (si no tiene
// tijeras se las hace), les da de comer de a pares para que tengan crias sin pasarse del tope,
// saca de a poco los adultos que sobran, junta lo que quedo en el piso (lana, huevos, plumas,
// carne, cuero) y lo guarda en los cofres. Solo animales de granja: vacas, ovejas, cerdos,
// gallinas, conejos y cabras. Nunca toca los que tienen nombre, estan atados, montados o son
// de alguien.
public final class RanchErrand extends Errand
{
    public enum Chore { SHEAR, BREED, CULL, COLLECT, STORE }

    public static final Set<Chore> ALL = EnumSet.allOf(Chore.class);

    // Hasta donde llega "la casa" desde el centro (los corrales suelen quedar cerca).
    public static final double RADIUS = 16.0D;
    // Esquilar es rapido; sacar animales va despacito, para que no parezca una masacre.
    private static final int MAX_SHEAR = 10;
    private static final int MAX_CULL = 2;
    private static final int CULL_GIVE_UP_TICKS = 20 * 20;

    // Lo que dan los animales: se junta del piso y se guarda.
    private static final Set<Item> PRODUCTS = Set.of(Items.EGG, Items.FEATHER, Items.LEATHER, Items.BEEF, Items.COOKED_BEEF,
            Items.PORKCHOP, Items.COOKED_PORKCHOP, Items.MUTTON, Items.COOKED_MUTTON, Items.CHICKEN, Items.COOKED_CHICKEN,
            Items.RABBIT, Items.COOKED_RABBIT, Items.RABBIT_HIDE, Items.RABBIT_FOOT, Items.GOAT_HORN);

    // Lo que comen: cuidando huerta y animales juntos, se queda con un poco de la cosecha para ellos.
    public static final List<Item> FEED = List.of(Items.WHEAT, Items.CARROT, Items.WHEAT_SEEDS);

    private enum Phase { SHEARS, SHEAR, BREED, CULL, CULL_WAIT, COLLECT, STORE, END }

    private final BlockPos center;
    private final Set<Chore> chores;
    private final boolean warnCull;
    // Pedido suelto ("esquila las ovejas"): al final cuenta como le fue.
    private boolean talk;

    private Phase phase = Phase.SHEARS;
    private boolean triedShears;
    private boolean missingShears;
    private final Deque<Animal> victims = new ArrayDeque<>();
    @Nullable
    private Animal hunting;
    private int huntTicks;
    private boolean warnedCull;
    @Nullable
    private BreedErrand breeding;
    @Nullable
    private PickupErrand pickup;

    private int sheared;
    private int pairs;
    private int culled;
    private int collected;

    // warnCull: la primera vez que va a sacar animales lo avisa (despues ya lo sabes).
    public RanchErrand(RoccoEntity rocco, @Nullable Player requester, BlockPos center, Set<Chore> chores, boolean warnCull)
    {
        super(rocco, requester);
        this.center = center.immutable();
        this.chores = chores.isEmpty() ? EnumSet.noneOf(Chore.class) : EnumSet.copyOf(chores);
        this.warnCull = warnCull;
    }

    // "Esquila las ovejas": esquila, junta la lana y se la queda (te la da si se la pedis).
    public static RanchErrand shearing(RoccoEntity rocco, @Nullable Player requester, BlockPos center)
    {
        RanchErrand errand = new RanchErrand(rocco, requester, center, EnumSet.of(Chore.SHEAR, Chore.COLLECT), false);
        errand.talk = true;
        return errand;
    }

    public static boolean isFarmAnimal(Animal animal)
    {
        return animal instanceof Cow || animal instanceof Sheep || animal instanceof Pig || animal instanceof Chicken
                || animal instanceof Rabbit || animal instanceof Goat;
    }

    // "vacas", "ovejas": para decirlo (el nombre del juego viene en el idioma del servidor).
    private static String plural(Animal animal)
    {
        if (animal instanceof Sheep) return "ovejas";
        if (animal instanceof Cow) return "vacas";
        if (animal instanceof Pig) return "cerdos";
        if (animal instanceof Chicken) return "gallinas";
        if (animal instanceof Rabbit) return "conejos";
        if (animal instanceof Goat) return "cabras";
        return "animales";
    }

    public static boolean isProduct(ItemStack stack)
    {
        return stack.is(ItemTags.WOOL) || PRODUCTS.contains(stack.getItem());
    }

    // Nunca: con nombre, atado, montado, llevando a alguien, o de alguien (un caballo domado no es
    // de granja igual, pero por las dudas).
    private static boolean untouchable(Animal animal)
    {
        return animal.hasCustomName() || animal.isLeashed() || animal.isVehicle() || animal.isPassenger()
                || (animal instanceof TamableAnimal tamable && tamable.isTame());
    }

    public int sheared()
    {
        return this.sheared;
    }

    public int pairs()
    {
        return this.pairs;
    }

    public int culled()
    {
        return this.culled;
    }

    public int collected()
    {
        return this.collected;
    }

    // Habia ovejas para esquilar y no tenia (ni pudo hacerse) tijeras.
    public boolean missingShears()
    {
        return this.missingShears;
    }

    public boolean warnedCull()
    {
        return this.warnedCull;
    }

    @Override
    public boolean allowsFighting()
    {
        return true;
    }

    @Override
    protected Status step()
    {
        return switch (this.phase)
        {
            case SHEARS -> shears();
            case SHEAR -> shear();
            case BREED -> breed();
            case CULL -> cull();
            case CULL_WAIT -> cullWait();
            case COLLECT -> collect();
            case STORE -> store();
            case END -> finish();
        };
    }

    private Status finish()
    {
        if (!this.talk)
        {
            return Status.DONE;
        }
        if (this.sheared > 0)
        {
            say("shear_done", "count", String.valueOf(this.sheared));
            return Status.DONE;
        }
        say(this.missingShears ? "home_need_shears" : "shear_none");
        return Status.FAILED;
    }

    // ---------- esquilar ----------

    private List<Sheep> woolly()
    {
        return this.level.getEntitiesOfClass(Sheep.class, area(),
                        sheep -> sheep.isAlive() && sheep.readyForShearing() && !untouchable(sheep))
                .stream()
                .sorted(Comparator.comparingDouble(sheep -> sheep.distanceToSqr(this.rocco)))
                .toList();
    }

    // Sin tijeras se las hace: con el hierro que lleva, o con el de los cofres si se lo permitiste.
    private Status shears()
    {
        this.phase = Phase.SHEAR;
        if (!this.chores.contains(Chore.SHEAR) || (!RoccoCommonConfig.HOME_SHEAR.get() && !this.talk) || shearsStack() != null
                || this.triedShears || woolly().isEmpty())
        {
            return Status.RUNNING;
        }
        this.triedShears = true;
        boolean ownIron = Inv.count(this.rocco.inventory(), Items.IRON_INGOT) >= 2;
        // Si se lo pediste vos ("esquila las ovejas"), puede usar el hierro de los cofres.
        if (ownIron || ((RoccoCommonConfig.HOME_CHEST_MATERIALS.get() || this.talk) && ironInChests() >= 2))
        {
            // Vuelve a este paso para ver si salio.
            this.phase = Phase.SHEARS;
            CraftErrand craft = new CraftErrand(this.rocco, requester(), List.of(Items.SHEARS), 1, true, true);
            delegate(this.talk ? craft : craft.silently());
            return Status.RUNNING;
        }
        this.missingShears = true;
        return Status.RUNNING;
    }

    private Status shear()
    {
        this.phase = Phase.BREED;
        ItemStack shears = shearsStack();
        if (!this.chores.contains(Chore.SHEAR) || (!RoccoCommonConfig.HOME_SHEAR.get() && !this.talk))
        {
            return Status.RUNNING;
        }
        List<Sheep> sheep = woolly();
        if (shears == null)
        {
            this.missingShears = !sheep.isEmpty();
            return Status.RUNNING;
        }
        for (Sheep one : sheep.subList(0, Math.min(MAX_SHEAR, sheep.size())))
        {
            work(new RoccoEntity.Job(RoccoTask.INTERACTING, one.blockPosition(), shears.copyWithCount(1), one.getId(),
                    level -> shearOne(one), null, 0, 2.6D));
        }
        return Status.RUNNING;
    }

    private void shearOne(Sheep sheep)
    {
        ItemStack shears = shearsStack();
        if (shears == null || !sheep.isAlive() || !sheep.readyForShearing() || sheep.distanceTo(this.rocco) > 5.0F)
        {
            return;
        }
        // Como un jugador: la lana salta al piso (despues la junta) y las tijeras se gastan.
        sheep.shear(SoundSource.NEUTRAL);
        shears.hurtAndBreak(1, this.rocco, rocco -> { });
        this.sheared++;
    }

    @Nullable
    private ItemStack shearsStack()
    {
        Container inventory = this.rocco.inventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++)
        {
            ItemStack stack = inventory.getItem(slot);
            if (stack.getItem() instanceof ShearsItem)
            {
                return stack;
            }
        }
        return null;
    }

    private int ironInChests()
    {
        int total = 0;
        ServerPlayer player = requester();
        for (BlockPos chest : Storage.findWide(this.level, this.rocco.blockPosition(), player, RoccoCommonConfig.WORK_SEARCH_RADIUS.get()))
        {
            Container container = Storage.open(this.level, chest, player);
            if (container != null)
            {
                total += Inv.count(container, Items.IRON_INGOT);
            }
        }
        return total;
    }

    // ---------- crias ----------

    private Status breed()
    {
        this.phase = Phase.CULL;
        if (this.chores.contains(Chore.BREED))
        {
            this.breeding = new BreedErrand(this.rocco, requester(), "").capped(RoccoCommonConfig.HOME_ANIMAL_CAP.get(), this.center);
            delegate(this.breeding);
        }
        return Status.RUNNING;
    }

    // ---------- los que sobran ----------

    private Status cull()
    {
        this.phase = Phase.COLLECT;
        if (!this.chores.contains(Chore.CULL) || !RoccoCommonConfig.HOME_CULL.get())
        {
            return Status.RUNNING;
        }
        int cap = RoccoCommonConfig.HOME_ANIMAL_CAP.get();
        Map<EntityType<?>, List<Animal>> adults = new LinkedHashMap<>();
        for (Animal animal : this.level.getEntitiesOfClass(Animal.class, area(),
                animal -> animal.isAlive() && !animal.isBaby() && isFarmAnimal(animal)))
        {
            adults.computeIfAbsent(animal.getType(), key -> new ArrayList<>()).add(animal);
        }

        String extra = null;
        for (List<Animal> group : adults.values())
        {
            int over = group.size() - cap;
            if (over <= 0)
            {
                continue;
            }
            // Los mas lejos del centro primero: los del medio del corral quedan.
            List<Animal> pick = group.stream().filter(animal -> !untouchable(animal))
                    .sorted(Comparator.comparingDouble((Animal animal) -> animal.distanceToSqr(this.center.getCenter())).reversed())
                    .toList();
            for (Animal animal : pick.subList(0, Math.min(over, pick.size())))
            {
                if (this.victims.size() < MAX_CULL)
                {
                    this.victims.add(animal);
                    if (extra == null)
                    {
                        extra = plural(animal);
                    }
                }
            }
        }
        if (!this.victims.isEmpty())
        {
            if (this.warnCull)
            {
                this.warnedCull = true;
                say("home_cull_notice", "animal", extra == null ? "animales" : extra, "cap", String.valueOf(cap));
            }
            this.phase = Phase.CULL_WAIT;
        }
        return Status.RUNNING;
    }

    // Lo saca con el sistema de pelea (camina, le pega con su arma) y espera a que termine.
    private Status cullWait()
    {
        if (this.hunting != null)
        {
            if (!this.hunting.isAlive())
            {
                this.culled++;
                this.hunting = null;
            }
            else if (++this.huntTicks > CULL_GIVE_UP_TICKS || !this.rocco.hasOrderedTarget())
            {
                // No llego (se escapo, quedo del otro lado de un cerco): lo deja para otra vuelta.
                this.rocco.stopHunting();
                this.hunting = null;
            }
            else
            {
                // Si se le escapa un poco, la sigue (de lejos la pelea no la persigue).
                if (this.huntTicks % 20 == 0 && this.rocco.distanceTo(this.hunting) > 8.0F)
                {
                    this.rocco.getNavigation().moveTo(this.hunting, 1.1D);
                }
                return Status.RUNNING;
            }
        }
        Animal next = this.victims.poll();
        while (next != null && (!next.isAlive() || untouchable(next)))
        {
            next = this.victims.poll();
        }
        if (next == null)
        {
            this.phase = Phase.COLLECT;
            return Status.RUNNING;
        }
        // Primero se acerca: de lejos la pelea no arranca.
        if (this.rocco.distanceTo(next) > 10.0F)
        {
            this.victims.addFirst(next);
            if (++this.huntTicks > CULL_GIVE_UP_TICKS)
            {
                this.victims.poll();
                this.huntTicks = 0;
            }
            else if (this.huntTicks % 10 == 1)
            {
                this.rocco.getNavigation().moveTo(next, 1.0D);
            }
            return Status.RUNNING;
        }
        if (this.rocco.huntForWork(next))
        {
            this.hunting = next;
            this.huntTicks = 0;
        }
        return Status.RUNNING;
    }

    // ---------- juntar y guardar ----------

    private Status collect()
    {
        this.phase = Phase.STORE;
        if (this.chores.contains(Chore.COLLECT))
        {
            this.pickup = new PickupErrand(this.rocco, requester()).around(this.center, RADIUS, RanchErrand::isProduct);
            delegate(this.pickup);
        }
        return Status.RUNNING;
    }

    private Status store()
    {
        this.phase = Phase.END;
        if (this.chores.contains(Chore.STORE) && Inv.count(this.rocco.inventory(), RanchErrand::isProduct) > 0)
        {
            delegate(new DepositErrand(this.rocco, requester(), DepositErrand.Mode.MATCHING, RanchErrand::isProduct, null, true));
        }
        return Status.RUNNING;
    }

    // Que un paso no salga no corta la vuelta: sigue con lo demas.
    @Override
    protected boolean childDone(Errand child, boolean success)
    {
        if (child == this.breeding)
        {
            this.pairs += this.breeding.pairs();
            this.breeding = null;
        }
        else if (child == this.pickup)
        {
            this.collected += this.pickup.picked();
            this.pickup = null;
        }
        return true;
    }

    @Override
    public void cancelled()
    {
        super.cancelled();
        if (this.hunting != null)
        {
            this.rocco.stopHunting();
            this.hunting = null;
        }
    }

    private AABB area()
    {
        return new AABB(this.center).inflate(RADIUS, 8.0D, RADIUS);
    }

    @Override
    public String describe()
    {
        return "cuidando los animales";
    }
}
