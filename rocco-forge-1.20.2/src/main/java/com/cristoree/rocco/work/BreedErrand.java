package com.cristoree.rocco.work;

import com.cristoree.rocco.entity.RoccoEntity;
import com.cristoree.rocco.entity.RoccoTask;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// Cria animales: les da de comer de a pares lo que les gusta a cada uno (trigo a las vacas,
// zanahorias a los cerdos...) para que tengan crias. La comida sale de su inventario o de los
// cofres de alrededor. Cuidando la casa lo hace en silencio y sin pasarse del tope de animales.
public final class BreedErrand extends Errand
{
    private static final double RADIUS = 14.0D;
    private static final int MAX_PAIRS = 6;

    // Para decir que comida falta: las mas comunes, en el orden en que conviene probar.
    private static final Item[] FOODS = {
            Items.WHEAT, Items.CARROT, Items.POTATO, Items.BEETROOT, Items.WHEAT_SEEDS, Items.GOLDEN_CARROT,
            Items.GOLDEN_APPLE, Items.SWEET_BERRIES, Items.DANDELION, Items.SEAGRASS, Items.BAMBOO, Items.COD,
            Items.BEEF, Items.HAY_BLOCK, Items.CACTUS, Items.SLIME_BALL, Items.TROPICAL_FISH_BUCKET,
            Items.TORCHFLOWER_SEEDS, Items.CRIMSON_FUNGUS, Items.WARPED_FUNGUS, Items.POPPY};

    private final String wanted;
    private boolean queued;
    private boolean fetchTried;
    private int fed;
    // Cuidando la casa: cuantos de cada especie puede haber como mucho (crias incluidas), alrededor
    // de que punto, y sin hablar. 0 = sin tope.
    private int cap;
    @Nullable
    private net.minecraft.core.BlockPos center;
    private boolean quiet;

    public BreedErrand(RoccoEntity rocco, @Nullable Player requester, String wanted)
    {
        super(rocco, requester);
        this.wanted = Names.normalize(wanted == null ? "" : wanted);
    }

    // Solo los animales de granja, hasta cap de cada uno alrededor de center, sin hablar.
    public BreedErrand capped(int cap, net.minecraft.core.BlockPos center)
    {
        this.cap = cap;
        this.center = center.immutable();
        this.quiet = true;
        return this;
    }

    // Cuantas parejas se enamoraron.
    public int pairs()
    {
        return this.fed / 2;
    }

    @Override
    protected void say(String category, String... keyValues)
    {
        if (!this.quiet)
        {
            super.say(category, keyValues);
        }
    }

    @Override
    protected Status step()
    {
        if (this.queued)
        {
            if (this.fed < 2)
            {
                say("breed_failed");
                return Status.FAILED;
            }
            say("breed_done", "count", String.valueOf(this.fed / 2));
            return Status.DONE;
        }

        Map<EntityType<?>, List<Animal>> groups = eligibleByType();
        if (groups.isEmpty())
        {
            say(this.wanted.isEmpty() ? "breed_no_animals" : "breed_no_pair", "animal", this.wanted);
            return this.quiet ? Status.DONE : Status.FAILED;
        }

        int pairs = 0;
        String missingFood = null;
        for (List<Animal> animals : groups.values())
        {
            Animal sample = animals.get(0);
            int food = Inv.count(this.rocco.inventory(), sample::isFood);
            if (food < 2)
            {
                if (!this.fetchTried)
                {
                    this.fetchTried = true;
                    FetchErrand fetch = FetchErrand.plan(this.rocco, requester(), sample::isFood, "comida",
                            Math.min(animals.size(), MAX_PAIRS * 2), null, false, true);
                    if (fetch != null)
                    {
                        delegate(fetch);
                        return Status.RUNNING;
                    }
                }
                missingFood = foodName(sample);
                continue;
            }

            int count = Math.min(animals.size() / 2, Math.min(food / 2, MAX_PAIRS - pairs));
            if (this.cap > 0)
            {
                // Cada pareja da una cria: no mas de las que entran hasta el tope.
                count = Math.min(count, Math.max(0, this.cap - population(sample.getType())));
            }
            for (int i = 0; i < count * 2; i++)
            {
                Animal animal = animals.get(i);
                ItemStack shown = Inv.contents(this.rocco.inventory()).stream().filter(animal::isFood).findFirst()
                        .map(stack -> stack.copyWithCount(1)).orElse(ItemStack.EMPTY);
                work(new RoccoEntity.Job(RoccoTask.INTERACTING, animal.blockPosition(), shown, animal.getId(),
                        level -> feed(animal), null, 0, 2.6D));
            }
            pairs += count;
            if (pairs >= MAX_PAIRS)
            {
                break;
            }
        }

        if (pairs == 0)
        {
            say("breed_need_food", "food", missingFood == null ? "su comida" : missingFood);
            return this.quiet ? Status.DONE : Status.FAILED;
        }
        this.queued = true;
        return Status.RUNNING;
    }

    @Override
    protected boolean childDone(Errand child, boolean success)
    {
        return true;
    }

    private void feed(Animal animal)
    {
        if (!animal.isAlive() || !animal.canFallInLove() || animal.getAge() != 0 || animal.distanceTo(this.rocco) > 5.0F)
        {
            return;
        }
        List<ItemStack> taken = Inv.take(this.rocco.inventory(), animal::isFood, 1);
        if (taken.isEmpty())
        {
            return;
        }
        animal.setInLove(null);
        this.fed++;
        this.level.playSound(null, animal.blockPosition(), SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 0.6F, 1.0F);
    }

    // Cuantos hay de esa especie (crias incluidas) alrededor de la casa.
    private int population(EntityType<?> type)
    {
        return this.level.getEntitiesOfClass(Animal.class, area(), animal -> animal.isAlive() && animal.getType() == type).size();
    }

    private net.minecraft.world.phys.AABB area()
    {
        return this.center == null ? this.rocco.getBoundingBox().inflate(RADIUS)
                : new net.minecraft.world.phys.AABB(this.center).inflate(RanchErrand.RADIUS, 8.0D, RanchErrand.RADIUS);
    }

    // Adultos, sin cria reciente, que todavia no esten enamorados, agrupados por especie.
    private Map<EntityType<?>, List<Animal>> eligibleByType()
    {
        Map<EntityType<?>, List<Animal>> groups = new LinkedHashMap<>();
        List<Animal> nearby = this.level.getEntitiesOfClass(Animal.class, area(),
                animal -> animal.isAlive() && !animal.isBaby() && animal.getAge() == 0 && animal.canFallInLove() && matches(animal)
                        && (this.cap <= 0 || RanchErrand.isFarmAnimal(animal)));
        nearby.sort(java.util.Comparator.comparingDouble(animal -> animal.distanceToSqr(this.rocco)));

        for (Animal animal : nearby)
        {
            groups.computeIfAbsent(animal.getType(), key -> new ArrayList<>()).add(animal);
        }
        groups.values().removeIf(list -> list.size() < 2);
        return groups;
    }

    private boolean matches(Animal animal)
    {
        if (this.wanted.isEmpty() || this.wanted.matches(".*\\b(animales|todos|todo|bichos)\\b.*"))
        {
            return true;
        }
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(animal.getType());
        String path = id == null ? "" : id.getPath();
        for (String word : this.wanted.split(" "))
        {
            String english = Names.entityFor(word);
            if ((english != null && english.equals(path)) || path.equals(word) || path.equals(Names.singular(word)))
            {
                return true;
            }
        }
        String typeName = Names.normalize(animal.getType().getDescription().getString());
        return !typeName.isEmpty() && this.wanted.contains(Names.singular(typeName));
    }

    private static String foodName(Animal animal)
    {
        for (Item food : FOODS)
        {
            if (animal.isFood(new ItemStack(food)))
            {
                return Names.name(food).toLowerCase(Locale.ROOT);
            }
        }
        return "su comida";
    }

    @Override
    public String describe()
    {
        return "criando animales";
    }
}
